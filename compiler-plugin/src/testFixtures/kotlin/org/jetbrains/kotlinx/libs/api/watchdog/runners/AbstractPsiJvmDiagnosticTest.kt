package org.jetbrains.kotlinx.libs.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlinx.libs.api.watchdog.WatchdogComponentRegistrar
import org.jetbrains.kotlin.test.FirParser

/**
 * Runs the same diagnostics over PSI-backed sources - the representation the Analysis API (IDE)
 * builds FIR from - so checkers that inspect source trees are verified in both parser modes.
 */
open class AbstractPsiJvmDiagnosticTest : DevKitJvmDiagnosticTest(
    { configurePlugin(WatchdogComponentRegistrar()) },
    parser = FirParser.Psi,
)
