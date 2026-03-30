package ai.openclaw.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/** 连接页默认网关共享令牌（安装/配对令牌）。 */
internal const val DEFAULT_MANUAL_CONNECT_GATEWAY_TOKEN = "1d8bb9d66e60b6a35e2c2d098811af129629def393969ed6"

@Composable
fun ConnectTabScreen(viewModel: MainViewModel) {
  val context = LocalContext.current
  val statusText by viewModel.statusText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val remoteAddress by viewModel.remoteAddress.collectAsState()
  val manualHost by viewModel.manualHost.collectAsState()
  val manualPort by viewModel.manualPort.collectAsState()
  val manualTls by viewModel.manualTls.collectAsState()
  val gatewayToken by viewModel.gatewayToken.collectAsState()
  val pairingDeviceId by viewModel.pairingDeviceId.collectAsState()
  val pendingTrust by viewModel.pendingGatewayTrust.collectAsState()

  var advancedOpen by rememberSaveable { mutableStateOf(false) }
  var showResetGatewayPairingDialog by rememberSaveable { mutableStateOf(false) }

  LaunchedEffect(gatewayToken) {
    if (gatewayToken.isBlank()) {
      viewModel.setGatewayToken(DEFAULT_MANUAL_CONNECT_GATEWAY_TOKEN)
    }
  }

  var manualHostInput by rememberSaveable { mutableStateOf(manualHost.ifBlank { "" }) }
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

  if (showResetGatewayPairingDialog) {
    AlertDialog(
      onDismissRequest = { showResetGatewayPairingDialog = false },
      containerColor = mobileCardSurface,
      title = { Text("重置网关配对？", style = mobileHeadline, color = mobileText) },
      text = {
        Text(
          "将清除本机设备密钥、所有已存设备令牌、网关安装令牌与 TLS 指纹，并轮换客户端实例 ID，然后断开连接。\n\n" +
            "若网关仍登记本设备，请先在 Gateway 主机上执行「移除设备」：在该主机终端运行 openclaw devices list 查看 device_id，再执行 openclaw devices remove <device_id>（高级选项中可复制含本机 device_id 的完整命令）。",
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

  val manualResolvedEndpoint = remember(manualHostInput, manualPortInput, manualTlsInput) {
    composeGatewayManualUrl(manualHostInput, manualPortInput, manualTlsInput)?.let { parseGatewayEndpoint(it)?.displayUrl }
  }

  val activeEndpoint =
    remember(isConnected, remoteAddress, manualResolvedEndpoint) {
      when {
        isConnected && !remoteAddress.isNullOrBlank() -> remoteAddress!!
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
              useSetupCode = false,
              setupCode = "",
              manualHost = manualHostInput,
              manualPort = manualPortInput,
              manualTls = manualTlsInput,
              fallbackToken = gatewayToken,
              fallbackPassword = passwordInput,
            )

          if (config == null) {
            validationText = "请输入有效的主机与端口以连接。"
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = mobileAccentSoft,
        ) {
          Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = null,
            modifier = Modifier.padding(8.dp).size(18.dp),
            tint = mobileAccent,
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          Text("高级选项", style = mobileHeadline, color = mobileText)
          Text("连接参数；以及两项独立操作——打开引导、重置本机配对（用途不同）。", style = mobileCaption1, color = mobileTextSecondary)
        }
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = mobileAccentSoft,
        ) {
          Icon(
            imageVector = if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (advancedOpen) "收起高级选项" else "展开高级选项",
            modifier = Modifier.padding(8.dp).size(20.dp),
            tint = mobileAccent,
          )
        }
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
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("连接参数", style = mobileHeadline, color = mobileText)
            Text(
              "手动指定 Gateway 与可选令牌/密码，不涉及本机设备身份。",
              style = mobileCaption1,
              color = mobileTextSecondary,
            )
          }

          Text("主机", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
          OutlinedTextField(
            value = manualHostInput,
            onValueChange = {
              manualHostInput = it
              validationText = null
            },
            placeholder = { Text("网关主机名或 IP", style = mobileBody, color = mobileTextTertiary) },
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

          HorizontalDivider(color = mobileBorder)

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("维护", style = mobileHeadline, color = mobileText)
            Text(
              "「打开引导」与「重置配对」不是同一件事：前者只复查向导，后者清除本机密钥与网关侧登记所需的数据。请按需选择。",
              style = mobileCallout,
              color = mobileTextSecondary,
            )
          }

          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = mobileAccentSoft,
            border = BorderStroke(1.dp, mobileAccent.copy(alpha = 0.28f)),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
              ) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = mobileAccent.copy(alpha = 0.12f),
                ) {
                  Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                    tint = mobileAccent,
                  )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text("重新运行引导", style = mobileHeadline, color = mobileText)
                  Text(
                    "再次打开首次设置向导（欢迎、Gateway、权限等）。不会清除本机设备密钥，也不替代网关侧的移除/批准操作。",
                    style = mobileCallout,
                    color = mobileTextSecondary,
                  )
                }
              }
              OutlinedButton(
                onClick = { viewModel.setOnboardingCompleted(false) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, mobileAccent.copy(alpha = 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = mobileAccent),
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("打开首次设置向导", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
              }
            }
          }

          Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = mobileDangerSoft,
            border = BorderStroke(1.dp, mobileDanger.copy(alpha = 0.35f)),
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
              ) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = mobileDanger.copy(alpha = 0.12f),
                ) {
                  Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                    tint = mobileDanger,
                  )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text("重置网关配对（本机）", style = mobileHeadline, color = mobileText)
                  Text(
                    "清除本机设备密钥、已存令牌与 TLS 信任等并断开连接；用于换新配对。影响大于仅打开引导。",
                    style = mobileCallout,
                    color = mobileTextSecondary,
                  )
                }
              }
              Text(
                "在网关主机终端执行 openclaw devices remove …。下方为含本机 device_id 的完整命令，可一键复制。若本机曾重置身份而网关仍为旧登记，以网关 list 中的 id 为准。",
                style = mobileCallout,
                color = mobileTextSecondary,
              )
              CopyableCommandRow(command = "openclaw devices remove $pairingDeviceId", context = context)
              Button(
                onClick = { showResetGatewayPairingDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = mobileDanger.copy(alpha = 0.28f),
                    contentColor = mobileDanger,
                  ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, mobileDanger.copy(alpha = 0.45f)),
              ) {
                Text("执行重置（需确认）", style = mobileCallout.copy(fontWeight = FontWeight.SemiBold))
              }
            }
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
private fun CopyableCommandRow(command: String, context: Context) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    color = mobileCodeBg,
    border = BorderStroke(1.dp, mobileCodeBorder),
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(end = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(modifier = Modifier.width(3.dp).height(42.dp).background(mobileCodeAccent))
        Text(
          text = command,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          style = mobileCallout.copy(fontFamily = FontFamily.Monospace),
          color = mobileCodeText,
        )
      }
      IconButton(
        onClick = {
          copyPlainTextToClipboard(
            context = context,
            clipLabel = "openclaw devices remove",
            text = command,
            toastMessage = "已复制命令",
          )
        },
        modifier = Modifier.align(Alignment.CenterEnd),
      ) {
        Icon(Icons.Default.ContentCopy, contentDescription = "复制命令", tint = mobileAccent)
      }
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
