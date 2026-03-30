package ai.openclaw.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import ai.openclaw.app.BuildConfig

internal fun openClawAndroidVersionLabel(): String {
  val versionName = BuildConfig.VERSION_NAME.trim().ifEmpty { "dev" }
  return if (BuildConfig.DEBUG && !versionName.contains("dev", ignoreCase = true)) {
    "$versionName-dev"
  } else {
    versionName
  }
}

internal fun gatewayStatusForDisplay(statusText: String): String {
  return statusText.trim().ifEmpty { "离线" }
}

/** Maps internal/runtime English status lines to Chinese for the status chip and connection UI. */
internal fun formatConnectionStatusForUi(statusText: String): String {
  val t = statusText.trim()
  if (t.isEmpty()) return "离线"
  if (t == "Offline") return "离线"
  if (t == "Connected") return "已连接"
  if (t == "Connecting…") return "连接中…"
  if (t == "Reconnecting…") return "重新连接中…"
  if (t == "Connected (node offline)") return "已连接（节点离线）"
  if (t == "Connected (operator offline)") return "已连接（操作端离线）"
  if (t.startsWith("Connected (operator: ") && t.endsWith(")")) {
    val inner = t.removePrefix("Connected (operator: ").removeSuffix(")")
    return "已连接（操作端：$inner）"
  }
  if (t == "Verify gateway TLS fingerprint…") return "正在验证 Gateway TLS 指纹…"
  if (t == "Failed: can't read TLS fingerprint") return "失败：无法读取 TLS 指纹"
  if (t == "Failed: no cached gateway endpoint") return "失败：无已缓存的 Gateway 端点"
  if (t == "Failed: invalid manual host/port") return "失败：主机或端口无效"
  return t
}

internal fun gatewayStatusHasDiagnostics(statusText: String): Boolean {
  val raw = statusText.trim().lowercase()
  if (raw.isEmpty()) return false
  return raw != "offline" && !raw.contains("connecting") && !raw.contains("reconnecting")
}

internal fun gatewayStatusLooksLikePairing(statusText: String): Boolean {
  val raw = statusText.trim().lowercase()
  return raw.contains("pair") || raw.contains("approve")
}

internal fun buildGatewayDiagnosticsReport(
  screen: String,
  gatewayAddress: String,
  statusText: String,
): String {
  val device =
    listOfNotNull(Build.MANUFACTURER, Build.MODEL)
      .joinToString(" ")
      .trim()
      .ifEmpty { "Android" }
  val androidVersion = Build.VERSION.RELEASE?.trim().orEmpty().ifEmpty { Build.VERSION.SDK_INT.toString() }
  val endpoint = gatewayAddress.trim().ifEmpty { "unknown" }
  val status = gatewayStatusForDisplay(statusText)
  return """
    请协助诊断本机 OpenClaw Android 客户端与 Gateway 的连接问题。

    请说明：
    - 仅选一种路径：本机 / 同局域网 / Tailscale / 公网地址
    - 归类为：配对或鉴权、TLS 信任、路由或地址端口错误、Gateway 宕机等
    - 引用下方应用状态/错误原文
    - `openclaw devices list` 是否应出现待配对请求
    - 若需更多信息，可索取 `openclaw qr --json`、`openclaw devices list`、`openclaw nodes status`
    - 给出下一步具体命令或操作

    调试信息：
    - 界面：$screen
    - 应用版本：${openClawAndroidVersionLabel()}
    - 设备：$device
    - Android：$androidVersion（SDK ${Build.VERSION.SDK_INT}）
    - Gateway 地址：$endpoint
    - 状态/错误：$status
  """.trimIndent()
}

internal fun copyGatewayDiagnosticsReport(
  context: Context,
  screen: String,
  gatewayAddress: String,
  statusText: String,
) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  val report = buildGatewayDiagnosticsReport(screen = screen, gatewayAddress = gatewayAddress, statusText = statusText)
  clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw Gateway 诊断信息", report))
  Toast.makeText(context, "已复制 Gateway 诊断信息", Toast.LENGTH_SHORT).show()
}

internal fun copyPlainTextToClipboard(
  context: Context,
  clipLabel: String,
  text: String,
  toastMessage: String,
) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText(clipLabel, text))
  Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
}
