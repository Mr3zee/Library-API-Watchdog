package org.jetbrains.kotlinx.library.api.watchdog

import org.jetbrains.kotlin.compiler.plugin.devkit.generateDevKitTestsWithJUnit5
import org.jetbrains.kotlinx.library.api.watchdog.runners.AbstractJvmDiagnosticTest
import org.jetbrains.kotlinx.library.api.watchdog.runners.AbstractPsiJvmDiagnosticTest

fun main() = generateDevKitTestsWithJUnit5 {
    testClass<AbstractJvmDiagnosticTest> {
        model("diagnostics")
    }
    testClass<AbstractPsiJvmDiagnosticTest> {
        model("diagnostics")
    }
}
