package org.jetbrains.kotlinx.library.api.watchdog.conventions

import groovy.json.JsonOutput
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiagnosticsGeneratorsTest {
    @get:Rule
    val temporaryDirectory = TemporaryFolder()

    private val project by lazy {
        ProjectBuilder.builder().withProjectDir(temporaryDirectory.newFolder("project")).build()
    }
    private var taskIndex = 0

    @Test
    fun `text lines preserve paragraph and code breaks`() {
        assertEquals(
            "First sentence. Second sentence.\n\nNext paragraph.\n    val x = 1\n    println(x)\nLast sentence.",
            listOf(
                "First sentence.", "Second sentence.", "", "Next paragraph.",
                "    val x = 1", "    println(x)", "Last sentence.",
            ).joinTextLines(),
        )
    }

    @Test
    fun `Kotlin string literals escape special characters`() {
        assertEquals(
            """\"quoted\" \${'$'}value \\path\r\n""",
            "\"quoted\" \$value \\path\r\n".escapedForKotlinString(),
        )
    }

    @Test
    fun `compiler messages convert links and quote parameterized templates`() {
        val source = generate<GenerateDiagnosticMessages>()
        assertContains(
            source,
            """
                private val warningByDefault: Set<String> = setOf(
                        "SAMPLE_WARNING",
                    )
            """.trimIndent(),
        )
        assertContains(source, """Author''s \"quoted\" `{0}` costs \${'$'}5. Guide (https://example.test/guide).""")
        assertContains(source, """\n\nUse " +""")
        assertContains(source, """`@IntentionallySample` twice: `@IntentionallySample`.\n""")
        assertContains(source, """println(\"\${'$'}value\")\nContinue " +""")
        assertContains(source, """with `@IntentionallyShared`.\n\nSee more: """)
        assertContains(source, """https://example.test/\${'$'}docs/checks/java-interop/sample-\${'$'}value""")
        assertContains(source, """Author's file costs \${'$'}2. Apply `@IntentionallyShared`.""")
    }

    @Test
    fun `compiler parameter values preserve shared and local text`() {
        val source = generate<GenerateDiagnosticMessages>()
        assertContains(source, """local {0}, '\${'$'}quoted' \\path""")
        assertContains(source, "shared {0}, don't change quotes")
        assertContains(source, """line one\r\nline two""")
        assertContains(source, "\"${"x".repeat(120)}\",")
        assertContains(source, """Read Guide (https://example.test/guide).\n\n    val value = \"\${'$'}name\"\nDone.""")
    }

    @Test
    fun `report descriptions retain markdown and collect distinct exemption annotations`() {
        val source = generate<GenerateCheckDescriptions> { toolVersion.set("1.0-\$version") }
        assertContains(source, """const val TOOL_VERSION: String = "1.0-\${'$'}version"""")
        assertContains(source, """const val DOCS_URL: String = "https://example.test/\${'$'}docs/"""")
        assertContains(source, """WatchdogCheckDescription("Check \"quoted\" \${'$'}title \\path",""")
        assertContains(source, """https://example.test/\${'$'}docs/checks/java-interop/sample-\${'$'}value""")
        assertContains(source, """Author's \"quoted\" `{0}` costs \${'$'}5. [Guide](https://example.test/guide).\n\n""")
        assertContains(source, """"IntentionallySample" to listOf("SAMPLE_WARNING"),""")
        assertContains(source, """"IntentionallyShared" to listOf("SAMPLE_WARNING", "SAMPLE_ERROR"),""")
    }

    @Test
    fun `functional diagnostics use the first paragraph and local parameter overrides`() {
        val source = generate<GenerateFunctionalTestDiagnostics>()
        assertContains(source, """docsUrl = "https://example.test/\${'$'}docs/checks/java-interop/sample-\${'$'}value",""")
        assertContains(
            source,
            """template = "Author's \"quoted\" `{0}` costs \${'$'}5. Guide (https://example.test/guide).",""",
        )
        assertContains(source, "javaInterop = true,")
        assertContains(source, "javaInterop = false,")
        assertContains(source, """"common" to "local {0}, '\${'$'}quoted' \\path",""")
        assertContains(source, """"common" to "shared {0}, don't change quotes",""")
        assertContains(source, """"controlCharacters" to "line one\r\nline two",""")
        assertFalse(source.contains("twice:"))
    }

    @Test
    fun `check name enums preserve diagnostic names in both visibility modes`() {
        val publicSource = generate<GenerateCheckNames>()
        val internalSource = generate<GenerateCheckNames> { internalVisibility.set(true) }
        assertContains(publicSource, "public enum class WatchdogChecks {")
        assertContains(internalSource, "internal enum class WatchdogChecks {")
        for (source in listOf(publicSource, internalSource)) {
            assertContains(source, "    SAMPLE_WARNING,")
            assertContains(source, "    SAMPLE_ERROR,")
        }
        assertFalse(internalSource.contains("WatchdogGradleExtension.ignore"))
    }

    @Test
    fun `empty metadata generates empty collections`() {
        val input = mapOf("docsBaseUrl" to BASE_URL, "diagnostics" to emptyList<Any>())
        val messages = generate<GenerateDiagnosticMessages>(input)
        val descriptions = generate<GenerateCheckDescriptions>(input) { toolVersion.set("1.0") }
        assertContains(messages, "val messages: Map<String, String> = mapOf(\n    )")
        assertContains(descriptions, "val checks: Map<String, WatchdogCheckDescription> = mapOf(\n    )")
        assertContains(descriptions, "val diagnosticsByAnnotation: Map<String, List<String>> = mapOf(\n    )")
        assertContains(generate<GenerateFunctionalTestDiagnostics>(input), ") {\n}")
        assertContains(generate<GenerateCheckNames>(input), "public enum class WatchdogChecks {\n}")
    }

    private inline fun <reified T : DiagnosticsJsonGeneratorTask> generate(
        input: Map<String, Any> = fixture(),
        configure: T.() -> Unit = {},
    ): String {
        val metadata = temporaryDirectory.newFile().apply { writeText(JsonOutput.toJson(input)) }
        val outputDirectory = temporaryDirectory.newFolder()
        project.tasks.register("generate${taskIndex++}", T::class.java).get().apply {
            diagnostics.set(metadata)
            packageName.set("fixtures")
            this.outputDirectory.set(outputDirectory)
            configure()
            generate()
        }
        return outputDirectory.walkTopDown().single { it.extension == "kt" }.readText()
    }

    private fun fixture(): Map<String, Any> = mapOf(
        "docsBaseUrl" to BASE_URL,
        "sharedParameterValues" to mapOf(
            "common" to listOf("shared {0}, don't change quotes"),
            "controlCharacters" to listOf("line one\r\nline two"),
            "longWord" to listOf("x".repeat(120)),
        ),
        "diagnostics" to listOf(
            mapOf(
                "name" to "SAMPLE_WARNING",
                "title" to "Check \"quoted\" \$title \\path",
                "docs" to "checks/java-interop/sample-\$value",
                "defaultSeverity" to "warning",
                "message" to listOf(
                    "Author's \"quoted\" `{0}` costs \$5.",
                    "[Guide](https://example.test/guide).",
                    "",
                    "Use `@IntentionallySample` twice: `@IntentionallySample`.",
                    "    println(\"\$value\")",
                    "Continue with `@IntentionallyShared`.",
                ),
                "parameterValues" to mapOf(
                    "common" to listOf("local {0}, '\$quoted' \\path"),
                    "example" to listOf(
                        "Read [Guide](https://example.test/guide).", "", "    val value = \"\$name\"", "Done.",
                    ),
                ),
            ),
            mapOf(
                "name" to "SAMPLE_ERROR",
                "title" to "Sample error",
                "docs" to "checks/sample-error",
                "message" to listOf("Author's file costs \$2.", "Apply `@IntentionallyShared`."),
            ),
        ),
    )

    companion object {
        private const val BASE_URL = "https://example.test/\$docs/"
    }
}
