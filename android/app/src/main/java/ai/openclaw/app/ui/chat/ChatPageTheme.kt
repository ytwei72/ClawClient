package ai.openclaw.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ai.openclaw.app.ui.mobileAccent
import ai.openclaw.app.ui.mobileAccentBorderStrong
import ai.openclaw.app.ui.mobileAccentSoft
import ai.openclaw.app.ui.mobileBorder
import ai.openclaw.app.ui.mobileBorderStrong
import ai.openclaw.app.ui.mobileCardSurface
import ai.openclaw.app.ui.mobileDanger
import ai.openclaw.app.ui.mobileSuccess
import ai.openclaw.app.ui.mobileSurface
import ai.openclaw.app.ui.mobileSurfaceStrong
import ai.openclaw.app.ui.mobileCaption1
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
  val isDark = isSystemInDarkTheme()
  val card = mobileCardSurface
  val baseBorder = mobileBorder
  val partShape = RoundedCornerShape(10.dp)
  val shapeDefaultBubble = RoundedCornerShape(12.dp)

  val composerDefault =
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
    )

  return when (theme) {
    ChatPageThemeKind.LegacyBlue ->
      ChatBubbleThemeTokens(
        kind = theme,
        shapeBubbleUser = shapeDefaultBubble,
        shapeBubbleAssistant = shapeDefaultBubble,
        shapePartCard = partShape,
        userBubble =
          RoleBubbleChrome(
            lerp(mobileSurface, mobileAccent, 0.125f),
            lerp(mobileBorder, mobileAccent, 0.45f),
            mobileAccent,
          ),
        systemBubble =
          RoleBubbleChrome(
            lerp(mobileSurface, mobileWarning, 0.11f),
            lerp(mobileBorder, mobileWarning, 0.38f),
            mobileWarning,
          ),
        assistantBubble =
          RoleBubbleChrome(
            lerp(card, mobileAccent, 0.048f),
            lerp(mobileBorderStrong, mobileAccent, 0.18f),
            mobileTextSecondary,
          ),
        partText =
          PartCardChrome(
            background = lerp(card, mobileAccent, 0.072f),
            border = lerp(baseBorder, mobileAccent, 0.24f),
            borderDashed = false,
            contentText = mobileText,
            headerText = mobileTextSecondary,
          ),
        partImage =
          PartCardChrome(
            background = lerp(card, mobileSuccess, 0.078f),
            border = lerp(baseBorder, mobileSuccess, 0.28f),
            borderDashed = false,
            contentText = mobileText,
            headerText = mobileTextSecondary,
          ),
        partReasoning =
          PartCardChrome(
            background = lerp(card, mobileWarning, 0.082f),
            border = lerp(baseBorder, mobileWarning, 0.26f),
            borderDashed = false,
            contentText = mobileTextSecondary,
            headerText = mobileTextSecondary,
          ),
        partToolCall =
          PartCardChrome(
            background = lerp(lerp(card, mobileSurfaceStrong, 0.32f), mobileAccent, 0.055f),
            border = lerp(baseBorder, mobileAccentBorderStrong, 0.32f),
            borderDashed = false,
            contentText = mobileText,
            headerText = mobileTextSecondary,
          ),
        partToolResult =
          PartCardChrome(
            background = lerp(lerp(card, mobileSurfaceStrong, 0.28f), mobileWarning, 0.08f),
            border = lerp(baseBorder, mobileWarning, 0.35f),
            borderDashed = false,
            contentText = mobileText,
            headerText = mobileTextSecondary,
          ),
        partMeta =
          PartCardChrome(
            background = lerp(card, mobileDanger, 0.052f),
            border = lerp(baseBorder, mobileDanger, 0.22f),
            borderDashed = false,
            contentText = mobileText,
            headerText = mobileTextSecondary,
          ),
        streamingAccentBorder = mobileAccent,
        composer = composerDefault,
      )

    ChatPageThemeKind.SemanticViolet ->
      if (!isDark) {
        val userFill = Color(0xFFEEEDFE)
        val userStroke = Color(0xFF534AB7)
        val teal = Color(0xFF0F6E56)
        val assistantShellFill = Color(0xFFF3F2F7)
        val assistantShellBorder = Color(0xFFE0DFE8)
        ChatBubbleThemeTokens(
          kind = theme,
          shapeBubbleUser = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp),
          shapeBubbleAssistant = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
          shapePartCard = partShape,
          userBubble = RoleBubbleChrome(userFill, userStroke, userStroke),
          systemBubble =
            RoleBubbleChrome(
              Color(0xFFFFF6E8),
              Color(0xFFE8C48A),
              Color(0xFFB07A1A),
            ),
          assistantBubble = RoleBubbleChrome(assistantShellFill, assistantShellBorder, teal),
          partText =
            PartCardChrome(
              background = Color(0xFFE1F5EE),
              border = teal,
              contentText = Color(0xFF10221C),
              headerText = teal.copy(alpha = 0.88f),
            ),
          partImage =
            PartCardChrome(
              background = Color(0xFFD8EFE7),
              border = Color(0xFF2A8F6E),
              contentText = Color(0xFF10221C),
              headerText = teal.copy(alpha = 0.88f),
            ),
          partReasoning =
            PartCardChrome(
              background = Color(0xFFF1F2F4),
              border = Color(0xFF9CA3AF),
              borderDashed = true,
              contentText = Color(0xFF4B5563),
              headerText = Color(0xFF6B7280),
              bodyItalic = true,
            ),
          partToolCall =
            PartCardChrome(
              background = Color(0xFF2C2C2A),
              border = Color(0xFF454440),
              contentText = Color(0xFFE8EAEE),
              headerText = Color(0xFFB8C0CC),
            ),
          partToolResult =
            PartCardChrome(
              background = Color(0xFFFAEEDA),
              border = Color(0xFFEF9F27),
              contentText = Color(0xFF3D2E12),
              headerText = Color(0xFFEF9F27),
            ),
          partMeta =
            PartCardChrome(
              background = Color(0xFFEFEFF5),
              border = Color(0xFF9B9CBB),
              contentText = Color(0xFF2C2C35),
              headerText = Color(0xFF5D5F82),
            ),
          streamingAccentBorder = teal,
          composer =
            ComposerThemeChrome(
              textFieldBg = Color(0xFFEBEBF0),
              textFieldBorderFocused = userStroke,
              textFieldBorderUnfocused = Color(0xFFC8C8D4),
              sendBg = userStroke,
              sendContent = Color.White,
              sendBorder = Color(0xFF4338B0),
              sendBorderDisabled = Color(0xFFBABACC),
              sendDisabledBg = Color(0xFFD9D8E6),
              secondaryBg = Color(0xFFF2F2F6),
              secondaryBorder = Color(0xFFC9C9D5),
              secondaryContent = Color(0xFF4B5563),
              thinkingMenuBg = Color(0xFFF2F2F6),
              thinkingMenuBorder = Color(0xFFC9C9D5),
              attachmentChipBg = Color(0xFFEEEDFE),
              attachmentChipBorder = Color(0xFFB8B0E6),
              attachmentRemoveBg = Color(0xFFE4E4EB),
              attachmentRemoveBorder = Color(0xFFBABACC),
              dropdownBg = Color(0xFFF7F7FA),
              dropdownBorder = Color(0xFFC8C8D4),
            ),
        )
      } else {
        val userFill = Color(0xFF2A2638)
        val userStroke = Color(0xFF8B7FD6)
        val userLabel = Color(0xFFB4ACF0)
        val teal = Color(0xFF5CD9B0)
        val assistantShellFill = Color(0xFF232428)
        val assistantShellBorder = Color(0xFF3D4048)
        ChatBubbleThemeTokens(
          kind = theme,
          shapeBubbleUser = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp),
          shapeBubbleAssistant = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
          shapePartCard = partShape,
          userBubble = RoleBubbleChrome(userFill, userStroke, userLabel),
          systemBubble =
            RoleBubbleChrome(
              Color(0xFF342A1E),
              Color(0xFF7A5E28),
              Color(0xFFE8C478),
            ),
          assistantBubble = RoleBubbleChrome(assistantShellFill, assistantShellBorder, teal),
          partText =
            PartCardChrome(
              background = Color(0xFF1A2C26),
              border = Color(0xFF3D9B7A),
              contentText = Color(0xFFE2F5ED),
              headerText = Color(0xFF7FD4B8),
            ),
          partImage =
            PartCardChrome(
              background = Color(0xFF152822),
              border = Color(0xFF2E8A6E),
              contentText = Color(0xFFE2F5ED),
              headerText = Color(0xFF7FD4B8),
            ),
          partReasoning =
            PartCardChrome(
              background = Color(0xFF292E35),
              border = Color(0xFF6B7280),
              borderDashed = true,
              contentText = Color(0xFFBFC7D1),
              headerText = Color(0xFF9CA3AF),
              bodyItalic = true,
            ),
          partToolCall =
            PartCardChrome(
              background = Color(0xFF1A1918),
              border = Color(0xFF3A3835),
              contentText = Color(0xFFE8EAEE),
              headerText = Color(0xFF9CA8B8),
            ),
          partToolResult =
            PartCardChrome(
              background = Color(0xFF3A3220),
              border = Color(0xFFD4A535),
              contentText = Color(0xFFFFEED0),
              headerText = Color(0xFFF5C96A),
            ),
          partMeta =
            PartCardChrome(
              background = Color(0xFF2C2C34),
              border = Color(0xFF6E6F8F),
              contentText = Color(0xFFE4E5EC),
              headerText = Color(0xFFAFB2DD),
            ),
          streamingAccentBorder = teal,
          composer =
            ComposerThemeChrome(
              textFieldBg = Color(0xFF2A2C32),
              textFieldBorderFocused = userStroke,
              textFieldBorderUnfocused = Color(0xFF4A4D56),
              sendBg = userStroke,
              sendContent = Color.White,
              sendBorder = Color(0xFF6E5FD0),
              sendBorderDisabled = Color(0xFF3D4048),
              sendDisabledBg = Color(0xFF363840),
              secondaryBg = Color(0xFF2E3038),
              secondaryBorder = Color(0xFF454854),
              secondaryContent = Color(0xFFC4C8D4),
              thinkingMenuBg = Color(0xFF2E3038),
              thinkingMenuBorder = Color(0xFF454854),
              attachmentChipBg = Color(0xFF322E45),
              attachmentChipBorder = Color(0xFF524A78),
              attachmentRemoveBg = Color(0xFF363840),
              attachmentRemoveBorder = Color(0xFF4A4D56),
              dropdownBg = Color(0xFF2E3038),
              dropdownBorder = Color(0xFF4A4D56),
            ),
        )
      }

    ChatPageThemeKind.WarmStudio ->
      if (!isDark) {
        val ochre = Color(0xFFCC785C)
        val ochreStroke = Color(0xFFB86A4E)
        val brownAccent = Color(0xFF6B5344)
        val assistantShellFill = Color(0xFFF5F0EB)
        val assistantShellBorder = Color(0xFFE0D4C8)
        ChatBubbleThemeTokens(
          kind = theme,
          shapeBubbleUser = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp),
          shapeBubbleAssistant = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
          shapePartCard = partShape,
          userBubble =
            RoleBubbleChrome(
              container = ochre,
              border = ochreStroke,
              roleLabel = Color.White,
              contentText = Color.White,
              metaText = Color.White.copy(alpha = 0.88f),
            ),
          systemBubble =
            RoleBubbleChrome(
              Color(0xFFFFF4EC),
              Color(0xFFE8D4BC),
              Color(0xFF9A6B3A),
            ),
          assistantBubble = RoleBubbleChrome(assistantShellFill, assistantShellBorder, brownAccent),
          partText =
            PartCardChrome(
              background = Color(0xFFF5EDE4),
              border = Color(0xFFC4A88F),
              contentText = Color(0xFF2C2218),
              headerText = Color(0xFF7A5E48),
            ),
          partImage =
            PartCardChrome(
              background = Color(0xFFEDE6DC),
              border = Color(0xFFB08F72),
              contentText = Color(0xFF2C2218),
              headerText = Color(0xFF7A5E48),
            ),
          partReasoning =
            PartCardChrome(
              background = Color(0xFFF7F1EA),
              border = Color(0xFFC4A990),
              borderDashed = true,
              contentText = Color(0xFF5C4E42),
              headerText = Color(0xFF8B7A6A),
              bodyItalic = true,
            ),
          partToolCall =
            PartCardChrome(
              background = Color(0xFF1E1611),
              border = Color(0xFF3D342A),
              contentText = Color(0xFFEAE2D8),
              headerText = Color(0xFFB5A898),
            ),
          partToolResult =
            PartCardChrome(
              background = Color(0xFFFBF3E4),
              border = Color(0xFFD4A850),
              contentText = Color(0xFF3D2E12),
              headerText = Color(0xFFB07A1A),
            ),
          partMeta =
            PartCardChrome(
              background = Color(0xFFEFEBE6),
              border = Color(0xFFA89888),
              contentText = Color(0xFF2E2A26),
              headerText = Color(0xFF6E6258),
            ),
          streamingAccentBorder = ochre,
          composer =
            ComposerThemeChrome(
              textFieldBg = Color(0xFFF5F0EB),
              textFieldBorderFocused = ochre,
              textFieldBorderUnfocused = Color(0xFFD4C4B8),
              sendBg = ochre,
              sendContent = Color.White,
              sendBorder = ochreStroke,
              sendBorderDisabled = Color(0xFFC9B8A8),
              sendDisabledBg = Color(0xFFE5DBD2),
              secondaryBg = Color(0xFFEFEBE6),
              secondaryBorder = Color(0xFFC9B8A8),
              secondaryContent = Color(0xFF5C4E42),
              thinkingMenuBg = Color(0xFFEFEBE6),
              thinkingMenuBorder = Color(0xFFC9B8A8),
              attachmentChipBg = Color(0xFFF0E5DC),
              attachmentChipBorder = Color(0xFFC4A88F),
              attachmentRemoveBg = Color(0xFFE8DFD6),
              attachmentRemoveBorder = Color(0xFFC9B8A8),
              dropdownBg = Color(0xFFF7F3EF),
              dropdownBorder = Color(0xFFD4C4B8),
            ),
        )
      } else {
        val ochreFill = Color(0xFF5C3D32)
        val ochreStroke = Color(0xFFD4A088)
        val userLabel = Color(0xFFFFD4C4)
        val warmBody = Color(0xFFFFF8F4)
        val brownAccent = Color(0xFFE8C4A8)
        val assistantShellFill = Color(0xFF2A2420)
        val assistantShellBorder = Color(0xFF453D36)
        ChatBubbleThemeTokens(
          kind = theme,
          shapeBubbleUser = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp),
          shapeBubbleAssistant = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
          shapePartCard = partShape,
          userBubble =
            RoleBubbleChrome(
              container = ochreFill,
              border = ochreStroke,
              roleLabel = userLabel,
              contentText = warmBody,
              metaText = warmBody.copy(alpha = 0.78f),
            ),
          systemBubble =
            RoleBubbleChrome(
              Color(0xFF3A2E24),
              Color(0xFF7A5E48),
              Color(0xFFFFD8A8),
            ),
          assistantBubble = RoleBubbleChrome(assistantShellFill, assistantShellBorder, brownAccent),
          partText =
            PartCardChrome(
              background = Color(0xFF3A322C),
              border = Color(0xFF8B7355),
              contentText = Color(0xFFF5EDE4),
              headerText = Color(0xFFD4BCA8),
            ),
          partImage =
            PartCardChrome(
              background = Color(0xFF342C27),
              border = Color(0xFF7F6A56),
              contentText = Color(0xFFF5EDE4),
              headerText = Color(0xFFD4BCA8),
            ),
          partReasoning =
            PartCardChrome(
              background = Color(0xFF383028),
              border = Color(0xFF8B7868),
              borderDashed = true,
              contentText = Color(0xFFC8B8A8),
              headerText = Color(0xFFAB9A8A),
              bodyItalic = true,
            ),
          partToolCall =
            PartCardChrome(
              background = Color(0xFF12100E),
              border = Color(0xFF3D342A),
              contentText = Color(0xFFEAE2D8),
              headerText = Color(0xFF9A8B7C),
            ),
          partToolResult =
            PartCardChrome(
              background = Color(0xFF3A3220),
              border = Color(0xFFD4A850),
              contentText = Color(0xFFFFEED8),
              headerText = Color(0xFFF5C96A),
            ),
          partMeta =
            PartCardChrome(
              background = Color(0xFF36302A),
              border = Color(0xFF7A6E62),
              contentText = Color(0xFFE8E0D8),
              headerText = Color(0xFFBCB0A4),
            ),
          streamingAccentBorder = Color(0xFFE8A080),
          composer =
            ComposerThemeChrome(
              textFieldBg = Color(0xFF332C28),
              textFieldBorderFocused = ochreStroke,
              textFieldBorderUnfocused = Color(0xFF554840),
              sendBg = ochreFill,
              sendContent = warmBody,
              sendBorder = ochreStroke,
              sendBorderDisabled = Color(0xFF454038),
              sendDisabledBg = Color(0xFF3A3430),
              secondaryBg = Color(0xFF383028),
              secondaryBorder = Color(0xFF554840),
              secondaryContent = Color(0xFFC8B8A8),
              thinkingMenuBg = Color(0xFF383028),
              thinkingMenuBorder = Color(0xFF554840),
              attachmentChipBg = Color(0xFF3D322C),
              attachmentChipBorder = Color(0xFF6B5A4A),
              attachmentRemoveBg = Color(0xFF403830),
              attachmentRemoveBorder = Color(0xFF554840),
              dropdownBg = Color(0xFF383028),
              dropdownBorder = Color(0xFF554840),
            ),
        )
      }
  }
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
