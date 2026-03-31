package ai.openclaw.app.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private data class TintedPalette(
  val primary: Color,
  val secondary: Color,
  val userText: Color,
)

private fun buildTintedTheme(
  kind: ChatPageThemeKind,
  env: ChatThemeRenderEnv,
  light: TintedPalette,
  dark: TintedPalette,
): ChatBubbleThemeTokens {
  val p = if (env.isDark) dark else light
  val c = env.baseColors
  val s = env.shapes
  val userFill = lerp(c.surface, p.primary, if (env.isDark) 0.34f else 0.20f)
  val userBorder = lerp(c.border, p.primary, if (env.isDark) 0.62f else 0.58f)
  val assistantFill = lerp(c.cardSurface, p.secondary, if (env.isDark) 0.16f else 0.11f)
  val assistantBorder = lerp(c.borderStrong, p.secondary, if (env.isDark) 0.34f else 0.28f)
  val partTextBg = lerp(assistantFill, p.secondary, if (env.isDark) 0.20f else 0.12f)
  val partTextBorder = lerp(c.border, p.secondary, if (env.isDark) 0.44f else 0.35f)
  val toolCallBg = lerp(c.surfaceStrong, p.secondary, if (env.isDark) 0.18f else 0.08f)
  val toolCallBorder = lerp(c.borderStrong, p.secondary, if (env.isDark) 0.48f else 0.34f)
  val toolResultBg = lerp(lerp(c.cardSurface, c.warning, 0.12f), p.primary, if (env.isDark) 0.08f else 0.06f)
  val toolResultBorder = lerp(c.warning, p.primary, if (env.isDark) 0.30f else 0.22f)
  val metaBg = lerp(c.cardSurface, p.primary, if (env.isDark) 0.12f else 0.06f)
  val metaBorder = lerp(c.border, p.primary, if (env.isDark) 0.30f else 0.20f)
  val softText = if (env.isDark) p.userText.copy(alpha = 0.78f) else p.userText.copy(alpha = 0.88f)

  return ChatBubbleThemeTokens(
    kind = kind,
    shapeBubbleUser = s.bubbleUserAsymmetric,
    shapeBubbleAssistant = s.bubbleAssistantAsymmetric,
    shapePartCard = s.partShape,
    userBubble =
      RoleBubbleChrome(
        container = userFill,
        border = userBorder,
        roleLabel = p.userText,
        contentText = p.userText,
        metaText = softText,
      ),
    systemBubble =
      RoleBubbleChrome(
        container = lerp(c.surface, c.warning, if (env.isDark) 0.18f else 0.11f),
        border = lerp(c.border, c.warning, if (env.isDark) 0.40f else 0.34f),
        roleLabel = lerp(c.warning, p.primary, 0.20f),
      ),
    assistantBubble = RoleBubbleChrome(assistantFill, assistantBorder, lerp(p.secondary, p.userText, 0.20f)),
    partText =
      PartCardChrome(
        background = partTextBg,
        border = partTextBorder,
        contentText = if (env.isDark) c.text else Color(0xFF1F262B),
        headerText = lerp(p.secondary, if (env.isDark) c.textSecondary else Color(0xFF41505D), 0.26f),
      ),
    partImage =
      PartCardChrome(
        background = lerp(partTextBg, c.success, if (env.isDark) 0.10f else 0.08f),
        border = lerp(partTextBorder, c.success, if (env.isDark) 0.30f else 0.24f),
        contentText = if (env.isDark) c.text else Color(0xFF1F262B),
        headerText = lerp(p.secondary, if (env.isDark) c.textSecondary else Color(0xFF41505D), 0.26f),
      ),
    partReasoning =
      PartCardChrome(
        background = lerp(partTextBg, c.surfaceStrong, if (env.isDark) 0.24f else 0.12f),
        border = lerp(partTextBorder, c.borderStrong, if (env.isDark) 0.34f else 0.28f),
        borderDashed = true,
        contentText = if (env.isDark) c.textSecondary else Color(0xFF586272),
        headerText = if (env.isDark) c.textSecondary.copy(alpha = 0.82f) else Color(0xFF697282),
        bodyItalic = true,
      ),
    partToolCall =
      PartCardChrome(
        background = toolCallBg,
        border = toolCallBorder,
        contentText = if (env.isDark) Color(0xFFE8EDF3) else Color(0xFF24303A),
        headerText = if (env.isDark) Color(0xFFB9C7D5) else Color(0xFF4B6070),
      ),
    partToolResult =
      PartCardChrome(
        background = toolResultBg,
        border = toolResultBorder,
        contentText = if (env.isDark) Color(0xFFFFEFD7) else Color(0xFF3B2D16),
        headerText = lerp(c.warning, p.primary, 0.36f),
      ),
    partMeta =
      PartCardChrome(
        background = metaBg,
        border = metaBorder,
        contentText = if (env.isDark) c.text else Color(0xFF2B3139),
        headerText = if (env.isDark) c.textSecondary else Color(0xFF5A6472),
      ),
    streamingAccentBorder = p.primary,
    composer =
      env.composerDefault.copy(
        textFieldBg = lerp(env.composerDefault.textFieldBg, assistantFill, if (env.isDark) 0.34f else 0.20f),
        textFieldBorderFocused = userBorder,
        textFieldBorderUnfocused = lerp(c.border, p.secondary, if (env.isDark) 0.30f else 0.22f),
        sendBg = p.primary,
        sendBorder = userBorder,
        secondaryBg = lerp(env.composerDefault.secondaryBg, assistantFill, if (env.isDark) 0.34f else 0.20f),
        secondaryBorder = lerp(c.borderStrong, p.secondary, if (env.isDark) 0.30f else 0.24f),
        attachmentChipBg = lerp(env.composerDefault.attachmentChipBg, p.primary, if (env.isDark) 0.22f else 0.14f),
        attachmentChipBorder = lerp(c.borderStrong, p.primary, if (env.isDark) 0.28f else 0.20f),
        dropdownBg = lerp(env.composerDefault.dropdownBg, assistantFill, if (env.isDark) 0.32f else 0.18f),
        dropdownBorder = lerp(c.border, p.secondary, if (env.isDark) 0.32f else 0.22f),
      ),
  )
}

