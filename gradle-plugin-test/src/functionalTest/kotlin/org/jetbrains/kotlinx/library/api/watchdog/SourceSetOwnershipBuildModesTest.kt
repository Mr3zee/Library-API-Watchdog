@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.FROM_CACHE
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

/**
 * The owner of a shared source set in incremental, cached, multi-project, and collection-mode builds. The
 * options of a compile task depend on the task graph, so a task runs again when its role changes. An
 * up-to-date or cached task doesn't print its diagnostics again.
 */
class SourceSetOwnershipBuildModesTest {
    @Test
    fun `compile task runs again when the build changes its role`() {
        val project = jvmAndJsProject()
        project.build("assemble", PLAIN_CONSOLE)

        val jvm = project.build("compileKotlinJvm", PLAIN_CONSOLE)
        jvm.assertTaskOutcome(JVM, SUCCESS)
        jvm.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        jvm.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertTaskOutcome(COMMON_METADATA, UP_TO_DATE)
        assemble.assertTaskOutcome(JVM, SUCCESS)
        assemble.assertTaskOutcome(JS, UP_TO_DATE)
        assemble.assertOpenApiBy("JvmApi.kt" to JVM)
        assemble.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        project.build("assemble", PLAIN_CONSOLE).assertTaskOutcome(JVM, UP_TO_DATE)
    }

    @Test
    fun `cached compile task of another role isn't reused`() {
        val project = jvmAndJsProject()
        project.file("settings.gradle.kts").appendText("\nbuildCache { local { directory = file(\"build-cache\") } }\n")
        project.build("assemble", "--build-cache", PLAIN_CONSOLE)

        val cached = project.build("clean", "assemble", "--build-cache", PLAIN_CONSOLE)
        cached.assertTaskOutcome(COMMON_METADATA, FROM_CACHE)
        cached.assertTaskOutcome(JVM, FROM_CACHE)
        cached.assertTaskOutcome(JS, FROM_CACHE)
        cached.assertNoWatchdogDiagnostics()

        val jvm = project.build("clean", "compileKotlinJvm", "--build-cache", PLAIN_CONSOLE)
        jvm.assertTaskOutcome(JVM, SUCCESS)
        jvm.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        jvm.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        project.build("clean", "compileKotlinJvm", "--build-cache", PLAIN_CONSOLE).assertTaskOutcome(JVM, FROM_CACHE)
        project.build("clean", "assemble", "--build-cache", PLAIN_CONSOLE).assertTaskOutcome(JVM, FROM_CACHE)
    }

    @Test
    fun `configuration cache keeps the roles of its task graph`() {
        val project = jvmAndJsProject()
        val platforms = arrayOf("compileKotlinJvm", "compileKotlinJs", *CONFIGURATION_CACHE, PLAIN_CONSOLE)

        val stored = project.build(*platforms)
        stored.assertOutputHas("Configuration cache entry stored.")
        stored.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, "JsApi.kt" to JS)

        project.editSources()
        val reused = project.build(*platforms)
        reused.assertOutputHas("Configuration cache entry reused.")
        reused.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        reused.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        val js = project.build("compileKotlinJs", *CONFIGURATION_CACHE, PLAIN_CONSOLE)
        js.assertOutputHas("Configuration cache entry stored.")
        js.assertTaskOutcome(JS, SUCCESS)
        js.assertOpenApiBy("SharedApi.kt" to JS, "JsApi.kt" to JS)

