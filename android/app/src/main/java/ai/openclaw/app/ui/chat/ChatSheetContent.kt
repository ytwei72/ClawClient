package ai.openclaw.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.chat.ChatGatewayAgent
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.chat.OutgoingAttachment
import ai.openclaw.app.ui.mobileAccent
import ai.openclaw.app.ui.mobileAccentBorderStrong
import ai.openclaw.app.ui.mobileBorder
import ai.openclaw.app.ui.mobileBorderStrong
import ai.openclaw.app.ui.mobileCallout
import ai.openclaw.app.ui.mobileCardSurface
import ai.openclaw.app.ui.mobileCaption1
import ai.openclaw.app.ui.mobileCaption2
import ai.openclaw.app.ui.mobileDanger
import ai.openclaw.app.ui.mobileDangerSoft
import ai.openclaw.app.ui.mobileHeadline
import ai.openclaw.app.ui.mobileText
import ai.openclaw.app.ui.mobileTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSheetContent(viewModel: MainViewModel) {
  val messages by viewModel.chatMessages.collectAsState()
  val errorText by viewModel.chatError.collectAsState()
  val pendingRunCount by viewModel.pendingRunCount.collectAsState()
  val healthOk by viewModel.chatHealthOk.collectAsState()
  val sessionKey by viewModel.chatSessionKey.collectAsState()
  val mainSessionKey by viewModel.mainSessionKey.collectAsState()
  val thinkingLevel by viewModel.chatThinkingLevel.collectAsState()
  val streamingAssistantText by viewModel.chatStreamingAssistantText.collectAsState()
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val gatewayAgents by viewModel.chatGatewayAgents.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()

  LaunchedEffect(mainSessionKey) {
    viewModel.loadChat(mainSessionKey)
  }

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshChatGatewayAgents()
      viewModel.refreshChatSessions(limit = 500)
    }
  }

  val context = LocalContext.current
  val resolver = context.contentResolver
  val scope = rememberCoroutineScope()

  val attachments = remember { mutableStateListOf<PendingImageAttachment>() }

  val pickImages =
    rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
      if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
      scope.launch(Dispatchers.IO) {
        val next =
          uris.take(8).mapNotNull { uri ->
            try {
              loadSizedImageAttachment(resolver, uri)
            } catch (_: Throwable) {
              null
            }
          }
        withContext(Dispatchers.Main) {
          attachments.addAll(next)
        }
      }
    }

  val assistantLabel =
    remember(sessionKey, gatewayAgents) {
      resolveAssistantLabel(
        sessionKey = sessionKey,
        selectedAgentId = null,
        agents = gatewayAgents,
      )
    }

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .padding(horizontal = 20.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (!errorText.isNullOrBlank()) {
      ChatErrorRail(errorText = errorText!!)
    }

    ChatMessageListCard(
      messages = messages,
      pendingRunCount = pendingRunCount,
      pendingToolCalls = pendingToolCalls,
      streamingAssistantText = streamingAssistantText,
      assistantLabel = assistantLabel,
      healthOk = healthOk,
      modifier = Modifier.weight(1f, fill = true),
    )

    Row(modifier = Modifier.fillMaxWidth().imePadding()) {
      ChatComposer(
        healthOk = healthOk,
        thinkingLevel = thinkingLevel,
        pendingRunCount = pendingRunCount,
        attachments = attachments,
        onPickImages = { pickImages.launch("image/*") },
        onRemoveAttachment = { id -> attachments.removeAll { it.id == id } },
        onSetThinkingLevel = { level -> viewModel.setChatThinkingLevel(level) },
        onRefresh = {
          viewModel.refreshChat()
          viewModel.refreshChatSessions(limit = 500)
          viewModel.refreshChatGatewayAgents()
        },
        onAbort = { viewModel.abortChat() },
        onSend = { text ->
          val outgoing =
            attachments.map { att ->
              OutgoingAttachment(
                type = "image",
                mimeType = att.mimeType,
                fileName = att.fileName,
                base64 = att.base64,
              )
            }
          viewModel.sendChat(message = text, thinking = thinkingLevel, attachments = outgoing)
          attachments.clear()
        },
      )
    }
  }
}

