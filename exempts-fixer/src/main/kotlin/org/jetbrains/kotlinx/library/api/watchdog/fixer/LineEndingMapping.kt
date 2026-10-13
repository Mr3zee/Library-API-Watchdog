package org.jetbrains.kotlinx.library.api.watchdog.fixer

/**
 * PSI parses only `\n` text, and the compiler records offsets on the original text. The fixer
 * resolves targets on [normalizedText] and maps each insertion offset back with [toRawOffset],
 * so the existing line endings stay. Inserted text uses [newline], the dominant separator.
 */
internal class LineEndingMapping private constructor(
    val normalizedText: String,
    /** Null when the text has no `\r`. */
    private val rawOffsets: IntArray?,
    val newline: String,
) {
    /** For a dropped `\r\n` pair, the offset of the `\n` that replaced it. */
    fun toNormalizedOffset(rawOffset: Int): Int {
        val offsets = rawOffsets ?: return rawOffset
        var low = 0
        var high = offsets.size - 1
        while (low < high) {
            val middle = (low + high) / 2
            if (offsets[middle] < rawOffset) low = middle + 1 else high = middle
        }
        return low
    }

    fun toRawOffset(normalizedOffset: Int): Int = rawOffsets?.get(normalizedOffset) ?: normalizedOffset

    companion object {
        fun of(text: String): LineEndingMapping {
            if ('\r' !in text) {
                return LineEndingMapping(text, rawOffsets = null, newline = "\n")
            }

            val crlfPairs = generateSequence(text.indexOf("\r\n").takeIf { it >= 0 }) { previous ->
                text.indexOf("\r\n", previous + 2).takeIf { it >= 0 }
            }.count()
            val otherBreaks = text.count { it == '\n' || it == '\r' } - 2 * crlfPairs
            val newline = if (crlfPairs > otherBreaks) "\r\n" else "\n"

            val normalized = StringBuilder(text.length)
            val rawOffsets = IntArray(text.length + 1)
            var rawIndex = 0
            while (rawIndex < text.length) {
                val char = text[rawIndex]
                if (char == '\r' && rawIndex + 1 < text.length && text[rawIndex + 1] == '\n') {
                    // The pair maps to one `\n` at the start of the pair, so an insertion can't split it.
                    rawOffsets[normalized.length] = rawIndex
                    normalized.append('\n')
                    rawIndex += 2
                    continue
                }
                rawOffsets[normalized.length] = rawIndex
                normalized.append(if (char == '\r') '\n' else char)
                rawIndex++
            }
            rawOffsets[normalized.length] = text.length
            return LineEndingMapping(normalized.toString(), rawOffsets.copyOf(normalized.length + 1), newline)
        }
    }
}
