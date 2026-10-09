package org.jetbrains.kotlinx.library.api.watchdog.services

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogConfigurationKeys

/** Test-data directives that stand in for the watchdog plugin's command-line options. */
object WatchdogTestDirectives : SimpleDirectivesContainer() {
    /**
     * Fully qualified annotation class names passed as repeated `internalApiAnnotation` plugin
     * options, separated by spaces:
     * `// INTERNAL_API_ANNOTATIONS: third.party.InternalApi other.lib.Markers.Internal`.
     */
    val INTERNAL_API_ANNOTATIONS by stringDirective(
        description = "Annotation FQ names configured as internal API markers (internalApiAnnotation option)",
    )
}

/** Translates [WatchdogTestDirectives] into the compiler configuration the plugin reads. */
class WatchdogOptionsConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(WatchdogTestDirectives)

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        val annotations = module.directives[WatchdogTestDirectives.INTERNAL_API_ANNOTATIONS]
        if (annotations.isNotEmpty()) {
            configuration.put(WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS, annotations.toSet())
        }
    }
}

/** Registers the watchdog option directives. Runners call it next to `configurePlugin`. */
fun TestConfigurationBuilder.configureWatchdogOptions() {
    useConfigurators(::WatchdogOptionsConfigurator)
}
