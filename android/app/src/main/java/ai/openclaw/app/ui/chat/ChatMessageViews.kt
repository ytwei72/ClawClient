package ai.openclaw.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
private const val assistantCollapsedPreviewChars = 64

/**
 * 是否把该 content 的 type 并入 role 卡片标题：**`trim` 后非空即纳入**，保留网关原文。
 *
 * 聊天 UI 里曾出现过、或解析分支里对照过的 `ChatMessageContent.type` 包括但不限于（网关可增删，未列出的 type 同样会进标题）：
 * - **`text`**：正文 Markdown
 * - **`image`**：常见于带 `base64` / `mimeType` 的图片块
 * - **`thinking`** / **`reasoning`** / **`thought`**：推理类块
 * - **`tool_call`** / **`toolcall`**：工具调用
 * - **`tool_result`** / **`toolresult`**：工具结果
 * - **其它任意字符串**：走 `AssistantMetaCard` 等默认展示的分支
 */
private fun toolPartTypeForRoleTitle(partType: String): String? {
  val raw = partType.trim()
  return raw.ifEmpty { null }
}

/**
 * Role 卡片标题：**`[各消息 role 用 + 连接]: [各 part type 用 + 连接]`**（冒号后有一空格），不含 Agent 名。
 * - **消息 role**：`user` 气泡仍单独用「你」，此处不处理；其余见 `ChatMessage.role`。
 * - **part type**：见 `toolPartTypeForRoleTitle`。
 * 两段各自去重（按不区分大小写合并写法）、组内字典序；无 part type 时仍为 `roles: `（冒号后保留一空格）。
 */
private fun roleCardTitle(messages: List<ChatMessage>): String {
  if (messages.isEmpty()) return ": "

  val messageRoles =
    messages
      .map { msg ->
        val r = msg.role.trim()
        if (r.isEmpty()) "?" else r
      }
      .distinctBy { it.lowercase(Locale.US) }

  val partTypes =
    messages
      .flatMap { msg ->
        msg.content.mapNotNull { toolPartTypeForRoleTitle(it.type) }
      }
      .distinctBy { it.lowercase(Locale.US) }

  val cmp = compareBy<String> { it.lowercase(Locale.US) }
  val rolesSegment = messageRoles.sortedWith(cmp).joinToString("+")
  val typesSegment = partTypes.sortedWith(cmp).joinToString("+")
  return "$rolesSegment: $typesSegment"
}

private fun ellipsizeOneLine(s: String, maxChars: Int = assistantCollapsedPreviewChars): String {
  val single = s.replace(Regex("\\s+"), " ").trim()
  if (single.isEmpty()) return ""
  return if (single.length <= maxChars) single else single.take(maxChars) + "…"
}

private fun plainContentOneLineSummary(part: ChatMessageContent): String {
  val type = part.type.trim().lowercase(Locale.US)
  if (part.base64 != null && (type == "image" || part.mimeType?.startsWith("image/") == true)) {
    return "图片附件"
  }
  part.text?.trim()?.takeIf { it.isNotEmpty() }?.let { return ellipsizeOneLine(it) }
  part.fileName?.takeIf { it.isNotBlank() }?.let { return ellipsizeOneLine("file=$it") }
  part.mimeType?.takeIf { it.isNotBlank() }?.let { return ellipsizeOneLine(it) }
  if (part.base64 != null) {
    return "附件·base64Len=${part.base64.length}"
  }
  return "（空）"
}

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

  val roleLabelText =
    when (role) {
      "user" -> roleLabel(role, assistantLabel)
      else -> roleCardTitle(listOf(message))
    }

  ChatBubbleContainer(style = style, roleLabel = roleLabelText, timestampMs = message.timestampMs) {
    ChatMessageBody(content = displayableContent, textColor = mobileText, role = role, messageId = message.id)
  }
}

/**
 * One assistant "turn": rows after the same user message (incl. `system` in between). [messages] is oldest → newest.
 * Each non-user content part has its own fold; there is no whole-turn collapse.
 */
