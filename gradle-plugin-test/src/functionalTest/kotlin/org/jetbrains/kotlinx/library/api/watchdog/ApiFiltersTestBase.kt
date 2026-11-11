@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.testkit.runner.BuildResult
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN

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

    protected fun kotlinAbiValidation(filters: String): String =
        """
            kotlin {
                @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
                abiValidation {
                    filters {
                        ${filters.trimIndent().prependIndent("                        ").trimStart()}
                    }
                }
            }
        """

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
