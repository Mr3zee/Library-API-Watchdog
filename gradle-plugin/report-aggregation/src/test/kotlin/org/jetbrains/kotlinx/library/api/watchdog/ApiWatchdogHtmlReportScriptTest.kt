package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import java.nio.file.Files
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeDriverService
import org.openqa.selenium.chrome.ChromeOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the scripts of the HTML report in the Chrome for Testing headless shell. The Gradle build gives the paths of the
 * browser and the driver in system properties, so run the test with Gradle. The tests skip themselves on a platform
 * without Chrome for Testing builds.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiWatchdogHtmlReportScriptTest {
    private lateinit var fixtures: File
    private lateinit var driver: ChromeDriver

    @BeforeAll
    fun startBrowser() {
        val unsupportedPlatform = System.getProperty("chromeForTesting.unsupportedPlatform")
        assumeTrue(unsupportedPlatform == null) { "Chrome for Testing has no build for $unsupportedPlatform." }
        fixtures = Files.createTempDirectory("html-report-fixtures").toFile()
        writeFixtures()
        // Ubuntu 24.04 blocks the Chrome sandbox for a Chrome without an AppArmor profile, for example Chrome for
        // Testing: https://chromium.googlesource.com/chromium/src/+/main/docs/security/apparmor-userns-restrictions.md
        val options = ChromeOptions()
            .setBinary(chromeForTestingPath("browser"))
            .addArguments("--no-sandbox", "--window-size=1280,1024")
        val service = ChromeDriverService.Builder().usingDriverExecutable(File(chromeForTestingPath("driver"))).build()
        driver = ChromeDriver(service, options)
    }

    private fun chromeForTestingPath(executable: String): String =
        checkNotNull(System.getProperty("chromeForTesting.$executable")) {
            "The chromeForTesting.$executable system property isn't set. Run the test with the Gradle test task."
        }

    @AfterAll
    fun stopBrowser() {
        if (::driver.isInitialized) driver.quit()
        if (::fixtures.isInitialized) fixtures.deleteRecursively()
    }

    @Test
    fun `reports remain readable when JavaScript is disabled`() {
        val document = Jsoup.parse(File(fixtures, "mixed.html").readText())
        assertTrue(document.selectFirst(".report-controls")!!.hasAttr("hidden"))
        assertEquals(6, document.select(".finding:not([hidden])").size)
        assertEquals(5, document.select(".diagnostic-group[open]").size)
        assertEquals("public data class Api(\n    val text: String = \"😀<&>\",\n)\n", document.selectFirst("pre code")!!.wholeText())
        assertNull(document.selectFirst(".reported-element"))
    }

    @Test
    fun `open in IDE buttons link to the one Toolbox IDE with 0-based positions`() = openReport("ide-studio") {
        assertEquals(0, count("#ide-target"))
        assertEquals(0, count("a.open-in-ide[hidden]"))
        val prefix = "jetbrains://studio/navigate/reference?project=my%20lib%26&path="
        val links = ideLinks()
        assertTrue(links.all { it.startsWith(prefix) }, links.toString())
        for (path in listOf("src%2FApi.kt%3A1", "modules%2Flibrary%2Fsrc%2FApi.kt%3A11%3A4", "modules%2Flibrary%2Fsrc%2FApi.kt%3A11")) {
            assertTrue(prefix + path in links, links.toString())
        }
    }

    @Test
    fun `open in IDE buttons without Toolbox are idea links with a 1-based line and a 0-based column`() = openReport("ide-url") {
        assertEquals(0, count("#ide-target"))
        assertTrue(
            "idea://open?file=%2Fwork%2Fmy%20lib%2Fmodules%2Flibrary%2Fsrc%2FApi.kt&line=12&column=4" in ideLinks(),
            ideLinks().toString(),
        )
        assertTrue("idea://open?file=%2Fwork%2Fmy%20lib%2Fsrc%2FApi.kt&line=2" in ideLinks(), ideLinks().toString())
    }

    @Test
    fun `the IDE selector sets every button and remembers the selection`() = openReport("ide-select") {
        js("localStorage.clear()")
        reload()
        assertEquals(listOf("idea", "studio"), optionValues("ide-target"))
        assertEquals("idea", value("ide-target"))
        assertTrue(ideLinks().all { it.startsWith("jetbrains://idea/navigate/reference?project=my%20lib%26&path=") }, ideLinks().toString())
        change("ide-target", "studio")
        assertTrue(ideLinks().all { it.startsWith("jetbrains://studio/") }, ideLinks().toString())
        reload()
        assertEquals("studio", value("ide-target"))
        assertTrue(ideLinks().all { it.startsWith("jetbrains://studio/") }, ideLinks().toString())
        js("localStorage.clear()")
    }

    @Test
    fun `empty reports initialize without controls or errors`() = openReport("empty") {
        js("Prism.highlightAll()")
        assertEquals(0, count(".report-controls"))
        assertEquals(0, count(".finding"))
        assertEquals(2, count(".empty"))
    }

    @Test
    fun `controls enable with an unfiltered diagnostic view`() = openReport {
        assertEquals(false, property(".report-controls", "hidden"))
        assertEquals("diagnostic", value("view-mode"))
        assertEquals("", value("diagnostic-filter"))
        assertEquals("", value("project-filter"))
        assertEquals(true, property("#reset-filter", "disabled"))
        assertEquals("", property("#filter-summary", "textContent"))
        assertEquals(6, visibleFindings().size)
    }

    @Test
    fun `diagnostic filters match individual checks in combined applied annotations`() = openReport {
        change("diagnostic-filter", "STATEFUL_CLASS_WITHOUT_HASH_CODE")
        val visible = visibleFindings()
        assertEquals(1, visible.size)
        assertEquals("Applied", textIn(visible.single(), ".fix-status"))
        assertEquals("Showing 1 of 6 findings.", property("#filter-summary", "textContent"))
        assertEquals(false, property("#reset-filter", "disabled"))
        assertFalse(filterEmptyHidden("not-applied"))
        assertTrue(filterEmptyHidden("applied"))
        val groupCounts = js(
            """return [...document.querySelectorAll(".diagnostic-group")]
                .filter(group => !group.hidden)
                .map(group => group.querySelector(".group-count").textContent)""",
        )
        assertEquals(listOf("(1)"), groupCounts)
        change("diagnostic-filter", "DATA_CLASS_PUBLIC_API")
        assertEquals(2, visibleFindings().size)
        assertEquals("Showing 2 of 6 findings.", property("#filter-summary", "textContent"))
        click("reset-filter")
        assertEquals(6, visibleFindings().size)
        assertEquals("", value("diagnostic-filter"))
        assertEquals("", property("#filter-summary", "textContent"))
        assertEquals(true, property("#reset-filter", "disabled"))
    }

    @Test
    fun `project filters narrow findings together with the diagnostic filter and reset with it`() = openReport {
        val findings = findings()
        assertEquals(listOf("", ":", ":library"), optionValues("project-filter"))
        assertEquals(listOf("All projects", ":", ":library"), optionLabels("project-filter"))
        change("project-filter", ":")
        assertEquals(listOf("src/Api.kt:2"), locations(visibleFindings()))
        assertEquals("Showing 1 of 6 findings.", property("#filter-summary", "textContent"))
        assertEquals(false, property("#reset-filter", "disabled"))
        change("project-filter", ":library")
        assertEquals(5, visibleFindings().size)
        assertTrue(filterEmptyHidden("not-applied"))
        change("diagnostic-filter", "DATA_CLASS_PUBLIC_API")
        val expected = findings.matching(":library", "DATA_CLASS_PUBLIC_API")
        assertEquals(1, expected.size)
        assertEquals(expected, visibleFindings())
        change("view-mode", "directory")
        assertEquals(expected, visibleFindings())
        assertTreeCounts()
        click("reset-filter")
        assertEquals("", value("project-filter"))
        assertEquals("", value("diagnostic-filter"))
        assertEquals(6, visibleFindings().size)
        assertEquals("", property("#filter-summary", "textContent"))
    }

    @Test
    fun `single-project reports have no project filter`() = openReport("duplicates") {
        assertEquals(0, count("#project-filter"))
        assertEquals(":library", js("return document.querySelector('.finding').dataset.project"))
        change("diagnostic-filter", "FUTURE_CHECK")
        assertEquals(1, visibleFindings().size)
        click("reset-filter")
        assertEquals(5, visibleFindings().size)
    }

    @Test
    fun `unknown annotations use the unknown diagnostic filter`() = openReport {
        change("diagnostic-filter", "unknown")
        val visible = visibleFindings()
        assertEquals(listOf("src/<script>&\".kt:1"), locations(visible))
        assertEquals("@IntentionallyCustom", textIn(visible.single(), ".group-annotation"))
    }

    @Test
    fun `unknown diagnostic names with spaces punctuation Unicode or no text remain filterable`() =
        openReport("unusual-diagnostics") {
            val options = optionValues("diagnostic-filter").drop(1)
            assertEquals(9, options.size)
            assertEquals(9, options.toSet().size)
            assertTrue(options.none { it.isEmpty() })
            assertTrue(optionLabels("diagnostic-filter").drop(1).none { it.isEmpty() })
            for (view in listOf("diagnostic", "directory")) {
                change("view-mode", view)
                for (option in options) {
                    change("diagnostic-filter", option)
                    val visible = visibleFindings()
                    assertEquals(1, visible.size, option)
                    assertEquals(option, js("return arguments[0].dataset.diagnostics", visible.single()))
                }
            }
        }

    @Test
    fun `directory views preserve nodes and restore original diagnostic order`() = openReport {
        val findings = elements(".finding")
        val groups = elements(".diagnostic-group")
        val original = groups.map { elementsIn(it, ".finding") }
        js("arguments[0].open = false", groups.first())
        change("view-mode", "directory")
        assertEquals(findings.size, count(".finding"))
        assertEquals(0, count(".diagnostic-groups:not([hidden])"))
        assertEquals(0, count(".directory-tree[hidden]"))
        assertEquals(0, count(".tree-file[open]"))
        assertEquals(0, count(".tree-branch:not(.tree-file):not([open])"))
        assertEquals("directory", js("return document.querySelector('.finding-heading').closest('section').dataset.view"))
        val firstFile = elements(".tree-file").first()
        js("arguments[0].open = true", firstFile)
        repeat(4) {
            change("view-mode", "diagnostic")
            assertEquals(original, groups.map { elementsIn(it, ".finding") })
            assertEquals(false, js("return arguments[0].open", groups.first()))
            assertEquals(0, count(".directory-tree:not([hidden])"))
            change("view-mode", "directory")
            assertEquals(true, js("return arguments[0].open", firstFile))
            assertEquals(findings.size, count(".finding"))
            assertTrue(inDocument(findings))
        }
    }

    @Test
    fun `directory branches separate projects and sort findings numerically by source line`() = openReport {
        change("view-mode", "directory")
        val apiFiles = js(
            """return [...document.querySelectorAll("#not-applied ~ .directory-tree .tree-file")]
                .filter(file => file.querySelector(".tree-name").textContent === "Api.kt")
                .map(file => [...file.querySelectorAll(".finding")])""",
        ).asList<List<WebElement>>()
        assertEquals(2, apiFiles.size)
        assertEquals(listOf("src/Api.kt:3", "src/Api.kt:12"), locations(apiFiles.single { it.size == 2 }))
        val top = js(
            """const top = document.querySelector("#not-applied ~ .directory-tree > ul");
                const merged = top.children[0].querySelector(":scope > details > .tree-children");
                const name = node => node.querySelector(".tree-name").textContent;
                return {
                    top: [...top.children].map(name),
                    merged: [...merged.children].map(name),
                    nested: merged.children[0].querySelector(".tree-file .tree-name").textContent,
                };""",
        ) as Map<*, *>
        assertEquals(listOf("modules/library/src", "src"), top["top"])
        assertEquals(listOf("z", "Api.kt"), top["merged"])
        assertEquals("Other.kt", top["nested"])
        assertEquals(0, count(".directory-tree script, .directory-tree img"))
        assertTrue("<script>&\".kt" in texts(".tree-name"))
    }

    @Test
    fun `duplicate findings at the same location survive directory grouping and filters`() = openReport("duplicates") {
        val findings = elements(".finding")
        change("view-mode", "directory")
        assertEquals(2, count(".tree-file"))
        assertEquals(5, count(".finding"))
        change("diagnostic-filter", "DATA_CLASS_PUBLIC_API")
        assertEquals(2, visibleFindings().size)
        change("view-mode", "diagnostic")
        click("reset-filter")
        assertEquals(5, visibleFindings().size)
        assertTrue(inDocument(findings))
    }

    @Test
    fun `directory filters hide empty ancestors and keep counts correct across view changes`() = openReport {
        change("diagnostic-filter", "UNDOCUMENTED_PUBLIC_API")
        change("view-mode", "directory")
        repeat(3) {
            assertEquals(1, visibleFindings().size)
            assertTreeCounts()
            change("view-mode", "diagnostic")
            assertEquals(1, visibleFindings().size)
            change("view-mode", "directory")
        }
        click("reset-filter")
        assertEquals(6, visibleFindings().size)
        assertEquals(0, count(".tree-node[hidden]"))
    }

    @Test
    fun `applied-only reports support both views and filtering with an empty section`() =
        assertOneSectionReport("applied-only", diagnostic = "unknown")

    @Test
    fun `not-applied-only reports support both views and filtering with an empty section`() =
        assertOneSectionReport("not-applied-only", diagnostic = "FUTURE_CHECK")

    private fun assertOneSectionReport(name: String, diagnostic: String) = openReport(name) {
        val total = count(".finding")
        assertEquals(1, count(".empty"))
        change("view-mode", "directory")
        change("diagnostic-filter", diagnostic)
        assertEquals(1, visibleFindings().size)
        change("view-mode", "diagnostic")
        click("reset-filter")
        assertEquals(total, visibleFindings().size)
    }

    @Test
    fun `directory views tolerate missing filenames and malformed source locations`() = openReport("malformed") {
        change("view-mode", "directory")
        assertEquals(5, visibleFindings().size)
        assertEquals(5, count(".directory-tree .finding"))
        assertTrue(texts(".tree-file .tree-name").none { it.isEmpty() })
        val unknownFiles = js(
            """return [...document.querySelectorAll(".tree-file")]
                .filter(file => file.querySelector(".tree-name").textContent === "Unknown file")
                .map(file => file.querySelectorAll(".finding").length)""",
        ).asList<Long>()
        assertEquals(listOf(1L, 2L), unknownFiles.sorted())
        change("view-mode", "diagnostic")
        assertEquals(5, count(".diagnostic-groups .finding"))
    }

    @Test
    fun `seeded view and filter changes never lose or duplicate findings`() = openReport {
        val findings = findings()
        val diagnostics = optionValues("diagnostic-filter")
        val projects = optionValues("project-filter")
        var seed = 0x5EED2026
        fun next(): UInt {
            seed = seed * 1664525 + 1013904223
            return seed.toUInt()
        }
        fun <T> List<T>.pick(): T = this[(next() % size.toUInt()).toInt()]
        repeat(128) { iteration ->
            when (next() % 4u) {
                0u -> change("view-mode", if (next() % 2u == 1u) "directory" else "diagnostic")
                1u -> change("project-filter", projects.pick())
                else -> change("diagnostic-filter", diagnostics.pick())
            }
            val state = js(
                """return {
                    diagnostic: document.getElementById("diagnostic-filter").value,
                    project: document.getElementById("project-filter").value,
                    total: document.querySelectorAll(".finding").length,
                    visible: [...document.querySelectorAll(".finding")].filter(finding => !finding.hidden),
                    attached: arguments[0].every(finding => document.contains(finding)),
                };""",
                findings.map { it.element },
            ) as Map<*, *>
            val expected = findings.matching(state["project"] as String, state["diagnostic"] as String)
            assertEquals(findings.size.toLong(), state["total"], "iteration=$iteration")
            assertEquals(expected.toSet(), state["visible"].asList<WebElement>().toSet(), "iteration=$iteration")
            assertEquals(true, state["attached"], "iteration=$iteration")
        }
    }

    @Test
    fun `Prism preserves source text and underlines the exact reported UTF-16 range`() = openReport {
        val pre = elements("pre[data-range-start]").first()
        val range = js(
            """const pre = arguments[0];
                return {source: pre.querySelector("code").textContent, start: pre.dataset.rangeStart, end: pre.dataset.rangeEnd};""",
            pre,
        ) as Map<*, *>
        val source = range["source"] as String
        val expected = source.substring((range["start"] as String).toInt(), (range["end"] as String).toInt())
        repeat(3) { iteration ->
            val state = js(
                """const pre = arguments[0];
                    const code = pre.querySelector("code");
                    Prism.highlightElement(code);
                    return {
                        text: code.textContent,
                        reported: [...code.querySelectorAll(".reported-error")].map(span => span.textContent).join(""),
                        tokens: code.querySelectorAll(".token").length,
                        nested: code.querySelectorAll(".reported-element .reported-element").length,
                        rows: code.querySelectorAll(".line-numbers-rows").length,
                        lineHighlights: pre.querySelectorAll(".line-highlight").length,
                    };""",
                pre,
            ) as Map<*, *>
            assertEquals(source, state["text"])
            assertEquals(expected, state["reported"])
            assertTrue((state["tokens"] as Long) > 0)
            assertEquals(0L, state["nested"])
            assertEquals(1L, state["rows"])
            assertEquals(1L, state["lineHighlights"])
            change("view-mode", if (iteration % 2 == 1) "directory" else "diagnostic")
        }
    }

    @Test
    fun `range highlighting crosses syntax tokens and lines without changing source text`() = openReport("empty") {
        val source = "val text = \"😀<&>\"\n// café e\u0301\nval next = 42"
        for (severity in listOf("error", "warning")) {
            for ((start, end) in listOf(0 to source.length, 4 to 8, 10 to 18, 16 to 30)) {
                val result = highlight(source, "$start", "$end", severity)
                assertEquals(source, result["text"])
                assertEquals(source.substring(start, end), result["reported"])
            }
        }
    }

    @Test
    fun `without IntersectionObserver the page highlights every snippet on load`() = openReport {
        val highlighted = js("return [...document.querySelectorAll('pre code')].map(code => code.querySelector('.token') !== null)")
            .asList<Boolean>()
        assertTrue(highlighted.size > 1)
        assertTrue(highlighted.all { it })
        assertEquals(count("pre.line-numbers"), count("pre.line-numbers .line-numbers-rows"))
    }

    @Test
    fun `with IntersectionObserver the page highlights and refreshes only snippets in view`() =
        openReport(lazyHighlighting = true) {
            val snippets = elements("pre")
            assertEquals(snippets, js("return harness.observers[0].targets"))
            assertEquals(0, count(".token"))
            val (first, second) = snippets
            intersect(first, true)
            assertTrue(hasToken(first))
            assertFalse(hasToken(second))
            js(
                """harness.resized = [];
                    Prism.plugins.lineNumbers.resize = element => harness.resized.push(element);
                    Prism.plugins.lineHighlight.highlightLines = () => () => {};""",
            )
            resize()
            flushFrames()
            assertEquals(listOf(first), js("return harness.resized"))
            intersect(first, true)
            assertEquals(listOf(first, first), js("return harness.resized"))
            assertEquals(1L, js("return arguments[0].querySelectorAll('.line-numbers-rows').length", first))
            intersect(first, false)
            js("harness.resized.length = 0")
            resize()
            flushFrames()
            assertEquals(emptyList<WebElement>(), js("return harness.resized"))
            intersect(second, true)
            assertTrue(hasToken(second))
        }

    @Test
    fun `code blocks without source ranges get syntax colors without reported element marks`() = openReport {
        val sources = texts("pre code")
        js("Prism.highlightAll()")
        assertEquals(sources, texts("pre code"))
        val applied = js("return document.querySelector('#applied ~ .diagnostic-groups pre code')") as WebElement
        assertTrue(hasToken(applied))
        assertEquals(0L, js("return arguments[0].querySelectorAll('.reported-element').length", applied))
        val detached = js(
            """const code = document.createElement("code");
                code.className = "language-kotlin";
                code.textContent = "val x = 1";
                Prism.highlightElement(code);
                return {text: code.textContent, marks: code.querySelectorAll(".reported-element").length};""",
        ) as Map<*, *>
        assertEquals("val x = 1", detached["text"])
        assertEquals(0L, detached["marks"])
    }

    @Test
    fun `invalid highlight ranges and severities leave code readable`() = openReport("empty") {
        for ((start, end, severity) in listOf(
            Triple("-1", "2", "error"), Triple("0", "100", "error"), Triple("1", "1", "error"), Triple("3", "2", "error"),
            Triple("NaN", "2", "error"), Triple("0.5", "2", "error"), Triple("0", "Infinity", "error"),
            Triple("0", "2", "info"), Triple("0", "2", ""), Triple("0", "2", "error\" onclick=\"alert(1)"),
        )) {
            val result = highlight("val x = 1", start, end, severity)
            assertEquals("val x = 1", result["text"])
            assertEquals(0L, result["marks"], "start=$start end=$end severity=$severity")
        }
    }

    @Test
    fun `code refreshes batch resize and disclosure events and skip hidden snippets`() = openReport {
        // The report opens its groups on load, and each opened group schedules a code refresh.
        flushFrames()
        js(
            """harness.resized = [];
                harness.highlighted = [];
                Prism.plugins.lineNumbers.resize = element => harness.resized.push(element);
                Prism.plugins.lineHighlight.highlightLines = element => () => harness.highlighted.push(element);""",
        )
        val shown = js("""return [...document.querySelectorAll("pre.line-numbers")].filter(pre => !pre.closest("details:not([open]), [hidden]"))""")
            .asList<WebElement>()
        val withLine = js("return arguments[0].filter(pre => pre.hasAttribute('data-line'))", shown).asList<WebElement>()
        val pre = withLine.first()
        js(
            """window.dispatchEvent(new Event("resize"));
                document.querySelector("details").dispatchEvent(new Event("toggle"));
                window.dispatchEvent(new Event("resize"));""",
        )
        assertEquals(1L, js("return harness.frames.length"))
        refresh()
        assertEquals(shown, js("return harness.resized"))
        assertEquals(withLine, js("return harness.highlighted"))
        js("arguments[0].removeAttribute('data-line')", pre)
        resize()
        refresh()
        assertEquals(shown, js("return harness.resized"))
        assertEquals(withLine - pre, js("return harness.highlighted"))
        js("arguments[0].hidden = true", pre)
        resize()
        refresh()
        assertEquals(shown - pre, js("return harness.resized"))
    }

    private fun openReport(name: String = "mixed", lazyHighlighting: Boolean = false, test: ReportPage.() -> Unit) {
        val charset = "<meta charset=\"utf-8\">"
        val html = File(fixtures, "$name.html").readText()
        check(charset in html) { "The $name fixture has no $charset." }
        // The charset declaration must stay in the first 1024 bytes, so the harness goes after it.
        val page = File(fixtures, "$name-${if (lazyHighlighting) "lazy" else "eager"}.html")
        page.writeText(html.replaceFirst(charset, "$charset\n<script>${harness(lazyHighlighting)}</script>"))
        driver.get(page.toURI().toString())
        ReportPage(driver).test()
        assertEquals(emptyList<Any>(), driver.executeScript("return harness.errors"))
    }

    /**
     * Records script errors and animation frames. Chrome calls `IntersectionObserver` callbacks asynchronously, so
     * the harness removes `IntersectionObserver`. For [lazyHighlighting], the harness replaces it with a fake, and the
     * test calls the callback.
     */
    private fun harness(lazyHighlighting: Boolean): String {
        val intersectionObserver = if (lazyHighlighting) {
            """window.IntersectionObserver = class {
                constructor(callback) {
                    this.callback = callback;
                    this.targets = [];
                    harness.observers.push(this);
                }
                observe(target) { this.targets.push(target); }
                unobserve() {}
                disconnect() {}
            };"""
        } else {
            "delete window.IntersectionObserver;"
        }
        return """
            window.harness = {
                errors: [],
                frames: [],
                observers: [],
                change(id, value) {
                    const select = document.getElementById(id);
                    select.value = value;
                    select.dispatchEvent(new Event("change"));
                },
                flushFrames() {
                    for (const callback of harness.frames.splice(0)) callback();
                },
            };
            window.addEventListener("error", event => harness.errors.push(String(event.error ?? event.message)));
            window.requestAnimationFrame = callback => harness.frames.push(callback);
            $intersectionObserver
        """.trimIndent()
    }

    private class Finding(val element: WebElement, val project: String, val diagnostics: List<String>)

    private fun List<Finding>.matching(project: String, diagnostic: String): List<WebElement> =
        filter { (project.isEmpty() || it.project == project) && (diagnostic.isEmpty() || diagnostic in it.diagnostics) }
            .map { it.element }

    private class ReportPage(private val driver: ChromeDriver) {
        fun js(script: String, vararg arguments: Any?): Any? = driver.executeScript(script, *arguments)

        fun count(selector: String): Int =
            (js("return document.querySelectorAll(arguments[0]).length", selector) as Long).toInt()

        fun elements(selector: String): List<WebElement> =
            js("return [...document.querySelectorAll(arguments[0])]", selector).asList()

        fun elementsIn(element: WebElement, selector: String): List<WebElement> =
            js("return [...arguments[0].querySelectorAll(arguments[1])]", element, selector).asList()

        fun texts(selector: String): List<String> =
            js("return [...document.querySelectorAll(arguments[0])].map(element => element.textContent)", selector).asList()

        fun textIn(element: WebElement, selector: String): String? =
            js("return arguments[0].querySelector(arguments[1]).textContent", element, selector) as String?

        fun property(selector: String, name: String): Any? =
            js("return document.querySelector(arguments[0])[arguments[1]]", selector, name)

        fun value(id: String): String = property("#$id", "value") as String

        fun ideLinks(): List<String> =
            js("return [...document.querySelectorAll('a.open-in-ide')].map(link => link.getAttribute('href'))").asList()

        fun reload() {
            driver.navigate().refresh()
        }

        fun optionValues(id: String): List<String> =
            js("return [...document.getElementById(arguments[0]).options].map(option => option.value)", id).asList()

        fun optionLabels(id: String): List<String> =
            js("return [...document.getElementById(arguments[0]).options].map(option => option.textContent)", id).asList()

        fun visibleFindings(): List<WebElement> =
            js("return [...document.querySelectorAll('.finding')].filter(finding => !finding.hidden)").asList()

        fun findings(): List<Finding> =
            js("return [...document.querySelectorAll('.finding')].map(finding => [finding, finding.dataset.project, finding.dataset.diagnostics])")
                .asList<List<*>>()
                .map { (element, project, diagnostics) ->
                    Finding(element as WebElement, project as String, (diagnostics as String).split(" "))
                }

        fun locations(findings: List<WebElement>): List<String> =
            js("return arguments[0].map(finding => finding.querySelector('.location code').textContent)", findings).asList()

        fun filterEmptyHidden(sectionId: String): Boolean =
            js("return document.getElementById(arguments[0]).parentElement.querySelector('.filter-empty').hidden", sectionId) as Boolean

        fun inDocument(elements: List<WebElement>): Boolean =
            js("return arguments[0].every(element => document.contains(element))", elements) as Boolean

        fun hasToken(element: WebElement): Boolean = js("return arguments[0].querySelector('.token') !== null", element) as Boolean

        fun change(id: String, value: String) {
            js("harness.change(arguments[0], arguments[1])", id, value)
        }

        fun click(id: String) {
            js("document.getElementById(arguments[0]).click()", id)
        }

        fun intersect(element: WebElement, isIntersecting: Boolean) {
            js("const observer = harness.observers[0]; observer.callback([{target: arguments[0], isIntersecting: arguments[1]}], observer)", element, isIntersecting)
        }

        fun resize() {
            js("window.dispatchEvent(new Event('resize'))")
        }

        fun flushFrames() {
            js("harness.flushFrames()")
        }

        /** Clears the recorded refresh calls, then runs the scheduled frames. */
        fun refresh() {
            js("harness.resized.length = 0; harness.highlighted.length = 0; harness.flushFrames()")
        }

        fun assertTreeCounts() {
            val nodes = js(
                """return [...document.querySelectorAll(".tree-node")].map(node => ({
                    hidden: node.hidden,
                    visible: [...node.querySelectorAll(".finding")].filter(finding => !finding.hidden).length,
                    label: node.querySelector(".group-count").textContent,
                }))""",
            ).asList<Map<*, *>>()
            for (node in nodes) {
                val visible = node["visible"] as Long
                assertEquals(visible == 0L, node["hidden"])
                assertEquals("($visible)", node["label"])
            }
        }

        /** Highlights [source] in a new snippet with the given range attributes, then removes the snippet. */
        fun highlight(source: String, start: String, end: String, severity: String): Map<*, *> = js(
            """const [source, start, end, severity] = arguments;
                const pre = document.createElement("pre");
                pre.dataset.rangeStart = start;
                pre.dataset.rangeEnd = end;
                pre.dataset.severity = severity;
                const code = document.createElement("code");
                code.className = "language-kotlin";
                code.textContent = source;
                pre.append(code);
                document.body.append(pre);
                Prism.highlightElement(code);
                const marks = [...code.querySelectorAll(".reported-element")];
                pre.remove();
                return {
                    text: code.textContent,
                    reported: marks.filter(mark => mark.classList.contains("reported-" + severity)).map(mark => mark.textContent).join(""),
                    marks: marks.length,
                };""",
            source, start, end, severity,
        ) as Map<*, *>
    }

    private fun writeFixtures() {
        val code = "public data class Api(\n    val text: String = \"😀<&>\",\n)\n"
        val entries = listOf(
            entry("src/Api.kt:12", range = ApiWatchdogSourceRange(12, 5, 12, 27)),
            entry("src/Api.kt:3", diagnostic = "UNDOCUMENTED_PUBLIC_API", severity = "warning")
                .copy(messageParameters = listOf("class", "Api")),
            entry("src/z/Other.kt:1", diagnostic = "FUTURE_CHECK"),
        )
        val library = projectReport(
            entries = entries,
            applied = listOf(
                AppliedExemptionReportEntry("IntentionallyWithoutEqualsHashCodeOrToString", "src/Api.kt:12"),
                AppliedExemptionReportEntry("IntentionallyCustom", "src/<script>&\".kt:1"),
            ),
            snippets = mapOf(entries.first().location to ApiWatchdogSourceSnippet(11, code)),
        )
        val root = projectReport(entries = listOf(entry("src/Api.kt:2")), project = ":", directory = "")
        val malformedLocations = listOf("", "/", "Api.kt:NaN", "C:\\Api.kt", "Unknown file:1")
        val unusualDiagnostics = listOf(
            "FUTURE CHECK", "FUTURE\tCHECK", "FUTURE\nCHECK", "FUTURE%20CHECK", "FUTURE+CHECK", "", "a\"<&>'", "日本語", "~",
        )
        for ((name, reports) in mapOf(
            "mixed" to listOf(library, root),
            "empty" to emptyList(),
            "applied-only" to listOf(library.copy(notApplied = emptyList())),
            "not-applied-only" to listOf(library.copy(applied = emptyList())),
            "malformed" to listOf(projectReport(entries = malformedLocations.map { entry(it) }, directory = "")),
            "unusual-diagnostics" to listOf(projectReport(
                entries = unusualDiagnostics.mapIndexed { index, diagnostic -> entry("src/Api$index.kt:1", diagnostic) },
            )),
            "duplicates" to listOf(projectReport(
                entries = listOf(entries.first(), entries.first(), entries.first().copy(diagnostic = "FUTURE_CHECK")),
                applied = listOf(library.applied.first(), library.applied.first()),
            )),
        )) {
            ApiWatchdogHtmlReport.write(reports, File(fixtures, "$name.html"), aggregate = true)
        }
        val ideProject = ApiWatchdogIdeProject("my lib&", "/work/my lib")
        for ((name, links) in mapOf(
            "ide-studio" to ApiWatchdogIdeLinks(ideProject, toolbox = true, tags = listOf("studio")),
            "ide-select" to ApiWatchdogIdeLinks(ideProject, toolbox = true, tags = listOf("idea", "studio")),
            "ide-url" to ApiWatchdogIdeLinks(ideProject, toolbox = false, tags = emptyList()),
        )) {
            ApiWatchdogHtmlReport.write(listOf(library, root), File(fixtures, "$name.html"), aggregate = true, ideLinks = links)
        }
    }

    private fun entry(
        location: String,
        diagnostic: String = "DATA_CLASS_PUBLIC_API",
        severity: String = "error",
        range: ApiWatchdogSourceRange? = null,
    ) = NotAppliedExemptionReportEntry(null, diagnostic, location, "Manual fix.", severity, listOf("Api"), range)

    private fun projectReport(
        entries: List<NotAppliedExemptionReportEntry> = emptyList(),
        applied: List<AppliedExemptionReportEntry> = emptyList(),
        snippets: Map<String, ApiWatchdogSourceSnippet> = emptyMap(),
        project: String = ":library",
        directory: String = "modules/library",
    ) = ApiWatchdogProjectReport(project, directory, applied, entries, snippets)
}

@Suppress("UNCHECKED_CAST")
private fun <T> Any?.asList(): List<T> = this as List<T>
