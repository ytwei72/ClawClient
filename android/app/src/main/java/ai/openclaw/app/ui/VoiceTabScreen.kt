package ai.openclaw.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.VoiceWakeMode
import ai.openclaw.app.WakeEngine
import ai.openclaw.app.WakeWords
import ai.openclaw.app.voice.SherpaKwsKeywords
import ai.openclaw.app.voice.VoiceConversationEntry
import ai.openclaw.app.voice.VoiceConversationRole
import kotlin.math.max

/** 语音页 TTS 卡片内可选试听句（与助手对话内容无关）。 */
private val VOICE_TTS_SAMPLE_PHRASES =
  listOf(
    "你好，我是灵犀助手，很高兴为你服务。",
    "今天天气不错，适合外出走走，记得防晒补水。",
    "记得多喝水、少熬夜，保持作息规律。",
    "这是一条语音播报试听，请确认音量是否合适。",
    "正在为您处理请求，请稍候片刻。",
    "好的，我已经记下您说的内容。",
    "有需要随时叫我，我会尽力协助。",
    "我明白了，让我们继续下一步。",
    "本条为中文语音合成示例。",
    "感谢使用 OpenClaw，祝使用愉快。",
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceTabScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val activity = remember(context) { context.findActivity() }
  val listState = rememberLazyListState()

  val gatewayStatus by viewModel.statusText.collectAsState()
  val micEnabled by viewModel.micEnabled.collectAsState()
  val micCooldown by viewModel.micCooldown.collectAsState()
  val speakerEnabled by viewModel.speakerEnabled.collectAsState()
  val micLiveTranscript by viewModel.micLiveTranscript.collectAsState()
  val micQueuedMessages by viewModel.micQueuedMessages.collectAsState()
  val micConversation by viewModel.micConversation.collectAsState()
  val micInputLevel by viewModel.micInputLevel.collectAsState()
  val micIsSending by viewModel.micIsSending.collectAsState()

  val hasStreamingAssistant = micConversation.any { it.role == VoiceConversationRole.Assistant && it.isStreaming }
  val showThinkingBubble = micIsSending && !hasStreamingAssistant

  var hasMicPermission by remember { mutableStateOf(context.hasRecordAudioPermission()) }
  var pendingMicEnable by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner, context) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          hasMicPermission = context.hasRecordAudioPermission()
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      // Stop TTS when leaving the voice screen
      viewModel.setVoiceScreenActive(false)
    }
  }

  val requestMicPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMicPermission = granted
      if (granted && pendingMicEnable) {
        viewModel.setMicEnabled(true)
      }
      pendingMicEnable = false
    }

  // 仅在有对话或思考气泡时滚到最新一轮，避免抢跑整张列表
  LaunchedEffect(micConversation.size, showThinkingBubble) {
    if (micConversation.isEmpty() && !showThinkingBubble) return@LaunchedEffect
    val lastConvIndex = micConversation.size + if (showThinkingBubble) 1 else 0
    listState.animateScrollToItem(lastConvIndex)
  }

  val queueCount = micQueuedMessages.size
  val asrStateLine =
    remember(queueCount, micIsSending, micCooldown, micEnabled) {
      when {
        queueCount > 0 -> "$queueCount 条排队"
        micIsSending -> "发送中"
        micCooldown -> "冷却中"
        micEnabled -> "聆听中"
        else -> "麦克风关"
      }
    }

  val onMicMainClick: () -> Unit = l@{
    if (micCooldown) return@l
    if (micEnabled) {
      viewModel.setMicEnabled(false)
    } else if (hasMicPermission) {
      viewModel.setMicEnabled(true)
    } else {
      pendingMicEnable = true
      requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  LazyColumn(
    state = listState,
    modifier =
      Modifier
        .fillMaxSize()
        .background(mobileBackgroundGradient)
        .imePadding()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    item {
      VoiceWakeHotwordSection(viewModel = viewModel)
    }
    items(items = micConversation, key = { it.id }) { entry ->
      VoiceTurnBubble(entry = entry)
    }
    if (showThinkingBubble) {
      item {
        VoiceThinkingBubble()
      }
    }
    item {
      VoiceAsrControlCard(
        micEnabled = micEnabled,
        micCooldown = micCooldown,
        micLiveTranscript = micLiveTranscript,
        gatewayLine =
          "${formatConnectionStatusForUi(gatewayStatusForDisplay(gatewayStatus))} · $asrStateLine",
        asrStateAccent =
          when {
            micEnabled -> mobileSuccess
            micIsSending -> mobileAccent
            else -> mobileTextSecondary
          },
        micInputLevel = micInputLevel,
        onMicClick = onMicMainClick,
        hasMicPermission = hasMicPermission,
      )
    }
    item {
      VoiceTtsControlCard(
        speakerEnabled = speakerEnabled,
        onSpeakerToggle = { viewModel.setSpeakerEnabled(!speakerEnabled) },
        samplePhrases = VOICE_TTS_SAMPLE_PHRASES,
        onSpeakSample = { viewModel.speakVoiceTtsSample(it) },
      )
    }
    if (!hasMicPermission) {
      item {
        val showRationale =
          if (activity == null) {
            false
          } else {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
          }
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            if (showRationale) {
              "语音模式需要麦克风权限。"
            } else {
              "麦克风不可用。请在系统设置中开启。"
            },
            style = mobileCaption1,
            color = mobileWarning,
            textAlign = TextAlign.Center,
          )
          Button(
            onClick = { openAppSettings(context) },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = mobileSurfaceStrong, contentColor = mobileText),
          ) {
            Text("打开设置", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
          }
        }
      }
    }
  }
}

