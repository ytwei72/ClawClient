package ai.openclaw.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.chat.ChatPendingToolCall
import ai.openclaw.app.tools.ToolDisplayRegistry
import ai.openclaw.app.ui.mobileAccent
import ai.openclaw.app.ui.mobileAccentSoft
import ai.openclaw.app.ui.mobileBorder
import ai.openclaw.app.ui.mobileBorderStrong
import ai.openclaw.app.ui.mobileCallout
import ai.openclaw.app.ui.mobileCaption1
import ai.openclaw.app.ui.mobileCaption2
import ai.openclaw.app.ui.mobileCardSurface
import ai.openclaw.app.ui.mobileCodeBg
import ai.openclaw.app.ui.mobileCodeBorder
import ai.openclaw.app.ui.mobileCodeText
import ai.openclaw.app.ui.mobileHeadline
import ai.openclaw.app.ui.mobileText
import ai.openclaw.app.ui.mobileTextSecondary
import ai.openclaw.app.ui.mobileWarning
import ai.openclaw.app.ui.mobileWarningSoft
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import org.json.JSONArray
import org.json.JSONObject

private data class ChatBubbleStyle(
  val alignEnd: Boolean,
  val containerColor: Color,
  val borderColor: Color,
  val roleColor: Color,
)

private val numberedLineRegex = Regex("""^\d+[.)]\s+.*$""")
private const val assistantTextPreviewChars = 420
private const val assistantMetaPreviewChars = 240

@Composable
fun ChatMessageBubble(message: ChatMessage, assistantLabel: String) {
  val role = message.role.trim().lowercase(Locale.US)
  val style = bubbleStyle(role)

  // Filter to only displayable content parts (text-ish content, or base64 images).
  val displayableContent =
    message.content.filter { part ->
      part.base64 != null || !part.text.isNullOrBlank()
    }

  if (displayableContent.isEmpty()) return

  ChatBubbleContainer(style = style, roleLabel = roleLabel(role, assistantLabel), timestampMs = message.timestampMs) {
    ChatMessageBody(content = displayableContent, textColor = mobileText, role = role, messageId = message.id)
  }
}

@Composable
private fun ChatBubbleContainer(
  style: ChatBubbleStyle,
  roleLabel: String,
  timestampMs: Long?,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = if (style.alignEnd) Arrangement.End else Arrangement.Start,
  ) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      border = BorderStroke(1.dp, style.borderColor),
      color = style.containerColor,
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
      modifier = Modifier.fillMaxWidth(0.90f),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = roleLabel,
            style = mobileCaption2.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
            color = style.roleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          timestampMs?.let {
            Text(
              text = formatMessageTime(it),
              style = mobileCaption2,
              color = mobileTextSecondary,
            )
          }
        }
        content()
      }
    }
  }
}

@Composable
private fun ChatMessageBody(content: List<ChatMessageContent>, textColor: Color, role: String, messageId: String) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    for ((idx, part) in content.withIndex()) {
      when (part.type) {
        "text" -> {
          val text = part.text ?: continue
          if (role == "user") {
            UserTextWithDebugToggle(rawText = text, textColor = textColor, stateKey = "$messageId:$idx")
          } else if (role == "assistant") {
            AssistantTextCard(
              title = assistantTypeLabel(part.type),
              text = text,
              textColor = textColor,
              stateKey = "$messageId:$idx:text",
            )
          } else {
            ChatMarkdown(text = text, textColor = textColor)
          }
        }
        else -> {
          if (role == "assistant") {
            AssistantTypedContentCard(
              part = part,
              textColor = textColor,
              stateKey = "$messageId:$idx:${part.type}",
            )
          } else {
            val b64 = part.base64 ?: continue
            ChatBase64Image(base64 = b64, mimeType = part.mimeType)
          }
        }
      }
    }
  }
}

