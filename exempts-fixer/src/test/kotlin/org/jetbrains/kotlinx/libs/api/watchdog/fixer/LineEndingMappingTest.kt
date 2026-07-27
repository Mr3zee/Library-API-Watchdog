package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import kotlin.test.Test
import kotlin.test.assertEquals

class LineEndingMappingTest {
    @Test
    fun lfOnlyTextIsUnchangedAndUsesIdentityOffsets() {
        val text = "first\nsecond\n"

        val mapping = LineEndingMapping.of(text)

        assertEquals(text, mapping.normalizedText)
        assertEquals("\n", mapping.newline)
        for (offset in 0..text.length) {
            assertEquals(offset, mapping.toNormalizedOffset(offset))
            assertEquals(offset, mapping.toRawOffset(offset))
        }
    }

    @Test
    fun mixedLineEndingsAreNormalizedAndOffsetsMapToTheOriginalText() {
        val mapping = LineEndingMapping.of("a\r\nb\rc\n")

        assertEquals("a\nb\nc\n", mapping.normalizedText)
        assertEquals(
            listOf(0, 1, 3, 4, 5, 6, 7),
            (0..mapping.normalizedText.length).map(mapping::toRawOffset),
        )
        assertEquals(
            listOf(0, 1, 2, 2, 3, 4, 5, 6),
            (0..7).map(mapping::toNormalizedOffset),
        )
    }

    @Test
    fun normalizedNewlineMapsToTheStartOfACrlfPair() {
        val mapping = LineEndingMapping.of("left\r\nright")
        val normalizedNewlineOffset = mapping.normalizedText.indexOf('\n')

        assertEquals("left\nright", mapping.normalizedText)
        assertEquals(4, mapping.toRawOffset(normalizedNewlineOffset))
        assertEquals(6, mapping.toRawOffset(normalizedNewlineOffset + 1))
    }

    @Test
    fun crlfIsUsedForInsertionsOnlyWhenItIsStrictlyDominant() {
        val cases = listOf(
            "a\r\nb\r\nc\n" to "\r\n",
            "a\r\nb\n" to "\n",
            "a\r\nb\nc\n" to "\n",
            "a\rb\r\nc" to "\n",
        )

        for ((text, expectedNewline) in cases) {
            assertEquals(expectedNewline, LineEndingMapping.of(text).newline, "text=${text.escape()}")
        }
    }

    private fun String.escape(): String = replace("\r", "\\r").replace("\n", "\\n")
}
