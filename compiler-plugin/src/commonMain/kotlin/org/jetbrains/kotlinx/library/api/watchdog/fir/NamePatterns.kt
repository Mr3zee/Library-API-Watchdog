package org.jetbrains.kotlinx.library.api.watchdog.fir

/**
 * Fully qualified names and name patterns in the grammar of the ABI validation filters of the
 * Kotlin Gradle plugin. A `.` separates all parts of a name, also a nested class and a member.
 *
 * | Wildcard | Matches                      |
 * |----------|------------------------------|
 * | `**`     | Any characters.              |
 * | `*`      | Any characters except `.`.   |
 * | `?`      | One character.               |
 */
internal class NamePatterns(patterns: Set<String>) {
    private val names: Set<String> = patterns.filterNotTo(linkedSetOf(), String::hasWildcard)
    private val wildcards: List<Pair<String, Regex>> =
        patterns.filter(String::hasWildcard).map { it to it.wildcardRegex() }

    val isEmpty: Boolean get() = names.isEmpty() && wildcards.isEmpty()

    /** The pattern that matches [name], or null. */
    fun firstMatch(name: String): String? =
        name.takeIf { it in names } ?: wildcards.firstOrNull { (_, regex) -> regex.matches(name) }?.first

    fun matches(name: String): Boolean = firstMatch(name) != null
}

private fun String.hasWildcard(): Boolean = any { it == '*' || it == '?' }

private fun String.wildcardRegex(): Regex {
    val regex = StringBuilder()
    val literal = StringBuilder()
    var index = 0
    while (index < length) {
        val wildcard = when (this[index]) {
            '*' -> if (getOrNull(index + 1) == '*') ".*" else "[^.]*"
            '?' -> "."
            else -> null
        }
        if (wildcard == null) {
            literal.append(this[index])
            index++
            continue
        }
        if (literal.isNotEmpty()) {
            regex.append(Regex.escape(literal.toString()))
            literal.clear()
        }
        regex.append(wildcard)
        index += if (wildcard == ".*") 2 else 1
    }
    if (literal.isNotEmpty()) regex.append(Regex.escape(literal.toString()))
    return Regex(regex.toString())
}