@Composable
private fun UserTextWithDebugToggle(rawText: String, textColor: Color, stateKey: String) {
  var showRaw by rememberSaveable(stateKey) { mutableStateOf(false) }
  val sanitized = extractUserVisibleInput(rawText)
  val displayText = if (showRaw) rawText else sanitized

  Box(modifier = Modifier.fillMaxWidth()) {
    ChatMarkdown(text = displayText, textColor = textColor)
    if (sanitized != rawText) {
      TextButton(
        onClick = { showRaw = !showRaw },
        modifier = Modifier.align(Alignment.TopEnd),
      ) {
        Text(
          text = if (showRaw) "显示净化文本" else "调试展开",
          style = mobileCaption1,
          color = mobileTextSecondary,
        )
      }
    }
  }
}

@Composable
private fun AssistantTextCard(title: String, text: String, textColor: Color, stateKey: String) {
  var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
  val needsCollapse = text.length > assistantTextPreviewChars
  val display = if (needsCollapse && !expanded) text.take(assistantTextPreviewChars) + "…" else text

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileBorder),
    color = mobileCardSurface.copy(alpha = 0.7f),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (title != "文本") {
        Text(title, style = mobileCaption2, color = mobileTextSecondary)
      }
      ChatMarkdown(text = display, textColor = textColor)
      if (needsCollapse) {
        TextButton(onClick = { expanded = !expanded }) {
          Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
        }
      }
    }
  }
}

@Composable
private fun AssistantTypedContentCard(part: ChatMessageContent, textColor: Color, stateKey: String) {
  val type = part.type.trim().lowercase(Locale.US)
  if (part.base64 != null && (type == "image" || part.mimeType?.startsWith("image/") == true)) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, mobileBorder),
      color = mobileCardSurface.copy(alpha = 0.7f),
    ) {
      Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("图片", style = mobileCaption2, color = mobileTextSecondary)
        ChatBase64Image(base64 = part.base64, mimeType = part.mimeType)
      }
    }
    return
  }

  when (type) {
    "thinking", "reasoning", "thought" -> {
      AssistantReasoningCard(part = part, textColor = textColor, stateKey = stateKey)
    }
    "tool_call", "toolcall", "tool_result", "toolresult" -> {
      AssistantToolCard(part = part, textColor = textColor, stateKey = stateKey)
    }
    else -> {
      AssistantMetaCard(part = part, textColor = textColor, stateKey = stateKey)
    }
  }
}

private fun assistantTypeLabel(type: String): String {
  return when (type.trim().lowercase(Locale.US)) {
    "text" -> "文本"
    "image" -> "图片"
    "tool_call", "toolcall" -> "工具调用"
    "tool_result", "toolresult" -> "工具结果"
    "thinking", "reasoning", "thought" -> "思考"
    else -> "内容 · $type"
  }
}

@Composable
private fun AssistantReasoningCard(part: ChatMessageContent, textColor: Color, stateKey: String) {
  var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
  val raw = part.text?.trim().orEmpty().ifEmpty { "(empty reasoning)" }
  val needsCollapse = raw.length > assistantMetaPreviewChars
  val display = if (needsCollapse && !expanded) raw.take(assistantMetaPreviewChars) + "…" else raw

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileBorder),
    color = mobileCardSurface.copy(alpha = 0.55f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("思考", style = mobileCaption2, color = mobileTextSecondary)
      Text(display, style = mobileCallout.copy(fontFamily = FontFamily.Monospace), color = mobileTextSecondary)
      if (needsCollapse) {
        TextButton(onClick = { expanded = !expanded }) {
          Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
        }
      }
    }
  }
}

