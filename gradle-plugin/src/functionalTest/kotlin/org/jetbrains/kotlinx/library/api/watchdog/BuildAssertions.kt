package org.jetbrains.kotlinx.library.api.watchdog

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/** `WatchdogSeverity` of the Gradle plugin. */
enum class Severity { ERROR, WARNING, NONE }

/** The diagnostics without a severity property in the Gradle extension that are always errors. */
val alwaysErrorDiagnostics: Set<Diagnostic> = setOf(
    Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API,
    Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
    Diagnostic.EXEMPTION_WITHOUT_EXPLANATION,
)

/** The diagnostics without a severity property in the Gradle extension that are always warnings. */
val alwaysWarningDiagnostics: Set<Diagnostic> = setOf(
    Diagnostic.REDUNDANT_EXEMPTION,
)

val configurableDiagnostics: List<Diagnostic> = Diagnostic.entries - alwaysErrorDiagnostics - alwaysWarningDiagnostics

/** The severity property of the Gradle extension. */
val Diagnostic.property: String
    get() = name.lowercase(Locale.US).split('_').joinToString("") { it.replaceFirstChar(Char::titlecase) }
        .replaceFirstChar(Char::lowercase)

/** Matches an output line that reports the diagnostic with any placeholder values. */
val Diagnostic.pattern: Regex
    get() = patterns.getValue(this)

private val PLACEHOLDER = Regex("\\{\\d+}")

private val patterns: Map<Diagnostic, Regex> = Diagnostic.entries.associateWith { diagnostic ->
    Regex(diagnostic.template.split(PLACEHOLDER).joinToString(".*?") { Regex.escape(it) })
}

/**
 * The output lines that report [diagnostic], one for each source location. The compilation
 * exception repeats the first error without a severity prefix, so that line doesn't count.
 */
fun BuildResult.reports(diagnostic: Diagnostic): List<String> =
    output.lineSequence()
        .filter { (it.hasSeverity("e: ") || it.hasSeverity("w: ")) && diagnostic.pattern.containsMatchIn(it) }
        .toList()

fun BuildResult.assertErrors(vararg diagnostics: Diagnostic) {
    diagnostics.forEach { assertReported("e: ", it) }
}

fun BuildResult.assertWarnings(vararg diagnostics: Diagnostic) {
    diagnostics.forEach { assertReported("w: ", it) }
}

fun BuildResult.assertNotReported(vararg diagnostics: Diagnostic) {
    for (diagnostic in diagnostics) {
        assertEquals(emptyList(), reports(diagnostic), "Expected no $diagnostic in build output:\n$output")
    }
}

fun BuildResult.assertNoWatchdogDiagnostics() {
    assertNotReported(*Diagnostic.entries.toTypedArray())
}

/** Asserts that the output reports [diagnostic] once in each of [files] and nowhere else. */
fun BuildResult.assertReportedIn(diagnostic: Diagnostic, vararg files: String) {
    val reported = reports(diagnostic).map { line -> SOURCE_FILE.find(line)?.value ?: line }
    assertEquals(files.sorted(), reported.sorted(), "Unexpected locations of $diagnostic in build output:\n$output")
}

/** Asserts that a compiler error line has [text]. */
fun BuildResult.assertError(text: String) {
    assertSeverityLine("e: ", text)
}

/** Asserts that a compiler warning line has [text]. */
fun BuildResult.assertWarning(text: String) {
    assertSeverityLine("w: ", text)
}

fun BuildResult.assertOutputHas(text: String) {
    assertTrue(output.contains(text), "Expected '$text' in build output:\n$output")
}

fun BuildResult.assertOutputLacks(text: String) {
    assertFalse(output.contains(text), "Expected no '$text' in build output:\n$output")
}

/** A null [outcome] asserts that the task wasn't in the task graph. */
fun BuildResult.assertTaskOutcome(path: String, outcome: TaskOutcome?) {
    assertEquals(outcome, task(path)?.outcome, "Unexpected outcome of $path:\n$output")
}

fun assertReportedElement(html: String, text: String, severity: String) {
    val snippets = Regex(
        """<pre[^>]*data-severity="$severity" data-range-start="(\d+)" data-range-end="(\d+)"><code class="language-kotlin">(.*?)</code>""",
        RegexOption.DOT_MATCHES_ALL,
    ).findAll(html)
    val highlighted = snippets.map { match ->
        val (start, end, encoded) = match.destructured
        val code = encoded.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&amp;", "&")
        code.substring(start.toInt(), end.toInt())
    }.toList()
    assertTrue(text in highlighted, "Expected $severity highlight on '$text', found $highlighted")
}

fun assertReportGroup(html: String, annotation: String?, count: Int, diagnostic: Diagnostic? = null) {
    val pattern = Regex(
        """<details class="diagnostic-group"[^>]*data-annotation="${Regex.escape(annotation.orEmpty())}"[^>]*><summary>(.*?)</summary>""",
        RegexOption.DOT_MATCHES_ALL,
    )
    val headers = pattern.findAll(html).map { it.groupValues[1] }.toList()
    assertTrue(
        headers.any { header ->
            "class=\"group-count\">($count)</span>" in header && (diagnostic == null || diagnostic.title in header)
        },
        "Expected a report group for $annotation with $count entries and diagnostic $diagnostic. Found $headers",
    )
}

private fun BuildResult.assertReported(severityPrefix: String, diagnostic: Diagnostic) {
    assertTrue(
        output.lineSequence().any { it.hasSeverity(severityPrefix) && diagnostic.pattern.containsMatchIn(it) },
        "Expected a '$severityPrefix' line reporting $diagnostic in build output:\n$output",
    )
}

private fun BuildResult.assertSeverityLine(severityPrefix: String, text: String) {
    assertTrue(
        output.lineSequence().any { it.hasSeverity(severityPrefix) && text in it },
        "Expected a '$severityPrefix' line with '$text' in build output:\n$output",
    )
}

private fun String.hasSeverity(prefix: String): Boolean = startsWith(prefix) || " $prefix" in this

private val SOURCE_FILE = Regex("[A-Za-z0-9_]+\\.kt(?=:)")
