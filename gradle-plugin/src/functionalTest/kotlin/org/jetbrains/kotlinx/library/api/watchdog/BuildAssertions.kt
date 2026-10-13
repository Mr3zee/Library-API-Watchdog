package org.jetbrains.kotlinx.library.api.watchdog

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome

/** A value of the `WatchdogSeverity` enum of the Gradle plugin. */
enum class Severity { ERROR, WARNING, NONE }

/**
 * The diagnostics that are always errors. The Gradle extension offers them no severity
 * property, only a switch for the two dependency checks.
 */
val alwaysErrorDiagnostics: Set<Diagnostic> = setOf(
    Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API,
    Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
    Diagnostic.EXEMPTION_WITHOUT_EXPLANATION,
)

/** The diagnostics with a severity property in the Gradle extension. */
val configurableDiagnostics: List<Diagnostic> = Diagnostic.entries - alwaysErrorDiagnostics

/** The severity property of the Gradle extension: the diagnostic name in camel case. */
val Diagnostic.property: String
    get() = name.lowercase(Locale.US).split('_').joinToString("") { it.replaceFirstChar(Char::titlecase) }
        .replaceFirstChar(Char::lowercase)

/** Matches a line of build output that reports the diagnostic, whatever the placeholders hold. */
val Diagnostic.pattern: Regex
    get() = patterns.getValue(this)

private val PLACEHOLDER = Regex("\\{\\d+}")

private val patterns: Map<Diagnostic, Regex> = Diagnostic.entries.associateWith { diagnostic ->
    Regex(diagnostic.template.split(PLACEHOLDER).joinToString(".*?") { Regex.escape(it) })
}

/** The output lines reporting [diagnostic], one per source location. */
fun BuildResult.reports(diagnostic: Diagnostic): List<String> =
    output.lineSequence().filter { diagnostic.pattern.containsMatchIn(it) }.toList()

/** Asserts each diagnostic is reported as a compiler error. */
fun BuildResult.assertErrors(vararg diagnostics: Diagnostic) {
    diagnostics.forEach { assertReported("e: ", it) }
}

/** Asserts each diagnostic is reported as a compiler warning. */
fun BuildResult.assertWarnings(vararg diagnostics: Diagnostic) {
    diagnostics.forEach { assertReported("w: ", it) }
}

/** Asserts none of the diagnostics is reported. */
fun BuildResult.assertNotReported(vararg diagnostics: Diagnostic) {
    for (diagnostic in diagnostics) {
        assertEquals(emptyList(), reports(diagnostic), "Expected no $diagnostic in build output:\n$output")
    }
}

/** Asserts no watchdog diagnostic at all is reported. */
fun BuildResult.assertNoWatchdogDiagnostics() {
    assertNotReported(*Diagnostic.entries.toTypedArray())
}

/** Asserts [diagnostic] is reported once in each of [files] and nowhere else. */
fun BuildResult.assertReportedIn(diagnostic: Diagnostic, vararg files: String) {
    val reported = reports(diagnostic).map { line -> SOURCE_FILE.find(line)?.value ?: line }
    assertEquals(files.sorted(), reported.sorted(), "Unexpected locations of $diagnostic in build output:\n$output")
}

/** Asserts a compiler error line contains [text]. */
fun BuildResult.assertError(text: String) {
    assertSeverityLine("e: ", text)
}

/** Asserts a compiler warning line contains [text]. */
fun BuildResult.assertWarning(text: String) {
    assertSeverityLine("w: ", text)
}

/** Asserts the build output contains [text]. */
fun BuildResult.assertOutputHas(text: String) {
    assertTrue(output.contains(text), "Expected '$text' in build output:\n$output")
}

/** Asserts the build output doesn't contain [text]. */
fun BuildResult.assertOutputLacks(text: String) {
    assertFalse(output.contains(text), "Expected no '$text' in build output:\n$output")
}

/** Asserts the task at [path] ended with [outcome], or wasn't in the task graph when null. */
fun BuildResult.assertTaskOutcome(path: String, outcome: TaskOutcome?) {
    assertEquals(outcome, task(path)?.outcome, "Unexpected outcome of $path:\n$output")
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
        "Expected a '$severityPrefix' line containing '$text' in build output:\n$output",
    )
}

private fun String.hasSeverity(prefix: String): Boolean = startsWith(prefix) || " $prefix" in this

private val SOURCE_FILE = Regex("[A-Za-z0-9_]+\\.kt(?=:)")