@Composable
private fun AssistantToolCard(part: ChatMessageContent, textColor: Color, stateKey: String) {
  var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
  val label = assistantTypeLabel(part.type)
  val rawText = part.text?.trim()
  val pretty = rawText?.let(::tryPrettyJson)
  val body = pretty ?: rawText

  val meta =
    buildString {
      part.mimeType?.takeIf { it.isNotBlank() }?.let { append("mime=" + it) }
      part.fileName?.takeIf { it.isNotBlank() }?.let {
        if (isNotEmpty()) append(" · ")
        append("file=" + it)
      }
      part.base64?.let {
        if (isNotEmpty()) append(" · ")
        append("base64Len=" + it.length)
      }
    }.ifBlank { null }

  val contentRaw =
    body
      ?: buildString {
        append("type=")
        append(part.type.ifBlank { "unknown" })
        part.base64?.let { append("\ncontent(base64Len)=" + it.length) }
      }
  val needsCollapse = contentRaw.length > assistantTextPreviewChars
  val contentDisplay = if (needsCollapse && !expanded) contentRaw.take(assistantTextPreviewChars) + "…" else contentRaw

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileCodeBorder),
    color = mobileCodeBg.copy(alpha = 0.55f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(label, style = mobileCaption2, color = mobileTextSecondary)
      meta?.let { Text(it, style = mobileCaption1, color = mobileTextSecondary, fontFamily = FontFamily.Monospace) }
      ChatMarkdown(text = "```json\n$contentDisplay\n```", textColor = textColor)
      if (needsCollapse) {
        TextButton(onClick = { expanded = !expanded }) {
          Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
        }
      }
    }
  }
}

@Composable
private fun AssistantMetaCard(part: ChatMessageContent, textColor: Color, stateKey: String) {
  var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
  val metaRaw =
    buildString {
      append("type=")
      append(part.type.ifBlank { "unknown" })
      part.text?.takeIf { it.isNotBlank() }?.let { append("\ntext=" + it.trim()) }
      part.mimeType?.takeIf { it.isNotBlank() }?.let { append("\nmime=" + it) }
      part.fileName?.takeIf { it.isNotBlank() }?.let { append("\nfile=" + it) }
      part.base64?.let { append("\ncontent(base64Len)=" + it.length) }
    }
  val needsCollapse = metaRaw.length > assistantMetaPreviewChars
  val metaDisplay =
    if (needsCollapse && !expanded) metaRaw.take(assistantMetaPreviewChars) + "…" else metaRaw

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileCodeBorder),
    color = mobileCodeBg.copy(alpha = 0.55f),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(assistantTypeLabel(part.type), style = mobileCaption2, color = mobileTextSecondary)
      ChatMarkdown(text = "```text\n$metaDisplay\n```", textColor = textColor)
      if (needsCollapse) {
        TextButton(onClick = { expanded = !expanded }) {
          Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
        }
      }
    }
  }
}

private fun tryPrettyJson(raw: String): String? {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return null
  return try {
    when {
      trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
      trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
      else -> null
    }
  } catch (_: Throwable) {
    null
  }
}

private fun extractUserVisibleInput(raw: String): String {
  var text = raw.trim()
  if (text.isEmpty()) return raw

  // Shared channel wrapper used by gateway history contexts.
  val currentMessageMarker = "[Current message - respond to this]"
  val markerIdx = text.indexOf(currentMessageMarker, ignoreCase = true)
  if (markerIdx >= 0) {
    text =
      text.substring(markerIdx + currentMessageMarker.length)
        .trim()
        .trimStart(':', '：', '-', ' ')
  }

  val policyHeader = "Skills store policy (operator configured):"
  if (!text.contains(policyHeader, ignoreCase = true)) return text

  val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
  if (lines.isEmpty()) return text

  // Prefer trailing "[Thu ...] actual input" format.
  for (line in lines.asReversed()) {
    if (line.startsWith("[")) {
      val close = line.indexOf(']')
      if (close in 1 until line.lastIndex) {
        val tail = line.substring(close + 1).trim()
        if (tail.isNotEmpty()) return tail
      }
    }
  }

  // Fallback: last non-policy/non-list line.
  for (line in lines.asReversed()) {
    if (line.equals(policyHeader, ignoreCase = true)) continue
    if (numberedLineRegex.matches(line)) continue
    if (line.startsWith("`") && line.endsWith("`")) continue
    return line
  }

  return text
}

@Composable
fun ChatTypingIndicatorBubble(assistantLabel: String) {
  ChatBubbleContainer(
    style = bubbleStyle("assistant"),
    roleLabel = roleLabel("assistant", assistantLabel),
    timestampMs = null,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      DotPulse(color = mobileTextSecondary)
      Text("思考中…", style = mobileCallout, color = mobileTextSecondary)
    }
  }
}

