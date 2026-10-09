package org.jetbrains.kotlinx.library.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.config.LanguageFeature.*
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives.LANGUAGE
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogComponentRegistrar
import org.jetbrains.kotlinx.library.api.watchdog.services.configureWatchdogOptions

open class AbstractJvmDiagnosticTest : DevKitJvmDiagnosticTest({
    configurePlugin(WatchdogComponentRegistrar())
    configureWatchdogOptions()
    defaultDirectives {
        LANGUAGE with listOf(
            "+$PropertyParamAnnotationDefaultTargetMode",
            "-$AnnotationDefaultTargetMigrationWarning",
            "+$ContextParameters"
        )
    }
})