@Composable
fun ChatAssistantTurnBubble(messages: List<ChatMessage>) {
  if (messages.isEmpty()) return

  val withDisplayable =
    remember(messages) {
      messages.mapNotNull { msg ->
        val parts =
          msg.content.filter { part ->
            part.base64 != null || !part.text.isNullOrBlank()
          }
        if (parts.isEmpty()) null else msg to parts
      }
    }

  if (withDisplayable.isEmpty()) return

  val style = bubbleStyle("assistant")
  val headerTime = withDisplayable.last().first.timestampMs
  val turnFoldKey = remember(messages) { messages.joinToString(":") { it.id } }
  val totalParts = withDisplayable.sumOf { it.second.size }
  val needsTurnFold = withDisplayable.size > 1 || totalParts > 1
  var turnExpanded by rememberSaveable(turnFoldKey) { mutableStateOf(true) }

  ChatBubbleContainer(
    style = style,
    roleLabel = roleCardTitle(messages),
    timestampMs = headerTime,
    headerTrailing =
      if (needsTurnFold) {
        {
          TextButton(
            onClick = { turnExpanded = !turnExpanded },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
          ) {
            Text(
              if (turnExpanded) "收起回合" else "展开回合",
              style = mobileCaption1,
              color = mobileAccent,
            )
          }
        }
      } else {
        null
      },
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(if (turnExpanded) 10.dp else 4.dp),
    ) {
      for ((index, chunk) in withDisplayable.withIndex()) {
        val (msg, displayable) = chunk
        if (turnExpanded && withDisplayable.size > 1) {
          Text(
            text =
              "分段 ${index + 1}/${withDisplayable.size}  ·  message.role=${msg.role.trim().ifEmpty { "?" }}  ·  id=${msg.id}",
            style = mobileCaption2,
            color = mobileAccent.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace,
          )
        }
        val bodyRole = msg.role.trim().ifEmpty { "assistant" }
        ChatMessageBody(
          content = displayable,
          textColor = mobileText,
          role = bodyRole,
          messageId = msg.id,
          assistantPartCollapse = true,
          compactSingleLine = !turnExpanded,
        )
      }
    }
  }
}

@Composable
private fun ChatBubbleContainer(
  style: ChatBubbleStyle,
  roleLabel: String,
  timestampMs: Long?,
  modifier: Modifier = Modifier,
  headerTrailing: (@Composable RowScope.() -> Unit)? = null,
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
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            headerTrailing?.invoke(this)
            timestampMs?.let {
              Text(
                text = formatMessageTime(it),
                style = mobileCaption2,
                color = mobileTextSecondary,
              )
            }
          }
        }
        content()
      }
    }
  }
}

