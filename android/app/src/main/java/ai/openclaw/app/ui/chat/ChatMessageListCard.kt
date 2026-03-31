package ai.openclaw.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatPendingToolCall
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
import ai.openclaw.app.ui.mobileBorder
import ai.openclaw.app.ui.mobileCallout
import ai.openclaw.app.ui.mobileCardSurface
import ai.openclaw.app.ui.mobileHeadline
import ai.openclaw.app.ui.mobileText
import ai.openclaw.app.ui.mobileTextSecondary

private sealed class ChatListRow {
  data class Single(val message: ChatMessage) : ChatListRow()

  data class AssistantTurn(val messages: List<ChatMessage>) : ChatListRow()
}

/**
 * [messagesOldestFirst] is stored gateway order. One assistant "round" is everything until the next **user**
 * message: all consecutive `assistant` rows **and** any `system` rows the gateway inserts between them
 * (tool status, etc.). Previously we flushed on every non-assistant, which split one user turn into many
 * one-line bubbles that each looked like a separate fold.
 */
private fun groupMessagesForChatList(messagesOldestFirst: List<ChatMessage>): List<ChatListRow> {
  if (messagesOldestFirst.isEmpty()) return emptyList()
  val rowsChrono = mutableListOf<ChatListRow>()
  var turnBuffer = mutableListOf<ChatMessage>()

  fun flushTurn() {
    if (turnBuffer.isEmpty()) return
    val onlySystem =
      turnBuffer.all { it.role.trim().lowercase(Locale.US) == "system" }
    if (onlySystem) {
      for (m in turnBuffer) rowsChrono.add(ChatListRow.Single(m))
    } else {
      rowsChrono.add(ChatListRow.AssistantTurn(turnBuffer.toList()))
    }
    turnBuffer = mutableListOf()
  }

  for (msg in messagesOldestFirst) {
    val role = msg.role.trim().lowercase(Locale.US)
    when (role) {
      "user" -> {
        flushTurn()
        rowsChrono.add(ChatListRow.Single(msg))
      }
      "assistant",
      "system",
      "model",
      -> turnBuffer.add(msg)

      else -> {
        flushTurn()
        rowsChrono.add(ChatListRow.Single(msg))
      }
    }
  }
  flushTurn()
  return rowsChrono.asReversed()
}

private fun lazyListHeaderItemCount(
  stream: String?,
  pendingToolCalls: List<*>,
  pendingRunCount: Int,
): Int {
  var n = 0
  if (!stream.isNullOrBlank()) n++
  if (pendingToolCalls.isNotEmpty()) n++
  if (pendingRunCount > 0) n++
  return n
}

/** [listRows] 与 LazyColumn `items` 顺序一致（新消息在前）；返回各用户气泡在 LazyColumn 中的 index。 */
private fun userMessageLazyIndices(
  listRows: List<ChatListRow>,
  headerItemCount: Int,
): List<Int> =
  listRows.mapIndexedNotNull { i, row ->
    if (row is ChatListRow.Single) {
      val r = row.message.role.trim().lowercase(Locale.US)
      if (r == "user") headerItemCount + i else null
    } else {
      null
    }
  }

/**
 * reverseLayout 列表中 index 较小者更靠近窗口底部（更新）。锚定视口内用户条，↑ 跳到更早的用户（更大 index），↓ 跳到更新的用户（更小 index）。
 */
private fun anchorUserLazyIndex(
  layoutInfo: LazyListLayoutInfo,
  userIndices: List<Int>,
): Int? {
  if (userIndices.isEmpty()) return null
  val sorted = userIndices.sorted()
  val set = sorted.toSet()
  val vis = layoutInfo.visibleItemsInfo
  if (vis.isEmpty()) return sorted[sorted.size / 2]
  val visibleUsers = vis.map { it.index }.filter { it in set }.sorted()
  if (visibleUsers.isNotEmpty()) {
    return visibleUsers[visibleUsers.size / 2]
  }
  val minV = vis.minOf { it.index }
  val maxV = vis.maxOf { it.index }
  val center = (minV + maxV) / 2
  return sorted.minByOrNull { abs(it - center) } ?: sorted.first()
}

