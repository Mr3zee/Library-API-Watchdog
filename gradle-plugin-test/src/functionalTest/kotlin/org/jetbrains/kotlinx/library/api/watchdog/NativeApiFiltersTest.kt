@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.junit.Test

/**
 * A Native compile task reads the compiler plugin options when Gradle configures the task, not when
 * it runs. `linuxX64` compiles on every host.
 */
class NativeApiFiltersTest : ApiFiltersTestBase() {
    @Test
    fun `Kotlin ABI validation exclusion filters apply`() {
        val project = kmpProject(targets = NATIVE_TARGET) {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
        }

        project.build(COMPILE_NATIVE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Kotlin ABI validation inclusion filters apply`() {
        val project = kmpProject(targets = NATIVE_TARGET) {
            buildScript(kotlinAbiValidation(KGP_INCLUSION_FILTERS))
            inclusionSources()
        }

        project.build(COMPILE_NATIVE).assertOnlyIncludedIsReported()
    }

    @Test
    fun `Binary Compatibility Validator exclusion sets apply`() {
        val project = kmpProject(targets = NATIVE_TARGET) {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }

        project.build(COMPILE_NATIVE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `filters work with the configuration cache`() {
        val project = kmpProject(targets = NATIVE_TARGET) {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
        }
        val arguments = arrayOf(COMPILE_NATIVE, "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")
        project.build(*arguments, "--rerun-tasks").apply {
            assertOutputHas("Configuration cache entry reused.")
            assertOnlyWatchedIsReported()
        }
    }

    @Test
    fun `the watchdog doesn't activate the Kotlin ABI validation`() {
        val project = kmpProject(targets = NATIVE_TARGET) {
            buildScript(
                """
                    tasks.register("printAbiValidationTasks") {
                        mustRunAfter("$COMPILE_NATIVE")
                        val exists = provider { "checkKotlinAbi" in project.tasks.names }
                        doLast { println("checkKotlinAbi exists: " + exists.get()) }
                    }
                """
            )
            apiWatchdog { severityOfEveryCheck(Severity.WARNING) }
            exclusionSources()
        }

        val result = project.build(COMPILE_NATIVE, "printAbiValidationTasks")

        result.assertOutputHas("checkKotlinAbi exists: false")
    }

    @Test
    fun `filters from an afterEvaluate block apply`() {
        val project = kmpProject(targets = NATIVE_TARGET) {
            buildScript(
                """
                    afterEvaluate {
                        ${kotlinAbiValidation(KGP_EXCLUSION_FILTERS).trimIndent().prependIndent("                        ").trimStart()}
                    }
                """
            )
            exclusionSources()
        }

        project.build(COMPILE_NATIVE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Binary Compatibility Validator sets from an afterEvaluate block apply`() {
        val project = kmpProject(targets = NATIVE_TARGET) {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(
                """
                    afterEvaluate {
                        ${apiValidation(bcvExclusionSets).prependIndent("                        ").trimStart()}
                    }
                """
            )
            exclusionSources()
        }

        project.build(COMPILE_NATIVE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `without filters every declaration is checked`() {
        val result = kmpProject(targets = NATIVE_TARGET) { exclusionSources() }.buildAndFail(COMPILE_NATIVE)

        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        for (name in excludedNames) result.assertOutputHas("`$name`")
    }

    private companion object {
        const val NATIVE_TARGET = "linuxX64()"
        const val COMPILE_NATIVE = "compileKotlinLinuxX64"
    }
}