@Composable
private fun ChatMessageBody(
  content: List<ChatMessageContent>,
  textColor: Color,
  role: String,
  messageId: String,
  /** Long non-user parts use 收起/展开；user 消息不受影响。 */
  assistantPartCollapse: Boolean = true,
  /** 最外层回合折叠时，每块类型卡片仅用一行摘要展示。 */
  compactSingleLine: Boolean = false,
) {
  val isUser = role.trim().equals("user", ignoreCase = true)

  Column(verticalArrangement = Arrangement.spacedBy(if (compactSingleLine) 4.dp else 8.dp)) {
    for ((idx, part) in content.withIndex()) {
      val partType = part.type.trim().lowercase(Locale.US)
      when (partType) {
        "text" -> {
          val text = part.text ?: continue
          if (isUser) {
            UserTextWithDebugToggle(rawText = text, textColor = textColor, stateKey = "$messageId:$idx")
          } else {
            AssistantTextCard(
              rawContentType = part.type,
              text = text,
              textColor = textColor,
              stateKey = "$messageId:$idx:text",
              collapseLongText = assistantPartCollapse,
              compactSingleLine = compactSingleLine,
            )
          }
        }
        else -> {
          if (isUser) {
            val b64 = part.base64 ?: continue
            ChatBase64Image(base64 = b64, mimeType = part.mimeType)
          } else {
            AssistantTypedContentCard(
              part = part,
              textColor = textColor,
              stateKey = "$messageId:$idx:${part.type}",
              collapseLongText = assistantPartCollapse,
              compactSingleLine = compactSingleLine,
            )
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

/** 类型卡片标题：展示网关 content 的 `type` 原文（仅 trim）。 */
@Composable
private fun AgentContentTypeHeader(rawContentType: String) {
  val t = rawContentType.trim().ifEmpty { "?" }
  Text(
    text = t,
    style = mobileCaption2,
    color = mobileTextSecondary,
    fontFamily = FontFamily.Monospace,
  )
}

@Composable
private fun AssistantTextCard(
  rawContentType: String,
  text: String,
  textColor: Color,
  stateKey: String,
  collapseLongText: Boolean = true,
  compactSingleLine: Boolean = false,
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileBorder),
    color = mobileCardSurface.copy(alpha = 0.7f),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      AgentContentTypeHeader(rawContentType = rawContentType)
      if (compactSingleLine) {
        Text(
          ellipsizeOneLine(text),
          style = mobileCallout,
          color = textColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      } else {
        if (text.contains("```")) {
          Text(
            text = "· 含 ``` 围栏：代码块等为 Markdown 子渲染，仍为同一 text part，无独立 type",
            style = mobileCaption2,
            color = mobileTextSecondary.copy(alpha = 0.88f),
          )
        }
        if (!collapseLongText) {
          ChatMarkdown(text = text, textColor = textColor)
        } else {
          var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
          val needsCollapse = text.length > assistantCollapsedPreviewChars
          val display = if (needsCollapse && !expanded) text.take(assistantCollapsedPreviewChars) + "…" else text
          ChatMarkdown(text = display, textColor = textColor)
          if (needsCollapse) {
            TextButton(onClick = { expanded = !expanded }) {
              Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AssistantTypedContentCard(
  part: ChatMessageContent,
  textColor: Color,
  stateKey: String,
  collapseLongText: Boolean = true,
  compactSingleLine: Boolean = false,
) {
  val type = part.type.trim().lowercase(Locale.US)
  if (part.base64 != null && (type == "image" || part.mimeType?.startsWith("image/") == true)) {
    Surface(
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, mobileBorder),
      color = mobileCardSurface.copy(alpha = 0.7f),
    ) {
      Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AgentContentTypeHeader(rawContentType = part.type)
        if (compactSingleLine) {
          Text(
            plainContentOneLineSummary(part),
            style = mobileCallout,
            color = mobileTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        } else if (!collapseLongText) {
          ChatBase64Image(base64 = part.base64, mimeType = part.mimeType)
        } else {
          var imageExpanded by rememberSaveable(stateKey) { mutableStateOf(true) }
          if (imageExpanded) {
            ChatBase64Image(base64 = part.base64, mimeType = part.mimeType)
            TextButton(onClick = { imageExpanded = false }) {
              Text("收起图片", style = mobileCaption1, color = mobileAccent)
            }
          } else {
            Text("（图片已折叠）", style = mobileCaption2, color = mobileTextSecondary)
            TextButton(onClick = { imageExpanded = true }) {
              Text("展开图片", style = mobileCaption1, color = mobileAccent)
            }
          }
        }
      }
    }
    return
  }

  when (type) {
    "thinking", "reasoning", "thought" -> {
      AssistantReasoningCard(
        part = part,
        textColor = textColor,
        stateKey = stateKey,
        collapseLongText = collapseLongText,
        compactSingleLine = compactSingleLine,
      )
    }
    "tool_call", "toolcall", "tool_result", "toolresult" -> {
      AssistantToolCard(
        part = part,
        textColor = textColor,
        stateKey = stateKey,
        collapseLongText = collapseLongText,
        compactSingleLine = compactSingleLine,
      )
    }
    else -> {
      AssistantMetaCard(
        part = part,
        textColor = textColor,
        stateKey = stateKey,
        collapseLongText = collapseLongText,
        compactSingleLine = compactSingleLine,
      )
    }
  }
}

@Composable
private fun AssistantReasoningCard(
  part: ChatMessageContent,
  textColor: Color,
  stateKey: String,
  collapseLongText: Boolean = true,
  compactSingleLine: Boolean = false,
) {
  val raw = part.text?.trim().orEmpty().ifEmpty { "(empty reasoning)" }

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileBorder),
    color = mobileCardSurface.copy(alpha = 0.55f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      AgentContentTypeHeader(rawContentType = part.type)
      if (compactSingleLine) {
        Text(
          plainContentOneLineSummary(part.copy(text = raw)),
          style = mobileCallout.copy(fontFamily = FontFamily.Monospace),
          color = mobileTextSecondary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      } else if (!collapseLongText) {
        Text(raw, style = mobileCallout.copy(fontFamily = FontFamily.Monospace), color = mobileTextSecondary)
      } else {
        var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
        val needsCollapse = raw.length > assistantCollapsedPreviewChars
        val display = if (needsCollapse && !expanded) raw.take(assistantCollapsedPreviewChars) + "…" else raw
        Text(display, style = mobileCallout.copy(fontFamily = FontFamily.Monospace), color = mobileTextSecondary)
        if (needsCollapse) {
          TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
          }
        }
      }
    }
  }
}

@Composable
private fun AssistantToolCard(
  part: ChatMessageContent,
  textColor: Color,
  stateKey: String,
  collapseLongText: Boolean = true,
  compactSingleLine: Boolean = false,
) {
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

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileCodeBorder),
    color = mobileCodeBg.copy(alpha = 0.55f),
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      AgentContentTypeHeader(rawContentType = part.type)
      if (compactSingleLine) {
        val line = (meta?.let { "$it · " }.orEmpty()) + ellipsizeOneLine(contentRaw)
        Text(
          line,
          style = mobileCallout,
          color = mobileTextSecondary,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      } else {
        meta?.let { Text(it, style = mobileCaption1, color = mobileTextSecondary, fontFamily = FontFamily.Monospace) }
        if (!collapseLongText) {
          ChatMarkdown(text = "```json\n$contentRaw\n```", textColor = textColor)
        } else {
          var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
          val needsCollapse = contentRaw.length > assistantCollapsedPreviewChars
          val contentDisplay =
            if (needsCollapse && !expanded) contentRaw.take(assistantCollapsedPreviewChars) + "…" else contentRaw
          ChatMarkdown(text = "```json\n$contentDisplay\n```", textColor = textColor)
          if (needsCollapse) {
            TextButton(onClick = { expanded = !expanded }) {
              Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AssistantMetaCard(
  part: ChatMessageContent,
  textColor: Color,
  stateKey: String,
  collapseLongText: Boolean = true,
  compactSingleLine: Boolean = false,
) {
  val metaRaw =
    buildString {
      append("type=")
      append(part.type.ifBlank { "unknown" })
      part.text?.takeIf { it.isNotBlank() }?.let { append("\ntext=" + it.trim()) }
      part.mimeType?.takeIf { it.isNotBlank() }?.let { append("\nmime=" + it) }
      part.fileName?.takeIf { it.isNotBlank() }?.let { append("\nfile=" + it) }
      part.base64?.let { append("\ncontent(base64Len)=" + it.length) }
    }

  Surface(
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, mobileCodeBorder),
    color = mobileCodeBg.copy(alpha = 0.55f),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      AgentContentTypeHeader(rawContentType = part.type)
      if (compactSingleLine) {
        Text(
          ellipsizeOneLine(metaRaw.replace("\n", " ")),
          style = mobileCallout,
          color = mobileTextSecondary,
          fontFamily = FontFamily.Monospace,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      } else if (!collapseLongText) {
        ChatMarkdown(text = "```text\n$metaRaw\n```", textColor = textColor)
      } else {
        var expanded by rememberSaveable(stateKey) { mutableStateOf(false) }
        val needsCollapse = metaRaw.length > assistantCollapsedPreviewChars
        val metaDisplay =
          if (needsCollapse && !expanded) metaRaw.take(assistantCollapsedPreviewChars) + "…" else metaRaw
        ChatMarkdown(text = "```text\n$metaDisplay\n```", textColor = textColor)
        if (needsCollapse) {
          TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "收起" else "展开查看全部", style = mobileCaption1, color = mobileAccent)
          }
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = "assistant：typing",
        style = mobileCaption2,
        color = mobileTextSecondary,
        fontFamily = FontFamily.Monospace,
      )
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        DotPulse(color = mobileTextSecondary)
        Text("思考中…", style = mobileCallout, color = mobileTextSecondary)
      }
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
      Text(
        text = "assistant：tools_pending",
        style = mobileCaption2,
        color = mobileTextSecondary,
        fontFamily = FontFamily.Monospace,
      )
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = "assistant：stream",
        style = mobileCaption2,
        color = mobileTextSecondary,
        fontFamily = FontFamily.Monospace,
      )
      ChatMarkdown(text = text, textColor = mobileText)
    }
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
