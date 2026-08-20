package org.jetbrains.kotlinx.libs.api.watchdog

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration

internal expect interface WatchdogComponentRegistrarContract {
    fun CompilerPluginRegistrar.ExtensionStorage.registerExtensions(
        configuration: CompilerConfiguration,
    )
}

internal expect interface WatchdogCommandLineProcessorContract {
    fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    )

    val pluginOptions: Collection<AbstractCliOption>
}
