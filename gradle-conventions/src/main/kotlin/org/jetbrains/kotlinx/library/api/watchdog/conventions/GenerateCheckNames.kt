package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

abstract class GenerateCheckNames : DiagnosticsJsonGeneratorTask() {
    /** `true` generates an `internal` enum without the Gradle usage sample. Default `false`. */
    @get:Input
    @get:Optional
    abstract val internalVisibility: Property<Boolean>

    override val fileName: String get() = "WatchdogChecks.kt"

    override fun StringBuilder.appendDeclarations(diagnostics: DiagnosticsJson) {
        val internal = internalVisibility.getOrElse(false)
        val visibility = if (internal) "internal" else "public"
        appendLine("/**")
        appendLine(" * The watchdog checks, generated from the shared `diagnostics.json`. The entry name is the diagnostic name.")
        if (!internal) {
            appendLine(" * Pass one to [WatchdogGradleExtension.ignore]:")
            appendLine(" *")
            appendLine(" * ```kotlin")
            appendLine(" * import org.jetbrains.kotlinx.library.api.watchdog.WatchdogChecks")
            appendLine(" *")
            appendLine(" * apiWatchdog {")
            appendLine(" *     ignore(WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS, whenAnnotatedWith = listOf(\"com.example.GeneratedValueMembers\"))")
            appendLine(" * }")
            appendLine(" * ```")
            appendLine(" *")
            appendLine(" * Every check is listed, including the always-error checks that accept no ignore rules.")
            appendLine(" * [WatchdogGradleExtension.ignore] rejects those.")
        }
        appendLine(" */")
        appendLine("$visibility enum class WatchdogChecks {")
        for ((index, entry) in diagnostics.entries.withIndex()) {
            if (index > 0) appendLine()
            appendLine("    /** [${entry.title}](${entry.docsUrl}) */")
            appendLine("    ${entry.name},")
        }
        appendLine("}")
    }
}
