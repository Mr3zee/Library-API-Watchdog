@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KotlinPlugins
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.junit.Test

/** `exemptExistingApi` in a build with the report aggregation plugin writes the aggregate reports. */
class ExemptExistingApiAggregationTest {
    @Test
    fun `exempt task writes the aggregate report from its own data`() {
        val project = aggregateProject(watchdogInRoot = true) {
            source("/** A root API. */\npublic open class RootApi", "Api")
            subproject("library") {
                source("/** A library API. */\npublic data class LibraryApi(public val value: Int)", "Api")
            }
        }
        val arguments = arrayOf("exemptExistingApi", *PROJECT_ISOLATION)

        val result = project.build(*arguments)

        result.assertOutputHas("Configuration cache entry stored.")
        result.assertTaskOutcome(":exemptExistingApi", SUCCESS)
        result.assertTaskOutcome(":library:exemptExistingApi", SUCCESS)
        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        result.assertTaskOutcome(":generateApiWatchdogReport", null)
        result.assertTaskOutcome(":library:generateApiWatchdogReport", null)
        assertContains(project.file("src/main/kotlin/test/Api.kt").readText(), "@IntentionallyOpen")
        assertContains(project.file("library/src/main/kotlin/test/Api.kt").readText(), "@IntentionallyDataClass")
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 2 projects")
        assertContains(html, "No diagnostics without an exemption.")
        assertReportGroup(html, "IntentionallyOpen", 1)
        assertReportGroup(html, "IntentionallyDataClass", 1)
        assertContains(html, "data-path=\"library/src/main/kotlin/test/Api.kt\"")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"results\": []")

        val second = project.build(*arguments)

        second.assertOutputHas("Configuration cache entry reused.")
        second.assertTaskOutcome(":library:exemptExistingApi", SUCCESS)
        second.assertTaskOutcome(":library:generateApiWatchdogReport", null)
        second.assertOutputHas("No watchdog errors to exempt.")
        second.assertOutputHas("Aggregate API Watchdog report:")
    }

    @Test
    fun `exempt task writes the aggregate report when the root has no watchdog`() {
        val project = aggregateProject(watchdogInRoot = false) {
            subproject("library") {
                source("/** A library API. */\npublic open class LibraryApi", "Api")
            }
        }

        val result = project.build("exemptExistingApi", *PROJECT_ISOLATION)

        result.assertOutputHas("Configuration cache entry stored.")
        // The root task has no actions.
        result.assertTaskOutcome(":exemptExistingApi", UP_TO_DATE)
        result.assertTaskOutcome(":library:exemptExistingApi", SUCCESS)
        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        result.assertTaskOutcome(":library:generateApiWatchdogReport", null)
        assertContains(project.file("library/src/main/kotlin/test/Api.kt").readText(), "@IntentionallyOpen")
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 1 project")
        assertReportGroup(html, "IntentionallyOpen", 1)
    }

    @Test
    fun `exempt task leaves included builds unchanged and reports them`() {
        val included = jvmProject {
            buildScript("group = \"sample.included\"")
            source("/** An included API. */\npublic open class IncludedApi", "Api")
        }
        val project = aggregateProject(watchdogInRoot = false) {
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports("sample.included:the-project")
                    }
                """
            )
            subproject("library") {
                source("/** A library API. */\npublic open class LibraryApi", "Api")
            }
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"included-api\" }\n")
        val includedSource = project.file("components/src/main/kotlin/test/Api.kt")
        val original = includedSource.readText()

        val result = project.build("exemptExistingApi", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":library:exemptExistingApi", SUCCESS)
        result.assertTaskOutcome(":included-api:exemptExistingApi", null)
        result.assertTaskOutcome(":included-api:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        assertEquals(original, includedSource.readText())
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 2 projects")
        assertReportGroup(html, "IntentionallyOpen", 1)
        assertReportedElement(html, "IncludedApi", "error")
    }

    @Test
    fun `aggregate task alone uses the report task`() {
        val project = aggregateProject(watchdogInRoot = true) {
            subproject("library") {
                source("/** A library API. */\npublic open class LibraryApi", "Api")
            }
        }
        val source = project.file("library/src/main/kotlin/test/Api.kt")
        val original = source.readText()

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":library:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":library:exemptExistingApi", null)
        result.assertTaskOutcome(":exemptExistingApi", null)
        assertEquals(original, source.readText())
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertFalse(html.contains("No diagnostics without an exemption."), html)
        assertReportedElement(html, "LibraryApi", "error")
    }

    /** The root declares every project with the Watchdog. [watchdogInRoot] applies the Watchdog to the root too. */
    private fun aggregateProject(watchdogInRoot: Boolean, block: ProjectSpec.() -> Unit): WatchdogBuild = jvmProject {
        val aggregation = Plugin("org.jetbrains.kotlin.library.api-watchdog-report-aggregation", pluginUnderTestVersion)
        if (watchdogInRoot) {
            plugins(KotlinPlugins.jvm("$TEST_COMPILER_VERSION"), Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion), aggregation)
            buildScript("kotlin { explicitApi() }")
            buildScript("dependencies { allprojects.forEach { apiWatchdogReports(project(it.path)) } }")
        } else {
            plugins(aggregation)
            buildScript("dependencies { subprojects.forEach { apiWatchdogReports(project(it.path)) } }")
        }
        block()
    }

    private companion object {
        val PROJECT_ISOLATION = arrayOf(
            "-Dorg.gradle.unsafe.isolated-projects=true",
            "--configuration-cache-problems=fail",
            "-Dorg.gradle.configuration-cache.read-only=false",
        )
    }
}
