package ai.openclaw.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ai.openclaw.app.BuildConfig
import ai.openclaw.app.LocationMode
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.VoiceWakeMode
import ai.openclaw.app.WakeEngine
import ai.openclaw.app.WakeWords
import ai.openclaw.app.node.DeviceNotificationListenerService
import ai.openclaw.app.voice.SherpaKwsKeywords

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: MainViewModel) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val instanceId by viewModel.instanceId.collectAsState()
  val displayName by viewModel.displayName.collectAsState()
  val cameraEnabled by viewModel.cameraEnabled.collectAsState()
  val locationMode by viewModel.locationMode.collectAsState()
  val locationPreciseEnabled by viewModel.locationPreciseEnabled.collectAsState()
  val preventSleep by viewModel.preventSleep.collectAsState()
  val canvasDebugStatusEnabled by viewModel.canvasDebugStatusEnabled.collectAsState()
  val voiceWakeMode by viewModel.voiceWakeMode.collectAsState()
  val wakeEngine by viewModel.wakeEngine.collectAsState()
  val wakeWords by viewModel.wakeWords.collectAsState()
  val hotwordDebugLogs by viewModel.hotwordDebugLogs.collectAsState()
  var hotwordTestStatus by remember { mutableStateOf<String?>(null) }
  var wakeWordMenuExpanded by remember { mutableStateOf(false) }
  var showResetGatewayPairingDialog by remember { mutableStateOf(false) }
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
  }

  LaunchedEffect(wakeWords, wakeWordOptions) {
    val primary = wakeWords.firstOrNull().orEmpty()
    if (wakeWordOptions.isEmpty()) return@LaunchedEffect
    if (primary.isEmpty() || primary !in wakeWordOptions) {
      viewModel.setWakeWords(listOf(wakeWordOptions.first()))
    }
  }

  val listState = rememberLazyListState()
  val deviceModel =
    remember {
      listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .trim()
        .ifEmpty { "Android" }
    }
  val appVersion =
    remember {
      val versionName = BuildConfig.VERSION_NAME.trim().ifEmpty { "dev" }
      if (BuildConfig.DEBUG && !versionName.contains("dev", ignoreCase = true)) {
        "$versionName-dev"
      } else {
        versionName
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

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val cameraOk = perms[Manifest.permission.CAMERA] == true
      viewModel.setCameraEnabled(cameraOk)
    }

  var pendingLocationRequest by remember { mutableStateOf(false) }
  var pendingPreciseToggle by remember { mutableStateOf(false) }

  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val fineOk = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true
      val coarseOk = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
      val granted = fineOk || coarseOk

      if (pendingPreciseToggle) {
        pendingPreciseToggle = false
        viewModel.setLocationPreciseEnabled(fineOk)
        return@rememberLauncherForActivityResult
      }

      if (pendingLocationRequest) {
        pendingLocationRequest = false
        viewModel.setLocationMode(if (granted) LocationMode.WhileUsing else LocationMode.Off)
      }
    }

  var micPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val audioPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      micPermissionGranted = granted
    }

  val smsPermissionAvailable =
    remember {
      BuildConfig.OPENCLAW_ENABLE_SMS &&
        context.packageManager?.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) == true
    }
  val callLogPermissionAvailable = remember { BuildConfig.OPENCLAW_ENABLE_CALL_LOG }
  val photosPermission =
    if (Build.VERSION.SDK_INT >= 33) {
      Manifest.permission.READ_MEDIA_IMAGES
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }
  val motionPermissionRequired = true
  val motionAvailable = remember(context) { hasMotionCapabilities(context) }

  var notificationsPermissionGranted by
    remember {
      mutableStateOf(hasNotificationsPermission(context))
    }
  val notificationsPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notificationsPermissionGranted = granted
    }

  var notificationListenerEnabled by
    remember {
      mutableStateOf(isNotificationListenerEnabled(context))
    }

  var photosPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, photosPermission) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val photosPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      photosPermissionGranted = granted
    }

  var contactsPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
          PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val contactsPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val readOk = perms[Manifest.permission.READ_CONTACTS] == true
      val writeOk = perms[Manifest.permission.WRITE_CONTACTS] == true
      contactsPermissionGranted = readOk && writeOk
    }

  var calendarPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
          PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val calendarPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val readOk = perms[Manifest.permission.READ_CALENDAR] == true
      val writeOk = perms[Manifest.permission.WRITE_CALENDAR] == true
      calendarPermissionGranted = readOk && writeOk
    }

  var callLogPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val callLogPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      callLogPermissionGranted = granted
    }

  var motionPermissionGranted by
    remember {
      mutableStateOf(
        !motionPermissionRequired ||
          ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val motionPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      motionPermissionGranted = granted
    }

  var smsPermissionGranted by
    remember {
      mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
          PackageManager.PERMISSION_GRANTED &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
          PackageManager.PERMISSION_GRANTED,
      )
    }
  val smsPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
      val sendOk = perms[Manifest.permission.SEND_SMS] == true
      val readOk = perms[Manifest.permission.READ_SMS] == true
      smsPermissionGranted = sendOk && readOk
      viewModel.refreshGatewayConnection()
    }

  DisposableEffect(lifecycleOwner, context) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          micPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
              PackageManager.PERMISSION_GRANTED
          notificationsPermissionGranted = hasNotificationsPermission(context)
          notificationListenerEnabled = isNotificationListenerEnabled(context)
          photosPermissionGranted =
            ContextCompat.checkSelfPermission(context, photosPermission) ==
              PackageManager.PERMISSION_GRANTED
          contactsPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
              PackageManager.PERMISSION_GRANTED &&
              ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
              PackageManager.PERMISSION_GRANTED
          calendarPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
              PackageManager.PERMISSION_GRANTED &&
              ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
              PackageManager.PERMISSION_GRANTED
          callLogPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
              PackageManager.PERMISSION_GRANTED
          motionPermissionGranted =
            !motionPermissionRequired ||
              ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
              PackageManager.PERMISSION_GRANTED
          smsPermissionGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
              PackageManager.PERMISSION_GRANTED &&
              ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
              PackageManager.PERMISSION_GRANTED
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  fun setCameraEnabledChecked(checked: Boolean) {
    if (!checked) {
      viewModel.setCameraEnabled(false)
      return
    }

    val cameraOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    if (cameraOk) {
      viewModel.setCameraEnabled(true)
    } else {
      permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }
  }

  fun requestLocationPermissions() {
    val fineOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    val coarseOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (fineOk || coarseOk) {
      viewModel.setLocationMode(LocationMode.WhileUsing)
    } else {
      pendingLocationRequest = true
      locationPermissionLauncher.launch(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
      )
    }
  }

  fun setPreciseLocationChecked(checked: Boolean) {
    if (!checked) {
      viewModel.setLocationPreciseEnabled(false)
      return
    }
    val fineOk =
      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    if (fineOk) {
      viewModel.setLocationPreciseEnabled(true)
    } else {
      pendingPreciseToggle = true
      locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
    }
  }

  if (showResetGatewayPairingDialog) {
    AlertDialog(
      onDismissRequest = { showResetGatewayPairingDialog = false },
      containerColor = mobileCardSurface,
      title = {
        Text("重置网关配对？", style = mobileHeadline, color = mobileText)
      },
      text = {
        Text(
          "将清除本机设备密钥、所有已存设备令牌、网关安装令牌与 TLS 指纹，并轮换客户端实例 ID，然后断开连接。若 OpenClaw 侧仍记着旧设备，请在网关主机执行文档 `android/docs/网关重新配对.md` 中的命令。",
          style = mobileCallout,
          color = mobileText,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showResetGatewayPairingDialog = false
            viewModel.resetGatewayPairingClientState()
          },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileDanger),
        ) {
          Text("重置")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showResetGatewayPairingDialog = false },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileTextSecondary),
        ) {
          Text("取消")
        }
      },
    )
  }

  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(mobileBackgroundGradient),
  ) {
    LazyColumn(
      state = listState,
      modifier =
        Modifier
          .fillMaxWidth()
          .fillMaxHeight()
          .imePadding()
          .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // ── Node ──
      item {
        Text(
          "设备",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
          OutlinedTextField(
            value = displayName,
            onValueChange = viewModel::setDisplayName,
            label = { Text("名称", style = mobileCaption1, color = mobileTextSecondary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            textStyle = mobileBody.copy(color = mobileText),
            colors = settingsTextFieldColors(),
          )
          HorizontalDivider(color = mobileBorder)
          Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            Text("$deviceModel · $appVersion", style = mobileCallout, color = mobileTextSecondary)
            Text(
              instanceId.take(8) + "…",
              style = mobileCaption1.copy(fontFamily = FontFamily.Monospace),
              color = mobileTextTertiary,
            )
            Text(
              "若云端已删设备但仍无「待批准」，可在此重置后再连；令牌需留空或改网关侧状态。",
              style = mobileCaption1,
              color = mobileTextSecondary,
              modifier = Modifier.padding(top = 6.dp),
            )
            Button(
              onClick = { showResetGatewayPairingDialog = true },
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(top = 8.dp),
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = mobileDanger.copy(alpha = 0.18f),
                  contentColor = mobileDanger,
                ),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("重置网关配对（本机）", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
            }
          }
        }
      }

      item {
        Text(
          "热词调试日志",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("最近 ${hotwordDebugLogs.size} 条", style = mobileCallout, color = mobileTextSecondary)
            Button(
              onClick = {
                viewModel.clearHotwordDebugLogs()
                hotwordTestStatus = null
              },
              enabled = hotwordDebugLogs.isNotEmpty(),
              colors = settingsPrimaryButtonColors(),
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

      // ── Media ──
      item {
        Text(
          "媒体",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("麦克风", style = mobileHeadline) },
            supportingContent = {
              Text(
                if (micPermissionGranted) "已授权" else "语音转写需要此权限。",
                style = mobileCallout,
              )
            },
            trailingContent = {
              Button(
                onClick = {
                  if (micPermissionGranted) {
                    openAppSettings(context)
                  } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  }
                },
                colors = settingsPrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  if (micPermissionGranted) "管理" else "允许",
                  style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                )
              }
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("相机", style = mobileHeadline) },
            supportingContent = { Text("拍照与短视频（仅前台）。", style = mobileCallout) },
            trailingContent = { Switch(checked = cameraEnabled, onCheckedChange = ::setCameraEnabledChecked) },
          )
        }
      }

      item {
        Text(
          "语音唤醒",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
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
                colors = settingsTextFieldColors(),
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
              colors = settingsPrimaryButtonColors(),
              shape = RoundedCornerShape(12.dp),
            ) {
              Text("测试唤醒", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "测试会模拟命中唤醒词并拉起麦克风，可在上方热词调试日志查看记录。",
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

      // ── Notifications & Messaging ──
      item {
        Text(
          "通知",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("系统通知", style = mobileHeadline) },
            supportingContent = {
              Text("提醒与前台服务。", style = mobileCallout)
            },
            trailingContent = {
              Button(
                onClick = {
                  if (notificationsPermissionGranted || Build.VERSION.SDK_INT < 33) {
                    openAppSettings(context)
                  } else {
                    notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  }
                },
                colors = settingsPrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  if (notificationsPermissionGranted) "管理" else "允许",
                  style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                )
              }
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("通知读取服务", style = mobileHeadline) },
            supportingContent = {
              Text("读取并响应系统通知。", style = mobileCallout)
            },
            trailingContent = {
              Button(
                onClick = { openNotificationListenerSettings(context) },
                colors = settingsPrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  if (notificationListenerEnabled) "管理" else "开启",
                  style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                )
              }
            },
          )
          if (smsPermissionAvailable) {
            HorizontalDivider(color = mobileBorder)
            ListItem(
              modifier = Modifier.fillMaxWidth(),
              colors = listItemColors,
              headlineContent = { Text("短信", style = mobileHeadline) },
              supportingContent = {
                Text("通过 Gateway 在本机发送与搜索短信。", style = mobileCallout)
              },
              trailingContent = {
                Button(
                  onClick = {
                    if (smsPermissionGranted) {
                      openAppSettings(context)
                    } else {
                      smsPermissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS))
                    }
                  },
                  colors = settingsPrimaryButtonColors(),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Text(
                    if (smsPermissionGranted) "管理" else "允许",
                    style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                  )
                }
              },
            )
          }
        }
      }

      // ── Data Access ──
      item {
        Text(
          "数据访问",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("照片", style = mobileHeadline) },
            supportingContent = { Text("访问最近照片。", style = mobileCallout) },
            trailingContent = {
              Button(
                onClick = {
                  if (photosPermissionGranted) {
                    openAppSettings(context)
                  } else {
                    photosPermissionLauncher.launch(photosPermission)
                  }
                },
                colors = settingsPrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  if (photosPermissionGranted) "管理" else "允许",
                  style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                )
              }
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("联系人", style = mobileHeadline) },
            supportingContent = { Text("搜索与添加联系人。", style = mobileCallout) },
            trailingContent = {
              Button(
                onClick = {
                  if (contactsPermissionGranted) {
                    openAppSettings(context)
                  } else {
                    contactsPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
                  }
                },
                colors = settingsPrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  if (contactsPermissionGranted) "管理" else "允许",
                  style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                )
              }
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("日历", style = mobileHeadline) },
            supportingContent = { Text("读取与创建日程。", style = mobileCallout) },
            trailingContent = {
              Button(
                onClick = {
                  if (calendarPermissionGranted) {
                    openAppSettings(context)
                  } else {
                    calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                  }
                },
                colors = settingsPrimaryButtonColors(),
                shape = RoundedCornerShape(14.dp),
              ) {
                Text(
                  if (calendarPermissionGranted) "管理" else "允许",
                  style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                )
              }
            },
          )
          if (callLogPermissionAvailable) {
            HorizontalDivider(color = mobileBorder)
            ListItem(
              modifier = Modifier.fillMaxWidth(),
              colors = listItemColors,
              headlineContent = { Text("通话记录", style = mobileHeadline) },
              supportingContent = { Text("搜索最近通话记录。", style = mobileCallout) },
              trailingContent = {
                Button(
                  onClick = {
                    if (callLogPermissionGranted) {
                      openAppSettings(context)
                    } else {
                      callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                    }
                  },
                  colors = settingsPrimaryButtonColors(),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Text(
                    if (callLogPermissionGranted) "管理" else "允许",
                    style = mobileCallout.copy(fontWeight = FontWeight.Bold),
                  )
                }
              },
            )
          }
          if (motionAvailable) {
            HorizontalDivider(color = mobileBorder)
            ListItem(
              modifier = Modifier.fillMaxWidth(),
              colors = listItemColors,
              headlineContent = { Text("运动识别", style = mobileHeadline) },
              supportingContent = { Text("计步与活动识别。", style = mobileCallout) },
              trailingContent = {
                val motionButtonLabel =
                  when {
                    !motionPermissionRequired -> "管理"
                    motionPermissionGranted -> "管理"
                    else -> "允许"
                  }
                Button(
                  onClick = {
                    if (!motionPermissionRequired || motionPermissionGranted) {
                      openAppSettings(context)
                    } else {
                      motionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                  },
                  colors = settingsPrimaryButtonColors(),
                  shape = RoundedCornerShape(14.dp),
                ) {
                  Text(motionButtonLabel, style = mobileCallout.copy(fontWeight = FontWeight.Bold))
                }
              },
            )
          }
        }
      }

      // ── Location ──
      item {
        Text(
          "位置",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("关闭", style = mobileHeadline) },
            supportingContent = { Text("不共享位置。", style = mobileCallout) },
            trailingContent = {
              RadioButton(
                selected = locationMode == LocationMode.Off,
                onClick = { viewModel.setLocationMode(LocationMode.Off) },
              )
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("使用应用期间", style = mobileHeadline) },
            supportingContent = { Text("仅在 OpenClaw 打开时使用位置。", style = mobileCallout) },
            trailingContent = {
              RadioButton(
                selected = locationMode == LocationMode.WhileUsing,
                onClick = { requestLocationPermissions() },
              )
            },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("精确位置", style = mobileHeadline) },
            supportingContent = { Text("在可用时使用高精度 GPS。", style = mobileCallout) },
            trailingContent = {
              Switch(
                checked = locationPreciseEnabled,
                onCheckedChange = ::setPreciseLocationChecked,
                enabled = locationMode != LocationMode.Off,
              )
            },
          )
        }
      }

      // ── Preferences ──
      item {
        Text(
          "偏好",
          style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
          color = mobileAccent,
        )
      }
      item {
        Column(modifier = Modifier.settingsRowModifier()) {
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("保持亮屏", style = mobileHeadline) },
            supportingContent = { Text("应用打开时屏幕保持常亮。", style = mobileCallout) },
            trailingContent = { Switch(checked = preventSleep, onCheckedChange = viewModel::setPreventSleep) },
          )
          HorizontalDivider(color = mobileBorder)
          ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = listItemColors,
            headlineContent = { Text("Canvas 调试", style = mobileHeadline) },
            supportingContent = { Text("在画布上显示状态浮层。", style = mobileCallout) },
            trailingContent = {
              Switch(
                checked = canvasDebugStatusEnabled,
                onCheckedChange = viewModel::setCanvasDebugStatusEnabled,
              )
            },
          )
        }
      }

      item { Spacer(modifier = Modifier.height(24.dp)) }
    }
  }
}

@Composable
private fun settingsTextFieldColors() =
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
private fun Modifier.settingsRowModifier() =
  this
    .fillMaxWidth()
    .border(width = 1.dp, color = mobileBorder, shape = RoundedCornerShape(14.dp))
    .background(mobileCardSurface, RoundedCornerShape(14.dp))

@Composable
private fun settingsPrimaryButtonColors() =
  ButtonDefaults.buttonColors(
    containerColor = mobileAccent,
    contentColor = Color.White,
    disabledContainerColor = mobileAccent.copy(alpha = 0.45f),
    disabledContentColor = Color.White.copy(alpha = 0.9f),
  )

private fun openAppSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    )
  context.startActivity(intent)
}

private fun openNotificationListenerSettings(context: Context) {
  val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
  runCatching {
    context.startActivity(intent)
  }.getOrElse {
    openAppSettings(context)
  }
}

private fun hasNotificationsPermission(context: Context): Boolean {
  if (Build.VERSION.SDK_INT < 33) return true
  return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
  return DeviceNotificationListenerService.isAccessEnabled(context)
}

private fun hasMotionCapabilities(context: Context): Boolean {
  val sensorManager = context.getSystemService(SensorManager::class.java) ?: return false
  return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
          sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
}
