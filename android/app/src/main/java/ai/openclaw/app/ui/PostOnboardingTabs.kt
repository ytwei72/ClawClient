package ai.openclaw.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.chat.ChatGatewayAgent
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.ui.chat.agentIdFromSessionKey
import ai.openclaw.app.ui.chat.friendlySessionName
import ai.openclaw.app.ui.chat.resolveAllSessionChoicesForAgent

private enum class HomeTab(
  val label: String,
  val icon: ImageVector,
) {
  Connect(label = "连接", icon = Icons.Default.CheckCircle),
  Chat(label = "聊天", icon = Icons.Default.ChatBubble),
  Voice(label = "语音", icon = Icons.Default.RecordVoiceOver),
  Screen(label = "屏幕", icon = Icons.AutoMirrored.Filled.ScreenShare),
  Settings(label = "设置", icon = Icons.Default.Settings),
}

@Composable
fun PostOnboardingTabs(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  var activeTab by rememberSaveable { mutableStateOf(HomeTab.Connect) }
  var chatTabStarted by rememberSaveable { mutableStateOf(false) }
  var screenTabStarted by rememberSaveable { mutableStateOf(false) }
  var showChatSelector by rememberSaveable { mutableStateOf(false) }

  // Stop TTS when user navigates away from voice tab, and lazily keep the Chat/Screen tabs
  // alive after the first visit so repeated tab switches do not rebuild their UI trees.
  val selectVoiceTabRevision by viewModel.selectVoiceTabRevision.collectAsState()

  LaunchedEffect(activeTab) {
    viewModel.setVoiceScreenActive(activeTab == HomeTab.Voice)
    if (activeTab == HomeTab.Chat) {
      chatTabStarted = true
    }
    if (activeTab == HomeTab.Screen) {
      screenTabStarted = true
    }
  }

  LaunchedEffect(selectVoiceTabRevision) {
    if (selectVoiceTabRevision > 0) {
      activeTab = HomeTab.Voice
    }
  }

  val statusText by viewModel.statusText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val chatSessionKey by viewModel.chatSessionKey.collectAsState()
  val mainSessionKey by viewModel.mainSessionKey.collectAsState()
  val chatSessions by viewModel.chatSessions.collectAsState()
  val chatGatewayAgents by viewModel.chatGatewayAgents.collectAsState()
  val chatTopBarTitles =
    remember(activeTab, chatSessionKey, chatGatewayAgents) {
      when (activeTab) {
        HomeTab.Chat -> {
          val (agent, session) = buildChatTopBarTitles(chatSessionKey, chatGatewayAgents)
          agent to session
        }
        else -> "OpenClaw" to null
      }
    }
  val (chatTopTitle, chatTopSubtitle) = chatTopBarTitles

  val statusVisual = remember(statusText, isConnected) { deriveScreenStatusVisual(statusText, isConnected) }
  val micEnabled by viewModel.micEnabled.collectAsState()
  val micCooldown by viewModel.micCooldown.collectAsState()
  val speakerEnabled by viewModel.speakerEnabled.collectAsState()

  val density = LocalDensity.current
  val imeVisible = WindowInsets.ime.getBottom(density) > 0
  val hideBottomTabBar = activeTab == HomeTab.Chat && imeVisible

  Scaffold(
    modifier = modifier,
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      ScreenTopStatusBar(
        titleText = chatTopTitle,
        titleSubtitle = chatTopSubtitle,
        titleClickable = activeTab == HomeTab.Chat,
        onTitleClick = { showChatSelector = true },
        connectionStatusText = formatConnectionStatusForUi(statusText),
        statusVisual = statusVisual,
        micEnabled = micEnabled,
        micCooldown = micCooldown,
        speakerEnabled = speakerEnabled,
      )
    },
    bottomBar = {
      if (!hideBottomTabBar) {
        BottomTabBar(
          activeTab = activeTab,
          onSelect = { activeTab = it },
        )
      }
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .consumeWindowInsets(innerPadding)
          .background(mobileBackgroundGradient),
    ) {
      if (chatTabStarted) {
        Box(
          modifier =
            Modifier
              .matchParentSize()
              .alpha(if (activeTab == HomeTab.Chat) 1f else 0f)
              .zIndex(if (activeTab == HomeTab.Chat) 1f else 0f),
        ) {
          ChatSheet(viewModel = viewModel)
        }
      }

      if (screenTabStarted) {
        ScreenTabScreen(
          viewModel = viewModel,
          visible = activeTab == HomeTab.Screen,
          modifier =
            Modifier
              .matchParentSize()
              .alpha(if (activeTab == HomeTab.Screen) 1f else 0f)
              .zIndex(if (activeTab == HomeTab.Screen) 1f else 0f),
        )
      }

      when (activeTab) {
        HomeTab.Connect -> ConnectTabScreen(viewModel = viewModel)
        HomeTab.Chat -> if (!chatTabStarted) ChatSheet(viewModel = viewModel)
        HomeTab.Voice -> VoiceTabScreen(viewModel = viewModel)
        HomeTab.Screen -> Unit
        HomeTab.Settings -> SettingsSheet(viewModel = viewModel)
      }
    }

    if (showChatSelector && activeTab == HomeTab.Chat) {
      ChatSessionSelectorSheet(
        sessionKey = chatSessionKey,
        mainSessionKey = mainSessionKey,
        sessions = chatSessions,
        agents = chatGatewayAgents,
        onDismiss = { showChatSelector = false },
        onSelectAgentDefault = {
          viewModel.switchChatSession(mainSessionKey.trim().ifEmpty { "main" })
        },
        onSelectAgent = { agentId ->
          viewModel.switchChatSession(toAgentSessionKey(agentId))
        },
        onSelectSession = { key ->
          viewModel.switchChatSession(key)
          showChatSelector = false
        },
      )
    }
  }
}

