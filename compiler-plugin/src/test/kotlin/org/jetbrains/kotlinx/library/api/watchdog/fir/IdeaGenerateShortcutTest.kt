package org.jetbrains.kotlinx.library.api.watchdog.fir

import kotlin.test.Test
import kotlin.test.assertEquals

/** The message dumps replace the macOS shortcut, so this test covers it. */
class IdeaGenerateShortcutTest {
    private val diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_EQUALS.name

    @Test
    fun `macOS shortcut`() {
        assertEquals("⌘N", ideaGenerateShortcut(diagnostic, osName = "Mac OS X"))
    }

    @Test
    fun `shortcut on other systems`() {
        assertEquals("Alt+Insert", ideaGenerateShortcut(diagnostic, osName = "Linux"))
        assertEquals("Alt+Insert", ideaGenerateShortcut(diagnostic, osName = "Windows 11"))
        assertEquals("Alt+Insert", ideaGenerateShortcut(diagnostic, osName = ""))
    }
}
