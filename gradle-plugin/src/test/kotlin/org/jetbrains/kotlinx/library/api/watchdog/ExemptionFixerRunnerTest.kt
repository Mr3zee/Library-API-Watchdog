package org.jetbrains.kotlinx.library.api.watchdog

import java.lang.reflect.Proxy
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.process.ExecOperations
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExemptionFixerRunnerTest {
    @get:Rule
    val temporaryDirectory = TemporaryFolder()

    @Test
    fun `missing response includes the process exit code`() {
        for (exitValue in listOf(0, 7)) {
            val failure = assertFailsWith<GradleException> {
                runFixer(exitValue = exitValue)
            }
            assertEquals(
                "The exempts fixer didn't write a response (exit code $exitValue). The process output above has the cause.",
                failure.message,
            )
        }
    }

    @Test
    fun `reported errors take precedence over the exit code`() {
        for (exitValue in listOf(0, 7)) {
            val failure = assertFailsWith<GradleException> {
                runFixer("error=first line\\nsecond line\\r\\nC:\\\\temp\n", exitValue)
            }
            assertEquals("The exempts fixer failed:\nfirst line\nsecond line\r\nC:\\temp", failure.message)
        }
    }

    @Test
    fun `nonzero exit without an error record fails`() {
        val failure = assertFailsWith<GradleException> {
            runFixer("modifiedFile=/tmp/Api.kt\n", exitValue = 7)
        }
        assertEquals(
            "The exempts fixer exited with code 7 without reporting an error.",
            failure.message,
        )
    }

    @Test
    fun `empty successful response has no findings`() {
        val response = runFixer("")
        assertTrue(response.fixed.isEmpty())
        assertTrue(response.skipped.isEmpty())
        assertTrue(response.warnings.isEmpty())
        assertTrue(response.exemptions.isEmpty())
        assertTrue(response.modifiedFiles.isEmpty())
    }

    @Test
    fun `response lines without a key or separator fail`() {
        for (line in listOf("modifiedFile", "=value")) {
            val failure = assertFailsWith<GradleException> { runFixer("$line\n") }
            assertContains(failure.message.orEmpty(), "malformed response")
        }
    }

    @Test
    fun `truncated diagnostic and exemption records fail`() {
        val records = listOf(
            "fixed=CHECK\tIntentionallyOpen\t7",
            "fixed=CHECK\tIntentionallyOpen\t7\t/tmp/Api.kt\t7\t1\t7",
            "skipped=CHECK\tIntentionallyOpen\t7\t/tmp/Api.kt",
            "skipped=CHECK\tIntentionallyOpen\t7\t/tmp/Api.kt\tManual fix\t7\t1\t7",
            "warning=CHECK\tIntentionallyOpen\t7",
            "warning=CHECK\tIntentionallyOpen\t7\t/tmp/Api.kt\t7\t1\t7",
            "exemption=IntentionallyOpen\t7",
        )
        for (record in records) {
            val failure = assertFailsWith<GradleException> { runFixer("$record\n") }
            assertContains(failure.message.orEmpty(), "malformed response")
        }
    }

    @Test
    fun `invalid or incomplete source ranges fail`() {
        for (range in listOf("seven\t1\t7\t8", "7\t\t7\t8", "\t1\t7\t8")) {
            for (kind in listOf("fixed", "skipped", "warning")) {
                val reason = if (kind == "skipped") "\tManual fix" else ""
                val failure = assertFailsWith<GradleException> {
                    runFixer("$kind=CHECK\tIntentionallyOpen\t7\t/tmp/Api.kt$reason\t$range\n")
                }
                assertContains(failure.message.orEmpty(), "malformed response")
            }
        }
    }

    @Test
    fun `response decodes every diagnostic kind and preserves parameters`() {
        val response = runFixer(
            listOf(
                "fixed=UNDOCUMENTED_PUBLIC_API\t\t7\t/tmp/Api=One.kt\t7\t1\t7\t8\tclass\tApi\\tName\\nC:\\\\temp\\r\t",
                "skipped=CHECK\tIntentionallyOpen\t8\t/tmp/Api=One.kt\tFirst\\nsecond\t\t\t\t\tvalue",
                "warning=CHECK\tIntentionallyOpen\t9\t/tmp/Api=One.kt\t9\t2\t9\t5\tvalue",
                "exemption=IntentionallyOpen\t10\t/tmp/Api=One.kt",
                "modifiedFile=/tmp/Api=One.kt",
                "modifiedFile=/tmp/Api=One.kt",
            ).joinToString("\n", postfix = "\n"),
        )

        val fixed = response.fixed.single()
        assertEquals("UNDOCUMENTED_PUBLIC_API", fixed.diagnostic)
        assertNull(fixed.annotation)
        assertEquals("7", fixed.line)
        assertEquals("/tmp/Api=One.kt", fixed.path)
        assertEquals(ApiWatchdogSourceRange(7, 1, 7, 8), fixed.sourceRange)
        assertEquals(listOf("class", "Api\tName\nC:\\temp\r", ""), fixed.messageParameters)

        val skipped = response.skipped.single()
        assertEquals("IntentionallyOpen", skipped.annotation)
        assertEquals("8", skipped.line)
        assertEquals("/tmp/Api=One.kt", skipped.path)
        assertEquals("First\nsecond", skipped.reason)
        assertNull(skipped.sourceRange)
        assertEquals(listOf("value"), skipped.messageParameters)

        val warning = response.warnings.single()
        assertEquals("IntentionallyOpen", warning.annotation)
        assertEquals("9", warning.line)
        assertEquals("/tmp/Api=One.kt", warning.path)
        assertEquals(ApiWatchdogSourceRange(9, 2, 9, 5), warning.sourceRange)
        assertEquals(listOf("value"), warning.messageParameters)

        val exemption = response.exemptions.single()
        assertEquals("IntentionallyOpen", exemption.annotation)
        assertEquals("10", exemption.line)
        assertEquals("/tmp/Api=One.kt", exemption.path)
        assertEquals(setOf("/tmp/Api=One.kt"), response.modifiedFiles)
    }

    @Test
    fun `incompatible overlay fails before the process starts`() {
        var processStarted = false
        val failure = assertFailsWith<GradleException> {
            runFixer(overlayVersion = "9.0.0", onStart = { processStarted = true })
        }
        assertEquals("The exempts fixer doesn't support Kotlin 2.4.10", failure.message)
        assertFalse(processStarted)
    }

    private fun runFixer(
        response: String? = null,
        exitValue: Int = 0,
        overlayVersion: String = "2.4.10",
        onStart: () -> Unit = {},
    ): FixerResponse {
        val workDirectory = temporaryDirectory.newFolder()
        val artifact = temporaryDirectory.newFolder()
        artifact.resolve("META-INF/MANIFEST.MF").apply {
            parentFile.mkdirs()
            writeText("Manifest-Version: 1.0\nKotlin-Compiler-Plugin-Multi-Release: true\n\n")
        }
        artifact.resolve(
            "META-INF/kotlin/plugin/org.jetbrains.kotlinx.library.api.watchdog.fixer/versions/$overlayVersion",
        ).mkdirs()
        val artifactFiles = Proxy.newProxyInstance(
            FileCollection::class.java.classLoader,
            arrayOf(FileCollection::class.java),
        ) { _, method, _ ->
            check(method.name == "getSingleFile") { "Unexpected FileCollection method: ${method.name}" }
            artifact
        } as FileCollection
        val process = object : ExecOperations {
            override fun exec(action: Action<in ExecSpec>): ExecResult = error("Unexpected exec call")

            override fun javaexec(action: Action<in JavaExecSpec>): ExecResult {
                onStart()
                response?.let { workDirectory.resolve("fixer/response.txt").writeText(it) }
                return object : ExecResult {
                    override fun getExitValue(): Int = exitValue
                    override fun assertNormalExitValue(): ExecResult = error("Unexpected exit assertion")
                    override fun rethrowFailure(): ExecResult = this
                }
            }
        }
        return ExemptionFixerRunner.run(
            temporaryDirectory = workDirectory,
            diagnosticReports = emptyList(),
            sourceFiles = emptyList(),
            fixerArtifact = artifactFiles,
            fixerClasspath = artifactFiles,
            diagnosticSeverities = emptyMap(),
            kotlinVersion = "2.4.10",
            execOperations = process,
            updateSources = false,
        )
    }
}