@Composable
private fun VoiceAsrControlCard(
  micEnabled: Boolean,
  micCooldown: Boolean,
  micLiveTranscript: String?,
  gatewayLine: String,
  asrStateAccent: Color,
  micInputLevel: Float,
  onMicClick: () -> Unit,
  hasMicPermission: Boolean,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = mobileCardSurface,
    border = BorderStroke(1.dp, mobileBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        "语音识别",
        style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = mobileAccent,
      )
      Text(
        "使用系统语音识别。打开麦克风后说话，识别文字会显示在下方；可多段合并为一轮，关闭麦克风后发送对话。",
        style = mobileCallout,
        color = mobileTextTertiary,
      )
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier = Modifier.size(76.dp),
          contentAlignment = Alignment.Center,
        ) {
          if (micEnabled && !micCooldown) {
            val ringLevel = micInputLevel.coerceIn(0f, 1f)
            val ringSize = 54.dp + (20.dp * max(ringLevel, 0.08f))
            Box(
              modifier =
                Modifier
                  .size(ringSize)
                  .background(mobileSuccess.copy(alpha = 0.1f + 0.16f * ringLevel), CircleShape),
            )
          }
          Button(
            onClick = onMicClick,
            enabled = !micCooldown,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(56.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor =
                  when {
                    micCooldown -> mobileTextSecondary
                    micEnabled -> mobileSuccess
                    else -> mobileSurfaceStrong
                  },
                contentColor =
                  if (micEnabled || micCooldown) Color.White else mobileTextTertiary,
                disabledContainerColor = mobileTextSecondary,
                disabledContentColor = Color.White.copy(alpha = 0.5f),
              ),
          ) {
            Icon(
              imageVector = if (micEnabled) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = if (micEnabled) "关闭麦克风" else "打开麦克风",
              modifier = Modifier.size(26.dp),
            )
          }
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            when {
              micCooldown -> "冷却中"
              micEnabled -> "聆听中"
              else -> "麦克风已关"
            },
            style = mobileHeadline,
            color = asrStateAccent,
          )
          Surface(
            modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
            shape = RoundedCornerShape(12.dp),
            color = mobileSurfaceStrong,
            border = BorderStroke(1.dp, mobileBorder.copy(alpha = 0.85f)),
          ) {
            val body =
              when {
                !micLiveTranscript.isNullOrBlank() -> micLiveTranscript.trim()
                micEnabled -> "正在聆听，请说话…"
                !hasMicPermission -> "需要麦克风权限后才能识别。"
                else -> "打开麦克风后，实时识别文字会显示在这里。"
              }
            val bodyColor =
              if (!micLiveTranscript.isNullOrBlank()) mobileText else mobileTextTertiary
            Text(
              body,
              style = mobileCallout,
              color = bodyColor,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
          }
        }
      }
      HorizontalDivider(color = mobileBorder.copy(alpha = 0.6f))
      Text(
        gatewayLine,
        style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold),
        color = mobileTextSecondary,
        maxLines = 2,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceTtsControlCard(
  speakerEnabled: Boolean,
  onSpeakerToggle: () -> Unit,
  samplePhrases: List<String>,
  onSpeakSample: (String) -> Unit,
) {
  var selectedSampleIndex by remember(samplePhrases) { mutableStateOf(0) }
  var phraseMenuExpanded by remember { mutableStateOf(false) }
  val displayPhrase =
    samplePhrases.getOrNull(selectedSampleIndex).orEmpty().ifEmpty { samplePhrases.firstOrNull().orEmpty() }

  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = mobileCardSurface,
    border = BorderStroke(1.dp, mobileBorder),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          "语音播报（TTS）",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
          color = mobileAccent,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            if (speakerEnabled) "扬声器开" else "扬声器关",
            style = mobileCaption2,
            color = if (speakerEnabled) mobileSuccess else mobileTextTertiary,
          )
          IconButton(
            onClick = onSpeakerToggle,
            modifier = Modifier.size(44.dp),
            colors =
              IconButtonDefaults.iconButtonColors(
                containerColor = if (speakerEnabled) mobileSuccessSoft else mobileSurfaceStrong,
              ),
          ) {
            Icon(
              imageVector =
                if (speakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
              contentDescription = if (speakerEnabled) "关闭扬声器" else "打开扬声器",
              modifier = Modifier.size(22.dp),
              tint = if (speakerEnabled) mobileSuccess else mobileTextTertiary,
            )
          }
        }
      }
      Text(
        "试听与助手对话无关。点选下方框可更换语句，再播放试听合成效果。",
        style = mobileCallout,
        color = mobileTextTertiary,
      )
      ExposedDropdownMenuBox(
        expanded = phraseMenuExpanded,
        onExpandedChange = { phraseMenuExpanded = it },
        modifier = Modifier.fillMaxWidth(),
      ) {
        OutlinedTextField(
          value = displayPhrase,
          onValueChange = {},
          readOnly = true,
          label = { Text("当前试听语句", style = mobileCaption1, color = mobileTextSecondary) },
          modifier =
            Modifier
              .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
              .fillMaxWidth(),
          textStyle = mobileBody.copy(color = mobileText),
          colors = voiceSettingsTextFieldColors(),
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = phraseMenuExpanded) },
          minLines = 2,
          maxLines = 4,
        )
        DropdownMenu(
          expanded = phraseMenuExpanded,
          onDismissRequest = { phraseMenuExpanded = false },
          modifier = Modifier.heightIn(max = 280.dp),
        ) {
          samplePhrases.forEachIndexed { index, phrase ->
            DropdownMenuItem(
              text = {
                Text(
                  "${index + 1}. $phrase",
                  style = mobileCallout,
                  maxLines = 3,
                  overflow = TextOverflow.Ellipsis,
                )
              },
              onClick = {
                phraseMenuExpanded = false
                selectedSampleIndex = index
              },
            )
          }
        }
      }
      FilledTonalButton(
        onClick = {
          samplePhrases.getOrNull(selectedSampleIndex)?.let(onSpeakSample)
        },
        enabled = speakerEnabled && samplePhrases.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
          ButtonDefaults.filledTonalButtonColors(
            containerColor = mobileAccentSoft,
            contentColor = mobileAccent,
            disabledContainerColor = mobileSurfaceStrong,
            disabledContentColor = mobileTextTertiary,
          ),
      ) {
        Icon(
          imageVector = Icons.Default.PlayArrow,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("播放当前语句", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
      }
      if (!speakerEnabled) {
        Text(
          "请先开启扬声器后再试听。",
          style = mobileCaption2,
          color = mobileTextTertiary,
        )
      }
    }
  }
}