@Composable
private fun UserTurnJumpCapsule(
  listState: LazyListState,
  userLazyIndices: List<Int>,
  modifier: Modifier = Modifier,
) {
  if (userLazyIndices.size < 2) return
  val scope = rememberCoroutineScope()
  @Suppress("UNUSED_VARIABLE")
  val scrollWatch = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
  val sorted = userLazyIndices.sorted()
  val anchor = anchorUserLazyIndex(listState.layoutInfo, sorted) ?: sorted.first()
  val canOlder = sorted.any { it > anchor }
  val canNewer = sorted.any { it < anchor }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(28.dp),
    color = mobileCardSurface.copy(alpha = 0.94f),
    border = BorderStroke(1.dp, mobileBorder),
    tonalElevation = 2.dp,
    shadowElevation = 0.dp,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      IconButton(
        onClick = {
          val a = anchorUserLazyIndex(listState.layoutInfo, sorted) ?: return@IconButton
          val target = sorted.filter { it > a }.minOrNull() ?: return@IconButton
          scope.launch { listState.animateScrollToItem(target) }
        },
        enabled = canOlder,
        modifier = Modifier.size(40.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowUp,
          contentDescription = "上一条用户消息",
        )
      }
      IconButton(
        onClick = {
          val a = anchorUserLazyIndex(listState.layoutInfo, sorted) ?: return@IconButton
          val target = sorted.filter { it < a }.maxOrNull() ?: return@IconButton
          scope.launch { listState.animateScrollToItem(target) }
        },
        enabled = canNewer,
        modifier = Modifier.size(40.dp),
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowDown,
          contentDescription = "下一条用户消息",
        )
      }
    }
  }
}

@Composable
fun ChatMessageListCard(
  messages: List<ChatMessage>,
  pendingRunCount: Int,
  pendingToolCalls: List<ChatPendingToolCall>,
  streamingAssistantText: String?,
  assistantLabel: String,
  healthOk: Boolean,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val listRows = remember(messages) { groupMessagesForChatList(messages) }
  val stream = streamingAssistantText?.trim()
  val headerCount =
    remember(stream, pendingToolCalls, pendingRunCount) {
      lazyListHeaderItemCount(stream, pendingToolCalls, pendingRunCount)
    }
  val userLazyIndices =
    remember(listRows, headerCount) { userMessageLazyIndices(listRows, headerCount) }

  // New list items/tool rows should animate into view, but token streaming should not restart
  // that animation on every delta.
  LaunchedEffect(messages.size, listRows.size, pendingRunCount, pendingToolCalls.size) {
    listState.animateScrollToItem(index = 0)
  }
  LaunchedEffect(stream) {
    if (!stream.isNullOrEmpty()) {
      listState.scrollToItem(index = 0)
    }
  }

  Box(modifier = modifier.fillMaxWidth()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      state = listState,
      reverseLayout = true,
      verticalArrangement = Arrangement.spacedBy(10.dp),
      contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp),
    ) {
      // With reverseLayout = true, index 0 renders at the BOTTOM.
      // So we emit newest items first: streaming → tools → typing → messages (newest→oldest).
      if (!stream.isNullOrEmpty()) {
        item(key = "stream") {
          ChatStreamingAssistantBubble(text = stream, assistantLabel = assistantLabel)
        }
      }

      if (pendingToolCalls.isNotEmpty()) {
        item(key = "tools") {
          ChatPendingToolsBubble(toolCalls = pendingToolCalls, assistantLabel = assistantLabel)
        }
      }

      if (pendingRunCount > 0) {
        item(key = "typing") {
          ChatTypingIndicatorBubble(assistantLabel = assistantLabel)
        }
      }

      items(
        items = listRows,
        key = { row ->
          when (row) {
            is ChatListRow.Single -> row.message.id
            is ChatListRow.AssistantTurn -> row.messages.joinToString(separator = ":") { it.id }
          }
        },
      ) { row ->
        when (row) {
          is ChatListRow.Single -> ChatMessageBubble(message = row.message, assistantLabel = assistantLabel)
          is ChatListRow.AssistantTurn ->
            ChatAssistantTurnBubble(messages = row.messages)
        }
      }
    }

    if (messages.isEmpty() && pendingRunCount == 0 && pendingToolCalls.isEmpty() && streamingAssistantText.isNullOrBlank()) {
      EmptyChatHint(modifier = Modifier.align(Alignment.Center), healthOk = healthOk)
    }

    UserTurnJumpCapsule(
      listState = listState,
      userLazyIndices = userLazyIndices,
      modifier =
        Modifier
          .align(Alignment.CenterEnd)
          .padding(end = 2.dp),
    )
  }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier, healthOk: Boolean) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    color = mobileCardSurface.copy(alpha = 0.9f),
    border = androidx.compose.foundation.BorderStroke(1.dp, mobileBorder),
  ) {
    androidx.compose.foundation.layout.Column(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text("暂无消息", style = mobileHeadline, color = mobileText)
      Text(
        text =
          if (healthOk) {
            "发送第一条消息以开始本会话。"
          } else {
            "请先连接 Gateway，再返回聊天。"
          },
        style = mobileCallout,
        color = mobileTextSecondary,
      )
    }
  }
}
