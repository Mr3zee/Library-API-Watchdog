@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.BOOLEAN_PARAMETER_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.DATA_CLASS_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.MANGLED_JVM_NAME_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.REDUNDANT_EXEMPTION
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_EQUALS
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_HASH_CODE
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_TO_STRING
import org.jetbrains.kotlinx.library.api.watchdog.Severity.NONE
import org.junit.Assume.assumeFalse
import org.junit.Test

class ExpectExemptionOwnershipTest : SourceSetOwnershipTestBase() {
    @Test
    fun `verdict of the expect declaration is printed once by the compilation that reports the source set`() {
        val project = expectProject(
            common = """
                /** A server. */
                public expect class Server() {
                    /** Configures the server. */
                    $BOOLEAN_EXEMPTION
                    public fun configure(name: String)
                }
            """,
            actual = """
                public actual class Server actual constructor() {
                    $BOOLEAN_EXEMPTION
                    public actual fun configure(name: String) {}
                }
            """,
        )

        project.build(REPORT_TASK, PLAIN_CONSOLE).assertRedundantBy("Shared.kt" to COMMON_METADATA)
    }

    @Test
    fun `verdict that needs the actual declarations is printed once by the first platform compilation`() {
        val project = expectProject(
            common = """
                /** A record. */
                $DATA_CLASS_EXEMPTION
                public expect class Record(value: Int) {
                    /** The value. */
                    public val value: Int
                }

                /** A worker. */
                public expect class Worker() {
                    /** Runs the work. */
                    $KOTLIN_ONLY_EXEMPTION
                    public fun run()
                }
            """,
            actual = """
                $DATA_CLASS_EXEMPTION
                public actual class Record actual constructor(public actual val value: Int)

                public actual class Worker actual constructor() {
                    $KOTLIN_ONLY_EXEMPTION
                    public actual fun run() {}
                }
            """,
        )

        project.build(REPORT_TASK, PLAIN_CONSOLE).assertRedundantBy("Shared.kt" to JVM, "Shared.kt" to JVM)
    }

    @Test
    fun `exemption that only the first platform compilation doesn't need is printed by it`() {
        val project = expectProject(
            common = """
                /** A record. */
                $DATA_CLASS_EXEMPTION
                public expect class Record(value: Int) {
                    /** The value. */
                    public val value: Int
                }
            """,
            actual = """
                $DATA_CLASS_EXEMPTION
                public actual class Record actual constructor(public actual val value: Int)
            """,
            jsActual = """
                $DATA_CLASS_EXEMPTION
                public actual data class Record actual constructor(public actual val value: Int)
            """,
        )

        project.build(REPORT_TASK, PLAIN_CONSOLE).assertRedundantBy("Shared.kt" to JVM)
    }

    @Test
    fun `exemption that the first platform compilation needs isn't printed`() {
        val project = expectProject(
            common = """
                /** A record. */
                $DATA_CLASS_EXEMPTION
                public expect class Record(value: Int) {
                    /** The value. */
                    public val value: Int
                }
            """,
            actual = """
                $DATA_CLASS_EXEMPTION
                public actual data class Record actual constructor(public actual val value: Int)
            """,
            jsActual = """
                $DATA_CLASS_EXEMPTION
                public actual class Record actual constructor(public actual val value: Int)
            """,
        )

        project.build(REPORT_TASK, PLAIN_CONSOLE).assertNotReported(REDUNDANT_EXEMPTION, DATA_CLASS_PUBLIC_API)
    }

    @Test
    fun `native compilation that the host skips isn't the first platform compilation`() {
        assumeFalse("A macOS host compiles every native target", System.getProperty("os.name").startsWith("Mac"))
        val actual = """
            $DATA_CLASS_EXEMPTION
            public actual class Record actual constructor(public actual val value: Int)
        """
        val project = kmpProject("iosArm64()\nlinuxX64()") {
            apiWatchdog {
                severity(NONE, STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING)
            }
            source(
                """
                    /** A record. */
                    $DATA_CLASS_EXEMPTION
                    public expect class Record(value: Int) {
                        /** The value. */
                        public val value: Int
                    }
                """,
                "Shared",
            )
            source(actual, "PlatformIos", sourceSet = "iosArm64Main")
            source(actual, "PlatformLinux", sourceSet = "linuxX64Main")
        }

        val result = project.build(REPORT_TASK, "-Pkotlin.native.enableKlibsCrossCompilation=false", PLAIN_CONSOLE)

        result.assertTaskOutcome(":compileKotlinIosArm64", SKIPPED)
        result.assertRedundantBy("Shared.kt" to ":compileKotlinLinuxX64")
    }

