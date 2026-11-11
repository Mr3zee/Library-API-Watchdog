@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

class SourceSetOwnershipBuildModesTest {
    @Test
    fun `compile task stays up to date when the task set changes`() {
        val project = jvmAndJsProject()
        project.build("assemble", PLAIN_CONSOLE)

        val jvm = project.build("compileKotlinJvm", PLAIN_CONSOLE)
        jvm.assertTaskOutcome(JVM, UP_TO_DATE)
        jvm.assertNoWatchdogDiagnostics()

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertTaskOutcome(COMMON_METADATA, UP_TO_DATE)
        assemble.assertTaskOutcome(JVM, UP_TO_DATE)
        assemble.assertTaskOutcome(JS, UP_TO_DATE)
    }

    @Test
    fun `cached compile task serves a build with another task set`() {
        val project = jvmAndJsProject()
        project.file("settings.gradle.kts").appendText("\nbuildCache { local { directory = file(\"build-cache\") } }\n")
        project.build("assemble", "--build-cache", PLAIN_CONSOLE)

        val jvm = project.build("clean", "compileKotlinJvm", "--build-cache", PLAIN_CONSOLE)

        jvm.assertTaskOutcome(JVM, FROM_CACHE)
        jvm.assertNoWatchdogDiagnostics()
    }

    @Test
    fun `report task prints shared diagnostics once with the configuration cache`() {
        val project = jvmAndJsProject()
        val report = arrayOf(REPORT_TASK, *CONFIGURATION_CACHE, PLAIN_CONSOLE)

        val stored = project.build(*report)
        stored.assertOutputHas("Configuration cache entry stored.")
        stored.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        stored.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        val reused = project.build(*report)
        reused.assertOutputHas("Configuration cache entry reused.")
        reused.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        reused.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `each project picks the owners of its own source sets`() {
        val project = multiProject()

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "ASharedApi.kt" to ":a$COMMON_METADATA")
        result.assertJvmNameBy("SharedApi.kt" to JVM, "ASharedApi.kt" to ":a$JVM")
    }

