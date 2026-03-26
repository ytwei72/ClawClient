package ai.openclaw.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.mobileCardSurface

private enum class ConnectInputMode {
  SetupCode,
  Manual,
}

@Composable
fun ConnectTabScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val statusText by viewModel.statusText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val remoteAddress by viewModel.remoteAddress.collectAsState()
  val manualHost by viewModel.manualHost.collectAsState()
  val manualPort by viewModel.manualPort.collectAsState()
  val manualTls by viewModel.manualTls.collectAsState()
  val manualEnabled by viewModel.manualEnabled.collectAsState()
  val gatewayToken by viewModel.gatewayToken.collectAsState()
  val pendingTrust by viewModel.pendingGatewayTrust.collectAsState()

  var advancedOpen by rememberSaveable { mutableStateOf(false) }
  var inputMode by
    remember(manualEnabled, manualHost, gatewayToken) {
      mutableStateOf(
        if (manualEnabled || manualHost.isNotBlank() || gatewayToken.trim().isNotEmpty()) {
          ConnectInputMode.Manual
        } else {
          ConnectInputMode.SetupCode
        },
      )
    }
  var setupCode by rememberSaveable { mutableStateOf("") }
  var manualHostInput by rememberSaveable { mutableStateOf(manualHost.ifBlank { "10.0.2.2" }) }
  var manualPortInput by rememberSaveable { mutableStateOf(manualPort.toString()) }
  var manualTlsInput by rememberSaveable { mutableStateOf(manualTls) }
  var passwordInput by rememberSaveable { mutableStateOf("") }
  var validationText by rememberSaveable { mutableStateOf<String?>(null) }

  if (pendingTrust != null) {
    val prompt = pendingTrust!!
    AlertDialog(
      onDismissRequest = { viewModel.declineGatewayTrustPrompt() },
      containerColor = mobileCardSurface,
      title = { Text("信任此 Gateway？", style = mobileHeadline, color = mobileText) },
      text = {
        Text(
          "首次 TLS 连接。\n\n信任前请核对以下 SHA-256 指纹：\n${prompt.fingerprintSha256}",
          style = mobileCallout,
          color = mobileText,
        )
      },
      confirmButton = {
        TextButton(
          onClick = { viewModel.acceptGatewayTrustPrompt() },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileAccent),
        ) {
          Text("信任并继续")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { viewModel.declineGatewayTrustPrompt() },
          colors = ButtonDefaults.textButtonColors(contentColor = mobileTextSecondary),
        ) {
          Text("取消")
        }
      },
    )
  }

  val setupResolvedEndpoint = remember(setupCode) { decodeGatewaySetupCode(setupCode)?.url?.let { parseGatewayEndpoint(it)?.displayUrl } }
  val manualResolvedEndpoint = remember(manualHostInput, manualPortInput, manualTlsInput) {
    composeGatewayManualUrl(manualHostInput, manualPortInput, manualTlsInput)?.let { parseGatewayEndpoint(it)?.displayUrl }
  }

  val activeEndpoint =
    remember(isConnected, remoteAddress, setupResolvedEndpoint, manualResolvedEndpoint, inputMode) {
      when {
        isConnected && !remoteAddress.isNullOrBlank() -> remoteAddress!!
        inputMode == ConnectInputMode.SetupCode -> setupResolvedEndpoint ?: "未设置"
        else -> manualResolvedEndpoint ?: "未设置"
      }
    }

  val showDiagnostics = !isConnected && gatewayStatusHasDiagnostics(statusText)
  val statusLabel = formatConnectionStatusForUi(gatewayStatusForDisplay(statusText))

  Column(
    modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Gateway 连接", style = mobileTitle1, color = mobileText)
      Text(
        if (isConnected) "Gateway 已连接并就绪。" else "请先连接 Gateway 以开始使用。",
        style = mobileCallout,
        color = mobileTextSecondary,
      )
    }

    // Status cards in a unified card group
    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = mobileCardSurface,
      border = BorderStroke(1.dp, mobileBorder),
    ) {
      Column {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = mobileAccentSoft,
          ) {
            Icon(
              imageVector = Icons.Default.Link,
              contentDescription = null,
              modifier = Modifier.padding(8.dp).size(18.dp),
              tint = mobileAccent,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("端点", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            Text(activeEndpoint, style = mobileBody.copy(fontFamily = FontFamily.Monospace), color = mobileText)
          }
        }
        HorizontalDivider(color = mobileBorder)
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isConnected) mobileSuccessSoft else mobileSurface,
          ) {
            Icon(
              imageVector = Icons.Default.Cloud,
              contentDescription = null,
              modifier = Modifier.padding(8.dp).size(18.dp),
              tint = if (isConnected) mobileSuccess else mobileTextTertiary,
            )
          }
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("状态", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            Text(formatConnectionStatusForUi(gatewayStatusForDisplay(statusText)), style = mobileBody, color = if (isConnected) mobileSuccess else mobileText)
          }
        }
      }
    }

    if (isConnected) {
      // Outlined secondary button when connected — don't scream "danger"
      Button(
        onClick = {
          viewModel.disconnect()
          validationText = null
        },
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = mobileCardSurface,
            contentColor = mobileDanger,
          ),
        border = BorderStroke(1.dp, mobileDanger.copy(alpha = 0.4f)),
      ) {
        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("断开连接", style = mobileHeadline.copy(fontWeight = FontWeight.SemiBold))
      }
    } else {
      Button(
        onClick = {
          if (statusText.contains("operator offline", ignoreCase = true)) {
            validationText = null
            viewModel.refreshGatewayConnection()
            return@Button
          }

          val config =
            resolveGatewayConnectConfig(
              useSetupCode = inputMode == ConnectInputMode.SetupCode,
              setupCode = setupCode,
              manualHost = manualHostInput,
              manualPort = manualPortInput,
              manualTls = manualTlsInput,
              fallbackToken = gatewayToken,
              fallbackPassword = passwordInput,
            )

          if (config == null) {
            validationText =
              if (inputMode == ConnectInputMode.SetupCode) {
                "请粘贴有效的安装码以连接。"
              } else {
                "请输入有效的主机与端口以连接。"
              }
            return@Button
          }

          validationText = null
          viewModel.setManualEnabled(true)
          viewModel.setManualHost(config.host)
          viewModel.setManualPort(config.port)
          viewModel.setManualTls(config.tls)
          viewModel.setGatewayBootstrapToken(config.bootstrapToken)
          if (config.token.isNotBlank()) {
            viewModel.setGatewayToken(config.token)
          } else if (config.bootstrapToken.isNotBlank()) {
            viewModel.setGatewayToken("")
          }
          viewModel.setGatewayPassword(config.password)
          viewModel.connectManual()
        },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = mobileAccent,
            contentColor = Color.White,
          ),
      ) {
        Text("连接 Gateway", style = mobileHeadline.copy(fontWeight = FontWeight.Bold))
      }
    }

    if (showDiagnostics) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = mobileWarningSoft,
        border = BorderStroke(1.dp, mobileWarning.copy(alpha = 0.25f)),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text("最近 Gateway 错误", style = mobileHeadline, color = mobileWarning)
          Text(statusLabel, style = mobileBody.copy(fontFamily = FontFamily.Monospace), color = mobileText)
          Text("OpenClaw Android ${openClawAndroidVersionLabel()}", style = mobileCaption1, color = mobileTextSecondary)
          Button(
            onClick = {
              copyGatewayDiagnosticsReport(
                context = context,
                screen = "connect tab",
                gatewayAddress = activeEndpoint,
                statusText = statusLabel,
              )
            },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = mobileCardSurface,
                contentColor = mobileWarning,
              ),
            border = BorderStroke(1.dp, mobileWarning.copy(alpha = 0.3f)),
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("复制诊断报告", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
          }
        }
      }
    }

    Surface(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      color = mobileSurface,
      border = BorderStroke(1.dp, mobileBorder),
      onClick = { advancedOpen = !advancedOpen },
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text("高级选项", style = mobileHeadline, color = mobileText)
          Text("安装码、端点、TLS、令牌、密码与引导流程。", style = mobileCaption1, color = mobileTextSecondary)
        }
        Icon(
          imageVector = if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (advancedOpen) "收起高级选项" else "展开高级选项",
          tint = mobileTextSecondary,
        )
      }
    }

    AnimatedVisibility(visible = advancedOpen) {
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = mobileCardSurface,
        border = BorderStroke(1.dp, mobileBorder),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text("连接方式", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MethodChip(
              label = "安装码",
              active = inputMode == ConnectInputMode.SetupCode,
              onClick = { inputMode = ConnectInputMode.SetupCode },
            )
            MethodChip(
              label = "手动",
              active = inputMode == ConnectInputMode.Manual,
              onClick = { inputMode = ConnectInputMode.Manual },
            )
          }

          Text("在 Gateway 主机上执行：", style = mobileCallout, color = mobileTextSecondary)
          CommandBlock("openclaw qr --setup-code-only")
          CommandBlock("openclaw qr --json")

          if (inputMode == ConnectInputMode.SetupCode) {
            Text("安装码", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            OutlinedTextField(
              value = setupCode,
              onValueChange = {
                setupCode = it
                validationText = null
              },
              placeholder = { Text("粘贴安装码", style = mobileBody, color = mobileTextTertiary) },
              modifier = Modifier.fillMaxWidth(),
              minLines = 3,
              maxLines = 5,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
              textStyle = mobileBody.copy(fontFamily = FontFamily.Monospace, color = mobileText),
              shape = RoundedCornerShape(14.dp),
              colors = outlinedColors(),
            )
            if (!setupResolvedEndpoint.isNullOrBlank()) {
              EndpointPreview(endpoint = setupResolvedEndpoint)
            }
          } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              QuickFillChip(
                label = "Android 模拟器",
                onClick = {
                  manualHostInput = "10.0.2.2"
                  manualPortInput = "18789"
                  manualTlsInput = false
                  validationText = null
                },
              )
              QuickFillChip(
                label = "本机",
                onClick = {
                  manualHostInput = "127.0.0.1"
                  manualPortInput = "18789"
                  manualTlsInput = false
                  validationText = null
                },
              )
            }

            Text("主机", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            OutlinedTextField(
              value = manualHostInput,
              onValueChange = {
                manualHostInput = it
                validationText = null
              },
              placeholder = { Text("10.0.2.2", style = mobileBody, color = mobileTextTertiary) },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              textStyle = mobileBody.copy(color = mobileText),
              shape = RoundedCornerShape(14.dp),
              colors = outlinedColors(),
            )

            Text("端口", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            OutlinedTextField(
              value = manualPortInput,
              onValueChange = {
                manualPortInput = it
                validationText = null
              },
              placeholder = { Text("18789", style = mobileBody, color = mobileTextTertiary) },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
              textStyle = mobileBody.copy(fontFamily = FontFamily.Monospace, color = mobileText),
              shape = RoundedCornerShape(14.dp),
              colors = outlinedColors(),
            )

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("使用 TLS", style = mobileHeadline, color = mobileText)
                Text("使用安全 WebSocket（`wss`）。", style = mobileCallout, color = mobileTextSecondary)
              }
              Switch(
                checked = manualTlsInput,
                onCheckedChange = {
                  manualTlsInput = it
                  validationText = null
                },
                colors =
                  SwitchDefaults.colors(
                    checkedTrackColor = mobileAccent,
                    uncheckedTrackColor = mobileBorderStrong,
                    checkedThumbColor = Color.White,
                    uncheckedThumbColor = Color.White,
                  ),
              )
            }

            Text("令牌（可选）", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            OutlinedTextField(
              value = gatewayToken,
              onValueChange = { viewModel.setGatewayToken(it) },
              placeholder = { Text("令牌", style = mobileBody, color = mobileTextTertiary) },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
              textStyle = mobileBody.copy(color = mobileText),
              shape = RoundedCornerShape(14.dp),
              colors = outlinedColors(),
            )

            Text("密码（可选）", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
            OutlinedTextField(
              value = passwordInput,
              onValueChange = { passwordInput = it },
              placeholder = { Text("密码", style = mobileBody, color = mobileTextTertiary) },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
              textStyle = mobileBody.copy(color = mobileText),
              shape = RoundedCornerShape(14.dp),
              colors = outlinedColors(),
            )

            if (!manualResolvedEndpoint.isNullOrBlank()) {
              EndpointPreview(endpoint = manualResolvedEndpoint)
            }
          }

          HorizontalDivider(color = mobileBorder)

          TextButton(onClick = { viewModel.setOnboardingCompleted(false) }) {
            Text("重新运行引导", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
          }
        }
      }
    }

    if (!validationText.isNullOrBlank()) {
      Text(validationText!!, style = mobileCaption1, color = mobileWarning)
    }
  }
}

