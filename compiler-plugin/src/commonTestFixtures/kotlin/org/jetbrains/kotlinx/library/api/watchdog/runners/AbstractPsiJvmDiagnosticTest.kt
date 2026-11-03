package org.jetbrains.kotlinx.library.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.test.FirParser

/** Runs the diagnostics tests over PSI sources, the representation the Analysis API builds FIR from. */
open class AbstractPsiJvmDiagnosticTest : DevKitJvmDiagnosticTest(
    {
        configureWatchdogDiagnosticTest()
    },
    parser = FirParser.Psi,
)
