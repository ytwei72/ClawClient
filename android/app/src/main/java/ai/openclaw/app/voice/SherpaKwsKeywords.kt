package ai.openclaw.app.voice

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.openClawKwsKeywordsAssetPath

/**
 * WeNetSpeech Sherpa KWS 要求 [com.k2fsa.sherpa.onnx.KeywordSpotter.createStream] 传入与
 * `keywords.txt` 行一致的字符串（空格分隔音素 + `@` + 展示文案），不能只传纯中文。
 * 参见 https://k2-fsa.github.io/sherpa/onnx/kws/pretrained_models/index.html
 */
object SherpaKwsKeywords {
  private val indexLock = Any()
  private var cachedPath: String? = null
  private var cachedIndex: KeywordsIndex? = null

  private class KeywordsIndex(
    val allLines: Set<String>,
    val byDisplay: Map<String, String>,
  )

  private fun buildIndex(assets: AssetManager, assetPath: String): KeywordsIndex {
    val allLines = mutableSetOf<String>()
    val byDisplay = linkedMapOf<String, String>()
    assets.open(assetPath).bufferedReader().use { reader ->
      reader.lineSequence().forEach { raw ->
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) return@forEach
        allLines.add(line)
        val at = line.lastIndexOf('@')
        if (at >= 0 && at < line.length - 1) {
          val display = line.substring(at + 1).trim()
          if (display.isNotEmpty() && !byDisplay.containsKey(display)) {
            byDisplay[display] = line
          }
        }
      }
    }
    return KeywordsIndex(allLines, byDisplay)
  }

  private fun getIndex(assets: AssetManager): KeywordsIndex {
    val path = openClawKwsKeywordsAssetPath()
    synchronized(indexLock) {
      if (cachedPath == path && cachedIndex != null) return cachedIndex!!
      val idx = buildIndex(assets, path)
      cachedPath = path
      cachedIndex = idx
      return idx
    }
  }

  data class Resolution(
    val keywordArg: String?,
    val unresolved: List<String>,
  )

  /** 与 assets 内 keywords.txt 中 `@` 后展示名一致，顺序与文件内首次出现顺序相同。 */
  fun displayLabelsOrdered(assets: AssetManager): List<String> {
    return getIndex(assets).byDisplay.keys.toList()
  }

  fun resolveForCreateStream(assets: AssetManager, wakeWords: List<String>): Resolution {
    val normalized =
      wakeWords
        .map { it.replace("\n", "/").trim() }
        .filter { it.isNotEmpty() }
    if (normalized.isEmpty()) {
      return Resolution("", emptyList())
    }
    val idx = getIndex(assets)
    val resolved = mutableListOf<String>()
    val unresolved = mutableListOf<String>()
    for (w in normalized) {
      when {
        w in idx.allLines -> resolved.add(w)
        idx.byDisplay[w] != null -> resolved.add(idx.byDisplay[w]!!)
        else -> unresolved.add(w)
      }
    }
    if (unresolved.isNotEmpty()) {
      return Resolution(null, unresolved)
    }
    return Resolution(resolved.joinToString("/"), emptyList())
  }
}