@Composable
private fun ScreenTabScreen(viewModel: MainViewModel, visible: Boolean, modifier: Modifier = Modifier) {
  val isConnected by viewModel.isConnected.collectAsState()
  var refreshedForCurrentConnection by rememberSaveable(isConnected) { mutableStateOf(false) }

  LaunchedEffect(isConnected, visible, refreshedForCurrentConnection) {
    if (visible && isConnected && !refreshedForCurrentConnection) {
      viewModel.refreshHomeCanvasOverviewIfConnected()
      refreshedForCurrentConnection = true
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    CanvasScreen(viewModel = viewModel, visible = visible, modifier = Modifier.fillMaxSize())
  }
}

/** 聊天顶栏：主行为 Agent 名称，副行为会话名称（双行，与右侧系统状态栏语义分离）。 */
private fun buildChatTopBarTitles(sessionKey: String, agents: List<ChatGatewayAgent>): Pair<String, String> {
  val agentId = agentIdFromSessionKey(sessionKey)
  val fullAgentName =
    if (agentId.isNullOrBlank()) {
      "默认"
    } else {
      val match = agents.firstOrNull { it.id.trim() == agentId.trim() }
      match?.name?.trim()?.takeIf { it.isNotEmpty() } ?: agentId
    }
  val sessionName = friendlySessionName(sessionKey)
  val agentLine = fullAgentName.trim().ifEmpty { "默认" }
  val sessionLine = sessionName.trim().ifEmpty { "会话" }
  return agentLine to sessionLine
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSessionSelectorSheet(
  sessionKey: String,
  mainSessionKey: String,
  sessions: List<ChatSessionEntry>,
  agents: List<ChatGatewayAgent>,
  onDismiss: () -> Unit,
  onSelectAgentDefault: () -> Unit,
  onSelectAgent: (String) -> Unit,
  onSelectSession: (String) -> Unit,
) {
  val selectedAgentId = agentIdFromSessionKey(sessionKey)
  val sortedAgents =
    remember(agents) {
      agents
        .filter { it.id.isNotBlank() }
        .sortedWith(compareBy({ it.name?.lowercase().orEmpty() }, { it.id.lowercase() }))
    }
  val allSessions =
    remember(sessions, mainSessionKey, selectedAgentId) {
      resolveAllSessionChoicesForAgent(
        sessions = sessions,
        mainSessionKey = mainSessionKey,
        agentId = selectedAgentId,
      )
    }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = mobileCardSurface,
    contentColor = mobileText,
    tonalElevation = 0.dp,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        IconButton(onClick = onDismiss) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = mobileText,
          )
        }
        Text(text = "选择 Agent 与会话", style = mobileTitle2, color = mobileText)
      }
      Text(
        text = "Agent",
        style = mobileCaption2.copy(fontWeight = FontWeight.SemiBold),
        color = mobileTextSecondary,
      )
      androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        item(key = "agent_default") {
          PickerRowItem(
            title = "默认",
            subtitle = mainSessionKey.trim().ifEmpty { "main" },
            active = selectedAgentId.isNullOrBlank(),
            onClick = onSelectAgentDefault,
          )
        }
        items(items = sortedAgents, key = { it.id }) { agent ->
          val title =
            buildString {
              agent.emoji?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append(it)
                append(' ')
              }
              append(agent.name?.trim()?.takeIf { it.isNotEmpty() } ?: agent.id)
            }
          PickerRowItem(
            title = title,
            subtitle = "agent:${agent.id}:main",
            active = selectedAgentId?.trim() == agent.id.trim(),
            onClick = { onSelectAgent(agent.id) },
          )
        }
      }
      Text(
        text = "会话",
        style = mobileCaption2.copy(fontWeight = FontWeight.SemiBold),
        color = mobileTextSecondary,
      )
      androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(items = allSessions, key = { it.key }) { entry ->
          PickerRowItem(
            title = friendlySessionName(entry.displayName ?: entry.key),
            subtitle = entry.key,
            active = entry.key == sessionKey,
            onClick = { onSelectSession(entry.key) },
          )
        }
      }
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun PickerRowItem(title: String, subtitle: String, active: Boolean, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(12.dp),
    color = if (active) mobileAccent.copy(alpha = 0.22f) else mobileCardSurface,
    border = BorderStroke(1.dp, if (active) LocalMobileColors.current.chipBorderConnecting else mobileBorder),
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

private fun toAgentSessionKey(agentId: String): String {
  val id = agentId.trim()
  return if (id.isEmpty()) "main" else "agent:$id:main"
}

@Composable
private fun BottomTabBar(
  activeTab: HomeTab,
  onSelect: (HomeTab) -> Unit,
) {
  val safeInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

  Box(
    modifier =
      Modifier
        .fillMaxWidth(),
  ) {
    Surface(
      modifier = Modifier.fillMaxWidth(),
      color = mobileCardSurface.copy(alpha = 0.97f),
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      border = BorderStroke(1.dp, mobileBorder),
      shadowElevation = 6.dp,
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .windowInsetsPadding(safeInsets)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        HomeTab.entries.forEach { tab ->
          val active = tab == activeTab
          Surface(
            onClick = { onSelect(tab) },
            modifier = Modifier.weight(1f).heightIn(min = 58.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (active) mobileAccentSoft else Color.Transparent,
            border = if (active) BorderStroke(1.dp, LocalMobileColors.current.chipBorderConnecting) else null,
            shadowElevation = 0.dp,
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
              Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (active) mobileAccent else mobileTextTertiary,
              )
              Text(
                text = tab.label,
                color = if (active) mobileAccent else mobileTextSecondary,
                style = mobileCaption2.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
              )
            }
          }
        }
      }
    }
  }
}
