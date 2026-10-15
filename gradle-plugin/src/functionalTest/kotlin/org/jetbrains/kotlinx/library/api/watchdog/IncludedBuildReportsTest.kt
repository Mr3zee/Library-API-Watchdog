@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.junit.Test

class IncludedBuildReportsTest {
    @Test
    fun `aggregates selected included projects with distinct identities and root-relative paths`() {
        val included = jvmProject {
            buildScript("group = \"sample.included\"")
            source("/** An included root API. */\npublic open class IncludedRootApi", "Api")
            subproject("library") {
                buildScript("group = \"sample.included\"")
                source("/** An included API. */\npublic data class IncludedApi(public val value: Int)", "Api")
            }
            subproject("unused") {
                source("/** An unselected API. */\npublic open class UnusedApi", "UnusedApi")
            }
            subproject("tools") {
                plugins(Plugin("base"))
                buildScript("group = \"sample.included\"")
            }
        }
        included.file("settings.gradle.kts").appendText("\nrootProject.name = \"bundle\"\n")
        val project = aggregateProject {
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports(project(":library"))
                        apiWatchdogReports("sample.included:bundle")
                        apiWatchdogReports("sample.included:library")
                        apiWatchdogReports("sample.included:tools")
                    }
                """
            )
            subproject("library") {
                source("/** A local API. */\npublic open class LocalApi", "Api")
            }
        }
        included.rootDir.copyRecursively(project.file("components/external"))
        project.file("settings.gradle.kts").appendText(
            "\nincludeBuild(\"components/external\") { name = \"external-api\" }\n",
        )
        val sourcePaths = listOf(
            "library/src/main/kotlin/test/Api.kt",
            "components/external/src/main/kotlin/test/Api.kt",
            "components/external/library/src/main/kotlin/test/Api.kt",
        )
        val sources = sourcePaths.associateWith { project.file(it).readText() }
        val arguments = arrayOf("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        val result = project.build(*arguments)

        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        result.assertOutputHas("Configuration cache entry stored.")
        result.assertTaskOutcome(":library:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":external-api:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":external-api:library:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":external-api:unused:generateApiWatchdogReport", null)
        result.assertTaskOutcome(":external-api:tools:generateApiWatchdogReport", null)
        result.assertTaskOutcome(":external-api:unused:compileKotlin", null)
        listOf(":library", ":external-api", ":external-api:library").forEach { path ->
            result.assertTaskOutcome("$path:exemptExistingApi", null)
        }
        sources.forEach { (path, text) -> assertEquals(text, project.file(path).readText()) }
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 3 projects")
        assertContains(html, "Project <code>:library</code>")
        assertContains(html, "Project <code>:external-api</code>")
        assertContains(html, "Project <code>:external-api:library</code>")
        assertFalse(html.contains("UnusedApi"), html)
        assertReportedElement(html, "LocalApi", "error")
        assertReportedElement(html, "IncludedRootApi", "error")
        assertReportedElement(html, "IncludedApi", "error")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        sourcePaths.forEach { path ->
            assertContains(html, "data-path=\"$path\"")
            assertContains(sarif, "\"uri\": \"$path\"")
        }
        listOf(
            Triple("library", ":library", "library"),
            Triple("components/external", ":external-api", ""),
            Triple("components/external/library", ":external-api:library", "library"),
        ).forEach { (directory, identity, relativeDirectory) ->
            val data = project.file("$directory/build/reports/api-watchdog/report.data").readText()
            assertContains(data, "project\t$identity\n")
            assertContains(data, "directory\t$relativeDirectory\n")
            assertFalse(data.contains(project.rootDir.absolutePath), data)
            val standaloneHtml = project.file("$directory/build/reports/api-watchdog/report.html").readText()
            assertContains(standaloneHtml, "data-path=\"src/main/kotlin/test/Api.kt\"")
        }

        val cached = project.build(*arguments)
        cached.assertOutputHas("Configuration cache entry reused.")
        cached.assertTaskOutcome(":aggregateApiWatchdogReports", UP_TO_DATE)
        assertEquals(html, project.file("build/reports/api-watchdog/aggregate-report.html").readText())
        assertEquals(sarif, project.file("build/reports/api-watchdog/aggregate-report.sarif").readText())
    }

    @Test
    fun `aggregate source paths can refer to a sibling included build`() {
        val included = jvmProject {
            buildScript("group = \"sample.sibling\"")
            source("/** A sibling API. */\npublic open class SiblingApi", "Api")
        }
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.sibling:the-project\") }")
        }
        val includedPath = included.rootDir.relativeTo(project.rootDir).invariantSeparatorsPath
        project.file("settings.gradle.kts").appendText(
            "\nincludeBuild(\"$includedPath\") { name = \"sibling-api\" }\n",
        )

        project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        val path = "$includedPath/src/main/kotlin/test/Api.kt"
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Project <code>:sibling-api</code>")
        assertContains(html, "data-path=\"$path\"")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"$path\"")
        assertReportedElement(html, "SiblingApi", "error")
    }

    @Test
    fun `aggregates reports from a nested included build`() {
        val nested = jvmProject {
            buildScript("group = \"sample.nested\"")
            source("/** A nested API. */\npublic open class NestedApi", "Api")
        }
        val included = jvmProject { plugins() }
        nested.rootDir.copyRecursively(included.file("nested"))
        included.file("settings.gradle.kts").appendText("\nincludeBuild(\"nested\") { name = \"nested-api\" }\n")
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.nested:the-project\") }")
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"outer-api\" }\n")

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":outer-api:nested-api:generateApiWatchdogReport", SUCCESS)
        val path = "components/nested/src/main/kotlin/test/Api.kt"
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Project <code>:outer-api:nested-api</code>")
        assertContains(html, "data-path=\"$path\"")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"$path\"")
        assertReportedElement(html, "NestedApi", "error")
    }

    @Test
    fun `projects with the same names in different included builds remain distinct`() {
        val project = aggregateProject {
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports("sample.first:bundle")
                        apiWatchdogReports("sample.first:library")
                        apiWatchdogReports("sample.second:bundle")
                        apiWatchdogReports("sample.second:library")
                    }
                """
            )
        }
        listOf("first", "second").forEach { name ->
            val included = jvmProject {
                buildScript("group = \"sample.$name\"")
                source("/** A root API. */\npublic open class RootApi", "Api")
                subproject("library") {
                    buildScript("group = \"sample.$name\"")
                    source("/** A library API. */\npublic open class LibraryApi", "Api")
                }
            }
            included.file("settings.gradle.kts").appendText("\nrootProject.name = \"bundle\"\n")
            included.rootDir.copyRecursively(project.file("components/$name"))
        }
        project.file("settings.gradle.kts").appendText(
            """

                includeBuild("components/first")
                includeBuild("components/second") { name = "second-api" }
            """.trimIndent(),
        )

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(html, "Aggregate report for 4 projects")
        listOf("first" to ":first", "second" to ":second-api").forEach { (directory, identity) ->
            listOf("", "/library").forEach { suffix ->
                val projectPath = identity + suffix.replace('/', ':')
                result.assertTaskOutcome("$projectPath:generateApiWatchdogReport", SUCCESS)
                assertContains(html, "Project <code>$projectPath</code>")
                val path = "components/$directory$suffix/src/main/kotlin/test/Api.kt"
                assertContains(html, "data-path=\"$path\"")
                assertContains(sarif, "\"uri\": \"$path\"")
            }
        }
        assertEquals(4, Regex("\"ruleId\":").findAll(sarif).count())
    }

    @Test
    fun `dependency aliases for the same included project produce one report`() {
        val included = jvmProject {
            buildScript("group = \"sample.included\"")
            source("/** An included API. */\npublic open class IncludedApi", "Api")
        }
        val project = aggregateProject {
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports("sample.included:the-project")
                        apiWatchdogReports("sample.included:the-project")
                        apiWatchdogReports("sample.alias:first")
                        apiWatchdogReports("sample.alias:second")
                    }
                """
            )
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText(
            """

                includeBuild("components") {
                    name = "included-api"
                    dependencySubstitution {
                        substitute(module("sample.alias:first")).using(project(":"))
                        substitute(module("sample.alias:second")).using(project(":"))
                    }
                }
            """.trimIndent(),
        )

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":included-api:generateApiWatchdogReport", SUCCESS)
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 1 project")
        assertReportGroup(html, "IntentionallyOpen", 1, Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertEquals(1, Regex("\"ruleId\":").findAll(sarif).count())
    }

    @Test
    fun `uses custom project directories and encodes special characters in SARIF paths`() {
        val included = jvmProject {
            plugins()
            subproject("library") {
                buildScript("group = \"sample.included\"")
                source("/** An included API. */\npublic open class IncludedApi", "Api")
            }
        }
        included.file("library").copyRecursively(included.file("modules/api #1"))
        included.file("library").deleteRecursively()
        included.file("settings.gradle.kts").appendText(
            "\nproject(\":library\").projectDir = file(\"modules/api #1\")\n",
        )
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.included:library\") }")
        }
        included.rootDir.copyRecursively(project.file("components/external #1"))
        project.file("settings.gradle.kts").appendText(
            "\nincludeBuild(\"components/external #1\") { name = \"included-api\" }\n",
        )

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":included-api:library:generateApiWatchdogReport", SUCCESS)
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Project <code>:included-api:library</code>")
        assertContains(html, "data-path=\"components/external #1/modules/api #1/src/main/kotlin/test/Api.kt\"")
        assertReportedElement(html, "IncludedApi", "error")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"components/external%20%231/modules/api%20%231/src/main/kotlin/test/Api.kt\"")
    }

    @Test
    fun `normalizes paths for included projects outside their own build root`() {
        val included = jvmProject {
            plugins()
            subproject("library") {
                buildScript("group = \"sample.included\"")
                source("/** An included API. */\npublic open class IncludedApi", "Api")
            }
        }
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.included:library\") }")
        }
        included.rootDir.copyRecursively(project.file("components/external"))
        project.file("components/external/library").copyRecursively(project.file("components/shared-api"))
        project.file("components/external/library").deleteRecursively()
        project.file("components/external/settings.gradle.kts").appendText(
            "\nproject(\":library\").projectDir = file(\"../shared-api\")\n",
        )
        project.file("settings.gradle.kts").appendText(
            "\nincludeBuild(\"components/external\") { name = \"included-api\" }\n",
        )

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":included-api:library:generateApiWatchdogReport", SUCCESS)
        val path = "components/shared-api/src/main/kotlin/test/Api.kt"
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Project <code>:included-api:library</code>")
        assertContains(html, "data-path=\"$path\"")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"$path\"")
        assertFalse(sarif.contains("external/../"), sarif)
    }

    @Test
    fun `an unselected included build produces an empty aggregate without compilation`() {
        val included = jvmProject {
            source("/** An unselected API. */\npublic open class UnselectedApi", "Api")
        }
        val project = aggregateProject {}
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText(
            "\nincludeBuild(\"components\") { name = \"unselected-api\" }\n",
        )

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        result.assertTaskOutcome(":unselected-api:compileKotlin", null)
        result.assertTaskOutcome(":unselected-api:generateApiWatchdogReport", null)
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 0 projects")
        assertFalse(html.contains("UnselectedApi"), html)
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"results\": []")
    }

    @Test
    fun `selected included projects without the watchdog produce an empty aggregate`() {
        val included = jvmProject {
            plugins(Plugin("base"))
            buildScript("group = \"sample.tools\"")
            subproject("kotlin") {
                plugins(Plugin("org.jetbrains.kotlin.jvm"))
                buildScript("group = \"sample.tools\"")
                source("/** A tool API. */\npublic open class ToolApi", "Api")
            }
        }
        val project = aggregateProject {
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports("sample.tools:the-project")
                        apiWatchdogReports("sample.tools:kotlin")
                    }
                """
            )
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"tools\" }\n")

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        result.assertTaskOutcome(":tools:kotlin:compileKotlin", null)
        result.assertTaskOutcome(":tools:generateApiWatchdogReport", null)
        result.assertTaskOutcome(":tools:kotlin:generateApiWatchdogReport", null)
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 0 projects")
        assertFalse(html.contains("ToolApi"), html)
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"results\": []")
    }

    @Test
    fun `includes projects with no sources no findings or only applied exemptions`() {
        val included = jvmProject {
            buildScript("group = \"sample.empty\"")
            subproject("clean") {
                buildScript("group = \"sample.empty\"")
                source("/** A stable API. */\npublic class StableApi", "Api")
            }
            subproject("exempted") {
                buildScript("group = \"sample.empty\"")
                source("/** An exempted API. */\n@IntentionallyOpen\npublic open class ExemptedApi", "Api")
            }
        }
        val project = aggregateProject {
            buildScript(
                """
                    dependencies {
                        apiWatchdogReports("sample.empty:the-project")
                        apiWatchdogReports("sample.empty:clean")
                        apiWatchdogReports("sample.empty:exempted")
                    }
                """
            )
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"included-api\" }\n")
        val source = project.file("components/exempted/src/main/kotlin/test/Api.kt")
        val original = source.readText()

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        listOf(":included-api", ":included-api:clean", ":included-api:exempted").forEach { path ->
            result.assertTaskOutcome("$path:generateApiWatchdogReport", SUCCESS)
            result.assertTaskOutcome("$path:exemptExistingApi", null)
        }
        assertEquals(original, source.readText())
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 3 projects")
        assertContains(html, "No diagnostics without an exemption.")
        assertContains(html, "Project <code>:included-api:exempted</code>")
        assertContains(html, "data-path=\"components/exempted/src/main/kotlin/test/Api.kt\"")
        assertReportGroup(html, "IntentionallyOpen", 1)
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"results\": []")
    }

    @Test
    fun `aggregation works when included reports are disabled and aggregate formats are customized`() {
        val included = jvmProject {
            buildScript("group = \"sample.included\"")
            apiWatchdog { reports { html = false; sarif = false } }
            source("/** An included API. */\npublic open class IncludedApi", "Api")
        }
        val project = aggregateProject {
            buildScript(
                """
                    dependencies { apiWatchdogReports("sample.included:the-project") }
                    tasks.aggregateApiWatchdogReports {
                        reports {
                            html = false
                            sarifFile = layout.buildDirectory.file("scan/included.sarif")
                        }
                    }
                """
            )
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"included-api\" }\n")

        val result = project.build("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":included-api:generateApiWatchdogReport", SUCCESS)
        assertFalse(project.file("components/build/reports/api-watchdog/report.html").exists())
        assertFalse(project.file("components/build/reports/api-watchdog/report.sarif").exists())
        assertFalse(project.file("build/reports/api-watchdog/aggregate-report.html").exists())
        assertFalse(project.file("build/reports/api-watchdog/aggregate-report.sarif").exists())
        val sarif = project.file("build/scan/included.sarif").readText()
        assertContains(sarif, "\"ruleId\": \"OPEN_API_WITHOUT_SUBCLASS_OPT_IN\"")
        assertContains(sarif, "\"uri\": \"components/src/main/kotlin/test/Api.kt\"")
    }

    @Test
    fun `aggregates multiplatform findings once across included target compilations with the configuration cache`() {
        val included = kmpProject("jvm()\njs { nodejs() }") {
            buildScript("group = \"sample.multiplatform\"")
            source("/** A common API. */\npublic open class CommonApi", "Api")
        }
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.multiplatform:the-project\") }")
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"included-api\" }\n")
        val arguments = arrayOf(
            "aggregateApiWatchdogReports",
            "--configuration-cache",
            "--configuration-cache-problems=fail",
            "-Dorg.gradle.configuration-cache.read-only=false",
        )

        val result = project.build(*arguments)

        result.assertTaskOutcome(":included-api:compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":included-api:compileKotlinJs", SUCCESS)
        result.assertTaskOutcome(":included-api:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":included-api:compileTestKotlinJvm", null)
        result.assertTaskOutcome(":included-api:compileTestKotlinJs", null)
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 1 project")
        assertReportGroup(html, "IntentionallyOpen", 1, Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
        assertReportedElement(html, "CommonApi", "error")
        val path = "components/src/commonMain/kotlin/test/Api.kt"
        assertContains(html, "data-path=\"$path\"")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"$path\"")
        assertEquals(1, Regex("\"ruleId\":").findAll(sarif).count())
        project.build(*arguments).assertOutputHas("Configuration cache entry reused.")
    }

    @Test
    fun `aggregates included multiplatform JVM reports with project isolation`() {
        val included = kmpProject {
            buildScript("group = \"sample.multiplatform\"")
            source("/** A common API. */\npublic open class CommonApi", "Api")
        }
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.multiplatform:the-project\") }")
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"included-api\" }\n")
        val arguments = arrayOf("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        val result = project.build(*arguments)

        result.assertOutputHas("Configuration cache entry stored.")
        result.assertTaskOutcome(":included-api:compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":included-api:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Aggregate report for 1 project")
        assertContains(html, "Project <code>:included-api</code>")
        assertReportedElement(html, "CommonApi", "error")
        val path = "components/src/commonMain/kotlin/test/Api.kt"
        assertContains(html, "data-path=\"$path\"")
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"$path\"")
        assertEquals(1, Regex("\"ruleId\":").findAll(sarif).count())

        val cached = project.build(*arguments)
        cached.assertOutputHas("Configuration cache entry reused.")
        cached.assertTaskOutcome(":aggregateApiWatchdogReports", UP_TO_DATE)
    }

    @Test
    fun `compilation failures in a selected included project fail aggregation`() {
        val included = jvmProject {
            buildScript("group = \"sample.broken\"")
            source("/** A broken API. */\npublic class BrokenApi : MissingType", "Api")
        }
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.broken:the-project\") }")
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"included-api\" }\n")

        val result = project.buildAndFail("aggregateApiWatchdogReports", *PROJECT_ISOLATION)

        result.assertTaskOutcome(":included-api:compileKotlin", FAILED)
        result.assertTaskOutcome(":included-api:generateApiWatchdogReport", null)
        result.assertTaskOutcome(":aggregateApiWatchdogReports", null)
        result.assertOutputHas("MissingType")
        assertFalse(project.file("build/reports/api-watchdog/aggregate-report.html").exists())
        assertFalse(project.file("build/reports/api-watchdog/aggregate-report.sarif").exists())
    }

    @Test
    fun `source changes in an included build refresh reports with the configuration cache`() {
        val included = jvmProject {
            buildScript("group = \"sample.included\"")
            source("/** An included API. */\npublic open class OriginalApi", "Api")
        }
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.included:the-project\") }")
        }
        included.rootDir.copyRecursively(project.file("components"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components\") { name = \"included-api\" }\n")
        val arguments = arrayOf("aggregateApiWatchdogReports", *PROJECT_ISOLATION)
        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")
        val originalHtml = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertReportedElement(originalHtml, "OriginalApi", "error")
        val source = project.file("components/src/main/kotlin/test/Api.kt")
        val updated = source.readText().replace("OriginalApi", "UpdatedApi")
        source.writeText(updated)

        val result = project.build(*arguments)

        result.assertOutputHas("Configuration cache entry reused.")
        result.assertTaskOutcome(":included-api:compileKotlin", SUCCESS)
        result.assertTaskOutcome(":included-api:generateApiWatchdogReport", SUCCESS)
        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        assertEquals(updated, source.readText())
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertReportedElement(html, "UpdatedApi", "error")
        assertFalse(html.contains("OriginalApi"), html)
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"components/src/main/kotlin/test/Api.kt\"")
        assertEquals(1, Regex("\"ruleId\":").findAll(sarif).count())

        source.writeText(updated.replace("open class", "class"))
        val clean = project.build(*arguments)

        clean.assertOutputHas("Configuration cache entry reused.")
        clean.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        val cleanHtml = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(cleanHtml, "Aggregate report for 1 project")
        assertContains(cleanHtml, "No diagnostics without an exemption.")
        val cleanSarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(cleanSarif, "\"results\": []")
    }

    @Test
    fun `moving an included build refreshes paths even when its report data is unchanged`() {
        val included = jvmProject {
            buildScript("group = \"sample.included\"")
            source("/** An included API. */\npublic open class IncludedApi", "Api")
        }
        val project = aggregateProject {
            buildScript("dependencies { apiWatchdogReports(\"sample.included:the-project\") }")
        }
        included.rootDir.copyRecursively(project.file("original"))
        val settings = project.file("settings.gradle.kts")
        settings.appendText(
            """

                buildCache { local { directory = file("build-cache") } }
                includeBuild("original") { name = "included-api" }
            """.trimIndent(),
        )
        val arguments = arrayOf("aggregateApiWatchdogReports", "--build-cache", *PROJECT_ISOLATION)
        project.build(*arguments)
        val originalData = project.file("original/build/reports/api-watchdog/report.data").readText()
        assertContains(
            project.file("build/reports/api-watchdog/aggregate-report.html").readText(),
            "data-path=\"original/src/main/kotlin/test/Api.kt\"",
        )
        included.rootDir.copyRecursively(project.file("relocated"))
        settings.writeText(settings.readText().replace("includeBuild(\"original\")", "includeBuild(\"relocated\")"))

        val result = project.build(*arguments)

        result.assertTaskOutcome(":aggregateApiWatchdogReports", SUCCESS)
        assertEquals(originalData, project.file("relocated/build/reports/api-watchdog/report.data").readText())
        val html = project.file("build/reports/api-watchdog/aggregate-report.html").readText()
        assertContains(html, "Project <code>:included-api</code>")
        assertContains(html, "data-path=\"relocated/src/main/kotlin/test/Api.kt\"")
        assertFalse(html.contains("data-path=\"original/"), html)
        val sarif = project.file("build/reports/api-watchdog/aggregate-report.sarif").readText()
        assertContains(sarif, "\"uri\": \"relocated/src/main/kotlin/test/Api.kt\"")
        assertFalse(sarif.contains("\"uri\": \"original/"), sarif)
    }

    private fun aggregateProject(block: ProjectSpec.() -> Unit): WatchdogBuild = jvmProject {
        plugins(Plugin("org.jetbrains.kotlin.library.api-watchdog-report-aggregation", pluginUnderTestVersion))
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
