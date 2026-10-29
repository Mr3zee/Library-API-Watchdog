package org.jetbrains.kotlinx.library.api.watchdog

/** The `apiWatchdog { }` block of a generated build script. */
class ApiWatchdogBlock internal constructor() {
    private val lines = mutableListOf<String>()
    private val javaInterop = JavaInteropBlock()

    var enabled: Boolean? = null

    var suggestAbiValidation: Boolean? = null

    var publicTypeWithInternalApi: Boolean? = null

    var publicTypesMustBeTransitiveDependencies: Boolean? = null

    var reportUndocumentedClassProperties: Boolean? = null

    /** A Java-interop diagnostic goes into the `javaInterop { }` group. */
    fun severity(severity: Severity, vararg diagnostics: Diagnostic) {
        for (diagnostic in diagnostics) {
            require(diagnostic in configurableDiagnostics) { "$diagnostic doesn't have a severity property" }
            val line = "${diagnostic.property} = WatchdogSeverity.$severity"
            if (diagnostic.javaInterop) javaInterop.lines += line else lines += line
        }
    }

    fun severityOfEveryCheck(severity: Severity) {
        severity(severity, *configurableDiagnostics.toTypedArray())
    }

    fun ignore(diagnostic: Diagnostic, vararg whenAnnotatedWith: String) {
        val annotations = whenAnnotatedWith.joinToString { "\"$it\"" }
        lines += "ignore(WatchdogChecks.${diagnostic.name}, whenAnnotatedWith = listOf($annotations))"
    }

    fun internalApiAnnotations(vararg annotations: String) {
        annotations.forEach { lines += "internalApiAnnotations.add(\"$it\")" }
    }

    fun javaInterop(block: JavaInteropBlock.() -> Unit) {
        javaInterop.block()
    }

    /** The `reports { }` block. A file is a path relative to the build directory. */
    fun reports(block: ReportsBlock.() -> Unit) {
        val reports = ReportsBlock().apply(block)
        lines += "reports {"
        reports.html?.let { lines += "    html = $it" }
        reports.htmlFile?.let { lines += "    htmlFile = layout.buildDirectory.file(\"$it\")" }
        reports.sarif?.let { lines += "    sarif = $it" }
        reports.sarifFile?.let { lines += "    sarifFile = layout.buildDirectory.file(\"$it\")" }
        lines += "}"
    }

    /** Script text written as is. */
    fun raw(script: String) {
        lines += script.trimIndent().lines()
    }

    internal fun render(): String = buildString {
        appendLine("apiWatchdog {")
        enabled?.let { appendLine("    enabled = $it") }
        suggestAbiValidation?.let { appendLine("    suggestAbiValidation = $it") }
        publicTypeWithInternalApi?.let { appendLine("    publicTypeWithInternalApi = $it") }
        publicTypesMustBeTransitiveDependencies?.let {
            appendLine("    publicTypesMustBeTransitiveDependencies = $it")
        }
        reportUndocumentedClassProperties?.let { appendLine("    reportUndocumentedClassProperties = $it") }
        lines.forEach { appendLine("    $it") }
        if (javaInterop.isNotEmpty) {
            appendLine("    javaInterop {")
            javaInterop.enabled?.let { appendLine("        enabled = $it") }
            javaInterop.lines.forEach { appendLine("        $it") }
            appendLine("    }")
        }
        append("}")
    }

    class ReportsBlock internal constructor() {
        var html: Boolean? = null
        var htmlFile: String? = null
        var sarif: Boolean? = null
        var sarifFile: String? = null
    }

    class JavaInteropBlock internal constructor() {
        internal val lines = mutableListOf<String>()

        var enabled: Boolean? = null

        fun severity(severity: Severity, vararg diagnostics: Diagnostic) {
            for (diagnostic in diagnostics) {
                require(diagnostic.javaInterop) { "$diagnostic isn't in the javaInterop group" }
                lines += "${diagnostic.property} = WatchdogSeverity.$severity"
            }
        }

        internal val isNotEmpty: Boolean get() = enabled != null || lines.isNotEmpty()
    }
}