        project.editSources()
        val reusedAgain = project.build(*platforms)
        reusedAgain.assertOutputHas("Configuration cache entry reused.")
        reusedAgain.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, "JsApi.kt" to JS)

        val assemble = arrayOf("assemble", *CONFIGURATION_CACHE, PLAIN_CONSOLE)
        project.build(*assemble).assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM)
        project.editSources()
        val reusedAssemble = project.build(*assemble)
        reusedAssemble.assertOutputHas("Configuration cache entry reused.")
        reusedAssemble.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        reusedAssemble.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `native compile tasks keep their roles with the configuration cache`() {
        // A native compile task reads the list of plugin options at configuration time.
        val project = kmpProject("jvm()\nlinuxX64()\nmacosArm64()") {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("NativeApi", "nativeMain")
        }
        val native = arrayOf("compileKotlinMacosArm64", "compileKotlinLinuxX64", *CONFIGURATION_CACHE, PLAIN_CONSOLE)

        project.build(*native).assertOpenApiBy("SharedApi.kt" to LINUX, "NativeApi.kt" to LINUX)
        project.editSources()
        val reused = project.build(*native)
        reused.assertOutputHas("Configuration cache entry reused.")
        reused.assertOpenApiBy("SharedApi.kt" to LINUX, "NativeApi.kt" to LINUX)

        val assemble = arrayOf("assemble", *CONFIGURATION_CACHE, PLAIN_CONSOLE)
        project.build(*assemble)
            .assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "NativeApi.kt" to ":compileNativeMainKotlinMetadata")
        project.editSources()
        val reusedAssemble = project.build(*assemble)
        reusedAssemble.assertOutputHas("Configuration cache entry reused.")
        reusedAssemble.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "NativeApi.kt" to ":compileNativeMainKotlinMetadata")
        reusedAssemble.assertJvmNameBy("SharedApi.kt" to JVM)
    }

    @Test
    fun `each project picks the owners of its own source sets`() {
        val project = multiProject()

        val mixed = project.build(":a:compileKotlinJvm", ":compileKotlinJs", PLAIN_CONSOLE)
        mixed.assertOpenApiBy("SharedApi.kt" to JS, "ASharedApi.kt" to ":a$JVM")
        mixed.assertJvmNameBy("ASharedApi.kt" to ":a$JVM")

        val subproject = project.build(":a:compileKotlinJs", PLAIN_CONSOLE)
        subproject.assertOpenApiBy("ASharedApi.kt" to ":a$JS")

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "ASharedApi.kt" to ":a$COMMON_METADATA")
        assemble.assertJvmNameBy("SharedApi.kt" to JVM, "ASharedApi.kt" to ":a$JVM")
    }

    @Test
    fun `each project picks the owners of its own source sets with project isolation`() {
        // The JS target of KGP applies a plugin to the root project. Project isolation forbids that.
        val project = multiProject(targets = "jvm()\nlinuxX64()")

        val mixed = project.build(":a:compileKotlinJvm", ":compileKotlinLinuxX64", *PROJECT_ISOLATION, PLAIN_CONSOLE)
        mixed.assertOutputHas("Configuration cache entry stored.")
        mixed.assertOpenApiBy("SharedApi.kt" to ":compileKotlinLinuxX64", "ASharedApi.kt" to ":a$JVM")
        mixed.assertJvmNameBy("ASharedApi.kt" to ":a$JVM")

        val assemble = project.build("assemble", *PROJECT_ISOLATION, PLAIN_CONSOLE)
        assemble.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "ASharedApi.kt" to ":a$COMMON_METADATA")
        assemble.assertJvmNameBy("SharedApi.kt" to JVM, "ASharedApi.kt" to ":a$JVM")
    }

    @Test
    fun `included build picks the owners from its own task graph`() {
        val included = jvmAndJsProject()
        val project = jvmProject { apiWatchdog { suggestAbiValidation = false } }
        included.rootDir.copyRecursively(project.file("components/lib"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components/lib\") { name = \"lib\" }\n")

        val platforms = project.build(":lib:compileKotlinJvm", ":lib:compileKotlinJs", PLAIN_CONSOLE)
        platforms.assertOpenApiBy("SharedApi.kt" to ":lib$JVM", "JvmApi.kt" to ":lib$JVM", "JsApi.kt" to ":lib$JS")

        val js = project.build(":lib:compileKotlinJs", PLAIN_CONSOLE)
        js.assertOpenApiBy("SharedApi.kt" to ":lib$JS", "JsApi.kt" to ":lib$JS")

        val assemble = project.build(":lib:assemble", PLAIN_CONSOLE)
        // The JS options changed, so the JS compilation runs again.
        assemble.assertOpenApiBy("SharedApi.kt" to ":lib$COMMON_METADATA", "JvmApi.kt" to ":lib$JVM", "JsApi.kt" to ":lib$JS")
        assemble.assertJvmNameBy("SharedApi.kt" to ":lib$JVM", "JvmApi.kt" to ":lib$JVM")
    }

    @Test
    fun `included build compiles only the target that a consumer resolves`() {
        val included = kmpProject(JVM_AND_JS_TARGETS) {
            buildScript("group = \"sample.lib\"")
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
        }
        val project = jvmProject {
            apiWatchdog { suggestAbiValidation = false }
            dependencies(implementation("sample.lib:the-project"))
        }
        included.rootDir.copyRecursively(project.file("components/lib"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components/lib\") { name = \"lib\" }\n")

        val result = project.build("compileKotlin", PLAIN_CONSOLE)

        result.assertTaskOutcome(":lib$COMMON_METADATA", null)
        result.assertOpenApiBy("SharedApi.kt" to ":lib$JVM")
        result.assertJvmNameBy("SharedApi.kt" to ":lib$JVM")
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

    /** The files of [api] in `commonMain`, `jvmMain`, and `jsMain`. */
    private fun jvmAndJsProject(): WatchdogBuild = kmpProject(JVM_AND_JS_TARGETS) {
        apiWatchdog { severityOfEveryCheck(WARNING) }
        api("SharedApi", "commonMain")
        api("JvmApi", "jvmMain")
        api("JsApi", "jsMain")
    }

    /** A root and an `:a` project, each with an [api] file in `commonMain`. */
    private fun multiProject(severity: Severity? = WARNING, targets: String = JVM_AND_JS_TARGETS): WatchdogBuild = kmpProject(targets) {
        if (severity != null) apiWatchdog { severityOfEveryCheck(severity) }
        api("SharedApi", "commonMain")
        subproject("a") {
            if (severity != null) apiWatchdog { severityOfEveryCheck(severity) }
            api("ASharedApi", "commonMain")
        }
    }

    /** Changes every source file, so an incremental compilation compiles it again. */
    private fun WatchdogBuild.editSources() {
        rootDir.walkTopDown().filter { it.isFile && it.extension == "kt" && "/src/" in it.invariantSeparatorsPath }
            .forEach { it.appendText("\n// Edited.\n") }
    }

    /** A file with one `OPEN_API_WITHOUT_SUBCLASS_OPT_IN` and one `TOP_LEVEL_API_WITHOUT_JVM_NAME` on the JVM. */
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
private const val JVM = ":compileKotlinJvm"
private const val JS = ":compileKotlinJs"
private const val WASM = ":compileKotlinWasmJs"
private const val COMMON_METADATA = ":compileCommonMainKotlinMetadata"
private const val WEB_METADATA = ":compileWebMainKotlinMetadata"
private const val LINUX = ":compileKotlinLinuxX64"
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