    @Test
    fun `class exemption of an expect class is judged once`() {
        val project = expectProject(
            common = """
                /** A Kotlin-only worker. */
                $KOTLIN_ONLY_EXEMPTION
                public expect class SuspendingWorker() {
                    /** Runs the work. */
                    public suspend fun run()
                }

                /** A worker without Kotlin-only members. */
                $KOTLIN_ONLY_EXEMPTION
                public expect class PlainWorker() {
                    /** Runs the work. */
                    public fun run()
                }
            """,
            actual = """
                $KOTLIN_ONLY_EXEMPTION
                public actual class SuspendingWorker actual constructor() {
                    public actual suspend fun run() {}
                }

                $KOTLIN_ONLY_EXEMPTION
                public actual class PlainWorker actual constructor() {
                    public actual fun run() {}
                }
            """,
        )

        project.build(REPORT_TASK, PLAIN_CONSOLE).assertRedundantBy("Shared.kt" to JVM)
    }

    @Test
    fun `exemption of a Java interop check that is off is printed once by the JVM compilation`() {
        val project = expectProject(
            severities = { severity(NONE, MANGLED_JVM_NAME_PUBLIC_API) },
            common = """
                /** A finder. */
                public expect class Finder() {
                    /** Finds something. */
                    $MANGLED_NAME_EXEMPTION
                    public fun find()
                }

                /** A finder with a class exemption. */
                $MANGLED_NAME_EXEMPTION
                public expect class ClassFinder() {
                    /** Finds something. */
                    public fun find()
                }
            """,
            actual = """
                public actual class Finder actual constructor() {
                    $MANGLED_NAME_EXEMPTION
                    public actual fun find() {}
                }

                $MANGLED_NAME_EXEMPTION
                public actual class ClassFinder actual constructor() {
                    public actual fun find() {}
                }
            """,
        )

        project.build(REPORT_TASK, PLAIN_CONSOLE).assertRedundantBy("Shared.kt" to JVM, "Shared.kt" to JVM)
    }

    @Test
    fun `actual declarations in a web source set read the exemptions of the expect declaration`() {
        val project = kmpProject(JVM_JS_AND_WASM_TARGETS) {
            source(
                """
                    /** A record. */
                    $DATA_CLASS_EXEMPTION
                    public expect class Record(value: Int) {
                        /** The value. */
                        public val value: Int
                    }
                """,
                "Shared",
            )
            for (sourceSet in listOf("jvmMain", "webMain")) {
                source(
                    """
                        $DATA_CLASS_EXEMPTION
                        public actual data class Record actual constructor(public actual val value: Int)
                    """,
                    "Platform${sourceSet.removeSuffix("Main").replaceFirstChar(Char::uppercase)}",
                    sourceSet = sourceSet,
                )
            }
        }

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertTaskOutcome(WEB_METADATA, SUCCESS)
        assemble.assertNotReported(REDUNDANT_EXEMPTION, DATA_CLASS_PUBLIC_API)
        project.build(REPORT_TASK, PLAIN_CONSOLE).assertNotReported(REDUNDANT_EXEMPTION, DATA_CLASS_PUBLIC_API)
    }

