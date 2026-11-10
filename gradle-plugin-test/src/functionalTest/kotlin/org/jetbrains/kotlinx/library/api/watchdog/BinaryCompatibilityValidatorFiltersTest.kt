@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API
import org.junit.Test

/**
 * The filters of the real Binary Compatibility Validator plugin. The plugin validates the project
 * that applies it and the projects below it, so most builds apply it to the root project.
 */
class BinaryCompatibilityValidatorFiltersTest : ApiFiltersTestBase() {
    @Test
    fun `exclusion sets apply`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }

        project.build(COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `inclusion sets apply`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvInclusionSets))
            inclusionSources()
        }

        project.build(COMPILE).assertOnlyIncludedIsReported()
    }

    @Test
    fun `an ignored package doesn't cover a package with a longer name`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation("""ignoredPackages.add("test.inter")"""))
            exclusionSources()
        }

        val result = project.buildAndFail(COMPILE)

        result.assertOutputHas("`InternalByPackage`")
        result.assertOutputHas("`InternalInSubpackage`")
    }

    @Test
    fun `the sets of the root project apply to a subproject`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            subproject("lib") { exclusionSources() }
        }

        project.build(":lib:$COMPILE").assertOnlyWatchedIsReported()
    }

    @Test
    fun `the sets of an intermediate project apply to the projects below it only`() {
        val project = jvmProject {
            subproject("libs") {
                withPlugins(binaryCompatibilityValidatorPlugin)
                buildScript(apiValidation(bcvExclusionSets))
            }
            subproject("libs:core") { exclusionSources() }
            subproject("other") { exclusionSources() }
        }

        project.build(":libs:core:$COMPILE").assertOnlyWatchedIsReported()
        project.buildAndFail(":other:$COMPILE").assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `a project of ignoredProjects doesn't get the sets`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets + "\nignoredProjects.add(\"lib\")"))
            subproject("lib") { exclusionSources() }
        }

        val result = project.buildAndFail(":lib:$COMPILE")

        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        for (name in excludedNames) result.assertOutputHas("`$name`")
    }

    @Test
    fun `the sets don't apply when validationDisabled is true`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets + "\nvalidationDisabled = true"))
            exclusionSources()
        }

        project.buildAndFail(COMPILE).assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `the sets are ignored when the switch is off`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            apiWatchdog { useAbiValidationFilters = false }
            exclusionSources()
        }

        project.buildAndFail(COMPILE).assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `the Kotlin ABI validation filters replace the sets`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            buildScript(kotlinAbiValidation("""exclude { byNames.add("test.internal.**") }"""))
            exclusionSources()
        }

        val result = project.buildAndFail(COMPILE)

        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        for (name in listOf("HiddenByAnnotation", "HiddenByName", "HiddenNested")) result.assertOutputHas("`$name`")
        for (name in listOf("InternalByPackage", "InternalInSubpackage")) result.assertOutputLacks("`$name`")
    }

    @Test
    fun `an exclusion set has priority over an inclusion set`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets + "\npublicPackages.add(\"test\")"))
            exclusionSources()
        }

        project.build(COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `a type of ignoredPackages or with a nonPublicMarkers annotation is an exposed internal API type`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(
                apiValidation(
                    """
                        ignoredPackages.add("test.internal")
                        nonPublicMarkers.add("test.markers.Markers\${'$'}Hidden")
                    """
                ),
            )
            kotlinFile(
                "Markers",
                text = """
                    package test.markers

                    /** Holds the marker. */
                    public object Markers {
                        /** Hides a declaration. */
                        public annotation class Hidden
                    }
                """.trimIndent(),
            )
            kotlinFile(
                "Internal",
                text = """
                    package test.internal

                    public open class InternalType
                """.trimIndent(),
            )
            kotlinFile(
                "Api",
                text = """
                    package test.api

                    import test.internal.InternalType
                    import test.markers.Markers

                    @Markers.Hidden
                    public open class HiddenType

                    /** Exposes a type of an ignored package. */
                    public fun exposesIgnored(value: InternalType) {}

                    /** Exposes a type with a marker. */
                    public fun exposesMarked(value: HiddenType) {}
                """.trimIndent(),
            )
        }

        val result = project.buildAndFail(COMPILE)

        result.assertReportedIn(PUBLIC_TYPE_WITH_INTERNAL_API, "Api.kt", "Api.kt")
        result.assertError(
            "publicly exposes `test.internal.InternalType`, but the API filter rule `test.internal.**` " +
                    "excludes that type from the API checks.",
        )
        result.assertError("publicly exposes `test.api.HiddenType`, but that type is marked as internal API with `@Hidden`.")
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `a type outside the inclusion sets isn't an exposed internal API type`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation("""publicPackages.add("test.api")"""))
            kotlinFile(
                "Other",
                text = """
                    package test.other

                    public open class NotIncluded
                """.trimIndent(),
            )
            kotlinFile(
                "Api",
                text = """
                    @file:JvmName("Api")

                    package test.api

                    import test.other.NotIncluded

                    /** Exposes a type outside the inclusion sets. */
                    public fun exposes(value: NotIncluded) {}
                """.trimIndent(),
            )
        }

        project.build(COMPILE).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `the sets apply to the compilations of a multiplatform project`() {
        val project = kmpProject(targets = "jvm()\njs { nodejs() }") {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }

        for (task in listOf("compileKotlinJvm", "compileKotlinJs", "compileCommonMainKotlinMetadata")) {
            project.build(task).assertOnlyWatchedIsReported()
        }
    }

    @Test
    fun `inclusion sets apply to the non-JVM compilations of a multiplatform project`() {
        val project = kmpProject(targets = "jvm()\njs { nodejs() }") {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvInclusionSets))
            inclusionSources()
        }

        for (task in listOf("compileKotlinJs", "compileCommonMainKotlinMetadata")) {
            project.build(task).assertOnlyIncludedIsReported()
        }
    }

    @Test
    fun `the sets work with the configuration cache`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }
        val arguments = arrayOf(COMPILE, "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")
        project.build(*arguments, "--rerun-tasks").apply {
            assertOutputHas("Configuration cache entry reused.")
            assertOnlyWatchedIsReported()
        }
    }

    @Test
    fun `the ABI validation suggestion is silent in a subproject that the root plugin validates`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            subproject("lib")
        }

        project.build(":lib:help").assertOutputLacks("Project ':lib' applies library-api-watchdog but doesn't enable binary")
    }

    @Test
    fun `the ABI validation suggestion stays in a project of ignoredProjects`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation("""ignoredProjects.add("lib")"""))
            subproject("lib")
        }

        project.build(":lib:help").assertOutputHas("Project ':lib' applies library-api-watchdog but doesn't enable binary")
    }
}
