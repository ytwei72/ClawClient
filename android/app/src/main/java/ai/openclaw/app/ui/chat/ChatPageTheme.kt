package ai.openclaw.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.openclaw.app.ui.mobileAccent
import ai.openclaw.app.ui.mobileAccentBorderStrong
import ai.openclaw.app.ui.mobileAccentSoft
import ai.openclaw.app.ui.mobileBorder
import ai.openclaw.app.ui.mobileBorderStrong
import ai.openclaw.app.ui.mobileCaption1
import ai.openclaw.app.ui.mobileCardSurface
import ai.openclaw.app.ui.mobileDanger
import ai.openclaw.app.ui.mobileSuccess
import ai.openclaw.app.ui.mobileSurface
import ai.openclaw.app.ui.mobileSurfaceStrong
import ai.openclaw.app.ui.mobileText
import ai.openclaw.app.ui.mobileTextSecondary
import ai.openclaw.app.ui.mobileTextTertiary
import ai.openclaw.app.ui.mobileWarning

/**
 * Chat 页气泡配色方案（与设计文档《chat页对话气泡配色》方案 B 一致）。
 * - [LegacyBlue]：经典蓝韵 — 应用主色蓝调的柔和语义色（user / assistant / part 的 lerp）。
 * - [SemanticViolet]：语义紫青 — 紫（用户）+ 青绿（助手正文）+ 琥珀（工具结果），见该文档「语义紫青」章节。
 * - [WarmStudio]：燕麦赭韵 — 赭橙用户实心、暖米助手与 part 层次，见《chat页对话气泡配色》「燕麦赭韵」章节。
 */
enum class ChatPageThemeKind(
  val label: String,
) {
  LegacyBlue("经典蓝韵"),
  SemanticViolet("语义紫青"),
  WarmStudio("燕麦赭韵"),
  ForestMist("森雾青岚"),
  RoseDawn("蔷薇晨曦"),
  AmberNight("琥珀夜幕"),
  MintGlass("薄荷冰透"),
  SlateMono("石墨灰阶"),
  OceanDeep("深海蓝潮"),
  LavenderHaze("薰衣薄雾"),
}

@Immutable
data class RoleBubbleChrome(
  val container: Color,
  val border: Color,
  val roleLabel: Color,
  /** 气泡内 Markdown 正文色；null 时使用应用全局正文色。 */
  val contentText: Color? = null,
  /** 用户气泡顶栏时间、原文切换等次要文案；null 时用全局 secondary。 */
  val metaText: Color? = null,
)

@Immutable
data class PartCardChrome(
  val background: Color,
  val border: Color,
  val borderDashed: Boolean = false,
  val contentText: Color,
  val headerText: Color,
  val bodyItalic: Boolean = false,
)

@Immutable
data class ComposerThemeChrome(
  val textFieldBg: Color,
  val textFieldBorderFocused: Color,
  val textFieldBorderUnfocused: Color,
  val sendBg: Color,
  val sendContent: Color,
  val sendBorder: Color,
  val sendBorderDisabled: Color,
  val sendDisabledBg: Color,
  val secondaryBg: Color,
  val secondaryBorder: Color,
  val secondaryContent: Color,
  val thinkingMenuBg: Color,
  val thinkingMenuBorder: Color,
  val attachmentChipBg: Color,
  val attachmentChipBorder: Color,
  val attachmentRemoveBg: Color,
  val attachmentRemoveBorder: Color,
  val dropdownBg: Color,
  val dropdownBorder: Color,
)

@Immutable
data class ChatBubbleThemeTokens(
  val kind: ChatPageThemeKind,
  val shapeBubbleUser: RoundedCornerShape,
  val shapeBubbleAssistant: RoundedCornerShape,
  val shapePartCard: RoundedCornerShape,
  val userBubble: RoleBubbleChrome,
  val systemBubble: RoleBubbleChrome,
  val assistantBubble: RoleBubbleChrome,
  val partText: PartCardChrome,
  val partImage: PartCardChrome,
  val partReasoning: PartCardChrome,
  val partToolCall: PartCardChrome,
  val partToolResult: PartCardChrome,
  val partMeta: PartCardChrome,
  /** 非 null 时覆盖「实时回复」气泡外框强调色。 */
  val streamingAccentBorder: Color?,
  val composer: ComposerThemeChrome,
)

