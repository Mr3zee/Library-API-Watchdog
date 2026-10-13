package org.jetbrains.kotlinx.library.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlin.config.LanguageFeature.AnnotationDefaultTargetMigrationWarning
import org.jetbrains.kotlin.config.LanguageFeature.ContextParameters
import org.jetbrains.kotlin.config.LanguageFeature.PropertyParamAnnotationDefaultTargetMode
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.LanguageSettingsDirectives.LANGUAGE
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogComponentRegistrar
import org.jetbrains.kotlinx.library.api.watchdog.services.configureWatchdogOptions

/** Registers the watchdog plugin, its option directives, and the language features the test data uses. */
fun TestConfigurationBuilder.configureWatchdogDiagnosticTest() {
    configurePlugin(WatchdogComponentRegistrar())
    configureWatchdogOptions()
    defaultDirectives {
        LANGUAGE with listOf(
            "+$PropertyParamAnnotationDefaultTargetMode",
            "-$AnnotationDefaultTargetMigrationWarning",
            "+$ContextParameters",
        )
    }
}