@Composable
private fun SessionRowItem(title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = if (active) mobileAccent.copy(alpha = 0.22f) else mobileCardSurface,
    border = BorderStroke(1.dp, if (active) mobileAccentBorderStrong else mobileBorderStrong),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Text(
        text = title,
        style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold),
        color = mobileText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = subtitle,
        style = mobileCaption2,
        color = mobileTextSecondary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

private fun chatSessionKeyForAgent(agentId: String): String {
  val id = agentId.trim()
  return if (id.isEmpty()) "main" else "agent:$id:main"
}

private fun gatewayMainSessionChipLabel(mainSessionKey: String): String {
  val m = mainSessionKey.trim()
  if (m.isEmpty() || m == "main") return "默认 (main)"
  return "默认 · ${friendlySessionName(m)}"
}

private fun resolveAssistantLabel(
  sessionKey: String,
  selectedAgentId: String?,
  agents: List<ChatGatewayAgent>,
): String {
  val currentAgentId = selectedAgentId?.trim().takeIf { !it.isNullOrEmpty() } ?: agentIdFromSessionKey(sessionKey)
  if (currentAgentId.isNullOrEmpty()) return "助手"
  val match = agents.firstOrNull { it.id.trim() == currentAgentId } ?: return currentAgentId
  return match.name?.trim()?.takeIf { it.isNotEmpty() } ?: match.id
}

@Composable
private fun ChatGatewaySelector(
  agents: List<ChatGatewayAgent>,
  sessionKey: String,
  mainSessionKey: String,
  onSelectGatewayDefault: () -> Unit,
  onSelectAgent: (String) -> Unit,
) {
  val canonicalMain = mainSessionKey.trim().ifEmpty { "main" }
  val gatewayDefaultActive = sessionKey.trim() == canonicalMain
  val sortedAgents =
    remember(agents) {
      agents
        .filter { it.id.isNotBlank() }
        .sortedWith(compareBy({ it.name?.lowercase().orEmpty() }, { it.id.lowercase() }))
    }

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Surface(
      onClick = onSelectGatewayDefault,
      shape = RoundedCornerShape(14.dp),
      color = if (gatewayDefaultActive) mobileAccent else mobileCardSurface,
      border = BorderStroke(1.dp, if (gatewayDefaultActive) mobileAccentBorderStrong else mobileBorderStrong),
      tonalElevation = 0.dp,
      shadowElevation = 0.dp,
    ) {
      Text(
        text = gatewayMainSessionChipLabel(mainSessionKey),
        style = mobileCaption1.copy(fontWeight = if (gatewayDefaultActive) FontWeight.Bold else FontWeight.SemiBold),
        color = if (gatewayDefaultActive) Color.White else mobileText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      )
    }

    for (agent in sortedAgents) {
      val agentKey = chatSessionKeyForAgent(agent.id)
      val active = sessionKey.trim() == agentKey
      val label =
        buildString {
          val em = agent.emoji?.trim().orEmpty()
          if (em.isNotEmpty()) {
            append(em)
            append(' ')
          }
          append(agent.name?.trim()?.takeIf { it.isNotEmpty() } ?: agent.id)
        }
      Surface(
        onClick = { onSelectAgent(agent.id) },
        shape = RoundedCornerShape(14.dp),
        color = if (active) mobileAccent else mobileCardSurface,
        border = BorderStroke(1.dp, if (active) mobileAccentBorderStrong else mobileBorderStrong),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
      ) {
        Text(
          text = label,
          style = mobileCaption1.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold),
          color = if (active) Color.White else mobileText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun ChatThreadSelector(
  sessionKey: String,
  sessions: List<ChatSessionEntry>,
  mainSessionKey: String,
  onSelectSession: (String) -> Unit,
) {
  val sessionOptions =
    remember(sessionKey, sessions, mainSessionKey) {
      resolveSessionChoices(sessionKey, sessions, mainSessionKey = mainSessionKey)
    }

  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    for (entry in sessionOptions) {
      val active = entry.key == sessionKey
      Surface(
        onClick = { onSelectSession(entry.key) },
        shape = RoundedCornerShape(14.dp),
        color = if (active) mobileAccent else mobileCardSurface,
        border = BorderStroke(1.dp, if (active) mobileAccentBorderStrong else mobileBorderStrong),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
      ) {
        Text(
          text = friendlySessionName(entry.displayName ?: entry.key),
          style = mobileCaption1.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold),
          color = if (active) Color.White else mobileText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }
  }
}

@Composable
private fun ChatErrorRail(errorText: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    color = mobileDangerSoft,
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, mobileDanger),
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        text = "聊天错误",
        style = mobileCaption2.copy(letterSpacing = 0.6.sp),
        color = mobileDanger,
      )
      Text(text = errorText, style = mobileCallout, color = mobileText)
    }
  }
}

data class PendingImageAttachment(
  val id: String,
  val fileName: String,
  val mimeType: String,
  val base64: String,
)
