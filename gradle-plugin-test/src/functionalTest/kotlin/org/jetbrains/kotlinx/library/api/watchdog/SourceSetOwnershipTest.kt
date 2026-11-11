@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
import org.jetbrains.kotlinx.library.api.watchdog.Severity.ERROR
import org.jetbrains.kotlinx.library.api.watchdog.Severity.NONE
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

class SourceSetOwnershipTest {
    @Test
    fun `build reports the common source set in its metadata compilation`() {
        val project = jvmAndJsProject()

        val result = project.build("build", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, SUCCESS)
        result.assertTaskOutcome(JVM, SUCCESS)
        result.assertTaskOutcome(JS, SUCCESS)
        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        // The JVM compilation runs the JVM checks on the common source set.
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `assemble reports the common source set in its metadata compilation`() {
        val project = jvmAndJsProject()

        val result = project.build("assemble", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, SUCCESS)
        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `each compile task alone reports the common source set`() {
        val project = jvmAndJsProject()

        val js = project.build("compileKotlinJs", PLAIN_CONSOLE)
        js.assertTaskOutcome(COMMON_METADATA, null)
        js.assertOpenApiBy("SharedApi.kt" to JS, "JsApi.kt" to JS)
        js.assertNotReported(TOP_LEVEL_API_WITHOUT_JVM_NAME)

        val jvm = project.build("compileKotlinJvm", PLAIN_CONSOLE)
        jvm.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        jvm.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        val metadata = project.build("compileCommonMainKotlinMetadata", PLAIN_CONSOLE)
        metadata.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA)
        metadata.assertNotReported(TOP_LEVEL_API_WITHOUT_JVM_NAME)
    }

    @Test
    fun `JVM compilation reports the common source set when the build has no metadata compilation`() {
        val project = jvmAndJsProject()

        val result = project.build("compileKotlinJvm", "compileKotlinJs", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, null)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `JS compilation that ran alone stops reporting the common source set next to the JVM compilation`() {
        val project = jvmAndJsProject()
        project.build("compileKotlinJs", "compileKotlinJvm", PLAIN_CONSOLE)
        project.build("compileKotlinJs", PLAIN_CONSOLE).assertOpenApiBy("SharedApi.kt" to JS, "JsApi.kt" to JS)

        val result = project.build("compileKotlinJvm", "compileKotlinJs", PLAIN_CONSOLE)

        // Only the options of the JS compilation changed.
        result.assertTaskOutcome(JVM, UP_TO_DATE)
        result.assertTaskOutcome(JS, SUCCESS)
        result.assertOpenApiBy("JsApi.kt" to JS)
    }

    @Test
    fun `excluded metadata compilation leaves the common source set to the JVM compilation`() {
        val project = jvmAndJsProject()

        val result = project.build("assemble", "-x", "compileCommonMainKotlinMetadata", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, null)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `disabled metadata compile task leaves the common source set to the JVM compilation`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            // KGP registers the metadata compile tasks after the build script runs.
            buildScript("tasks.configureEach { if (name == \"compileCommonMainKotlinMetadata\") enabled = false }")
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("JsApi", "jsMain")
        }

        val result = project.build("assemble", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, SKIPPED)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JsApi.kt" to JS)
        result.assertJvmNameBy("SharedApi.kt" to JVM)
    }

    @Test
    fun `web source set belongs to its metadata compilation or to the first web compilation`() {
        val project = kmpProject(JVM_JS_AND_WASM_TARGETS) {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("WebApi", "webMain")
            api("JvmApi", "jvmMain")
            api("JsApi", "jsMain")
            api("WasmApi", "wasmJsMain")
        }

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "WebApi.kt" to WEB_METADATA,
            "JvmApi.kt" to JVM,
            "JsApi.kt" to JS,
            "WasmApi.kt" to WASM,
        )
        assemble.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        val web = project.build("compileKotlinJs", "compileKotlinWasmJs", "--rerun-tasks", PLAIN_CONSOLE)
        web.assertOpenApiBy("SharedApi.kt" to JS, "WebApi.kt" to JS, "JsApi.kt" to JS, "WasmApi.kt" to WASM)
        web.assertNotReported(TOP_LEVEL_API_WITHOUT_JVM_NAME)

        val wasm = project.build("compileKotlinWasmJs", "--rerun-tasks", PLAIN_CONSOLE)
        wasm.assertOpenApiBy("SharedApi.kt" to WASM, "WebApi.kt" to WASM, "WasmApi.kt" to WASM)

        val jvmAndWasm = project.build("compileKotlinJvm", "compileKotlinWasmJs", "--rerun-tasks", PLAIN_CONSOLE)
        jvmAndWasm.assertOpenApiBy("SharedApi.kt" to JVM, "WebApi.kt" to WASM, "JvmApi.kt" to JVM, "WasmApi.kt" to WASM)
        jvmAndWasm.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        // A metadata compilation gets its parent source sets as klibs, so it reports only its own source set.
        val webMetadata = project.build("compileWebMainKotlinMetadata", "--rerun-tasks", PLAIN_CONSOLE)
        webMetadata.assertTaskOutcome(COMMON_METADATA, SUCCESS)
        webMetadata.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "WebApi.kt" to WEB_METADATA)
    }

    @Test
    fun `custom intermediate source set belongs to its metadata compilation or to the first compilation`() {
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
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("JvmAndJsApi", "jvmAndJs")
            api("WasmApi", "wasmJsMain")
        }

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "JvmAndJsApi.kt" to ":compileJvmAndJsKotlinMetadata",
            "WasmApi.kt" to WASM,
        )
        assemble.assertJvmNameBy("SharedApi.kt" to JVM, "JvmAndJsApi.kt" to JVM)

        val web = project.build("compileKotlinJs", "compileKotlinWasmJs", "--rerun-tasks", PLAIN_CONSOLE)
        web.assertOpenApiBy("SharedApi.kt" to JS, "JvmAndJsApi.kt" to JS, "WasmApi.kt" to WASM)

        val jvmAndWasm = project.build("compileKotlinJvm", "compileKotlinWasmJs", "--rerun-tasks", PLAIN_CONSOLE)
        jvmAndWasm.assertOpenApiBy("SharedApi.kt" to JVM, "JvmAndJsApi.kt" to JVM, "WasmApi.kt" to WASM)
        jvmAndWasm.assertJvmNameBy("SharedApi.kt" to JVM, "JvmAndJsApi.kt" to JVM)
    }

    @Test
    fun `native source sets belong to their own metadata compilations`() {
        val project = kmpProject("jvm()\nlinuxX64()\nmacosArm64()\niosArm64()\niosSimulatorArm64()") {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("NativeApi", "nativeMain")
            api("AppleApi", "appleMain")
            api("IosApi", "iosMain")
            api("MacosApi", "macosArm64Main")
        }

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "NativeApi.kt" to ":compileNativeMainKotlinMetadata",
            "AppleApi.kt" to ":compileAppleMainKotlinMetadata",
            "IosApi.kt" to ":compileIosMainKotlinMetadata",
            "MacosApi.kt" to ":compileKotlinMacosArm64",
        )
        assemble.assertJvmNameBy("SharedApi.kt" to JVM)

        // The other targets go by name.
        val apple = project.build("compileKotlinMacosArm64", "compileKotlinIosArm64", "--rerun-tasks", PLAIN_CONSOLE)
        apple.assertOpenApiBy(
            "SharedApi.kt" to ":compileKotlinIosArm64",
            "NativeApi.kt" to ":compileKotlinIosArm64",
            "AppleApi.kt" to ":compileKotlinIosArm64",
            "IosApi.kt" to ":compileKotlinIosArm64",
            "MacosApi.kt" to ":compileKotlinMacosArm64",
        )

        val linuxAndMacos = project.build("compileKotlinMacosArm64", "compileKotlinLinuxX64", "--rerun-tasks", PLAIN_CONSOLE)
        linuxAndMacos.assertOpenApiBy(
            "SharedApi.kt" to ":compileKotlinLinuxX64",
            "NativeApi.kt" to ":compileKotlinLinuxX64",
            "AppleApi.kt" to ":compileKotlinMacosArm64",
            "MacosApi.kt" to ":compileKotlinMacosArm64",
        )

        val appleMetadata = project.build("compileAppleMainKotlinMetadata", "--rerun-tasks", PLAIN_CONSOLE)
        appleMetadata.assertOpenApiBy(
            "SharedApi.kt" to COMMON_METADATA,
            "NativeApi.kt" to ":compileNativeMainKotlinMetadata",
            "AppleApi.kt" to ":compileAppleMainKotlinMetadata",
        )
    }

    @Test
    fun `single target reports the common source set in the platform compilation`() {
        val project = kmpProject {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("JvmApi", "jvmMain")
        }

        val result = project.build("build", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, null)
        result.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        result.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
    }

    @Test
    fun `custom compilation without explicit API mode never reports the common source set`() {
        val project = kmpProject(CUSTOM_COMPILATION_TARGETS) {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("IntegrationApi", "jvmIntegration")
        }

        project.build(INTEGRATION.removePrefix(":"), PLAIN_CONSOLE).assertNoWatchdogDiagnostics()

        val withJs = project.build(INTEGRATION.removePrefix(":"), "compileKotlinJs", PLAIN_CONSOLE)
        withJs.assertOpenApiBy("SharedApi.kt" to JS)
        withJs.assertNotReported(TOP_LEVEL_API_WITHOUT_JVM_NAME)
    }

    @Test
    fun `custom compilation with the raw explicit API flag reports the common source set`() {
        val project = kmpProject(CUSTOM_COMPILATION_TARGETS) {
            explicitApi = false
            buildScript("kotlin { compilerOptions { freeCompilerArgs.add(\"-Xexplicit-api=strict\") } }")
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("IntegrationApi", "jvmIntegration")
        }

        val withJs = project.build(INTEGRATION.removePrefix(":"), "compileKotlinJs", PLAIN_CONSOLE)
        withJs.assertOpenApiBy("SharedApi.kt" to INTEGRATION, "IntegrationApi.kt" to INTEGRATION)
        withJs.assertJvmNameBy("SharedApi.kt" to INTEGRATION, "IntegrationApi.kt" to INTEGRATION)

        // The `main` compilation of a target goes first.
        val withMain = project.build(INTEGRATION.removePrefix(":"), "compileKotlinJvm", "--rerun-tasks", PLAIN_CONSOLE)
        withMain.assertOpenApiBy("SharedApi.kt" to JVM, "IntegrationApi.kt" to INTEGRATION)
        withMain.assertJvmNameBy("SharedApi.kt" to JVM, "IntegrationApi.kt" to INTEGRATION)
    }

    @Test
    fun `custom compilation reports the common source set when explicit API mode isn't required`() {
        val project = kmpProject(CUSTOM_COMPILATION_TARGETS) {
            apiWatchdog {
                explicitApiRequirement = ExplicitApiRequirement.WARN
                severityOfEveryCheck(WARNING)
            }
            api("SharedApi", "commonMain")
            api("IntegrationApi", "jvmIntegration")
        }

        val withJs = project.build(INTEGRATION.removePrefix(":"), "compileKotlinJs", PLAIN_CONSOLE)
        withJs.assertOpenApiBy("SharedApi.kt" to INTEGRATION, "IntegrationApi.kt" to INTEGRATION)
        withJs.assertJvmNameBy("SharedApi.kt" to INTEGRATION, "IntegrationApi.kt" to INTEGRATION)
    }

    @Test
    fun `explicit API requirement WARN without explicit API mode reports each diagnostic once`() =
        withoutExplicitApiCase(ExplicitApiRequirement.WARN)

    @Test
    fun `explicit API requirement SKIP without explicit API mode reports each diagnostic once`() =
        withoutExplicitApiCase(ExplicitApiRequirement.SKIP)

    private fun withoutExplicitApiCase(requirement: ExplicitApiRequirement) {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            explicitApi = false
            apiWatchdog {
                explicitApiRequirement = requirement
                severityOfEveryCheck(WARNING)
            }
            api("SharedApi", "commonMain")
            api("JvmApi", "jvmMain")
            api("JsApi", "jsMain")
        }

        val assemble = project.build("assemble", PLAIN_CONSOLE)
        assemble.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
        assemble.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        val platforms = project.build("compileKotlinJs", "compileKotlinJvm", "--rerun-tasks", PLAIN_CONSOLE)
        platforms.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM, "JsApi.kt" to JS)
    }

    @Test
    fun `error in a shared source set fails the build of one target`() {
        val project = jvmAndJsProject(ERROR)

        val result = project.buildAndFail("compileKotlinJs", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, null)
        result.assertTaskOutcome(JS, FAILED)
        result.assertOpenApiBy("SharedApi.kt" to JS, "JsApi.kt" to JS)
    }

    @Test
    fun `error in a shared source set fails only the metadata compilation of assemble`() {
        val project = jvmAndJsProject(ERROR, targetSources = false)

        val result = project.buildAndFail("assemble", "--continue", PLAIN_CONSOLE)

        result.assertTaskOutcome(COMMON_METADATA, FAILED)
        result.assertTaskOutcome(JVM, SUCCESS)
        result.assertTaskOutcome(JS, SUCCESS)
        result.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA)
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `error in a shared source set fails only the JVM compilation of both targets`() {
        val project = jvmAndJsProject(ERROR, targetSources = false)

        val result = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue", PLAIN_CONSOLE)

        result.assertTaskOutcome(JVM, FAILED)
        result.assertTaskOutcome(JS, SUCCESS)
        result.assertOpenApiBy("SharedApi.kt" to JVM)
    }

    @Test
    fun `JVM-only error in a shared source set fails the JVM compilation`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            apiWatchdog { severity(WARNING, OPEN_API_WITHOUT_SUBCLASS_OPT_IN) }
            api("SharedApi", "commonMain")
        }

        project.build("compileKotlinJs", PLAIN_CONSOLE).assertNotReported(TOP_LEVEL_API_WITHOUT_JVM_NAME)

        val assemble = project.buildAndFail("assemble", "--continue", PLAIN_CONSOLE)
        assemble.assertTaskOutcome(COMMON_METADATA, SUCCESS)
        assemble.assertTaskOutcome(JVM, FAILED)
        assemble.assertJvmNameBy("SharedApi.kt" to JVM)

        val jvm = project.buildAndFail("compileKotlinJvm", PLAIN_CONSOLE)
        jvm.assertTaskOutcome(JVM, FAILED)
        jvm.assertJvmNameBy("SharedApi.kt" to JVM)
    }

    @Test
    fun `report task prints shared source set diagnostics once`() {
        val project = jvmAndJsProject(ERROR)

        val result = project.build("generateApiWatchdogReport", PLAIN_CONSOLE)

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
    fun `multiplatform Android library with a third target prints shared diagnostics once`() =
        multiplatformAndroidCase(KmpAndroidTarget.LIBRARY_AGP8)

    @Test
    fun `multiplatform Android library with a third target prints shared diagnostics once on AGP 9`() =
        multiplatformAndroidCase(KmpAndroidTarget.LIBRARY_AGP9)

    @Test
    fun `legacy multiplatform Android target with a third target prints shared diagnostics once`() =
        multiplatformAndroidCase(KmpAndroidTarget.LEGACY)

    /** The JS target `aJs` sorts before the Android target by name. */
    private fun multiplatformAndroidCase(target: KmpAndroidTarget) {
        val android = ":${target.mainCompileTask}"
        val project = kmpAndroidProject(target) {
            buildScript("kotlin { js(\"aJs\") { nodejs() } }")
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("AndroidApi", "androidMain")
            api("JvmApi", "jvmMain")
        }

        val all = project.build("compileKotlinJvm", target.mainCompileTask, "compileKotlinAJs", PLAIN_CONSOLE)
        all.assertOpenApiBy("SharedApi.kt" to JVM, "AndroidApi.kt" to android, "JvmApi.kt" to JVM)
        all.assertJvmNameBy("SharedApi.kt" to JVM, "AndroidApi.kt" to android, "JvmApi.kt" to JVM)

        // Android goes before `aJs`.
        val androidAndJs = project.build(target.mainCompileTask, "compileKotlinAJs", "--rerun-tasks", PLAIN_CONSOLE)
        androidAndJs.assertOpenApiBy("SharedApi.kt" to android, "AndroidApi.kt" to android)
        androidAndJs.assertJvmNameBy("SharedApi.kt" to android, "AndroidApi.kt" to android)

        val metadata = project.build("compileCommonMainKotlinMetadata", target.mainCompileTask, "--rerun-tasks", PLAIN_CONSOLE)
        metadata.assertOpenApiBy("SharedApi.kt" to COMMON_METADATA, "AndroidApi.kt" to android)
        metadata.assertJvmNameBy("SharedApi.kt" to android, "AndroidApi.kt" to android)
    }

    @Test
    fun `JVM and Android targets alone both check the common source set`() =
        jvmAndAndroidAloneCase(KmpAndroidTarget.LIBRARY_AGP8)

    @Test
    fun `JVM and Android targets alone both check the common source set on AGP 9`() =
        jvmAndAndroidAloneCase(KmpAndroidTarget.LIBRARY_AGP9)

    @Test
    fun `JVM and legacy Android targets alone both check the common source set`() =
        jvmAndAndroidAloneCase(KmpAndroidTarget.LEGACY)

    /** KGP doesn't compile the metadata of a source set that only JVM and Android targets share. */
    private fun jvmAndAndroidAloneCase(target: KmpAndroidTarget) {
        val android = ":${target.mainCompileTask}"
        val project = kmpAndroidProject(target) {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("AndroidApi", "androidMain")
            api("JvmApi", "jvmMain")
        }

        val androidAlone = project.build(target.mainCompileTask, PLAIN_CONSOLE)
        androidAlone.assertOpenApiBy("SharedApi.kt" to android, "AndroidApi.kt" to android)
        androidAlone.assertJvmNameBy("SharedApi.kt" to android, "AndroidApi.kt" to android)

        val jvmAlone = project.build("compileKotlinJvm", PLAIN_CONSOLE)
        jvmAlone.assertOpenApiBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)
        jvmAlone.assertJvmNameBy("SharedApi.kt" to JVM, "JvmApi.kt" to JVM)

        val both = project.build("compileKotlinJvm", target.mainCompileTask, "--rerun-tasks", PLAIN_CONSOLE)
        assertTrue(both.task(COMMON_METADATA)?.outcome in setOf(null, SKIPPED), both.output)
        both.assertOpenApiBy("SharedApi.kt" to JVM, "AndroidApi.kt" to android, "JvmApi.kt" to JVM)
        both.assertJvmNameBy("SharedApi.kt" to JVM, "AndroidApi.kt" to android, "JvmApi.kt" to JVM)
    }

    @Test
    fun `JVM project prints each diagnostic once`() {
        val project = jvmProject {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("JvmApi", "main")
        }

        val result = project.build("compileKotlin", PLAIN_CONSOLE)

        result.assertOpenApiBy("JvmApi.kt" to ":compileKotlin")
        result.assertJvmNameBy("JvmApi.kt" to ":compileKotlin")
    }

    @Test
    fun `Android library prints each diagnostic once for a variant`() = androidLibraryCase(AgpVersion.AGP8)

    @Test
    fun `Android library prints each diagnostic once for a variant on AGP 9`() = androidLibraryCase(AgpVersion.AGP9)

    private fun androidLibraryCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("AndroidApi", "main")
        }

        val result = project.build("compileDebugKotlin", PLAIN_CONSOLE)

        result.assertOpenApiBy("AndroidApi.kt" to ":compileDebugKotlin")
        result.assertJvmNameBy("AndroidApi.kt" to ":compileDebugKotlin")
    }

    private fun jvmAndJsProject(openApi: Severity = WARNING, targetSources: Boolean = true): WatchdogBuild =
        kmpProject(JVM_AND_JS_TARGETS) {
            apiWatchdog {
                severityOfEveryCheck(if (openApi == ERROR) NONE else openApi)
                severity(openApi, OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
                severity(WARNING, TOP_LEVEL_API_WITHOUT_JVM_NAME)
            }
            api("SharedApi", "commonMain")
            if (targetSources) {
                api("JvmApi", "jvmMain")
                api("JsApi", "jsMain")
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