    @Test
    fun `exemptExistingApi removes a redundant expect exemption with every copy`() {
        val project = expectProject(
            common = """
                /** A server. */
                public expect class Server() {
                    /** Configures the server. */
                    $BOOLEAN_EXEMPTION
                    public fun configure(name: String)
                }

                /** A record. */
                $DATA_CLASS_EXEMPTION
                public expect class Record(value: Int) {
                    /** The value. */
                    public val value: Int
                }
            """,
            actual = """
                public actual class Server actual constructor() {
                    $BOOLEAN_EXEMPTION
                    public actual fun configure(name: String) {}
                }

                $DATA_CLASS_EXEMPTION
                public actual class Record actual constructor(public actual val value: Int)
            """,
        )

        val result = project.build(EXEMPT_TASK, PLAIN_CONSOLE)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertRedundantBy("Shared.kt" to COMMON_METADATA, "Shared.kt" to JVM)
        assertEquals(6, result.output.lines().count { "Removed the redundant @Intentionally" in it }, result.output)
        for (path in SOURCE_PATHS) {
            val text = project.file(path).readText()
            assertFalse("@IntentionallyBooleanParameter" in text, text)
            assertFalse("@IntentionallyDataClass" in text, text)
        }

        val second = project.build(EXEMPT_TASK, PLAIN_CONSOLE)
        second.assertOutputHas("No watchdog errors to exempt.")
        second.assertOutputLacks("Removed the redundant")
        project.build("assemble", PLAIN_CONSOLE).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `report tags a redundant expect exemption once`() {
        val project = expectProject(
            common = """
                /** A server. */
                public expect class Server() {
                    /** Configures the server. */
                    $BOOLEAN_EXEMPTION
                    public fun configure(name: String)
                }

                /** A record. */
                $DATA_CLASS_EXEMPTION
                public expect class Record(value: Int) {
                    /** The value. */
                    public val value: Int
                }
            """,
            actual = """
                public actual class Server actual constructor() {
                    $BOOLEAN_EXEMPTION
                    public actual fun configure(name: String) {}
                }

                $DATA_CLASS_EXEMPTION
                public actual class Record actual constructor(public actual val value: Int)
            """,
        )
        val sources = SOURCE_PATHS.associateWith { project.file(it).readText() }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertRedundantBy("Shared.kt" to COMMON_METADATA, "Shared.kt" to JVM)
        sources.forEach { (path, text) -> assertEquals(text, project.file(path).readText()) }
        // The copies on the `actual` declarations are redundant with their originals.
        val report = project.file("build/reports/api-watchdog/report.html").readText()
        assertContains(report, "6 redundant annotations")
        assertReportGroup(report, "IntentionallyBooleanParameter", 3, REDUNDANT_EXEMPTION)
        assertReportGroup(report, "IntentionallyDataClass", 3, REDUNDANT_EXEMPTION)
    }

    @Test
    fun `exemptExistingApi copies a new expect exemption to every actual declaration`() {
        val project = expectProject(
            common = """
                /** A server. */
                public expect class Server() {
                    /** Enables the server. */
                    public fun enable(enabled: Boolean)
                }
            """,
            actual = """
                public actual class Server actual constructor() {
                    public actual fun enable(enabled: Boolean) {}
                }
            """,
        )

        val result = project.build(EXEMPT_TASK, PLAIN_CONSOLE)

        result.assertReportedBy(BOOLEAN_PARAMETER_PUBLIC_API, "Shared.kt" to COMMON_METADATA)
        val exemption = "@IntentionallyBooleanParameter(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)"
        for (path in SOURCE_PATHS) {
            assertEquals(1, Regex(Regex.escape(exemption)).findAll(project.file(path).readText()).count(), path)
        }
        val build = project.build("assemble", PLAIN_CONSOLE)
        build.assertNoWatchdogDiagnostics()
        build.assertOutputLacks("is missing on actual declaration")
        project.build(EXEMPT_TASK, PLAIN_CONSOLE).assertOutputHas("No watchdog errors to exempt.")
    }

    private fun expectProject(
        common: String,
        actual: String,
        jsActual: String = actual,
        severities: ApiWatchdogBlock.() -> Unit = {},
    ): WatchdogBuild = kmpProject(JVM_AND_JS_TARGETS) {
        apiWatchdog {
            severity(NONE, STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING)
            severities()
        }
        source(common, "Shared")
        source(actual, "PlatformJvm", sourceSet = "jvmMain")
        source(jsActual, "PlatformJs", sourceSet = "jsMain")
    }

    private fun BuildResult.assertRedundantBy(vararg reports: Pair<String, String>) =
        assertReportedBy(REDUNDANT_EXEMPTION, *reports)
}

private const val BOOLEAN_EXEMPTION = "@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)"
private const val DATA_CLASS_EXEMPTION = "@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)"
private const val KOTLIN_ONLY_EXEMPTION =
    "@IntentionallyKotlinOnlyApi(description = \"Coroutine-first API.\", reason = ExemptionReason.IGNORE_JAVA_INTEROP)"
private const val MANGLED_NAME_EXEMPTION = "@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)"

private val SOURCE_PATHS = listOf(
    "src/commonMain/kotlin/test/Shared.kt",
    "src/jvmMain/kotlin/test/PlatformJvm.kt",
    "src/jsMain/kotlin/test/PlatformJs.kt",
)
