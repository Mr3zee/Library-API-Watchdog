package org.jetbrains.kotlinx.libs.api.watchdog

import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitCLP
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitComponentRegistrar

internal actual typealias WatchdogComponentRegistrarContract = DevKitComponentRegistrar
internal actual typealias WatchdogCommandLineProcessorContract = DevKitCLP
