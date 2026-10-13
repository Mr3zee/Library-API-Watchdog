package org.jetbrains.kotlinx.library.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.DISABLE_FIR_DUMP_HANDLER
import org.jetbrains.kotlinx.library.api.watchdog.handlers.WatchdogDiagnosticMessagesHandler

/**
 * Checks the rendered message of every watchdog diagnostic against the `.messages.txt` dump next
 * to the test data, in addition to the inline `<!DIAGNOSTIC!>` markers. The FIR dump is off: the
 * messages don't depend on the FIR tree.
 */
open class AbstractJvmDiagnosticMessagesTest : DevKitJvmDiagnosticTest({
    configureWatchdogDiagnosticTest()
    defaultDirectives {
        +DISABLE_FIR_DUMP_HANDLER
    }
    configureFirHandlersStep {
        useHandlers(::WatchdogDiagnosticMessagesHandler)
    }
})
