package ai.openclaw.app.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HotwordDebugLogger {
  private const val maxEntries = 80
  private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

  private val _logs = MutableStateFlow<List<String>>(emptyList())
  val logs: StateFlow<List<String>> = _logs.asStateFlow()

  fun log(message: String) {
    val entry = "[${timeFormat.format(Date())}] $message"
    _logs.value = (_logs.value + entry).takeLast(maxEntries)
  }

  fun clear() {
    _logs.value = emptyList()
  }
}
