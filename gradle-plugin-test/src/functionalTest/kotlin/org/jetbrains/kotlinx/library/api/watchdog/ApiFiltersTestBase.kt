@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.testkit.runner.BuildResult
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.junit.Assume.assumeFalse

/**
 * An open class fails the build when the watchdog checks it, so a passing build shows that the
 * filters removed it.
 */
abstract class ApiFiltersTestBase {
    protected fun BuildResult.assertOnlyWatchedIsReported() {
        assertWarning("`Watched` doesn't have `KDoc`")
        assertWarning("`WatchedNested` doesn't have `KDoc`")
        for (name in excludedNames) assertOutputLacks("`$name`")
        assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    protected fun BuildResult.assertOnlyIncludedIsReported() {
        for (name in listOf("InApi", "IncludedClass", "IncludedByAnnotation")) {
            assertWarning("`$name` doesn't have `KDoc`")
        }
        assertOutputLacks("`NotIncluded`")
        assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    protected fun ModuleSpec.exclusionSources() {
        kotlinFile(
            "Markers",
            text = """
                package test.markers

                /** Hides a declaration. */
                public annotation class Hidden
            """.trimIndent(),
        )
        kotlinFile(
            "Internal",
            text = """
                package test.internal

                public open class InternalByPackage
            """.trimIndent(),
        )
        kotlinFile(
            "Deep",
            text = """
                package test.internal.deep

                public open class InternalInSubpackage
            """.trimIndent(),
        )
        kotlinFile(
            "Api",
            text = """
                package test.api

                import test.markers.Hidden

                @Hidden
                public open class HiddenByAnnotation

                public open class HiddenByName

                /** A watched class with an excluded nested class. */
                public class Container {
                    public open class HiddenNested

                    public class WatchedNested
                }

                public class Watched
            """.trimIndent(),
        )
    }

    protected fun ModuleSpec.inclusionSources() {
        kotlinFile(
            "Markers",
            text = """
                package test.markers

                public annotation class PublicApi
            """.trimIndent(),
        )
        kotlinFile(
            "Api",
            text = """
                package test.api

                public class InApi
            """.trimIndent(),
        )
        kotlinFile(
            "Other",
            text = """
                package test.other

                import test.markers.PublicApi

                public class IncludedClass

                @PublicApi
                public class IncludedByAnnotation

                public open class NotIncluded
            """.trimIndent(),
        )
    }

    protected fun ModuleSpec.facadeClassSources() {
        kotlinFile(
            "Utils",
            text = """
                package test.api

                public fun hiddenUtility() {}
            """.trimIndent(),
        )
        kotlinFile(
            "Api",
            text = """
                @file:JvmName("Api")

                package test.api

                public fun watchedFunction() {}
            """.trimIndent(),
        )
    }

    protected fun BuildResult.assertFacadeClassIsExcluded() {
        assertWarning("`watchedFunction` doesn't have `KDoc`")
        assertOutputLacks("`hiddenUtility`")
    }

    /**
     * The `abiValidation` block in the DSL of the Kotlin version under test. [filters] has the block
     * names of Kotlin 2.3.20 and later. Before Kotlin 2.4.0, [enabled] sets the `enabled` flag.
     */
    protected fun kotlinAbiValidation(filters: String, enabled: Boolean = true): String {
        val versionFilters = if (legacyAbiFilterNames) filters.replace(filterBlockName, "$1d") else filters
        val enabledFlag = if (legacyAbiValidationDsl && enabled) "enabled.set(true)" else ""
        return """
            kotlin {
                @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
                abiValidation {
                    $enabledFlag
                    filters {
                        ${versionFilters.trimIndent().prependIndent("                        ").trimStart()}
                    }
                }
            }
        """
    }

    protected fun assumeCheckTaskShowsActiveValidation() {
        assumeFalse("Kotlin 2.3.20 and 2.3.21 register `checkKotlinAbi` in every project", legacyAbiValidationDsl)
    }

    protected val bcvExclusionSets: String = """
        ignoredPackages.add("test.internal")
        ignoredClasses.addAll(listOf("test.api.HiddenByName", "test.api.Container\${'$'}HiddenNested"))
        nonPublicMarkers.add("test.markers.Hidden")
    """

    protected val bcvInclusionSets: String = """
        publicPackages.add("test.api")
        publicClasses.add("test.other.IncludedClass")
        publicMarkers.add("test.markers.PublicApi")
    """

    protected fun apiValidation(sets: String): String =
        "apiValidation {\n" + sets.trimIndent().prependIndent("    ") + "\n}"

    companion object {
        const val COMPILE: String = "compileKotlin"

        private val filterBlockName = Regex("""\b(exclude|include)(?=\s*\{)""")

        val excludedNames: List<String> =
            listOf("InternalByPackage", "InternalInSubpackage", "HiddenByAnnotation", "HiddenByName", "HiddenNested")

        const val KGP_EXCLUSION_FILTERS: String = """
            exclude {
                byNames.addAll("test.internal.**", "test.api.HiddenByName", "test.api.Container.HiddenNested")
                annotatedWith.add("test.markers.Hidden")
            }
        """

        const val KGP_INCLUSION_FILTERS: String = """
            include {
                byNames.addAll("test.api.**", "test.other.IncludedClass")
                annotatedWith.add("test.markers.PublicApi")
            }
        """
    }
}

/**
 * Kotlin 2.2 and 2.3 register `abiValidation` as an extension, and its `enabled` flag turns the
 * validation on. Kotlin 2.4.0 and later turn the validation on with a call of `abiValidation`.
 */
internal val legacyAbiValidationDsl: Boolean
    get() = TEST_COMPILER_VERSION.major == 2 && TEST_COMPILER_VERSION.minor < 4

/** The filter blocks have the names `excluded` and `included` before Kotlin 2.3.20. */
internal val legacyAbiFilterNames: Boolean
    get() = TEST_COMPILER_VERSION.major == 2 &&
        (TEST_COMPILER_VERSION.minor < 3 || TEST_COMPILER_VERSION.minor == 3 && TEST_COMPILER_VERSION.patch < 20)