@Composable
private fun MethodChip(label: String, active: Boolean, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    modifier = Modifier.height(40.dp),
    shape = RoundedCornerShape(12.dp),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = if (active) mobileAccent else mobileSurface,
        contentColor = if (active) Color.White else mobileText,
      ),
    border = BorderStroke(1.dp, if (active) mobileAccentBorderStrong else mobileBorderStrong),
  ) {
    Text(label, style = mobileCaption1.copy(fontWeight = FontWeight.Bold))
  }
}

@Composable
private fun QuickFillChip(label: String, onClick: () -> Unit) {
  Button(
    onClick = onClick,
    shape = RoundedCornerShape(999.dp),
    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = mobileAccentSoft,
        contentColor = mobileAccent,
      ),
    elevation = null,
  ) {
    Text(label, style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold))
  }
}

@Composable
private fun CommandBlock(command: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = mobileCodeBg,
    border = BorderStroke(1.dp, mobileCodeBorder),
  ) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.width(3.dp).height(42.dp).background(mobileCodeAccent))
      Text(
        text = command,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        style = mobileCallout.copy(fontFamily = FontFamily.Monospace),
        color = mobileCodeText,
      )
    }
  }
}

@Composable
private fun EndpointPreview(endpoint: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    HorizontalDivider(color = mobileBorder)
    Text("解析后的端点", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
    Text(endpoint, style = mobileCallout.copy(fontFamily = FontFamily.Monospace), color = mobileText)
    HorizontalDivider(color = mobileBorder)
  }
}

@Composable
private fun outlinedColors() =
  OutlinedTextFieldDefaults.colors(
    focusedContainerColor = mobileSurface,
    unfocusedContainerColor = mobileSurface,
    focusedBorderColor = mobileAccent,
    unfocusedBorderColor = mobileBorder,
    focusedTextColor = mobileText,
    unfocusedTextColor = mobileText,
    cursorColor = mobileAccent,
  )