@Composable
private fun VoiceTurnBubble(entry: VoiceConversationEntry) {
  val isUser = entry.role == VoiceConversationRole.User
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.90f),
      shape = RoundedCornerShape(12.dp),
      color = if (isUser) mobileAccentSoft else mobileCardSurface,
      border = BorderStroke(1.dp, if (isUser) mobileAccent else mobileBorderStrong),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Text(
          if (isUser) "你" else "OpenClaw",
          style = mobileCaption2.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
          color = if (isUser) mobileAccent else mobileTextSecondary,
        )
        Text(
          if (entry.isStreaming && entry.text.isBlank()) "正在听取回复…" else entry.text,
          style = mobileCallout,
          color = mobileText,
        )
      }
    }
  }
}

@Composable
private fun VoiceThinkingBubble() {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
    Surface(
      modifier = Modifier.fillMaxWidth(0.68f),
      shape = RoundedCornerShape(12.dp),
      color = mobileCardSurface,
      border = BorderStroke(1.dp, mobileBorderStrong),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ThinkingDots(color = mobileTextSecondary)
        Text("OpenClaw 思考中…", style = mobileCallout, color = mobileTextSecondary)
      }
    }
  }
}

@Composable
private fun ThinkingDots(color: Color) {
  Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    ThinkingDot(alpha = 0.38f, color = color)
    ThinkingDot(alpha = 0.62f, color = color)
    ThinkingDot(alpha = 0.90f, color = color)
  }
}