val LocalChatBubbleTheme =
  staticCompositionLocalOf<ChatBubbleThemeTokens> {
    error("CompositionLocal LocalChatBubbleTheme 未提供，应在 ChatSheetContent 根部注入。")
  }

/**
 * 在内层 part 卡片上绘制圆角虚线描边（实线由 [PartCardChrome.borderDashed] 为 false 时使用 [BorderStroke]）。
 * [cornerDp] 须与 [ChatBubbleThemeTokens.shapePartCard] 圆角一致。
 */
internal fun Modifier.partCardDashedBorder(
  cornerDp: Dp,
  strokeWidth: Dp,
  color: Color,
): Modifier =
  drawBehind {
    val w = strokeWidth.toPx()
    val stroke =
      Stroke(
        width = w,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 7f), 0f),
      )
    val r = cornerDp.toPx()
    drawRoundRect(
      color = color,
      topLeft = Offset(w * 0.5f, w * 0.5f),
      size = Size(this.size.width - w, this.size.height - w),
      cornerRadius = CornerRadius((r - w * 0.5f).coerceAtLeast(0f)),
      style = stroke,
    )
  }

@Composable
fun chatBubbleThemeTokens(theme: ChatPageThemeKind): ChatBubbleThemeTokens {
  val env =
    ChatThemeRenderEnv(
      kind = theme,
      isDark = isSystemInDarkTheme(),
      shapes = ChatThemeShapes(),
      baseColors =
        ChatThemeBaseColors(
          accent = mobileAccent,
          accentBorderStrong = mobileAccentBorderStrong,
          accentSoft = mobileAccentSoft,
          border = mobileBorder,
          borderStrong = mobileBorderStrong,
          cardSurface = mobileCardSurface,
          danger = mobileDanger,
          success = mobileSuccess,
          surface = mobileSurface,
          surfaceStrong = mobileSurfaceStrong,
          text = mobileText,
          textSecondary = mobileTextSecondary,
          warning = mobileWarning,
        ),
      composerDefault =
        ComposerThemeChrome(
          textFieldBg = mobileSurface,
          textFieldBorderFocused = mobileAccent,
          textFieldBorderUnfocused = mobileBorder,
          sendBg = mobileAccent,
          sendContent = Color.White,
          sendBorder = mobileAccentBorderStrong,
          sendBorderDisabled = mobileBorderStrong,
          sendDisabledBg = mobileBorderStrong,
          secondaryBg = mobileCardSurface,
          secondaryBorder = mobileBorderStrong,
          secondaryContent = mobileTextSecondary,
          thinkingMenuBg = mobileCardSurface,
          thinkingMenuBorder = mobileBorderStrong,
          attachmentChipBg = mobileAccentSoft,
          attachmentChipBorder = mobileBorderStrong,
          attachmentRemoveBg = mobileCardSurface,
          attachmentRemoveBorder = mobileBorderStrong,
          dropdownBg = mobileCardSurface,
          dropdownBorder = mobileBorder,
        ),
    )
  return resolveChatThemeModule(theme).create(env)
}

/** 顶栏紧凑下拉：展示当前气泡主题名，展开后切换 [ChatPageThemeKind]。 */
@Composable
fun ChatPageThemeDropdownMenu(
  selected: ChatPageThemeKind,
  onSelect: (ChatPageThemeKind) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  Box(modifier = modifier.widthIn(max = 140.dp)) {
    Surface(
      onClick = { expanded = true },
      shape = RoundedCornerShape(8.dp),
      color = mobileCardSurface,
      border = BorderStroke(1.dp, mobileBorderStrong),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          selected.label,
          style = mobileCaption1,
          color = mobileText,
          maxLines = 1,
        )
        Icon(
          Icons.Default.ArrowDropDown,
          contentDescription = "选择聊天气泡配色",
          modifier = Modifier.size(18.dp),
          tint = mobileTextTertiary,
        )
      }
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { expanded = false },
      containerColor = mobileCardSurface,
      border = BorderStroke(1.dp, mobileBorder),
    ) {
      for (kind in ChatPageThemeKind.entries) {
        DropdownMenuItem(
          text = { Text(kind.label, style = mobileCaption1, color = mobileText) },
          onClick = {
            onSelect(kind)
            expanded = false
          },
        )
      }
    }
  }
}