    @Test
    fun `each project picks the owners of its own source sets with project isolation`() {
        // No JS target, because the `NodeJsRootPlugin` of KGP fails under project isolation.
        val project = multiProject(targets = "jvm()\nlinuxX64()")

        val result = project.build(REPORT_TASK, *PROJECT_ISOLATION, PLAIN_CONSOLE)

        result.assertOutputHas("Configuration cache entry stored.")
        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "ASharedApi.kt" to ":a$COMMON_METADATA")
        result.assertJvmNameBy("SharedApi.kt" to JVM, "ASharedApi.kt" to ":a$JVM")
    }

    @Test
    fun `included build picks the owners from its own task graph`() {
        val included = jvmAndJsProject()
        val project = jvmProject { apiWatchdog { suggestAbiValidation = false } }
        included.rootDir.copyRecursively(project.file("components/lib"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components/lib\") { name = \"lib\" }\n")

        val result = project.build(":lib:$REPORT_TASK", PLAIN_CONSOLE)

        result.assertOpenApiBy("SharedApi.kt" to ":lib$COMMON_METADATA", "JvmApi.kt" to ":lib$JVM", "JsApi.kt" to ":lib$JS")
        result.assertJvmNameBy("SharedApi.kt" to ":lib$JVM", "JvmApi.kt" to ":lib$JVM")
    }

    @Test
    fun `help doesn't realize the compile tasks of a multiplatform project`() {
        val marker = "REALIZED_WATCHDOG_COMPILE_TASK"
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            buildScript("tasks.matching { it.name.startsWith(\"compile\") }.configureEach { println(\"$marker\") }")
        }

        project.build("help", PLAIN_CONSOLE).assertOutputLacks(marker)
    }

    @Test
    fun `exemptExistingApi exempts each web declaration once`() {
        val project = kmpProject(JVM_JS_AND_WASM_TARGETS) {
            api("SharedApi", "commonMain")
            api("WebApi", "webMain")
            api("JvmApi", "jvmMain")
            api("JsApi", "jsMain")
            api("WasmApi", "wasmJsMain")
        }

        val result = project.build(EXEMPT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "WebApi.kt" to WEB_METADATA,
            "JvmApi.kt" to JVM,
            "JsApi.kt" to JS,
            "WasmApi.kt" to WASM,
        )
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        result.assertOutputHas("7 backwards-compatibility exemption(s) in 5 file(s)")
        for ((name, sourceSet) in listOf(
            "SharedApi" to "commonMain",
            "WebApi" to "webMain",
            "JvmApi" to "jvmMain",
            "JsApi" to "jsMain",
            "WasmApi" to "wasmJsMain",
        )) {
            val text = project.file("src/$sourceSet/kotlin/test/$name.kt").readText()
            assertEquals(1, OPEN_EXEMPTION.findAll(text).count(), text)
        }
        assertContains(project.file("src/commonMain/kotlin/test/SharedApi.kt").readText(), "@file:IntentionallyDefaultFacadeName")

        project.build(EXEMPT_TASK, PLAIN_CONSOLE).assertOutputHas("No watchdog errors to exempt.")
        project.build("assemble", PLAIN_CONSOLE).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `exemptExistingApi exempts each native declaration once`() {
        val project = kmpProject("jvm()\nlinuxX64()\nmacosArm64()\niosArm64()\niosSimulatorArm64()") {
            api("SharedApi", "commonMain")
            api("NativeApi", "nativeMain")
            api("AppleApi", "appleMain")
            api("IosApi", "iosMain")
        }

        val result = project.build(EXEMPT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "NativeApi.kt" to ":compileNativeMainKotlinMetadata",
            "AppleApi.kt" to ":compileAppleMainKotlinMetadata",
            "IosApi.kt" to ":compileIosMainKotlinMetadata",
        )
        result.assertJvmNameBy("SharedApi.kt" to JVM)
        result.assertOutputHas("5 backwards-compatibility exemption(s) in 4 file(s)")

        project.build(EXEMPT_TASK, PLAIN_CONSOLE).assertOutputHas("No watchdog errors to exempt.")
        project.build("assemble", PLAIN_CONSOLE).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `exemptExistingApi without explicit API mode exempts each declaration once`() {
        // Collection mode turns explicit API mode on, so every compilation runs the checks.
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            explicitApi = false
            api("SharedApi", "commonMain")
            api("JsApi", "jsMain")
        }

        val result = project.build(EXEMPT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM)
        result.assertOutputHas("3 backwards-compatibility exemption(s) in 2 file(s)")
        project.build(EXEMPT_TASK, PLAIN_CONSOLE).assertOutputHas("No watchdog errors to exempt.")
    }

    @Test
    fun `exemptExistingApi in one project of a multi-project build exempts it once`() {
        val project = multiProject(severity = null)

        val result = project.build(":a:$EXEMPT_TASK", PLAIN_CONSOLE)

        result.assertOpenApiBy("ASharedApi.kt" to ":a$COMMON_METADATA")
        result.assertOutputHas("2 backwards-compatibility exemption(s) in 1 file(s)")
        project.build(":a:$EXEMPT_TASK", PLAIN_CONSOLE).assertOutputHas("No watchdog errors to exempt.")
    }

    private fun jvmAndJsProject(): WatchdogBuild = kmpProject(JVM_AND_JS_TARGETS) {
        apiWatchdog { severityOfEveryCheck(WARNING) }
        api("SharedApi", "commonMain")
        api("JvmApi", "jvmMain")
        api("JsApi", "jsMain")
    }

    private fun multiProject(severity: Severity? = WARNING, targets: String = JVM_AND_JS_TARGETS): WatchdogBuild = kmpProject(targets) {
        if (severity != null) apiWatchdog { severityOfEveryCheck(severity) }
        api("SharedApi", "commonMain")
        subproject("a") {
            if (severity != null) apiWatchdog { severityOfEveryCheck(severity) }
            api("ASharedApi", "commonMain")
        }
    }

    private fun ModuleSpec.api(name: String, sourceSet: String) {
        source(
            """
                /** An open API. */
                public open class $name

                /** A top-level function. */
                public fun ${name.replaceFirstChar(Char::lowercase)}(): Int = 0
            """,
            name,
            sourceSet = sourceSet,
            defaultImports = false,
        )
    }

    private fun BuildResult.assertOpenApiBy(vararg reports: Pair<String, String>) =
        assertReportedBy(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, *reports)

    private fun BuildResult.assertJvmNameBy(vararg reports: Pair<String, String>) =
        assertReportedBy(TOP_LEVEL_API_WITHOUT_JVM_NAME, *reports)
}

private val OPEN_EXEMPTION = Regex(Regex.escape("@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)"))

private const val EXEMPT_TASK = "exemptExistingApi"
private const val REPORT_TASK = "generateApiWatchdogReport"
private const val JVM = ":compileKotlinJvm"
private const val JS = ":compileKotlinJs"
private const val WASM = ":compileKotlinWasmJs"
private const val COMMON_METADATA = ":compileCommonMainKotlinMetadata"
private const val WEB_METADATA = ":compileWebMainKotlinMetadata"
private const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"
private const val JVM_JS_AND_WASM_TARGETS = "jvm()\njs { nodejs() }\nwasmJs { nodejs() }"

private val CONFIGURATION_CACHE = arrayOf(
    "--configuration-cache",
    "--configuration-cache-problems=fail",
    "-Dorg.gradle.configuration-cache.read-only=false",
)

private val PROJECT_ISOLATION = arrayOf(
    "-Dorg.gradle.unsafe.isolated-projects=true",
    "--configuration-cache-problems=fail",
    "-Dorg.gradle.configuration-cache.read-only=false",
)
