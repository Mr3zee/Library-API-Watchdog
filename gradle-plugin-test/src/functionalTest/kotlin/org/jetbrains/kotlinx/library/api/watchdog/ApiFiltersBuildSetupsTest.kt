@file:Suppress("FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API
import org.junit.Test

/** The API filters with project isolation, in included builds, in the reports, and in multiplatform compilations. */
class ApiFiltersBuildSetupsTest : ApiFiltersTestBase() {
    @Test
    fun `Kotlin ABI validation filters of a subproject apply with project isolation`() {
        val project = jvmProject {
            plugins()
            subproject("lib") {
                buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
                exclusionSources()
            }
        }
        val arguments = arrayOf(":lib:$COMPILE", *PROJECT_ISOLATION)

        project.build(*arguments).apply {
            assertOutputHas("Configuration cache entry stored.")
            assertOnlyWatchedIsReported()
        }
        project.build(*arguments, "--rerun-tasks").apply {
            assertOutputHas("Configuration cache entry reused.")
            assertOnlyWatchedIsReported()
        }
    }

    @Test
    fun `Binary Compatibility Validator sets of a subproject that applies the plugin apply with project isolation`() {
        val project = jvmProject {
            plugins()
            subproject("lib") {
                withPlugins(binaryCompatibilityValidatorPlugin)
                buildScript(apiValidation(bcvExclusionSets))
                exclusionSources()
            }
        }

        project.build(":lib:$COMPILE", *PROJECT_ISOLATION).assertOnlyWatchedIsReported()
    }

    @Test
    fun `a multiplatform subproject without a validation tool works with project isolation`() {
        val project = kmpProject {
            plugins()
            subproject("lib") { source("/** A stable public type. */\npublic class StableApi", "StableApi") }
        }

        project.build(":lib:compileKotlinJvm", *PROJECT_ISOLATION).assertOutputHas("BUILD SUCCESSFUL")
    }

    @Test
    fun `Kotlin ABI validation filters apply in an included build`() {
        val project = includedBuildProject {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
        }

        project.build(":included-lib:$COMPILE").assertOnlyWatchedIsReported()
    }

    @Test
    fun `Binary Compatibility Validator sets apply in an included build`() {
        val project = includedBuildProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
        }

        project.build(":included-lib:$COMPILE").assertOnlyWatchedIsReported()
    }

    @Test
    fun `generateApiWatchdogReport leaves out excluded declarations and reports an exposed excluded type`() {
        val project = jvmProject {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
            exposingSource()
        }

        project.build(REPORT_TASK)

        project.assertReportsLeaveOutExcludedDeclarations()
        val html = project.file("build/reports/api-watchdog/report.html").readText()
        assertReportedElement(html, "InternalByPackage", "error")
        assertContains(html, "data-path=\"src/main/kotlin/test/api/Exposing.kt\"")
        val sarif = project.file("build/reports/api-watchdog/report.sarif").readText()
        assertContains(sarif, "\"ruleId\": \"${PUBLIC_TYPE_WITH_INTERNAL_API.name}\"")
        assertContains(sarif, "\"uri\": \"src/main/kotlin/test/api/Exposing.kt\"")
    }

    @Test
    fun `exemptExistingApi with Kotlin ABI validation filters exempts only the watched declarations`() {
        val project = jvmProject {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
            exposingSource()
        }

        project.assertExemptExistingApiSkipsExcludedDeclarations()
    }

    @Test
    fun `exemptExistingApi with Binary Compatibility Validator sets exempts only the watched declarations`() {
        val project = jvmProject {
            withPlugins(binaryCompatibilityValidatorPlugin)
            buildScript(apiValidation(bcvExclusionSets))
            exclusionSources()
            exposingSource()
        }

        project.assertExemptExistingApiSkipsExcludedDeclarations()
    }

    @Test
    fun `Kotlin ABI validation filters apply to the multiplatform JVM, JS, Wasm, and metadata compilations`() {
        val project = kmpProject(targets = "jvm()\njs { nodejs() }\nwasmJs { nodejs() }") {
            buildScript(kotlinAbiValidation(KGP_EXCLUSION_FILTERS))
            exclusionSources()
        }

        for (task in listOf("compileKotlinJvm", "compileKotlinJs", "compileKotlinWasmJs", "compileCommonMainKotlinMetadata")) {
            project.build(task).assertOnlyWatchedIsReported()
        }
    }

    @Test
    fun `Kotlin ABI validation inclusion filters apply to the multiplatform JS, Wasm, and metadata compilations`() {
        val project = kmpProject(targets = "jvm()\njs { nodejs() }\nwasmJs { nodejs() }") {
            buildScript(kotlinAbiValidation(KGP_INCLUSION_FILTERS))
            inclusionSources()
        }

        for (task in listOf("compileKotlinJs", "compileKotlinWasmJs", "compileCommonMainKotlinMetadata")) {
            project.build(task).assertOnlyIncludedIsReported()
        }
    }

    /** A root build without plugins that includes a build of [block] with the name `included-lib`. */
    private fun includedBuildProject(block: ProjectSpec.() -> Unit): WatchdogBuild {
        val included = jvmProject(block)
        val project = jvmProject { plugins() }
        included.rootDir.copyRecursively(project.file("components/lib"))
        project.file("settings.gradle.kts").appendText("\nincludeBuild(\"components/lib\") { name = \"included-lib\" }\n")
        return project
    }

    /** A watched function that exposes an excluded type of [exclusionSources]. */
    private fun ModuleSpec.exposingSource() {
        kotlinFile(
            "Exposing",
            text = """
                @file:JvmName("Exposing")

                package test.api

                import test.internal.InternalByPackage

                /** Exposes an excluded type. */
                public fun exposeInternal(): InternalByPackage = InternalByPackage()
            """.trimIndent(),
        )
    }

    private fun WatchdogBuild.assertReportsLeaveOutExcludedDeclarations() {
        val html = file("build/reports/api-watchdog/report.html").readText()
        val highlighted = highlightedElements(html)
        assertTrue("Watched" in highlighted, "Expected a highlight on 'Watched', found $highlighted")
        for (name in excludedNames - "InternalByPackage") assertFalse(name in highlighted, "Unexpected highlight on '$name' in $highlighted")
        assertFalse(html.contains("data-path=\"src/main/kotlin/test/internal/"), html)

        val sarif = file("build/reports/api-watchdog/report.sarif").readText()
        assertFalse(sarif.contains("\"uri\": \"src/main/kotlin/test/internal/"), sarif)
        assertFalse(sarif.contains("\"ruleId\": \"${OPEN_API_WITHOUT_SUBCLASS_OPT_IN.name}\""), sarif)
        for (name in excludedNames - "InternalByPackage") assertFalse(sarif.contains(name), "Unexpected '$name' in $sarif")
    }

    private fun WatchdogBuild.assertExemptExistingApiSkipsExcludedDeclarations() {
        val excludedFiles = listOf("internal/Internal.kt", "internal/deep/Deep.kt").associateWith {
            file("src/main/kotlin/test/$it").readText()
        }

        build("exemptExistingApi")

        excludedFiles.forEach { (path, text) -> assertEquals(text, file("src/main/kotlin/test/$path").readText(), path) }
        val api = file("src/main/kotlin/test/api/Api.kt").readText()
        assertFalse(api.contains("@Intentionally"), api)
        assertContains(
            file("src/main/kotlin/test/api/Exposing.kt").readText(),
            "@IntentionallyExposedInternalApiForBackwardsCompatibility",
        )
        assertReportsLeaveOutExcludedDeclarations()
        val html = file("build/reports/api-watchdog/report.html").readText()
        assertReportGroup(html, "IntentionallyExposedInternalApiForBackwardsCompatibility", 1, PUBLIC_TYPE_WITH_INTERNAL_API)
        build(COMPILE).assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, PUBLIC_TYPE_WITH_INTERNAL_API)
    }

    private fun highlightedElements(html: String): List<String> =
        Regex(
            """<pre[^>]*data-range-start="(\d+)" data-range-end="(\d+)"><code class="language-kotlin">(.*?)</code>""",
            RegexOption.DOT_MATCHES_ALL,
        ).findAll(html).map { match ->
            val (start, end, encoded) = match.destructured
            val code = encoded.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&")
            code.substring(start.toInt(), end.toInt())
        }.toList()

    private companion object {
        const val REPORT_TASK = "generateApiWatchdogReport"
        val PROJECT_ISOLATION = arrayOf("-Dorg.gradle.unsafe.isolated-projects=true", "--configuration-cache-problems=fail")
    }
}
