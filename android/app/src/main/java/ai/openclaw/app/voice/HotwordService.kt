package ai.openclaw.app.voice

import ai.openclaw.app.WakeEngine
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.openclaw.app.MainActivity
import ai.openclaw.app.R
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.k2fsa.sherpa.onnx.openClawKwsKeywordsAssetPath
import com.k2fsa.sherpa.onnx.openClawKwsModelConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream

class HotwordService : Service() {
  companion object {
    const val actionWakeTriggered = "ai.openclaw.app.action.WAKE_TRIGGERED"
    /** Broadcast extras: recognized / trigger label and wall-clock time of the hit. */
    const val extraWakeTriggerLabel = "ai.openclaw.extra.WAKE_TRIGGER_LABEL"
    const val extraWakeTimeEpochMs = "ai.openclaw.extra.WAKE_TIME_EPOCH_MS"
    private const val channelId = "openclaw_hotword"
    private const val notificationId = 4001
    private const val sampleRate = 16_000.0f
    private const val extraWords = "extra_words"
    private const val extraWakeEngine = "extra_wake_engine"
    private val requiredModelFiles =
      listOf(
        "am/final.mdl",
        "conf/model.conf",
        "graph/HCLr.fst",
        "graph/Gr.fst",
        "graph/phones/word_boundary.int",
      )
    private val requiredSherpaKwsFiles =
      listOf(
        "sherpa-kws/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
        "sherpa-kws/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
        "sherpa-kws/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/joiner-epoch-12-avg-2-chunk-16-left-64.onnx",
        "sherpa-kws/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/tokens.txt",
        "sherpa-kws/sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01/keywords.txt",
      )

    fun start(context: Context, words: List<String>, engine: WakeEngine): Boolean {
      val intent =
        Intent(context, HotwordService::class.java).apply {
          putStringArrayListExtra(extraWords, ArrayList(words))
          putExtra(extraWakeEngine, engine.rawValue)
        }
      return try {
        context.startForegroundService(intent)
        true
      } catch (t: Throwable) {
        HotwordDebugLogger.log("启动热词服务失败: ${t.javaClass.simpleName}")
        false
      }
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, HotwordService::class.java))
    }
  }

  private val coroutineErrorHandler =
    CoroutineExceptionHandler { _, throwable ->
      debugLog("热词服务异常: ${throwable.javaClass.simpleName}")
      Log.e("HotwordService", "Unhandled coroutine error", throwable)
      stopSelf()
    }
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + coroutineErrorHandler)
  private var model: Model? = null
  private var speechService: SpeechService? = null
  private var restartJob: Job? = null
  private var sherpaLoopJob: Job? = null
  private var keywordSpotter: KeywordSpotter? = null
  private var wakeWords: List<String> = emptyList()
  private var wakeEngine: WakeEngine = WakeEngine.default
  private var isDestroyed = false
  private val hotwordPrefs by lazy { getSharedPreferences("hotword_prefs", Context.MODE_PRIVATE) }
  private val wakeTraceTag = "WakeTrace"
  private var lastHeardTextTraceAt = 0L

  private val voskListener: RecognitionListener =
    object : RecognitionListener {
      override fun onResult(hypothesis: String?) {
        maybeTriggerWakeVosk(hypothesis)
      }

      override fun onFinalResult(hypothesis: String?) {
        maybeTriggerWakeVosk(hypothesis)
      }

      override fun onPartialResult(hypothesis: String?) {}

      override fun onError(exception: Exception?) {
        debugLog("识别错误: ${exception?.message ?: "unknown"}")
        scheduleVoskRestart(delayMs = 1800L)
      }

      override fun onTimeout() {
        scheduleVoskRestart(delayMs = 350L)
      }
    }

  private fun debugLog(message: String) {
    HotwordDebugLogger.log(message)
    Log.i(wakeTraceTag, "HotwordService: $message")
  }

  override fun onCreate() {
    super.onCreate()
    ensureNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    debugLog("onStartCommand 收到启动请求")
    if (
      ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      debugLog("缺少 RECORD_AUDIO 权限，热词服务停止")
      stopSelf()
      return START_NOT_STICKY
    }

    wakeWords = intent?.getStringArrayListExtra(extraWords)?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    if (wakeWords.isEmpty()) {
      wakeWords = listOf("openclaw", "claude")
    }
    wakeEngine =
      WakeEngine.fromRawValue(
        intent?.getStringExtra(extraWakeEngine),
      )
    debugLog("热词服务启动，引擎=${wakeEngine.rawValue}，监听词: ${wakeWords.joinToString()}")

    val notification =
      buildNotification(
        content =
          when (wakeEngine) {
            WakeEngine.Vosk -> "离线语音唤醒监听中 (Vosk)"
            WakeEngine.SherpaOnnx -> "离线语音唤醒监听中 (Sherpa-ONNX)"
          },
      )
    try {
      startForeground(
        notificationId,
        notification,
        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
      )
    } catch (t: Throwable) {
      debugLog("前台服务启动失败: ${t.javaClass.simpleName}")
      stopSelf()
      return START_NOT_STICKY
    }

    when (wakeEngine) {
      WakeEngine.Vosk ->
        scope.launch(Dispatchers.IO) {
          try {
            shutdownEngines()
            initAndStartVosk()
          } catch (t: Throwable) {
            debugLog("Vosk 初始化失败: ${t.javaClass.simpleName}")
            Log.e("HotwordService", "init/start vosk failed", t)
            stopSelf()
          }
        }
      WakeEngine.SherpaOnnx ->
        scope.launch(Dispatchers.IO) {
          try {
            shutdownEngines()
            runSherpaLoopUntilStopped()
          } catch (t: Throwable) {
            if (t is CancellationException) throw t
            debugLog("Sherpa 初始化或运行失败: ${t.javaClass.simpleName}")
            Log.e("HotwordService", "sherpa kws failed", t)
            stopSelf()
          }
        }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    isDestroyed = true
    restartJob?.cancel()
    restartJob = null
    val sherpaJob = sherpaLoopJob
    sherpaLoopJob = null
    sherpaJob?.cancel()
    runBlocking(Dispatchers.IO) {
      withTimeoutOrNull(1_200L) {
        sherpaJob?.join()
      }
    }
    shutdownEngines()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun ensureNotificationChannel() {
    val manager = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(
        channelId,
        getString(R.string.voice_wake_label),
        NotificationManager.IMPORTANCE_LOW,
      )
    manager.createNotificationChannel(channel)
  }

  private fun buildNotification(content: String): Notification {
    val pending =
      PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    return NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.mipmap.ic_launcher)
      .setContentTitle(getString(R.string.voice_wake_label))
      .setContentText(content)
      .setContentIntent(pending)
      .setOngoing(true)
      .build()
  }

  private fun initAndStartVosk() {
    if (isDestroyed) return
    try {
      if (model == null) {
        val modelDir = File(filesDir, "model")
        val shouldCopy = shouldCopyModel(modelDir)
        if (shouldCopy) {
          debugLog("准备同步模型到本地目录")
          val copied = copyModelFromAssets(targetDir = modelDir)
          if (!copied) {
            debugLog("未找到可用模型，热词监听未启动")
            return
          }
        } else {
          val missing = missingModelFiles(modelDir)
          if (missing.isNotEmpty()) {
            debugLog("检测到模型损坏，尝试重拷贝: ${missing.joinToString()}")
            val copied = copyModelFromAssets(targetDir = modelDir)
            if (!copied) {
              debugLog("模型修复失败，热词监听未启动")
              return
            }
          }
        }
        if (!modelDir.exists() || modelDir.listFiles().isNullOrEmpty()) {
          debugLog("模型目录为空，热词监听未启动")
          return
        }
        debugLog("加载模型: ${modelDir.absolutePath}")
        model = Model(modelDir.absolutePath)
      }
      val jsonWords = (wakeWords + "[unk]").joinToString("\", \"", "[\"", "\"]")
      debugLog("开始热词识别 (Vosk)")
      val recognizer = Recognizer(model, sampleRate, jsonWords)
      speechService = SpeechService(recognizer, sampleRate).also { it.startListening(voskListener) }
      debugLog("识别器已启动，进入监听")
    } catch (t: Throwable) {
      val reason = t.message?.trim().orEmpty()
      val suffix = if (reason.isEmpty()) "" else ": $reason"
      debugLog("初始化识别器失败: ${t.javaClass.simpleName}$suffix")
      Log.e("HotwordService", "initAndStartVosk failed", t)
      shutdownEngines()
      stopSelf()
    }
  }

  private fun Throwable.containsUnsatisfiedLinkInChain(): Boolean =
    generateSequence(this) { it.cause }.any { it is UnsatisfiedLinkError }

  private fun isSherpaNativeLoadFailure(t: Throwable): Boolean {
    if (t.containsUnsatisfiedLinkInChain()) return true
    if (t is ExceptionInInitializerError) {
      val ex = t.exception ?: t.cause
      if (ex is UnsatisfiedLinkError) return true
    }
    if (t is NoClassDefFoundError) {
      val m = t.message?.lowercase().orEmpty()
      if (
        m.contains("keywordspotter") ||
          m.contains("onlinestream") ||
          m.contains("sherpa") ||
          m.contains("com.k2fsa.sherpa")
      ) {
        return true
      }
    }
    return false
  }

  private fun debugSherpaJniInstallHint() {
    debugLog(
      "请把官方 sherpa-onnx Android 包里的 .so 放入: android/app/src/main/jniLibs/<abi>/ （至少含 libsherpa-onnx-jni.so、常为 libonnxruntime.so）",
    )
    debugLog("或在 android 目录执行: powershell -File scripts\\fetch-sherpa-kws-assets.ps1 （不要加 -SkipJni），再重新编译安装 APK")
    debugLog("说明见 jniLibs/README.txt；真机多为 arm64-v8a")
  }

  private fun missingSherpaAssetFiles(): List<String> {
    return requiredSherpaKwsFiles.filter { path ->
      try {
        assets.open(path).close()
        false
      } catch (_: Throwable) {
        true
      }
    }
  }

  private fun runSherpaLoopUntilStopped() {
    if (isDestroyed) return
    val missing = missingSherpaAssetFiles()
    if (missing.isNotEmpty()) {
      debugLog("Sherpa 资源未就绪，缺失: ${missing.joinToString(limit = 3)} … 请运行 android/scripts/fetch-sherpa-kws-assets.ps1")
      shutdownEngines()
      stopSelf()
      return
    }

    val kws: KeywordSpotter
    try {
      val config =
        KeywordSpotterConfig(
          featConfig = getFeatureConfig(sampleRate = sampleRate.toInt(), featureDim = 80),
          modelConfig = openClawKwsModelConfig(),
          keywordsFile = openClawKwsKeywordsAssetPath(),
        )
      kws = KeywordSpotter(assetManager = assets, config = config)
      keywordSpotter = kws
    } catch (t: Throwable) {
      if (isSherpaNativeLoadFailure(t)) {
        debugLog("Sherpa JNI 未打进 APK 或设备架构不匹配: ${t.message}")
        debugSherpaJniInstallHint()
      } else {
        debugLog("Sherpa KeywordSpotter 初始化失败: ${t.javaClass.simpleName}: ${t.message}")
        Log.e("HotwordService", "KeywordSpotter init failed", t)
      }
      stopSelf()
      return
    }

    val resolution = SherpaKwsKeywords.resolveForCreateStream(assets, wakeWords)
    val keywordsArg = resolution.keywordArg
    if (keywordsArg == null) {
      debugLog(
        "Sherpa 唤醒词无法匹配 keywords.txt（@ 后展示名须与设置一致）: ${resolution.unresolved.joinToString("、")}",
      )
      debugLog(
        "可把这些词写入 assets 内 WeNetSpeech 的 keywords.txt（需含音素行），或用 sherpa-onnx-cli text2token 生成，详见 Sherpa KWS 文档。",
      )
      kws.release()
      keywordSpotter = null
      stopSelf()
      return
    }
    debugLog("Sherpa KWS 关键词串: $keywordsArg")

    val stream = kws.createStream(keywordsArg)
    if (stream.ptr == 0L) {
      debugLog("Sherpa createStream 失败（音素行无效或与模型不一致）")
      kws.release()
      keywordSpotter = null
      stopSelf()
      return
    }

    val sampleRateInHz = sampleRate.toInt()

    sherpaLoopJob =
      scope.launch(Dispatchers.IO) @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) {
        var audioRecord: AudioRecord? = null
        try {
          val channelConfig = AudioFormat.CHANNEL_IN_MONO
          val audioFormat = AudioFormat.ENCODING_PCM_16BIT
          val minBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
          if (minBytes <= 0) {
            debugLog("无法初始化 AudioRecord（buffer）")
            return@launch
          }

          audioRecord =
            AudioRecord(
              MediaRecorder.AudioSource.MIC,
              sampleRateInHz,
              channelConfig,
              audioFormat,
              minBytes * 2,
            )
          if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            debugLog("AudioRecord 未初始化")
            return@launch
          }
          audioRecord.startRecording()
          debugLog("Sherpa KWS 已开始从麦克风读入")

          val intervalSamples = (0.1 * sampleRateInHz).toInt().coerceAtLeast(160)
          val buffer = ShortArray(intervalSamples)

          while (isActive && !isDestroyed) {
            val ar = audioRecord ?: break
            val n = ar.read(buffer, 0, buffer.size)
            if (n > 0) {
              val samples = FloatArray(n) { i -> buffer[i] / 32768.0f }
              stream.acceptWaveform(samples, sampleRateInHz)
              while (kws.isReady(stream)) {
                kws.decode(stream)
                val keyword = kws.getResult(stream).keyword.trim()
                if (keyword.isNotEmpty()) {
                  kws.reset(stream)
                  maybeTriggerWakeSherpa(keyword)
                }
              }
            } else if (n < 0) {
              debugLog("AudioRecord.read 错误: $n")
              delay(350)
            }
          }
        } catch (t: CancellationException) {
          throw t
        } catch (t: Throwable) {
          debugLog("Sherpa 循环异常: ${t.javaClass.simpleName}")
          Log.e("HotwordService", "sherpa loop", t)
        } finally {
          try {
            stream.release()
          } catch (_: Throwable) {}
          try {
            audioRecord?.stop()
            audioRecord?.release()
          } catch (_: Throwable) {}
          if (!isDestroyed) {
            stopSelf()
          }
        }
      }
  }

  private fun maybeTriggerWakeSherpa(spottedKeyword: String) {
    val text = spottedKeyword.trim()
    if (text.isEmpty()) return
    val now = System.currentTimeMillis()
    if (now - lastHeardTextTraceAt >= 1200L) {
      lastHeardTextTraceAt = now
      debugLog("Sherpa 命中: \"$text\"")
    }
    dispatchWakeAndLaunch(text)
  }

  private fun copyModelFromAssets(targetDir: File): Boolean {
    return try {
      val modelEntries = assets.list("model").orEmpty()
      if (modelEntries.isEmpty()) {
        debugLog("assets/model 不存在或为空")
        false
      } else {
        if (targetDir.exists()) {
          targetDir.deleteRecursively()
        }
        targetDir.mkdirs()
        copyAssetDir("model", targetDir)
        val missing = missingModelFiles(targetDir)
        if (missing.isNotEmpty()) {
          debugLog("拷贝后模型仍不完整，缺失: ${missing.joinToString()}")
          false
        } else {
          hotwordPrefs.edit().putInt("model_version", currentAppVersion()).apply()
          true
        }
      }
    } catch (_: Throwable) {
      debugLog("模型拷贝失败")
      false
    }
  }

  private fun missingModelFiles(modelDir: File): List<String> {
    return requiredModelFiles.filterNot { relative ->
      File(modelDir, relative).isFile
    }
  }

  private fun shouldCopyModel(modelDir: File): Boolean {
    val currentVersion = currentAppVersion()
    val savedVersion = hotwordPrefs.getInt("model_version", 0)
    val exists = modelDir.exists()
    val notEmpty = modelDir.list()?.isNotEmpty() == true
    return !(savedVersion == currentVersion && exists && notEmpty)
  }

  private fun currentAppVersion(): Int {
    return try {
      packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
    } catch (_: Throwable) {
      1
    }
  }

  private fun copyAssetDir(fromAssetPath: String, toDir: File) {
    val children = assets.list(fromAssetPath).orEmpty()
    if (children.isEmpty()) {
      assets.open(fromAssetPath).use { input ->
        FileOutputStream(toDir).use { output ->
          input.copyTo(output)
        }
      }
      return
    }
    if (!toDir.exists()) toDir.mkdirs()
    for (name in children) {
      val childAssetPath = "$fromAssetPath/$name"
      val childFile = File(toDir, name)
      val sub = assets.list(childAssetPath).orEmpty()
      if (sub.isEmpty()) {
        assets.open(childAssetPath).use { input ->
          FileOutputStream(childFile).use { output ->
            input.copyTo(output)
          }
        }
      } else {
        copyAssetDir(childAssetPath, childFile)
      }
    }
  }

  private fun shutdownEngines() {
    sherpaLoopJob?.cancel()
    sherpaLoopJob = null
    try {
      speechService?.stop()
      speechService?.shutdown()
    } catch (_: Throwable) {
    } finally {
      speechService = null
    }
    try {
      model?.close()
    } catch (_: Throwable) {
    } finally {
      model = null
    }
    try {
      keywordSpotter?.release()
    } catch (_: Throwable) {
    } finally {
      keywordSpotter = null
    }
  }

  private fun scheduleVoskRestart(delayMs: Long = 800L) {
    if (isDestroyed || wakeEngine != WakeEngine.Vosk) return
    debugLog("识别中断，${delayMs}ms 后重启")
    restartJob?.cancel()
    restartJob =
      scope.launch {
        delay(delayMs)
        launch(Dispatchers.IO) {
          shutdownEngines()
          initAndStartVosk()
        }
      }
  }

  private fun maybeTriggerWakeVosk(hypothesis: String?) {
    if (hypothesis.isNullOrBlank()) return
    val text =
      try {
        JSONObject(hypothesis).optString("text", "")
      } catch (_: Throwable) {
        ""
      }.trim()
    if (text.isEmpty()) return

    val now = System.currentTimeMillis()
    if (now - lastHeardTextTraceAt >= 1200L) {
      lastHeardTextTraceAt = now
      debugLog("识别文本: \"$text\"")
    }

    val normalizedText = normalizeForWakeMatch(text)
    val compactText = normalizedText.replace(" ", "")
    val matched =
      wakeWords.any { rawWord ->
        val normalizedWord = normalizeForWakeMatch(rawWord)
        normalizedWord.isNotEmpty() &&
          (
            normalizedText.contains(normalizedWord) ||
              compactText.contains(normalizedWord.replace(" ", ""))
          )
      }
    if (!matched) return

    debugLog("命中唤醒词: $text")
    dispatchWakeAndLaunch(text)
    scheduleVoskRestart(delayMs = 1500L)
  }

  private fun dispatchWakeAndLaunch(triggerLabel: String) {
    debugLog("发送 WAKE_TRIGGERED 广播 ($triggerLabel)")
    sendBroadcast(
      Intent(actionWakeTriggered).setPackage(packageName).apply {
        putExtra(extraWakeTriggerLabel, triggerLabel)
        putExtra(extraWakeTimeEpochMs, System.currentTimeMillis())
      },
    )
    try {
      val launchIntent =
        Intent(this, MainActivity::class.java).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
      startActivity(launchIntent)
    } catch (t: Throwable) {
      debugLog("唤醒拉起应用失败: ${t.javaClass.simpleName}")
      Log.w("HotwordService", "Failed to launch activity from hotword", t)
    }
  }

  private fun normalizeForWakeMatch(value: String): String {
    val lowered = value.lowercase()
    val replaced = lowered.replace(Regex("[^a-z0-9\\s]"), " ")
    return replaced.replace(Regex("\\s+"), " ").trim()
  }
}
