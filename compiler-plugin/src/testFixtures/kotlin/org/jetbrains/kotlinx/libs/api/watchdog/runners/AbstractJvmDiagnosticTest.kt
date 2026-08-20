package org.jetbrains.kotlinx.libs.api.watchdog.runners

import org.jetbrains.kotlin.compiler.plugin.devkit.runners.DevKitJvmDiagnosticTest
import org.jetbrains.kotlin.compiler.plugin.devkit.services.configurePlugin
import org.jetbrains.kotlinx.libs.api.watchdog.WatchdogComponentRegistrar

open class AbstractJvmDiagnosticTest : DevKitJvmDiagnosticTest({
    configurePlugin(WatchdogComponentRegistrar())
})