@Composable
private fun ThinkingDot(alpha: Float, color: Color) {
  Surface(
    modifier = Modifier.size(6.dp).alpha(alpha),
    shape = CircleShape,
    color = color,
  ) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceWakeHotwordSection(viewModel: MainViewModel) {
  val context = LocalContext.current
  val voiceWakeMode by viewModel.voiceWakeMode.collectAsState()
  val wakeEngine by viewModel.wakeEngine.collectAsState()
  val wakeWords by viewModel.wakeWords.collectAsState()
  val hotwordDebugLogs by viewModel.hotwordDebugLogs.collectAsState()
  var hotwordTestStatus by remember { mutableStateOf<String?>(null) }
  var voiceWakeExpanded by remember { mutableStateOf(false) }
  var hotwordLogExpanded by remember { mutableStateOf(false) }
  var wakeWordMenuExpanded by remember { mutableStateOf(false) }
  var prevWakeModeForMicLinkage by remember { mutableStateOf<VoiceWakeMode?>(null) }
  val sherpaWakeWordLabels =
    remember(context) {
      runCatching { SherpaKwsKeywords.displayLabelsOrdered(context.assets) }.getOrElse { emptyList() }
    }
  val wakeWordOptions =
    remember(wakeEngine, sherpaWakeWordLabels) {
      when (wakeEngine) {
        WakeEngine.Vosk -> WakeWords.voskWakeWordMenuOptions
        WakeEngine.SherpaOnnx -> sherpaWakeWordLabels
      }
    }

  LaunchedEffect(voiceWakeMode) {
    hotwordTestStatus = null
    val prev = prevWakeModeForMicLinkage
    prevWakeModeForMicLinkage = voiceWakeMode
    val shouldOpenMic =
      when {
        prev == null && voiceWakeMode != VoiceWakeMode.Off -> true
        prev == VoiceWakeMode.Off && voiceWakeMode != VoiceWakeMode.Off -> true
        else -> false
      }
    if (shouldOpenMic && context.hasRecordAudioPermission()) {
      viewModel.setMicEnabled(true)
    }
  }

  LaunchedEffect(wakeWords, wakeWordOptions) {
    val primary = wakeWords.firstOrNull().orEmpty()
    if (wakeWordOptions.isEmpty()) return@LaunchedEffect
    if (primary.isEmpty() || primary !in wakeWordOptions) {
      viewModel.setWakeWords(listOf(wakeWordOptions.first()))
    }
  }

  val listItemColors =
    ListItemDefaults.colors(
      containerColor = Color.Transparent,
      headlineColor = mobileText,
      supportingColor = mobileTextSecondary,
      trailingIconColor = mobileTextSecondary,
      leadingIconColor = mobileTextSecondary,
    )

  val voiceWakeSummary =
    remember(voiceWakeMode, wakeEngine) {
      val mode =
        when (voiceWakeMode) {
          VoiceWakeMode.Off -> "已关闭"
          VoiceWakeMode.Foreground -> "仅前台"
          VoiceWakeMode.Always -> "始终监听"
        }
      val engine = if (wakeEngine == WakeEngine.Vosk) "Vosk" else "Sherpa"
      "$mode · $engine"
    }

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      "语音唤醒",
      style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
      color = mobileAccent,
    )
    Column(modifier = Modifier.voiceSettingsRowModifier()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { voiceWakeExpanded = !voiceWakeExpanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            if (voiceWakeExpanded) "收起" else "展开设置",
            style = mobileCallout,
            color = mobileTextSecondary,
          )
          if (!voiceWakeExpanded) {
            Text(
              voiceWakeSummary,
              style = mobileCaption1,
              color = mobileTextTertiary,
            )
          }
        }
        Icon(
          imageVector = if (voiceWakeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (voiceWakeExpanded) "收起" else "展开",
          tint = mobileTextSecondary,
          modifier = Modifier.size(22.dp),
        )
      }
      if (voiceWakeExpanded) {
        HorizontalDivider(color = mobileBorder)
        ListItem(
          modifier = Modifier.fillMaxWidth(),
          colors = listItemColors,
          headlineContent = { Text("关闭", style = mobileHeadline) },
          supportingContent = { Text("不启用后台热词监听。", style = mobileCallout) },
          trailingContent = {
            RadioButton(
              selected = voiceWakeMode == VoiceWakeMode.Off,
              onClick = { viewModel.setVoiceWakeMode(VoiceWakeMode.Off) },
            )
          },
        )
        HorizontalDivider(color = mobileBorder)
        ListItem(
          modifier = Modifier.fillMaxWidth(),
          colors = listItemColors,
          headlineContent = { Text("仅前台", style = mobileHeadline) },
          supportingContent = { Text("应用在前台时监听唤醒词。", style = mobileCallout) },
          trailingContent = {
            RadioButton(
              selected = voiceWakeMode == VoiceWakeMode.Foreground,
              onClick = { viewModel.setVoiceWakeMode(VoiceWakeMode.Foreground) },
            )
          },
        )
        HorizontalDivider(color = mobileBorder)
        ListItem(
          modifier = Modifier.fillMaxWidth(),
          colors = listItemColors,
          headlineContent = { Text("始终监听", style = mobileHeadline) },
          supportingContent = { Text("后台前台均可唤醒（更耗电）。", style = mobileCallout) },
          trailingContent = {
            RadioButton(
              selected = voiceWakeMode == VoiceWakeMode.Always,
              onClick = { viewModel.setVoiceWakeMode(VoiceWakeMode.Always) },
            )
          },
        )
        HorizontalDivider(color = mobileBorder)
        ListItem(
          modifier = Modifier.fillMaxWidth(),
          colors = listItemColors,
          headlineContent = { Text("唤醒引擎：Vosk", style = mobileHeadline) },
          supportingContent = {
            Text(
              "小型英文模型：下方唤醒词仅提供可命中的英文词（如 openclaw），勿与 Sherpa 中文词混用。",
              style = mobileCallout,
            )
          },
          trailingContent = {
            RadioButton(
              selected = wakeEngine == WakeEngine.Vosk,
              onClick = { viewModel.setWakeEngine(WakeEngine.Vosk) },
            )
          },
        )
        HorizontalDivider(color = mobileBorder)
        ListItem(
          modifier = Modifier.fillMaxWidth(),
          colors = listItemColors,
          headlineContent = { Text("唤醒引擎：Sherpa-ONNX", style = mobileHeadline) },
          supportingContent = {
            Text(
              "中文 KWS（WeNetSpeech）：唤醒词须与 APK 内 keywords.txt 中 @ 后面的词一致，或使用该文件中已有示例；自定义词需按官方文档用 text2token 生成音素行。",
              style = mobileCallout,
            )
          },
          trailingContent = {
            RadioButton(
              selected = wakeEngine == WakeEngine.SherpaOnnx,
              onClick = { viewModel.setWakeEngine(WakeEngine.SherpaOnnx) },
            )
          },
        )
        HorizontalDivider(color = mobileBorder)
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
          val displayWakeWord =
            wakeWords.firstOrNull().takeIf { it in wakeWordOptions }.orEmpty()
              .ifEmpty { wakeWordOptions.firstOrNull().orEmpty() }
          ExposedDropdownMenuBox(
            expanded = wakeWordMenuExpanded,
            onExpandedChange = { wakeWordMenuExpanded = it },
          ) {
            OutlinedTextField(
              value = displayWakeWord,
              onValueChange = {},
              readOnly = true,
              label = { Text("唤醒词", style = mobileCaption1, color = mobileTextSecondary) },
              modifier =
                Modifier
                  .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                  .fillMaxWidth(),
              textStyle = mobileBody.copy(color = mobileText),
              colors = voiceSettingsTextFieldColors(),
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wakeWordMenuExpanded) },
              singleLine = true,
            )
            DropdownMenu(
              expanded = wakeWordMenuExpanded,
              onDismissRequest = { wakeWordMenuExpanded = false },
            ) {
              wakeWordOptions.forEach { label ->
                DropdownMenuItem(
                  text = { Text(label, style = mobileBody.copy(color = mobileText)) },
                  onClick = {
                    wakeWordMenuExpanded = false
                    if (label != displayWakeWord) {
                      viewModel.setWakeWords(listOf(label))
                    }
                  },
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Button(
            onClick = {
              hotwordTestStatus = viewModel.triggerHotwordWakeTest()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = voiceSettingsPrimaryButtonColors(),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text("测试唤醒", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            "测试会模拟命中唤醒词并拉起麦克风，可在下方热词调试日志查看记录。",
            style = mobileCaption1,
            color = mobileTextTertiary,
          )
          hotwordTestStatus?.let { status ->
            val isFailure = status.startsWith("测试失败")
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              status,
              style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold),
              color = if (isFailure) mobileDanger else mobileAccent,
            )
          }
        }
      }
    }

    Text(
      "热词调试日志",
      style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
      color = mobileAccent,
    )
    Column(modifier = Modifier.voiceSettingsRowModifier()) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .clickable { hotwordLogExpanded = !hotwordLogExpanded }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          if (hotwordLogExpanded) "收起" else "展开（最近 ${hotwordDebugLogs.size} 条）",
          style = mobileCallout,
          color = mobileTextSecondary,
        )
        Icon(
          imageVector = if (hotwordLogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (hotwordLogExpanded) "收起" else "展开",
          tint = mobileTextSecondary,
          modifier = Modifier.size(22.dp),
        )
      }
      if (hotwordLogExpanded) {
        HorizontalDivider(color = mobileBorder)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          Button(
            onClick = {
              viewModel.clearHotwordDebugLogs()
              hotwordTestStatus = null
            },
            enabled = hotwordDebugLogs.isNotEmpty(),
            colors = voiceSettingsPrimaryButtonColors(),
            shape = RoundedCornerShape(12.dp),
          ) {
            Text("清空", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
          }
        }
        HorizontalDivider(color = mobileBorder)
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
          if (hotwordDebugLogs.isEmpty()) {
            Text("暂无日志", style = mobileCallout, color = mobileTextTertiary)
          } else {
            SelectionContainer {
              Column {
                hotwordDebugLogs.takeLast(20).forEach { line ->
                  Text(
                    line,
                    style = mobileCaption1.copy(fontFamily = FontFamily.Monospace),
                    color = mobileTextSecondary,
                    modifier = Modifier.padding(vertical = 2.dp),
                  )
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun voiceSettingsTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedContainerColor = mobileSurface,
    unfocusedContainerColor = mobileSurface,
    focusedBorderColor = mobileAccent,
    unfocusedBorderColor = mobileBorder,
    focusedTextColor = mobileText,
    unfocusedTextColor = mobileText,
    cursorColor = mobileAccent,
  )

@Composable
private fun Modifier.voiceSettingsRowModifier() =
  this
    .fillMaxWidth()
    .border(width = 1.dp, color = mobileBorder, shape = RoundedCornerShape(14.dp))
    .background(mobileCardSurface, RoundedCornerShape(14.dp))

@Composable
private fun voiceSettingsPrimaryButtonColors() =
  ButtonDefaults.buttonColors(
    containerColor = mobileAccent,
    contentColor = Color.White,
    disabledContainerColor = mobileAccent.copy(alpha = 0.45f),
    disabledContentColor = Color.White.copy(alpha = 0.9f),
  )

private fun Context.hasRecordAudioPermission(): Boolean {
  return (
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED
    )
}

private fun Context.findActivity(): Activity? =
  when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }

private fun openAppSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    )
  context.startActivity(intent)
}
