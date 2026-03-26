package ai.openclaw.app.voice

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ai.openclaw.app.MainActivity
import ai.openclaw.app.R
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileOutputStream

class HotwordService : Service(), RecognitionListener {
  companion object {
    const val actionWakeTriggered = "ai.openclaw.app.action.WAKE_TRIGGERED"
    private const val channelId = "openclaw_hotword"
    private const val notificationId = 4001
    private const val sampleRate = 16_000.0f
    private const val extraWords = "extra_words"
    private val requiredModelFiles =
      listOf(
        "am/final.mdl",
        "conf/model.conf",
        "graph/HCLr.fst",
        "graph/Gr.fst",
        "graph/phones/word_boundary.int",
      )

    fun start(context: Context, words: List<String>): Boolean {
      val intent =
        Intent(context, HotwordService::class.java).apply {
          putStringArrayListExtra(extraWords, ArrayList(words))
        }
      return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          context.startForegroundService(intent)
        } else {
          context.startService(intent)
        }
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
  private var wakeWords: List<String> = emptyList()
  private var isDestroyed = false
  private val hotwordPrefs by lazy { getSharedPreferences("hotword_prefs", Context.MODE_PRIVATE) }

  private fun debugLog(message: String) {
    HotwordDebugLogger.log(message)
  }

  override fun onCreate() {
    super.onCreate()
    ensureNotificationChannel()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
    debugLog("热词服务启动，监听词: ${wakeWords.joinToString()}")

    val notification = buildNotification(content = "离线语音唤醒监听中")
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        startForeground(
          notificationId,
          notification,
          android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )
      } else {
        startForeground(notificationId, notification)
      }
    } catch (t: Throwable) {
      debugLog("前台服务启动失败: ${t.javaClass.simpleName}")
      stopSelf()
      return START_NOT_STICKY
    }

    scope.launch(Dispatchers.IO) {
      try {
        shutdownRecognizer()
        initAndStartRecognizer()
      } catch (t: Throwable) {
        debugLog("识别初始化失败: ${t.javaClass.simpleName}")
        Log.e("HotwordService", "init/start recognizer failed", t)
        stopSelf()
      }
    }
    return START_STICKY
  }

  override fun onDestroy() {
    isDestroyed = true
    restartJob?.cancel()
    restartJob = null
    shutdownRecognizer()
    scope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun ensureNotificationChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = getSystemService(NotificationManager::class.java)
    val channel =
      NotificationChannel(channelId, "OpenClaw 语音唤醒", NotificationManager.IMPORTANCE_LOW)
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
      .setContentTitle("OpenClaw 语音唤醒")
      .setContentText(content)
      .setContentIntent(pending)
      .setOngoing(true)
      .build()
  }

  private fun initAndStartRecognizer() {
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
      debugLog("开始热词识别")
      val recognizer = Recognizer(model, sampleRate, jsonWords)
      speechService = SpeechService(recognizer, sampleRate).also { it.startListening(this) }
    } catch (t: Throwable) {
      val reason = t.message?.trim().orEmpty()
      val suffix = if (reason.isEmpty()) "" else ": $reason"
      debugLog("初始化识别器失败: ${t.javaClass.simpleName}$suffix")
      Log.e("HotwordService", "initAndStartRecognizer failed", t)
      shutdownRecognizer()
      stopSelf()
    }
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
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
      } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionCode
      }
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

  private fun shutdownRecognizer() {
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
  }

  private fun scheduleRestart(delayMs: Long = 800L) {
    if (isDestroyed) return
    debugLog("识别中断，${delayMs}ms 后重启")
    restartJob?.cancel()
    restartJob =
      scope.launch {
        delay(delayMs)
        launch(Dispatchers.IO) {
          shutdownRecognizer()
          initAndStartRecognizer()
        }
      }
  }

  private fun maybeTriggerWake(hypothesis: String?) {
    if (hypothesis.isNullOrBlank()) return
    val text =
      try {
        JSONObject(hypothesis).optString("text", "")
      } catch (_: Throwable) {
        ""
      }.trim()
    if (text.isEmpty()) return
    if (wakeWords.none { word -> text.contains(word, ignoreCase = true) }) return

    debugLog("命中唤醒词: $text")
    sendBroadcast(Intent(actionWakeTriggered).setPackage(packageName))
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
    scheduleRestart(delayMs = 1500L)
  }

  override fun onResult(hypothesis: String?) {
    maybeTriggerWake(hypothesis)
  }

  override fun onFinalResult(hypothesis: String?) {
    maybeTriggerWake(hypothesis)
  }

  override fun onPartialResult(hypothesis: String?) {}

  override fun onError(exception: Exception?) {
    debugLog("识别错误: ${exception?.message ?: "unknown"}")
    scheduleRestart(delayMs = 1800L)
  }

  override fun onTimeout() {
    scheduleRestart(delayMs = 350L)
  }
}
