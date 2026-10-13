package org.jetbrains.kotlinx.library.api.watchdog

/**
 * The `apiWatchdog { }` block of a generated build script. The script imports `WatchdogSeverity`
 * and `WatchdogChecks`, so the rendered lines use their short names.
 */
class ApiWatchdogBlock internal constructor() {
    private val lines = mutableListOf<String>()
    private val javaInterop = JavaInteropBlock()

    /** `suggestAbiValidation = ...` */
    var suggestAbiValidation: Boolean? = null

    /** `publicTypeWithInternalApi = ...` */
    var publicTypeWithInternalApi: Boolean? = null

    /** `publicTypesMustBeTransitiveDependencies = ...` */
    var publicTypesMustBeTransitiveDependencies: Boolean? = null

    /** Sets the severity property of each diagnostic, inside the `javaInterop { }` group when it belongs there. */
    fun severity(severity: Severity, vararg diagnostics: Diagnostic) {
        for (diagnostic in diagnostics) {
            require(diagnostic !in alwaysErrorDiagnostics) { "$diagnostic has no severity property" }
            val line = "${diagnostic.property} = WatchdogSeverity.$severity"
            if (diagnostic.javaInterop) javaInterop.lines += line else lines += line
        }
    }

    /** Sets the severity property of every configurable diagnostic. */
    fun severityOfEveryCheck(severity: Severity) {
        severity(severity, *configurableDiagnostics.toTypedArray())
    }

    /** `ignore(WatchdogChecks.<diagnostic>, whenAnnotatedWith = listOf(...))` */
    fun ignore(diagnostic: Diagnostic, vararg whenAnnotatedWith: String) {
        val annotations = whenAnnotatedWith.joinToString { "\"$it\"" }
        lines += "ignore(WatchdogChecks.${diagnostic.name}, whenAnnotatedWith = listOf($annotations))"
    }

    /** `internalApiAnnotations.add(...)` for each name. */
    fun internalApiAnnotations(vararg annotations: String) {
        annotations.forEach { lines += "internalApiAnnotations.add(\"$it\")" }
    }

    /** `exemptNonTransitiveDependencies { <script> }` */
    fun exemptNonTransitiveDependencies(script: String) {
        lines += "exemptNonTransitiveDependencies {"
        lines += script.trimIndent().prependIndent("    ")
        lines += "}"
    }

    /** The `javaInterop { }` group. */
    fun javaInterop(block: JavaInteropBlock.() -> Unit) {
        javaInterop.block()
    }

    /** Script text placed into the block as is. */
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

    /** The `javaInterop { }` group of the `apiWatchdog { }` block. */
    class JavaInteropBlock internal constructor() {
        internal val lines = mutableListOf<String>()

        /** `enabled = ...` */
        var enabled: Boolean? = null

        /** Sets the severity property of each Java-interop diagnostic. */
        fun severity(severity: Severity, vararg diagnostics: Diagnostic) {
            for (diagnostic in diagnostics) {
                require(diagnostic.javaInterop) { "$diagnostic is not in the javaInterop group" }
                lines += "${diagnostic.property} = WatchdogSeverity.$severity"
            }
        }

        internal val isNotEmpty: Boolean get() = enabled != null || lines.isNotEmpty()
    }
}