@Composable
fun ChatPendingToolsBubble(toolCalls: List<ChatPendingToolCall>, assistantLabel: String) {
  val context = LocalContext.current
  val displays =
    remember(toolCalls, context) {
      toolCalls.map { ToolDisplayRegistry.resolve(context, it.name, it.args) }
    }

  ChatBubbleContainer(
    style = bubbleStyle("assistant"),
    roleLabel = "$assistantLabel · 工具",
    timestampMs = null,
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text("正在运行工具…", style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileTextSecondary)
      for (display in displays.take(6)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            "${display.emoji} ${display.label}",
            style = mobileCallout,
            color = mobileTextSecondary,
            fontFamily = FontFamily.Monospace,
          )
          display.detailLine?.let { detail ->
            Text(
              detail,
              style = mobileCaption1,
              color = mobileTextSecondary,
              fontFamily = FontFamily.Monospace,
            )
          }
        }
      }
      if (toolCalls.size > 6) {
        Text(
          text = "... +${toolCalls.size - 6} more",
          style = mobileCaption1,
          color = mobileTextSecondary,
        )
      }
    }
  }
}

@Composable
fun ChatStreamingAssistantBubble(text: String, assistantLabel: String) {
  ChatBubbleContainer(
    style = bubbleStyle("assistant").copy(borderColor = mobileAccent),
    roleLabel = "$assistantLabel · 实时",
    timestampMs = null,
  ) {
    ChatMarkdown(text = text, textColor = mobileText)
  }
}

@Composable
private fun bubbleStyle(role: String): ChatBubbleStyle {
  return when (role) {
    "user" ->
      ChatBubbleStyle(
        alignEnd = true,
        containerColor = mobileAccentSoft,
        borderColor = mobileAccent,
        roleColor = mobileAccent,
      )

    "system" ->
      ChatBubbleStyle(
        alignEnd = false,
        containerColor = mobileWarningSoft,
        borderColor = mobileWarning.copy(alpha = 0.45f),
        roleColor = mobileWarning,
      )

    else ->
      ChatBubbleStyle(
        alignEnd = false,
        containerColor = mobileCardSurface,
        borderColor = mobileBorderStrong,
        roleColor = mobileTextSecondary,
      )
  }
}

private fun roleLabel(role: String, assistantLabel: String): String {
  return when (role) {
    "user" -> "你"
    "system" -> "系统"
    else -> assistantLabel
  }
}

private fun formatMessageTime(timestampMs: Long): String {
  val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
  return formatter.format(Date(timestampMs))
}

@Composable
private fun ChatBase64Image(base64: String, mimeType: String?) {
  val imageState = rememberBase64ImageState(base64)
  val image = imageState.image

  if (image != null) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, mobileBorder),
      color = mobileCardSurface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Image(
        bitmap = image!!,
        contentDescription = mimeType ?: "附件",
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  } else if (imageState.failed) {
    Text("无法显示的附件", style = mobileCaption1, color = mobileTextSecondary)
  }
}

@Composable
private fun DotPulse(color: Color) {
  Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
    PulseDot(alpha = 0.38f, color = color)
    PulseDot(alpha = 0.62f, color = color)
    PulseDot(alpha = 0.90f, color = color)
  }
}

@Composable
private fun PulseDot(alpha: Float, color: Color) {
  Surface(
    modifier = Modifier.size(6.dp).alpha(alpha),
    shape = CircleShape,
    color = color,
  ) {}
}

@Composable
fun ChatCodeBlock(code: String, language: String?) {
  Surface(
    shape = RoundedCornerShape(8.dp),
    color = mobileCodeBg,
    border = BorderStroke(1.dp, mobileCodeBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      if (!language.isNullOrBlank()) {
        Text(
          text = language.uppercase(Locale.US),
          style = mobileCaption2.copy(letterSpacing = 0.4.sp),
          color = mobileTextSecondary,
        )
      }
      Text(
        text = code.trimEnd(),
        fontFamily = FontFamily.Monospace,
        style = mobileCallout,
        color = mobileCodeText,
      )
    }
  }
}
