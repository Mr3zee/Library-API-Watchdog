package org.jetbrains.kotlinx.library.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.DISABLE_FIR_DUMP_HANDLER
import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.test.builders.configureFirHandlersStep
import org.jetbrains.kotlinx.library.api.watchdog.handlers.WatchdogDiagnosticMessagesHandler

/** Checks the markers and the `.messages.txt` dump. The FIR dump is off. */
open class AbstractJvmDiagnosticMessagesTest : DevKitJvmDiagnosticTest({
    configureWatchdogDiagnosticTest()
    defaultDirectives {
        +DISABLE_FIR_DUMP_HANDLER
    }
    configureFirHandlersStep {
        // Before Kotlin 2.4.0, the framework doesn't run the handlers that follow `NoFirCompilationErrorsHandler`
        // when the module has an error.
        useHandlersAtFirst(::WatchdogDiagnosticMessagesHandler)
    }
})
