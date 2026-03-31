package ai.openclaw.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ai.openclaw.app.ui.RootScreen
import ai.openclaw.app.ui.OpenClawTheme
import ai.openclaw.app.voice.HotwordService
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()
  private lateinit var permissionRequester: PermissionRequester
  private var didAttachRuntimeUi = false
  private var didStartNodeService = false
  private var pendingHotwordIntent: Intent? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    permissionRequester = PermissionRequester(this)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.preventSleep.collect { enabled ->
          if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          }
        }
      }
    }

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.runtimeInitialized.collect { ready ->
          if (!ready || didAttachRuntimeUi) return@collect
          viewModel.attachRuntimeUi(owner = this@MainActivity, permissionRequester = permissionRequester)
          didAttachRuntimeUi = true
          if (!didStartNodeService) {
            NodeForegroundService.start(this@MainActivity)
            didStartNodeService = true
          }
        }
      }
    }

    setContent {
      OpenClawTheme {
        Surface(modifier = Modifier) {
          RootScreen(viewModel = viewModel)
        }
      }
    }
    pendingHotwordIntent = intent
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    pendingHotwordIntent = intent
    if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
      handleHotwordIntent(pendingHotwordIntent)
      pendingHotwordIntent = null
    }
  }

  override fun onStart() {
    super.onStart()
    viewModel.setForeground(true)
    handleHotwordIntent(pendingHotwordIntent)
    pendingHotwordIntent = null
  }

  override fun onStop() {
    viewModel.setForeground(false)
    super.onStop()
  }

  private fun handleHotwordIntent(intent: Intent?) {
    val input = intent ?: return
    if (input.action != HotwordService.actionOpenFromHotwordNotification) return
    viewModel.handleHotwordNotificationOpen(
      triggerLabel = input.getStringExtra(HotwordService.extraWakeTriggerLabel),
      wakeEpochMs = input.getLongExtra(HotwordService.extraWakeTimeEpochMs, -1L).takeIf { it > 0L },
      wakeSource = input.getStringExtra(HotwordService.extraWakeSource),
    )
  }
}
