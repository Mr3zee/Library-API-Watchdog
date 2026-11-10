@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.REDUNDANT_EXEMPTION
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.UNDOCUMENTED_PUBLIC_API
import org.junit.Test

/** The `filters` block of `apiWatchdog` and the filters of the ABI validation of the Kotlin Gradle plugin. */
class ApiFiltersTest : ApiFiltersTestBase() {
    @Test
    fun `without filters every declaration is checked`() {
        val result = jvmProject { exclusionSources() }.buildAndFail(COMPILE)

        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        for (name in excludedNames) result.assertOutputHas("`$name`")
    }

    @Test
    fun `an exclusion filter has priority over an inclusion filter`() {
        val project = jvmProject {
            buildScript(
                kotlinAbiValidation(
                    """
                        include { byNames.add("test.**") }
                        exclude {
                            byNames.addAll("test.internal.**", "test.api.HiddenByName", "test.api.Container.HiddenNested")
                            annotatedWith.add("test.markers.Hidden")
                        }
                    """
                )
            )
            exclusionSources()
        }

        project.build(COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Kotlin ABI validation exclusion filters apply`() {
        val project = jvmProject {
            buildScript(
                kotlinAbiValidation(
                    """
                        exclude {
                            byNames.addAll("test.internal.**", "test.api.HiddenByName", "test.api.Container.HiddenNested")
                            annotatedWith.add("test.markers.Hidden")
                        }
                    """
                )
            )
            exclusionSources()
        }

        project.build(COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Kotlin ABI validation inclusion filters apply`() {
        val project = jvmProject {
            buildScript(
                kotlinAbiValidation(
                    """
                        include {
                            byNames.addAll("test.api.**", "test.other.IncludedClass")
                            annotatedWith.add("test.markers.PublicApi")
                        }
                    """
                )
            )
            inclusionSources()
        }

        project.build(COMPILE).assertOnlyIncludedIsReported()
    }

    @Test
    fun `Kotlin ABI validation filters from an afterEvaluate block apply`() {
        val project = jvmProject {
            buildScript(
                """
                    afterEvaluate {
                        ${kotlinAbiValidation("""
                            exclude {
                                byNames.addAll("test.internal.**", "test.api.HiddenByName", "test.api.Container.HiddenNested")
                                annotatedWith.add("test.markers.Hidden")
                            }
                        """).trimIndent().prependIndent("                        ").trimStart()}
                    }
                """
            )
            exclusionSources()
        }

        project.build(COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `a type outside the inclusion rules isn't an exposed internal API type`() {
        val project = jvmProject {
            buildScript(kotlinAbiValidation("""include { byNames.add("test.api.**") }"""))
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

                    /** Exposes a type outside the inclusion rules. */
                    public fun exposes(value: NotIncluded) {}
                """.trimIndent(),
            )
        }

        project.build(COMPILE).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `ABI validation filters are ignored when the switch is off`() {
        val project = jvmProject {
            buildScript(kotlinAbiValidation("""exclude { byNames.add("test.**") }"""))
            apiWatchdog { useAbiValidationFilters = false }
            exclusionSources()
        }

        val result = project.buildAndFail(COMPILE)

        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        for (name in excludedNames) result.assertOutputHas("`$name`")
    }

    @Test
    fun `the watchdog doesn't activate the Kotlin ABI validation`() {
        val project = jvmProject {
            apiWatchdog { severityOfEveryCheck(Severity.WARNING) }
            buildScript(
                """
                    tasks.register("printAbiValidationTasks") {
                        mustRunAfter("compileKotlin")
                        doLast { println("checkKotlinAbi exists: " + ("checkKotlinAbi" in project.tasks.names)) }
                    }
                """
            )
            exclusionSources()
        }

        // The compilation evaluates the compiler plugin options, which read the filters.
        val result = project.build(COMPILE, "printAbiValidationTasks")

        result.assertOutputHas("checkKotlinAbi exists: false")
    }

    @Test
    fun `a changed Kotlin ABI validation filter reruns the compilation`() {
        val project = jvmProject {
            buildScript(
                kotlinAbiValidation(
                    """
                        exclude {
                            byNames.addAll("test.internal.**", "test.api.HiddenByName", "test.api.Container.HiddenNested")
                            annotatedWith.add("test.markers.Hidden")
                            if (providers.gradleProperty("excludeAll").isPresent) byNames.add("test.**")
                        }
                    """
                )
            )
            exclusionSources()
        }

        project.build(COMPILE).assertOnlyWatchedIsReported()
        project.build(COMPILE).assertTaskOutcome(":$COMPILE", TaskOutcome.UP_TO_DATE)

        val changed = project.build(COMPILE, "-PexcludeAll=true")
        changed.assertTaskOutcome(":$COMPILE", TaskOutcome.SUCCESS)
        changed.assertNoWatchdogDiagnostics()
    }

    @Test
    fun `Kotlin ABI validation filters work with the configuration cache`() {
        val project = jvmProject {
            buildScript(
                kotlinAbiValidation(
                    """
                        exclude {
                            byNames.addAll("test.internal.**", "test.api.HiddenByName", "test.api.Container.HiddenNested")
                            annotatedWith.add("test.markers.Hidden")
                        }
                    """
                )
            )
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
    fun `a watched declaration that exposes a type excluded by name is an error`() {
        val project = jvmProject {
            buildScript(kotlinAbiValidation("""exclude { byNames.add("test.internal.**") }"""))
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

                    /** Exposes an excluded type. */
                    public class Leaking(value: InternalType)
                """.trimIndent(),
            )
        }

        val result = project.buildAndFail(COMPILE)

        result.assertErrors(PUBLIC_TYPE_WITH_INTERNAL_API)
        result.assertError(
            "The parameter `value` publicly exposes `test.internal.InternalType`, but the API filter rule " +
                    "`test.internal.**` excludes that type from the API checks.",
        )
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun `an exemption on an excluded declaration is redundant`() {
        val project = jvmProject {
            buildScript(kotlinAbiValidation("""exclude { byNames.add("test.Hidden") }"""))
            source(
                """
                    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
                    public open class Hidden
                """,
            )
        }

        val result = project.build(COMPILE)

        result.assertWarnings(REDUNDANT_EXEMPTION)
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API)
    }

    @Test
    fun `exemptExistingApi leaves an excluded declaration as it is`() {
        val project = jvmProject {
            buildScript(kotlinAbiValidation("""exclude { byNames.add("test.internal.**") }"""))
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

                    /** A watched open class. */
                    public open class WatchedOpen
                """.trimIndent(),
            )
        }
        val internalFile = project.file("src/main/kotlin/test/internal/Internal.kt")
        val internalText = internalFile.readText()

        project.build("exemptExistingApi")

        assertEquals(internalText, internalFile.readText())
        assertContains(project.file("src/main/kotlin/test/api/Api.kt").readText(), "@IntentionallyOpen(")
        project.build(COMPILE).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `a filter that the compiler plugin can't read is ignored`() {
        val project = jvmProject {
            buildScript(
                kotlinAbiValidation(
                    """
                        exclude {
                            byNames.addAll("test.internal .**", "test.internal.**", "test.api.HiddenByName", "test.api.Container.HiddenNested")
                            annotatedWith.addAll("test.markers:Hidden", "test.markers.Hidden")
                        }
                    """
                )
            )
            exclusionSources()
        }

        project.build(COMPILE).assertOnlyWatchedIsReported()
    }

    @Test
    fun `Kotlin ABI validation filters give an expect declaration and its actual the same verdict`() {
        val project = kmpProject {
            buildScript(
                kotlinAbiValidation(
                    """
                        exclude { byNames.addAll("test.Excluded", "test.excludedFunction") }
                        include { byNames.addAll("test.Excluded", "test.excludedFunction", "test.Included", "test.includedFunction") }
                    """
                )
            )
            kotlinFile(
                "Common",
                sourceSet = "commonMain",
                text = """
                    package test

                    public expect open class Excluded() {
                        public fun member(flag: Boolean = false)
                    }

                    public expect fun excludedFunction(flag: Boolean = false)

                    public expect open class NotIncluded()

                    public expect fun notIncludedFunction(flag: Boolean = false)

                    public expect class Included()

                    /** Watched. */
                    public expect fun includedFunction(count: Int)
                """.trimIndent(),
            )
            kotlinFile(
                "Platform",
                sourceSet = "jvmMain",
                text = """
                    @file:JvmName("Platform")

                    package test

                    public actual open class Excluded {
                        public actual fun member(flag: Boolean) {}
                    }

                    public actual fun excludedFunction(flag: Boolean) {}

                    public actual open class NotIncluded

                    public actual fun notIncludedFunction(flag: Boolean) {}

                    public actual class Included

                    public actual fun includedFunction(count: Int) {}
                """.trimIndent(),
            )
        }

        val result = project.build("assemble")

        result.assertWarning("`Included` doesn't have `KDoc`")
        for (name in listOf("Excluded", "excludedFunction", "NotIncluded", "notIncludedFunction", "member")) {
            result.assertOutputLacks("`$name`")
        }
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }
}
