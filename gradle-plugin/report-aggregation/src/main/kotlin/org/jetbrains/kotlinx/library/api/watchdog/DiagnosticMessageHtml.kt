package org.jetbrains.kotlinx.library.api.watchdog

import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.AttributeProvider
import org.commonmark.renderer.html.DefaultUrlSanitizer
import org.commonmark.renderer.html.HtmlRenderer

internal object DiagnosticMessageHtml {
    private val parameter = Regex("\\{(\\d+)}")
    private val parser = Parser.builder().build()
    private val renderer = HtmlRenderer.builder()
        .escapeHtml(true)
        .sanitizeUrls(true)
        .urlSanitizer(DefaultUrlSanitizer(listOf("http", "https", "mailto")))
        .attributeProviderFactory {
            AttributeProvider { node, tag, attributes ->
                if (tag == "code" && (node is IndentedCodeBlock || node is FencedCodeBlock && node.info.isBlank())) {
                    attributes["class"] = "language-kotlin"
                }
            }
        }
        .build()

    fun render(entry: NotAppliedExemptionReportEntry): String? {
        val template = WatchdogCheckDescriptions.checks[entry.diagnostic]?.message ?: return null
        if (parameter.findAll(template).any { it.groupValues[1].toInt() !in entry.messageParameters.indices }) return null
        val message = parameter.replace(template) { entry.messageParameters[it.groupValues[1].toInt()] }
        return renderer.render(parser.parse(message))
    }
}
