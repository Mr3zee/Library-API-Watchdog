package org.jetbrains.kotlinx.libs.api.watchdog.fixer

/**
 * Maps between a file's original text and its `\n`-normalized form. PSI only parses normalized
 * text, and the compiler records offsets against the original bytes, so the fixer resolves
 * targets on [normalizedText] and then maps each insertion offset back with [toRawOffset],
 * applying the insertions to the untouched original. That way the fix never rewrites existing
 * line endings, whichever mix of `\r\n`, `\n`, and stray `\r` the file carries. Only the inserted
 * text itself uses [newline], the file's dominant separator.
 */
internal class LineEndingMapping private constructor(
    val normalizedText: String,
    /** Original-text offset of each normalized-text offset. `null` when the text needed no work. */
    private val rawOffsets: IntArray?,
    val newline: String,
) {
    /**
     * The normalized offset of the original-text offset [rawOffset]: the position of the same
     * character, or of the character that replaced it (the `\n` of a dropped `\r\n` pair).
     */
    fun toNormalizedOffset(rawOffset: Int): Int {
        val offsets = rawOffsets ?: return rawOffset
        // Lower bound: the first normalized offset whose original offset is not before rawOffset.
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
                    // The pair collapses into one `\n` mapped to the pair's START, so an
                    // insertion in front of the normalized `\n` lands in front of the whole
                    // pair instead of splitting it.
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
