@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.REDUNDANT_EXEMPTION
import org.junit.Test

class SharedSourceSetDependencyTest {
    @Test
    fun `implementation dependency of the common source set is reported once by the first platform compilation`() {
        val project = jvmAndJsProject { dependencies(implementation(":model")) }

        val assemble = project.buildAndFail("assemble", "--continue", PLAIN_CONSOLE)
        assemble.assertTaskOutcome(COMMON_METADATA, SUCCESS)
        assemble.assertTaskOutcome(JVM, FAILED)
        assemble.assertTaskOutcome(JS, SUCCESS)
        assemble.assertExposureBy("Exposure.kt" to JVM)

        val platforms = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue", PLAIN_CONSOLE)
        platforms.assertTaskOutcome(JVM, FAILED)
        platforms.assertExposureBy("Exposure.kt" to JVM)

        val js = project.buildAndFail("compileKotlinJs", PLAIN_CONSOLE)
        js.assertTaskOutcome(JS, FAILED)
        js.assertExposureBy("Exposure.kt" to JS)
    }

    @Test
    fun `API dependency of the common source set is accepted`() {
        val project = jvmAndJsProject { dependencies(api(":model")) }

        project.build("assemble", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
        project.build("compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
        project.build("compileKotlinJvm", "--rerun-tasks", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
    }

    @Test
    fun `API dependency of one target is reported for the common source set`() {
        val project = jvmAndJsProject {
            dependencies(implementation(":model"))
            dependencies("jvmMain", api(":model"))
            exposure("JvmExposure", "jvmMain")
        }

        val jvm = project.buildAndFail("compileKotlinJvm", PLAIN_CONSOLE)
        jvm.assertExposureBy("Exposure.kt" to JVM)

        val platforms = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue", PLAIN_CONSOLE)
        platforms.assertTaskOutcome(JS, SUCCESS)
        platforms.assertExposureBy("Exposure.kt" to JVM)
    }

    @Test
    fun `API dependency of every target is accepted for the common source set`() {
        val project = jvmAndJsProject {
            dependencies(implementation(":model"))
            dependencies("jvmMain", api(":model"))
            dependencies("jsMain", api(":model"))
        }

        project.build("compileKotlinJvm", "compileKotlinJs", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
        project.build("compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
    }

    @Test
    fun `exempt dependency is accepted for the common source set`() {
        val project = jvmAndJsProject {
            dependencies(implementation(":model"))
            buildScript("dependencies { apiWatchdogExemptNonTransitive(project(\":model\")) }")
        }

        project.build("compileKotlinJvm", "compileKotlinJs", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
        project.build("compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
    }

    @Test
    fun `multiplatform library is matched through its root module`() {
        val datetime = "org.jetbrains.kotlinx:kotlinx-datetime:0.7.1"
        val calendar: ProjectSpec.() -> Unit = {
            source(
                """
                    import kotlinx.datetime.LocalDate

                    /** A calendar. */
                    public class Calendar {
                        /** Returns the current date, if known. */
                        public fun today(): LocalDate? = null
                    }
                """,
                "Calendar",
                "test.consumer",
                defaultImports = false,
            )
        }

        val published = kmpProject(JVM_AND_JS_TARGETS) { calendar(); dependencies(api(datetime)) }
        published.build("compileKotlinJvm", "compileKotlinJs", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
        published.build("compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)

        val hidden = kmpProject(JVM_AND_JS_TARGETS) { calendar(); dependencies(implementation(datetime)) }
        hidden.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue", PLAIN_CONSOLE)
            .assertReportedBy(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, "Calendar.kt" to JVM)
        hidden.buildAndFail("compileKotlinJs", PLAIN_CONSOLE)
            .assertReportedBy(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, "Calendar.kt" to JS)
    }

    @Test
    fun `only the web targets decide for the web source set`() {
        val project = kmpProject(JVM_JS_AND_WASM_TARGETS) {
            exposure("Exposure", "commonMain")
            exposure("WebExposure", "webMain")
            dependencies(implementation(":model"))
            dependencies("jsMain", api(":model"))
            dependencies("wasmJsMain", api(":model"))
            modelSubproject()
        }

        val result = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "compileKotlinWasmJs", "--continue", PLAIN_CONSOLE)

        result.assertTaskOutcome(JS, SUCCESS)
        result.assertTaskOutcome(WASM, SUCCESS)
        result.assertExposureBy("Exposure.kt" to JVM)

        // Without the JVM compilation, the JS compilation reports the common source set.
        val web = project.buildAndFail("compileKotlinJs", "compileKotlinWasmJs", "--continue", "--rerun-tasks", PLAIN_CONSOLE)
        web.assertTaskOutcome(WASM, SUCCESS)
        web.assertExposureBy("Exposure.kt" to JS)
    }

    @Test
    fun `JVM and multiplatform Android library decide together`() = jvmAndAndroidCase(KmpAndroidTarget.LIBRARY_AGP8)

    @Test
    fun `JVM and multiplatform Android library decide together on AGP 9`() = jvmAndAndroidCase(KmpAndroidTarget.LIBRARY_AGP9)

    @Test
    fun `JVM and legacy multiplatform Android target decide together`() = jvmAndAndroidCase(KmpAndroidTarget.LEGACY)

    private fun jvmAndAndroidCase(target: KmpAndroidTarget) {
        val android = ":${target.mainCompileTask}"
        val hidden = kmpAndroidProject(target) {
            exposure("Exposure", "commonMain")
            dependencies(implementation(":model"))
            dependencies("jvmMain", api(":model"))
            modelSubproject()
        }
        val both = hidden.buildAndFail("compileKotlinJvm", target.mainCompileTask, "--continue", PLAIN_CONSOLE)
        both.assertTaskOutcome(android, SUCCESS)
        both.assertExposureBy("Exposure.kt" to JVM)
        hidden.buildAndFail(target.mainCompileTask, PLAIN_CONSOLE).assertExposureBy("Exposure.kt" to android)

        val published = kmpAndroidProject(target) {
            exposure("Exposure", "commonMain")
            dependencies(api(":model"))
            modelSubproject()
        }
        published.build("compileKotlinJvm", target.mainCompileTask, PLAIN_CONSOLE)
            .assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
    }

    @Test
    fun `needed exemption in the common source set isn't redundant`() {
        val project = jvmAndJsProject(exposure = EXEMPTED_EXPOSURE) {
            dependencies(implementation(":model"))
            dependencies("jvmMain", api(":model"))
        }

        project.build("assemble", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, REDUNDANT_EXEMPTION)
        project.build("compileKotlinJvm", "compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE)
            .assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, REDUNDANT_EXEMPTION)
        project.build("compileKotlinJvm", "--rerun-tasks", PLAIN_CONSOLE)
            .assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, REDUNDANT_EXEMPTION)
    }

    @Test
    fun `exemption in the common source set is redundant once the dependency is published`() {
        val project = jvmAndJsProject(exposure = EXEMPTED_EXPOSURE) { dependencies(api(":model")) }

        project.build("assemble", PLAIN_CONSOLE).assertReportedBy(REDUNDANT_EXEMPTION, "Exposure.kt" to JVM)
        project.build("compileKotlinJvm", "compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE)
            .assertReportedBy(REDUNDANT_EXEMPTION, "Exposure.kt" to JVM)
        project.build("compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE)
            .assertReportedBy(REDUNDANT_EXEMPTION, "Exposure.kt" to JS)
    }

    @Test
    fun `needed exemption of an expect declaration isn't redundant`() {
        val project = expectExposureProject {
            dependencies(implementation(":model"))
            dependencies("jvmMain", api(":model"))
        }

        project.build("assemble", PLAIN_CONSOLE).assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, REDUNDANT_EXEMPTION)
        // The JVM compilation judges the `expect` exemption by the shared dependencies, not by its own.
        project.build("compileKotlinJvm", "compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE)
            .assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, REDUNDANT_EXEMPTION)
        project.build("compileKotlinJvm", "--rerun-tasks", PLAIN_CONSOLE)
            .assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, REDUNDANT_EXEMPTION)
    }

    @Test
    fun `exemption of an expect declaration is redundant once the dependency is published`() {
        val project = expectExposureProject { dependencies(api(":model")) }

        // The metadata compilation doesn't run the dependency check, so the JVM compilation judges the exemption.
        project.build("assemble", PLAIN_CONSOLE).assertReportedBy(REDUNDANT_EXEMPTION, "ModelSource.kt" to JVM)
        project.build("compileKotlinJvm", "compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE)
            .assertReportedBy(REDUNDANT_EXEMPTION, "ModelSource.kt" to JVM)
        project.build("compileKotlinJs", "--rerun-tasks", PLAIN_CONSOLE)
            .assertReportedBy(REDUNDANT_EXEMPTION, "ModelSource.kt" to JS)
    }

    private fun jvmAndJsProject(exposure: String = EXPOSURE, block: ProjectSpec.() -> Unit): WatchdogBuild =
        kmpProject(JVM_AND_JS_TARGETS) {
            source(exposure, "Exposure", "test.consumer")
            modelSubproject()
            block()
        }

    private fun expectExposureProject(block: ProjectSpec.() -> Unit): WatchdogBuild = kmpProject(JVM_AND_JS_TARGETS) {
        source(
            """
                import test.model.ExternalModel

                /** A source of models. */
                public expect class ModelSource() {
                    /** Returns a model. */
                    $EXEMPTION
                    public fun model(): ExternalModel
                }
            """,
            "ModelSource",
            "test.consumer",
        )
        for (sourceSet in listOf("jvmMain", "jsMain")) {
            source(
                """
                    import test.model.ExternalModel

                    public actual class ModelSource actual constructor() {
                        $EXEMPTION
                        public actual fun model(): ExternalModel = ExternalModel()
                    }
                """,
                "ModelSource${sourceSet.removeSuffix("Main").replaceFirstChar(Char::uppercase)}",
                "test.consumer",
                sourceSet,
            )
        }
        modelSubproject()
        block()
    }

    private fun ModuleSpec.exposure(name: String, sourceSet: String) {
        source(EXPOSURE.replace("class Exposure", "class $name"), name, "test.consumer", sourceSet)
    }

    private fun BuildResult.assertExposureBy(vararg reports: Pair<String, String>) =
        assertReportedBy(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, *reports)
}

private const val EXEMPTION =
    "@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = \"The host ships the models.\")"

private const val EXPOSURE = """
    import test.model.ExternalModel

    /** Uses a model of the dependency. */
    public class Exposure {
        /** Returns a model. */
        public fun model(): ExternalModel = ExternalModel()
    }
"""

private const val EXEMPTED_EXPOSURE = """
    import test.model.ExternalModel

    /** Uses a model of the dependency. */
    public class Exposure {
        /** Returns a model. */
        $EXEMPTION
        public fun model(): ExternalModel = ExternalModel()
    }
"""

private const val JVM = ":compileKotlinJvm"
private const val JS = ":compileKotlinJs"
private const val WASM = ":compileKotlinWasmJs"
private const val COMMON_METADATA = ":compileCommonMainKotlinMetadata"
private const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"
private const val JVM_JS_AND_WASM_TARGETS = "jvm()\njs { nodejs() }\nwasmJs { nodejs() }"
