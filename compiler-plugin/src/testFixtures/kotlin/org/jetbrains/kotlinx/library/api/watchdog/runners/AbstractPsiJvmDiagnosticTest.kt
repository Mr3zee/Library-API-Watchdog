package org.jetbrains.kotlinx.library.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.config.LanguageFeature.*
import org.jetbrains.kotlin.test.FirParser
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives.LANGUAGE
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogComponentRegistrar

/**
 * Runs the same diagnostics over PSI-backed sources - the representation the Analysis API (IDE)
 * builds FIR from - so checkers that inspect source trees are verified in both parser modes.
 */
open class AbstractPsiJvmDiagnosticTest : DevKitJvmDiagnosticTest(
    {
        defaultDirectives {
            LANGUAGE with listOf(
                "+$PropertyParamAnnotationDefaultTargetMode",
                "-$AnnotationDefaultTargetMigrationWarning",
                "+$ContextParameters"
            )
        }
        configurePlugin(WatchdogComponentRegistrar())
    },
    parser = FirParser.Psi,
)