internal object LegacyBlueThemeModule : ChatThemeModule {
  override val kind: ChatPageThemeKind = ChatPageThemeKind.LegacyBlue

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens {
    val c = env.baseColors
    val s = env.shapes
    return ChatBubbleThemeTokens(
      kind = kind,
      shapeBubbleUser = s.bubbleDefault,
      shapeBubbleAssistant = s.bubbleDefault,
      shapePartCard = s.partShape,
      userBubble =
        RoleBubbleChrome(
          lerp(c.surface, c.accent, 0.125f),
          lerp(c.border, c.accent, 0.45f),
          c.accent,
        ),
      systemBubble =
        RoleBubbleChrome(
          lerp(c.surface, c.warning, 0.11f),
          lerp(c.border, c.warning, 0.38f),
          c.warning,
        ),
      assistantBubble =
        RoleBubbleChrome(
          lerp(c.cardSurface, c.accent, 0.048f),
          lerp(c.borderStrong, c.accent, 0.18f),
          c.textSecondary,
        ),
      partText =
        PartCardChrome(
          background = lerp(c.cardSurface, c.accent, 0.072f),
          border = lerp(c.border, c.accent, 0.24f),
          borderDashed = false,
          contentText = c.text,
          headerText = c.textSecondary,
        ),
      partImage =
        PartCardChrome(
          background = lerp(c.cardSurface, c.success, 0.078f),
          border = lerp(c.border, c.success, 0.28f),
          borderDashed = false,
          contentText = c.text,
          headerText = c.textSecondary,
        ),
      partReasoning =
        PartCardChrome(
          background = lerp(c.cardSurface, c.warning, 0.082f),
          border = lerp(c.border, c.warning, 0.26f),
          borderDashed = false,
          contentText = c.textSecondary,
          headerText = c.textSecondary,
        ),
      partToolCall =
        PartCardChrome(
          background = lerp(lerp(c.cardSurface, c.surfaceStrong, 0.32f), c.accent, 0.055f),
          border = lerp(c.border, c.accentBorderStrong, 0.32f),
          borderDashed = false,
          contentText = c.text,
          headerText = c.textSecondary,
        ),
      partToolResult =
        PartCardChrome(
          background = lerp(lerp(c.cardSurface, c.surfaceStrong, 0.28f), c.warning, 0.08f),
          border = lerp(c.border, c.warning, 0.35f),
          borderDashed = false,
          contentText = c.text,
          headerText = c.textSecondary,
        ),
      partMeta =
        PartCardChrome(
          background = lerp(c.cardSurface, c.danger, 0.052f),
          border = lerp(c.border, c.danger, 0.22f),
          borderDashed = false,
          contentText = c.text,
          headerText = c.textSecondary,
        ),
      streamingAccentBorder = c.accent,
      composer = env.composerDefault,
    )
  }
}

