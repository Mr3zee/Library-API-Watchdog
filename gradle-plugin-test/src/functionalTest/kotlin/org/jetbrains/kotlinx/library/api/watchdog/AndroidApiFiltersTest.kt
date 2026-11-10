@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import org.jetbrains.kotlinx.library.api.watchdog.AgpVersion.AGP8
import org.jetbrains.kotlinx.library.api.watchdog.AgpVersion.AGP9
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.KmpAndroidTarget.LIBRARY_AGP8
import org.jetbrains.kotlinx.library.api.watchdog.KmpAndroidTarget.LIBRARY_AGP9
import org.junit.Test

/**
 * The API filters in Android compilations. The tests skip themselves without an Android SDK.
 *
 * - `kotlin-android` of AGP 8 and the built-in Kotlin of AGP 9 have the `abiValidation` block, and
 *   the Kotlin Gradle plugin registers `checkKotlinAbi` for both.
 * - The Binary Compatibility Validator plugin registers `apiCheck` for `kotlin-android` and for a
 *   multiplatform project, but not for the built-in Kotlin of AGP 9.
 */
class AndroidApiFiltersTest : ApiFiltersTestBase() {
    @Test
    fun `Kotlin ABI validation filters apply to an Android library`() = kotlinAbiValidationCase(AGP8)

    @Test
    fun `Kotlin ABI validation filters apply to an Android library with built-in Kotlin`() = kotlinAbiValidationCase(AGP9)

    private fun kotlinAbiValidationCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
        }

        project.build(ANDROID_COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Kotlin ABI validation inclusion filters apply to an Android library with built-in Kotlin`() {
        val project = androidLibrary(AGP9) {
            buildScript(kotlinAbiValidation(KGP_INCLUSION_FILTERS))
            inclusionSources()
        }

        project.build(ANDROID_COMPILE).assertOnlyIncludedIsReported()
    }

    @Test
    fun `Binary Compatibility Validator sets apply to an Android library`() {
        val project = androidLibrary(AGP8) {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }

        project.build(ANDROID_COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Binary Compatibility Validator sets don't apply with built-in Kotlin, because the plugin doesn't validate the project`() {
        val project = androidLibrary(AGP9) {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }

        val result = project.buildAndFail(ANDROID_COMPILE)

        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        for (name in excludedNames) result.assertOutputHas("`$name`")
    }

    @Test
    fun `Kotlin ABI validation filters apply to the multiplatform Android target`() = kmpKotlinAbiValidationCase(LIBRARY_AGP8)

    @Test
    fun `Kotlin ABI validation filters apply to the multiplatform Android target on AGP 9`() = kmpKotlinAbiValidationCase(LIBRARY_AGP9)

    private fun kmpKotlinAbiValidationCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
        }

        project.build(target.mainCompileTask).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Binary Compatibility Validator sets apply to the multiplatform Android target`() = kmpBcvCase(LIBRARY_AGP8)

    @Test
    fun `Binary Compatibility Validator sets apply to the multiplatform Android target on AGP 9`() = kmpBcvCase(LIBRARY_AGP9)

    private fun kmpBcvCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }

        project.build(target.mainCompileTask).assertOnlyWatchedIsReported()
    }

    private companion object {
        const val ANDROID_COMPILE = "compileDebugKotlin"
    }
}
