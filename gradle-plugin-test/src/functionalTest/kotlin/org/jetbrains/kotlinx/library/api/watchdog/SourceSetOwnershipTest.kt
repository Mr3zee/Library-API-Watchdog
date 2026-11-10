@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

/** One compilation reports the diagnostics of a source set, so the build output has each of them once. */
class SourceSetOwnershipTest {
    @Test
    fun `shared source set diagnostics are printed once`() {
        val project = kmpProject(JVM_JS_AND_WASM_TARGETS) {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("WebApi", "webMain")
            api("JvmApi", "jvmMain")
        }

        val result = project.build("compileKotlinJvm", "compileKotlinJs", "compileKotlinWasmJs")

        result.assertTaskOutcome(":compileCommonMainKotlinMetadata", SUCCESS)
        result.assertTaskOutcome(":compileWebMainKotlinMetadata", SUCCESS)
        result.assertReportedIn(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, "SharedApi.kt", "WebApi.kt", "JvmApi.kt")
        // A metadata compilation can't run a JVM-only check, so the JVM compilation runs it on the shared source set.
        result.assertReportedIn(TOP_LEVEL_API_WITHOUT_JVM_NAME, "SharedApi.kt", "JvmApi.kt")
    }

    @Test
    fun `build of one target checks the shared source sets`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
        }

        val js = project.build("compileKotlinJs")
        js.assertTaskOutcome(":compileCommonMainKotlinMetadata", SUCCESS)
        js.assertReportedIn(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, "SharedApi.kt")
        js.assertNotReported(TOP_LEVEL_API_WITHOUT_JVM_NAME)

        val jvm = project.build("compileKotlinJvm", "--rerun-tasks")
        jvm.assertReportedIn(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, "SharedApi.kt")
        jvm.assertReportedIn(TOP_LEVEL_API_WITHOUT_JVM_NAME, "SharedApi.kt")
    }

    @Test
    fun `error in a shared source set fails the build of one target`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            api("SharedApi", "commonMain")
        }

        val result = project.buildAndFail("compileKotlinJs")

        result.assertTaskOutcome(":compileCommonMainKotlinMetadata", FAILED)
        result.assertTaskOutcome(":compileKotlinJs", null)
        result.assertReportedIn(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, "SharedApi.kt")
    }

    @Test
    fun `JVM-only error in a shared source set fails the JVM compilation`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            apiWatchdog { severity(WARNING, OPEN_API_WITHOUT_SUBCLASS_OPT_IN) }
            api("SharedApi", "commonMain")
        }

        project.build("compileKotlinJs").assertNotReported(TOP_LEVEL_API_WITHOUT_JVM_NAME)

        val result = project.buildAndFail("compileKotlinJvm")
        result.assertTaskOutcome(":compileKotlinJvm", FAILED)
        result.assertReportedIn(TOP_LEVEL_API_WITHOUT_JVM_NAME, "SharedApi.kt")
    }

    @Test
    fun `single target checks the common source set in the platform compilation`() {
        val project = kmpProject {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("JvmApi", "jvmMain")
        }

        val result = project.build("compileKotlinJvm")

        result.assertTaskOutcome(":compileCommonMainKotlinMetadata", null)
        result.assertReportedOnce("SharedApi.kt", "JvmApi.kt")
    }

    @Test
    fun `report task prints shared source set diagnostics once`() {
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            api("SharedApi", "commonMain")
            api("JvmApi", "jvmMain")
        }

        val result = project.build("generateApiWatchdogReport")

        result.assertReportedOnce("SharedApi.kt", "JvmApi.kt")
        val html = project.file("build/reports/api-watchdog/report.html").readText()
        for (expected in listOf("SharedApi", "JvmApi", "sharedApi", "jvmApi")) {
            assertTrue(expected in html, "Expected '$expected' in the report")
        }
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
        val project = kmpAndroidProject(target) {
            buildScript("kotlin { js { nodejs() } }")
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("AndroidApi", "androidMain")
            api("JvmApi", "jvmMain")
        }

        project.build("compileKotlinJvm", target.mainCompileTask, "compileKotlinJs")
            .assertReportedOnce("SharedApi.kt", "AndroidApi.kt", "JvmApi.kt")
        // The JVM compilation runs the JVM-only checks on the common source set.
        project.build(target.mainCompileTask, "--rerun-tasks").apply {
            assertReportedIn(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, "SharedApi.kt", "AndroidApi.kt")
            assertReportedIn(TOP_LEVEL_API_WITHOUT_JVM_NAME, "AndroidApi.kt")
        }
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
        val project = kmpAndroidProject(target) {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("SharedApi", "commonMain")
            api("AndroidApi", "androidMain")
        }

        project.build(target.mainCompileTask).assertReportedOnce("SharedApi.kt", "AndroidApi.kt")
        project.build("compileKotlinJvm", "--rerun-tasks").assertReportedOnce("SharedApi.kt")
    }

    @Test
    fun `JVM project prints each diagnostic once`() {
        val project = jvmProject {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            api("JvmApi", "main")
        }

        project.build("compileKotlin").assertReportedOnce("JvmApi.kt")
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

        project.build("compileDebugKotlin").assertReportedOnce("AndroidApi.kt")
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

    private fun BuildResult.assertReportedOnce(vararg files: String) {
        assertReportedIn(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, *files)
        assertReportedIn(TOP_LEVEL_API_WITHOUT_JVM_NAME, *files)
    }
}

private const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"
private const val JVM_JS_AND_WASM_TARGETS = "jvm()\njs { nodejs() }\nwasmJs { nodejs() }"
