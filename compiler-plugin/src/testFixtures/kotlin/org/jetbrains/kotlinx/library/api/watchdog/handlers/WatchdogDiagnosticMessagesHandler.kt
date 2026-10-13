package org.jetbrains.kotlinx.library.api.watchdog.handlers

import org.jetbrains.kotlin.compiler.plugin.devkit.assertEqualsToDump
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.frontend.fir.handlers.FirAnalysisHandler
import org.jetbrains.kotlin.test.frontend.fir.handlers.firDiagnosticCollectorService
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.diagnosticsService
import org.jetbrains.kotlin.test.services.sourceFileProvider
import org.jetbrains.kotlin.test.utils.MultiModuleInfoDumper
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnostics

/**
 * Dumps every watchdog diagnostic of the test with its rendered message into the `.messages.txt`
 * file next to the test data. The `DIAGNOSTICS` directive filters the dump as it filters the
 * markers. The dev kit picks a Kotlin-version-specific dump when one exists.
 *
 * Each entry has a header line `/<file>:<line>:<column>: <severity>: <DIAGNOSTIC_NAME>` and the
 * message lines indented by four spaces. Entries are sorted by offset, then by message.
 *
 * The IntelliJ IDEA shortcut in the stateful class messages depends on the host OS. The dump
 * replaces the macOS shortcut with the shortcut of the other systems, so one dump serves every
 * host.
 */
class WatchdogDiagnosticMessagesHandler(testServices: TestServices) : FirAnalysisHandler(testServices) {
    private val dumper = MultiModuleInfoDumper()

    override fun processModule(module: TestModule, info: FirOutputArtifact) {
        val diagnosticsPerFile = testServices.firDiagnosticCollectorService.getFrontendDiagnosticsForModule(info)
        for (part in info.partsForDependsOnModules) {
            val currentModule = part.module
            for (file in currentModule.files) {
                val firFile = info.mainFirFilesByTestFile[file] ?: continue
                val diagnostics = diagnosticsPerFile[firFile]
                    .map { it.diagnostic }
                    .filter {
                        it.factory.name in watchdogDiagnosticNames &&
                            testServices.diagnosticsService.shouldRenderDiagnostic(currentModule, it.factory.name, it.severity)
                    }
                if (diagnostics.isEmpty()) continue

                val content = testServices.sourceFileProvider.getOrCreateRealFileForSourceFile(file).readText()
                val builder = dumper.builderForModule(currentModule)
                diagnostics
                    .map { RenderedDiagnostic(it, content) }
                    .sortedWith(compareBy({ it.offset }, { it.message }))
                    .forEach { builder.append(it.render(file.name)) }
            }
        }
    }

    override fun processAfterAllModules(someAssertionWasFailed: Boolean) {
        val actualDump = if (dumper.isEmpty()) null else dumper.generateResultingDump()
        assertEqualsToDump(DUMP_EXTENSION, actualDump?.replace(MAC_SHORTCUT, DEFAULT_SHORTCUT))
    }

    private class RenderedDiagnostic(diagnostic: KtDiagnostic, content: String) {
        val offset: Int = diagnostic.firstRange.startOffset
        val name: String = diagnostic.factory.name
        val severity: String = diagnostic.severity.name.lowercase()
        val message: String = diagnostic.renderMessage()
        private val line: Int = content.take(offset).count { it == '\n' } + 1
        private val column: Int = offset - (content.lastIndexOf('\n', offset - 1) + 1) + 1

        fun render(fileName: String): String = buildString {
            append("/").append(fileName).append(':').append(line).append(':').append(column)
            append(": ").append(severity).append(": ").append(name).append('\n')
            for (messageLine in message.lines()) {
                if (messageLine.isNotEmpty()) append("    ")
                append(messageLine).append('\n')
            }
            append('\n')
        }
    }

    private companion object {
        const val DUMP_EXTENSION = ".messages.txt"
        const val MAC_SHORTCUT = "⌘N"
        const val DEFAULT_SHORTCUT = "Alt+Insert"

        /** The configurable diagnostics and the three that are always errors. */
        val watchdogDiagnosticNames: Set<String> = buildSet {
            WatchdogDiagnostics.allDiagnostics.mapTo(this) { it.name }
            add(WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION.name)
            add(WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY.name)
            add(WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API.name)
        }
    }
}
