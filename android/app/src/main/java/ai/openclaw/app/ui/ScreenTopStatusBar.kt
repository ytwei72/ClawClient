package ai.openclaw.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api

/** 连接状态圆点与文案配色（用于 Scaffold 顶部栏右上角）。 */
enum class ScreenStatusVisual {
  Connected,
  Connecting,
  Warning,
  Error,
  Offline,
}

fun deriveScreenStatusVisual(statusText: String, isConnected: Boolean): ScreenStatusVisual {
  val lower = statusText.lowercase()
  return when {
    isConnected -> ScreenStatusVisual.Connected
    lower.contains("connecting") || lower.contains("reconnecting") -> ScreenStatusVisual.Connecting
    lower.contains("pairing") || lower.contains("approval") || lower.contains("auth") -> ScreenStatusVisual.Warning
    lower.contains("error") || lower.contains("failed") -> ScreenStatusVisual.Error
    else -> ScreenStatusVisual.Offline
  }
}

/**
 * 屏幕顶部整行：左侧标题 + 右侧状态簇（麦克风 / 扬声器 / 连接）。
 * - 无 [titleSubtitle] 时：左侧为应用级标题（如 OpenClaw），使用 [mobileTitle2]。
 * - 有 [titleSubtitle] 时：左侧为「主行 + 副行」（如聊天页的 Agent / 会话），用语义层次与右侧系统状态区分。
 *
 * 使用 [WindowInsets.safeDrawing] 顶边与左右留白，适合作为 [androidx.compose.material3.Scaffold] 的 topBar。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopStatusBar(
  titleText: String,
  titleSubtitle: String? = null,
  titleClickable: Boolean,
  onTitleClick: () -> Unit,
  connectionStatusText: String,
  statusVisual: ScreenStatusVisual,
  micEnabled: Boolean,
  micCooldown: Boolean,
  speakerEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
  Surface(
    modifier = modifier.fillMaxWidth().windowInsetsPadding(safeInsets),
    color = Color.Transparent,
    shadowElevation = 0.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Surface(
        modifier = Modifier.weight(1f),
        onClick = {
          if (titleClickable) onTitleClick()
        },
        color = Color.Transparent,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          if (titleSubtitle != null) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = titleText,
                style = mobileHeadline,
                color = mobileText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = titleSubtitle,
                style = mobileCaption1,
                color = mobileTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
            if (titleClickable) {
              Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = "选择 Agent 与会话",
                modifier = Modifier.size(22.dp),
                tint = mobileTextTertiary,
              )
            }
          } else {
            Text(
              text = titleText,
              style = mobileTitle2,
              color = mobileText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
      ScreenTopStatusBarTrailing(
        connectionStatusText = connectionStatusText,
        statusVisual = statusVisual,
        micEnabled = micEnabled,
        micCooldown = micCooldown,
        speakerEnabled = speakerEnabled,
      )
    }
  }
}

/**
 * 仅右上角状态簇：麦克风、扬声器图标 + 连接状态胶囊。
 * 可在自定义页面中单独放在 `Alignment.TopEnd` 等位置复用。
 */
@Composable
fun ScreenTopStatusBarTrailing(
  connectionStatusText: String,
  statusVisual: ScreenStatusVisual,
  micEnabled: Boolean,
  micCooldown: Boolean,
  speakerEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MediaStatusIndicators(
      micEnabled = micEnabled,
      micCooldown = micCooldown,
      speakerEnabled = speakerEnabled,
    )
    ConnectionStatusChip(
      statusText = connectionStatusText,
      statusVisual = statusVisual,
    )
  }
}

@Composable
private fun MediaStatusIndicators(
  micEnabled: Boolean,
  micCooldown: Boolean,
  speakerEnabled: Boolean,
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val micTint =
      when {
        micCooldown -> mobileTextTertiary
        micEnabled -> mobileSuccess
        else -> mobileTextTertiary
      }
    Icon(
      imageVector = if (micEnabled) Icons.Default.Mic else Icons.Default.MicOff,
      contentDescription =
        when {
          micCooldown -> "麦克风冷却中"
          micEnabled -> "麦克风已开"
          else -> "麦克风已关"
        },
      modifier =
        Modifier
          .size(20.dp)
          .alpha(if (micCooldown) 0.55f else 1f),
      tint = micTint,
    )
    Icon(
      imageVector = if (speakerEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
      contentDescription = if (speakerEnabled) "扬声器已开" else "扬声器已关",
      modifier = Modifier.size(20.dp),
      tint = if (speakerEnabled) mobileSuccess else mobileTextTertiary,
    )
  }
}

@Composable
private fun ConnectionStatusChip(statusText: String, statusVisual: ScreenStatusVisual) {
  val (chipBg, chipDot, chipText, chipBorder) =
    when (statusVisual) {
      ScreenStatusVisual.Connected ->
        listOf(
          mobileSuccessSoft,
          mobileSuccess,
          mobileSuccess,
          LocalMobileColors.current.chipBorderConnected,
        )
      ScreenStatusVisual.Connecting ->
        listOf(
          mobileAccentSoft,
          mobileAccent,
          mobileAccent,
          LocalMobileColors.current.chipBorderConnecting,
        )
      ScreenStatusVisual.Warning ->
        listOf(
          mobileWarningSoft,
          mobileWarning,
          mobileWarning,
          LocalMobileColors.current.chipBorderWarning,
        )
      ScreenStatusVisual.Error ->
        listOf(
          mobileDangerSoft,
          mobileDanger,
          mobileDanger,
          LocalMobileColors.current.chipBorderError,
        )
      ScreenStatusVisual.Offline ->
        listOf(
          mobileSurface,
          mobileTextTertiary,
          mobileTextSecondary,
          mobileBorder,
        )
    }

  Surface(
    shape = RoundedCornerShape(999.dp),
    color = chipBg,
    border = BorderStroke(1.dp, chipBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Surface(
        modifier = Modifier.padding(top = 1.dp),
        color = chipDot,
        shape = RoundedCornerShape(999.dp),
      ) {
        Box(modifier = Modifier.padding(4.dp))
      }
      Text(
        text = statusText.trim().ifEmpty { "离线" },
        style = mobileCaption1,
        color = chipText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
