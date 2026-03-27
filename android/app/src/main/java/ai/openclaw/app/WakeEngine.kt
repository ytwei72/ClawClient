package ai.openclaw.app

enum class WakeEngine(val rawValue: String) {
  Vosk("vosk"),
  SherpaOnnx("sherpa_onnx"),
  ;

  companion object {
    /** 新安装或未写入偏好时的默认引擎（与 TODO 双引擎序列一致：优先 Sherpa KWS）。 */
    val default: WakeEngine = SherpaOnnx

    fun fromRawValue(raw: String?): WakeEngine {
      val t = raw?.trim()?.lowercase().orEmpty()
      if (t.isEmpty()) return default
      return entries.firstOrNull { it.rawValue == t } ?: default
    }
  }
}
