package org.jetbrains.kotlinx.library.api.watchdog

/** The `apiWatchdog { }` block of a generated build script. */
class ApiWatchdogBlock internal constructor() {
    private val lines = mutableListOf<String>()
    private val javaInterop = JavaInteropBlock()

    var suggestAbiValidation: Boolean? = null

    var publicTypeWithInternalApi: Boolean? = null

    var publicTypesMustBeTransitiveDependencies: Boolean? = null

    /** A Java-interop diagnostic goes into the `javaInterop { }` group. */
    fun severity(severity: Severity, vararg diagnostics: Diagnostic) {
        for (diagnostic in diagnostics) {
            require(diagnostic !in alwaysErrorDiagnostics) { "$diagnostic has no severity property" }
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

    fun exemptNonTransitiveDependencies(script: String) {
        lines += "exemptNonTransitiveDependencies {"
        lines += script.trimIndent().prependIndent("    ")
        lines += "}"
    }

    fun javaInterop(block: JavaInteropBlock.() -> Unit) {
        javaInterop.block()
    }

    /** Script text written as is. */
    fun raw(script: String) {
        lines += script.trimIndent().lines()
    }

    internal fun render(): String = buildString {
        appendLine("apiWatchdog {")
        suggestAbiValidation?.let { appendLine("    suggestAbiValidation = $it") }
        publicTypeWithInternalApi?.let { appendLine("    publicTypeWithInternalApi = $it") }
        publicTypesMustBeTransitiveDependencies?.let {
            appendLine("    publicTypesMustBeTransitiveDependencies = $it")
        }
        lines.forEach { appendLine("    $it") }
        if (javaInterop.isNotEmpty) {
            appendLine("    javaInterop {")
            javaInterop.enabled?.let { appendLine("        enabled = $it") }
            javaInterop.lines.forEach { appendLine("        $it") }
            appendLine("    }")
        }
        append("}")
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
