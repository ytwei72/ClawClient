package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatSessionEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionFiltersAllSessionsTest {
  @Test
  fun resolveAllSessionChoices_putsMainFirst_and_dedupes() {
    val sessions =
      listOf(
        ChatSessionEntry(key = "telegram:a", updatedAtMs = 100L),
        ChatSessionEntry(key = "agent:main:main", updatedAtMs = 200L),
        ChatSessionEntry(key = "telegram:a", updatedAtMs = 300L),
      )
    val out = resolveAllSessionChoices(sessions, mainSessionKey = "agent:main:main")
    assertEquals("agent:main:main", out.first().key)
    assertEquals(2, out.size)
    assertTrue(out.any { it.key == "telegram:a" })
  }

  @Test
  fun resolveAllSessionChoices_hides_main_alias_when_mainKey_is_canonical() {
    val sessions =
      listOf(
        ChatSessionEntry(key = "main", updatedAtMs = 999L),
        ChatSessionEntry(key = "discord:x", updatedAtMs = 100L),
      )
    val out = resolveAllSessionChoices(sessions, mainSessionKey = "agent:foo:main")
    assertEquals("agent:foo:main", out.first().key)
    assertTrue(out.none { it.key == "main" })
    assertTrue(out.any { it.key == "discord:x" })
  }
}
