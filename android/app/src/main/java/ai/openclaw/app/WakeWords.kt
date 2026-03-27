package ai.openclaw.app

object WakeWords {
  const val maxWords: Int = 32
  const val maxWordLength: Int = 64

  /**
   * Vosk 路径下热词命中会先对识别文本与唤醒词做 ASCII 归一化（见
   * `HotwordService.normalizeForWakeMatch`），中文等非 ASCII 词无法匹配，故 UI
   * 仅展示此类选项；默认唤醒词与此列表一致。
   */
  val voskWakeWordMenuOptions: List<String> = listOf("openclaw", "claude")

  fun parseCommaSeparated(input: String): List<String> {
    return input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
  }

  fun parseIfChanged(input: String, current: List<String>): List<String>? {
    val parsed = parseCommaSeparated(input)
    return if (parsed == current) null else parsed
  }

  fun sanitize(words: List<String>, defaults: List<String>): List<String> {
    val cleaned =
      words.map { it.trim() }.filter { it.isNotEmpty() }.take(maxWords).map { it.take(maxWordLength) }
    return cleaned.ifEmpty { defaults }
  }
}
