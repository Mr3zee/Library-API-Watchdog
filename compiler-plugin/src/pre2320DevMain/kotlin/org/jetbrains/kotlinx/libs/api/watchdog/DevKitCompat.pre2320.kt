package org.jetbrains.kotlinx.libs.api.watchdog

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

internal actual interface WatchdogComponentRegistrarContract {
    actual fun CompilerPluginRegistrar.ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration,
    )
}

internal actual interface WatchdogCommandLineProcessorContract {
    actual fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    )

    actual val pluginOptions: Collection<AbstractCliOption>
}
