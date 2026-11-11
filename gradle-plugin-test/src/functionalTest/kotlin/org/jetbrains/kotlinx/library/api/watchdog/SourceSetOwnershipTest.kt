@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
import org.jetbrains.kotlinx.library.api.watchdog.Severity.ERROR
import org.jetbrains.kotlinx.library.api.watchdog.Severity.NONE
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

class SourceSetOwnershipTest {
    @Test
    fun `report task prints shared source set diagnostics once`() {
        val project = jvmAndJsProject(ERROR)

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, SUCCESS)
        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        val html = project.file("build/reports/api-watchdog/report.html").readText()
        for (expected in listOf("SharedApi", "JvmApi", "JsApi", "sharedApi", "jvmApi")) {
            assertTrue(expected in html, "Expected '$expected' in the report")
        }
        assertReportGroup(html, "IntentionallyOpen", 3, OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        assertReportGroup(html, "IntentionallyDefaultFacadeName", 2, TOP_LEVEL_API_WITHOUT_JVM_NAME)
    }

    @Test
    fun `regular build reports the common source set in every compilation`() {
        val project = jvmAndJsProject()

        val result = project.build("assemble", PLAIN_CONSOLE)

        result.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "SharedApi.kt" to JVM,
            "SharedApi.kt" to JS,
            "JvmApi.kt" to JVM,
            "JsApi.kt" to JS,
        )
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `excluded metadata compilation leaves the common source set to the JVM compilation`() {
        val project = jvmAndJsProject()

        val result = project.build(REPORT_TASK, "-x", "compileCommonMainKotlinMetadata", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, null)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `disabled metadata compile task leaves the common source set to the JVM compilation`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            // KGP registers the metadata compile tasks after the build script runs.
            buildScript("tasks.configureEach { if (name == \"compileCommonMainKotlinMetadata\") enabled = false }")
            api("SharedApi", "commonMain")
            api("JsApi", "jsMain")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, SKIPPED)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM)
    }

    @Test
    fun `web source set belongs to its metadata compilation`() {
        val project = kmpProject(JVM_JS_AND_WASM_TARGETS) {
            api("SharedApi", "commonMain")
            api("WebApi", "webMain")
            api("JvmApi", "jvmMain")
            api("JsApi", "jsMain")
            api("WasmApi", "wasmJsMain")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "WebApi.kt" to WEB_METADATA,
            "JvmApi.kt" to JVM,
            "JsApi.kt" to JS,
            "WasmApi.kt" to WASM,
        )
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `custom intermediate source set belongs to its metadata compilation`() {
        val project = kmpProject(
            """
                jvm()
                js { nodejs() }
                wasmJs { nodejs() }
                sourceSets {
                    val jvmAndJs by creating { dependsOn(commonMain.get()) }
                    jvmMain.get().dependsOn(jvmAndJs)
                    jsMain.get().dependsOn(jvmAndJs)
                }
            """
        ) {
            api("SharedApi", "commonMain")
            api("JvmAndJsApi", "jvmAndJs")
            api("WasmApi", "wasmJsMain")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "JvmAndJsApi.kt" to ":compileJvmAndJsKotlinMetadata",
            "WasmApi.kt" to WASM,
        )
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmAndJsApi.kt" to JVM)
    }

    @Test
    fun `native source sets belong to their own metadata compilations`() {
        val project = kmpProject("jvm()\nlinuxX64()\nmacosArm64()\niosArm64()\niosSimulatorArm64()") {
            api("SharedApi", "commonMain")
            api("NativeApi", "nativeMain")
            api("AppleApi", "appleMain")
            api("IosApi", "iosMain")
            api("MacosApi", "macosArm64Main")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "NativeApi.kt" to ":compileNativeMainKotlinMetadata",
            "AppleApi.kt" to ":compileAppleMainKotlinMetadata",
            "IosApi.kt" to ":compileIosMainKotlinMetadata",
            "MacosApi.kt" to ":compileKotlinMacosArm64",
        )
        result.assertJvmNameBy("SharedApi.kt" to JVM)
    }

    @Test
    fun `single target reports the common source set in the platform compilation`() {
        val project = kmpProject {
            api("SharedApi", "commonMain")
            api("JvmApi", "jvmMain")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, null)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `main compilation goes before a custom compilation of the target`() {
        val project = kmpProject(CUSTOM_COMPILATION_TARGETS) {
            explicitApi = false
            buildScript("kotlin { compilerOptions { freeCompilerArgs.add(\"-Xexplicit-api=strict\") } }")
            api("SharedApi", "commonMain")
            api("IntegrationApi", "jvmIntegration")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "IntegrationApi.kt" to INTEGRATION)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "IntegrationApi.kt" to INTEGRATION)
    }

    @Test
    fun `multiplatform Android library with a third target prints shared diagnostics once`() =
        multiplatformAndroidCase(KmpAndroidTarget.LIBRARY_AGP8)

    @Test
    fun `multiplatform Android library with a third target prints shared diagnostics once on AGP 9`() =
        multiplatformAndroidCase(KmpAndroidTarget.LIBRARY_AGP9)

    @Test
    fun `legacy multiplatform Android target with a third target prints shared diagnostics once`() =
        multiplatformAndroidCase(KmpAndroidTarget.LEGACY)

    private fun multiplatformAndroidCase(target: KmpAndroidTarget) {
        val android = target.androidApiReports()
        val project = kmpAndroidProject(target) {
            buildScript("kotlin { js { nodejs() } }")
            api("SharedApi", "commonMain")
            api("AndroidApi", "androidMain")
            api("JvmApi", "jvmMain")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, *android)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, *android)
    }

    @Test
    fun `JVM compilation reports the common source set of JVM and Android targets`() =
        jvmAndAndroidAloneCase(KmpAndroidTarget.LIBRARY_AGP8)

    @Test
    fun `JVM compilation reports the common source set of JVM and Android targets on AGP 9`() =
        jvmAndAndroidAloneCase(KmpAndroidTarget.LIBRARY_AGP9)

    @Test
    fun `JVM compilation reports the common source set of JVM and legacy Android targets`() =
        jvmAndAndroidAloneCase(KmpAndroidTarget.LEGACY)

    /** KGP doesn't compile the metadata of a source set that only JVM and Android targets share. */
    private fun jvmAndAndroidAloneCase(target: KmpAndroidTarget) {
        val android = target.androidApiReports()
        val project = kmpAndroidProject(target) {
            api("SharedApi", "commonMain")
            api("AndroidApi", "androidMain")
            api("JvmApi", "jvmMain")
        }

        val result = project.build(REPORT_TASK, PLAIN_CONSOLE)

        assertTrue(result.task(COMMON_METADATA)?.outcome in setOf(null, SKIPPED), result.output)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, *android)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, *android)
    }

    /** A report run compiles every variant of a legacy `androidTarget()`, and each one prints `androidMain`. */
    private fun KmpAndroidTarget.androidApiReports(): Array<Pair<String, String>> = when (this) {
        KmpAndroidTarget.LEGACY -> arrayOf(
            "AndroidApi.kt" to ":compileDebugKotlinAndroid",
            "AndroidApi.kt" to ":compileReleaseKotlinAndroid",
        )
        else -> arrayOf("AndroidApi.kt" to ":$mainCompileTask")
    }

    private fun jvmAndJsProject(openApi: Severity = WARNING): WatchdogBuild =
        kmpProject(JVM_AND_JS_TARGETS) {
            apiWatchdog {
                severityOfEveryCheck(if (openApi == ERROR) NONE else openApi)
                severity(openApi, OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
                severity(WARNING, TOP_LEVEL_API_WITHOUT_JVM_NAME)
            }
            api("SharedApi", "commonMain")
            api("JvmApi", "jvmMain")
            api("JsApi", "jsMain")
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

private const val REPORT_TASK = "generateApiWatchdogReport"
private const val JVM = ":compileKotlinJvm"
private const val JS = ":compileKotlinJs"
private const val WASM = ":compileKotlinWasmJs"
private const val COMMON_METADATA = ":compileCommonMainKotlinMetadata"
private const val WEB_METADATA = ":compileWebMainKotlinMetadata"
private const val INTEGRATION = ":compileIntegrationKotlinJvm"

private const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"
private const val JVM_JS_AND_WASM_TARGETS = "jvm()\njs { nodejs() }\nwasmJs { nodejs() }"

private const val CUSTOM_COMPILATION_TARGETS = """
    val commonMain = sourceSets.commonMain.get()
    jvm { compilations.create("integration") { defaultSourceSet.dependsOn(commonMain) } }
    js { nodejs() }
"""