internal object SemanticVioletThemeModule : ChatThemeModule {
  override val kind: ChatPageThemeKind = ChatPageThemeKind.SemanticViolet

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens {
    val s = env.shapes
    return if (!env.isDark) {
      val userFill = Color(0xFFEEEDFE)
      val userStroke = Color(0xFF534AB7)
      val teal = Color(0xFF0F6E56)
      val assistantShellFill = Color(0xFFF3F2F7)
      val assistantShellBorder = Color(0xFFE0DFE8)
      ChatBubbleThemeTokens(
        kind = kind,
        shapeBubbleUser = s.bubbleUserAsymmetric,
        shapeBubbleAssistant = s.bubbleAssistantAsymmetric,
        shapePartCard = s.partShape,
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
        kind = kind,
        shapeBubbleUser = s.bubbleUserAsymmetric,
        shapeBubbleAssistant = s.bubbleAssistantAsymmetric,
        shapePartCard = s.partShape,
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
  }
}

internal object WarmStudioThemeModule : ChatThemeModule {
  override val kind: ChatPageThemeKind = ChatPageThemeKind.WarmStudio

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens {
    val s = env.shapes
    return if (!env.isDark) {
      val ochre = Color(0xFFCC785C)
      val ochreStroke = Color(0xFFB86A4E)
      val brownAccent = Color(0xFF6B5344)
      val assistantShellFill = Color(0xFFF5F0EB)
      val assistantShellBorder = Color(0xFFE0D4C8)
      ChatBubbleThemeTokens(
        kind = kind,
        shapeBubbleUser = s.bubbleUserAsymmetric,
        shapeBubbleAssistant = s.bubbleAssistantAsymmetric,
        shapePartCard = s.partShape,
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
        kind = kind,
        shapeBubbleUser = s.bubbleUserAsymmetric,
        shapeBubbleAssistant = s.bubbleAssistantAsymmetric,
        shapePartCard = s.partShape,
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

internal object ForestMistThemeModule : ChatThemeModule {
  override val kind = ChatPageThemeKind.ForestMist

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens =
    buildTintedTheme(
      kind = kind,
      env = env,
      light = TintedPalette(primary = Color(0xFF4E8A73), secondary = Color(0xFF3C7D6A), userText = Color.White),
      dark = TintedPalette(primary = Color(0xFF5AA386), secondary = Color(0xFF3F7C69), userText = Color(0xFFE8FFF5)),
    )
}

internal object RoseDawnThemeModule : ChatThemeModule {
  override val kind = ChatPageThemeKind.RoseDawn

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens =
    buildTintedTheme(
      kind = kind,
      env = env,
      light = TintedPalette(primary = Color(0xFFC36A8A), secondary = Color(0xFF94627D), userText = Color.White),
      dark = TintedPalette(primary = Color(0xFFD186A3), secondary = Color(0xFF80596D), userText = Color(0xFFFFF1F6)),
    )
}

internal object AmberNightThemeModule : ChatThemeModule {
  override val kind = ChatPageThemeKind.AmberNight

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens =
    buildTintedTheme(
      kind = kind,
      env = env,
      light = TintedPalette(primary = Color(0xFFC98934), secondary = Color(0xFF7F5C2C), userText = Color.White),
      dark = TintedPalette(primary = Color(0xFFCC944C), secondary = Color(0xFF74562F), userText = Color(0xFFFFF2DE)),
    )
}

internal object MintGlassThemeModule : ChatThemeModule {
  override val kind = ChatPageThemeKind.MintGlass

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens =
    buildTintedTheme(
      kind = kind,
      env = env,
      light = TintedPalette(primary = Color(0xFF4AAFA3), secondary = Color(0xFF3A8790), userText = Color.White),
      dark = TintedPalette(primary = Color(0xFF58BEB2), secondary = Color(0xFF3D7F88), userText = Color(0xFFE9FFFD)),
    )
}

internal object SlateMonoThemeModule : ChatThemeModule {
  override val kind = ChatPageThemeKind.SlateMono

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens =
    buildTintedTheme(
      kind = kind,
      env = env,
      light = TintedPalette(primary = Color(0xFF616B78), secondary = Color(0xFF596271), userText = Color.White),
      dark = TintedPalette(primary = Color(0xFF7A8593), secondary = Color(0xFF5A6471), userText = Color(0xFFF2F5F8)),
    )
}

internal object OceanDeepThemeModule : ChatThemeModule {
  override val kind = ChatPageThemeKind.OceanDeep

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens =
    buildTintedTheme(
      kind = kind,
      env = env,
      light = TintedPalette(primary = Color(0xFF2A78B5), secondary = Color(0xFF2B5F90), userText = Color.White),
      dark = TintedPalette(primary = Color(0xFF3C8CCA), secondary = Color(0xFF2F5F87), userText = Color(0xFFEAF6FF)),
    )
}

internal object LavenderHazeThemeModule : ChatThemeModule {
  override val kind = ChatPageThemeKind.LavenderHaze

  override fun create(env: ChatThemeRenderEnv): ChatBubbleThemeTokens =
    buildTintedTheme(
      kind = kind,
      env = env,
      light = TintedPalette(primary = Color(0xFF8E74C8), secondary = Color(0xFF6B5C9E), userText = Color.White),
      dark = TintedPalette(primary = Color(0xFFA18ADD), secondary = Color(0xFF6B5E98), userText = Color(0xFFF4EEFF)),
    )
}
