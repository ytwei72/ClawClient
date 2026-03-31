package ai.openclaw.app.ui.chat

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal interface ChatThemeModule {
  val kind: ChatPageThemeKind

  fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens
}

internal data class ChatThemeShapes(
  val partShape: RoundedCornerShape = RoundedCornerShape(10.dp),
  val bubbleDefault: RoundedCornerShape = RoundedCornerShape(12.dp),
  val bubbleUserAsymmetric: RoundedCornerShape =
    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 4.dp, bottomStart = 12.dp),
  val bubbleAssistantAsymmetric: RoundedCornerShape =
    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomEnd = 12.dp, bottomStart = 4.dp),
)

internal data class ChatThemeBaseColors(
  val accent: Color,
  val accentBorderStrong: Color,
  val accentSoft: Color,
  val border: Color,
  val borderStrong: Color,
  val cardSurface: Color,
  val danger: Color,
  val success: Color,
  val surface: Color,
  val surfaceStrong: Color,
  val text: Color,
  val textSecondary: Color,
  val warning: Color,
)

internal data class ChatThemeRenderEnv(
  val kind: ChatPageThemeKind,
  val isDark: Boolean,
  val shapes: ChatThemeShapes,
  val baseColors: ChatThemeBaseColors,
  val composerDefault: ComposerThemeChrome,
)

private val themeModules: List<ChatThemeModule> =
  listOf(
    LegacyBlueThemeModule,
    SemanticVioletThemeModule,
    WarmStudioThemeModule,
    ForestMistThemeModule,
    RoseDawnThemeModule,
    AmberNightThemeModule,
    MintGlassThemeModule,
    SlateMonoThemeModule,
    OceanDeepThemeModule,
    LavenderHazeThemeModule,
  )

internal fun resolveChatThemeModule(kind: ChatPageThemeKind): ChatThemeModule =
  themeModules.firstOrNull { it.kind == kind } ?: LegacyBlueThemeModule
