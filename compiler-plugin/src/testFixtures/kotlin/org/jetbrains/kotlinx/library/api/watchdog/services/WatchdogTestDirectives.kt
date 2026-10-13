package org.jetbrains.kotlinx.library.api.watchdog.services

import java.io.File
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.DirectivesContainer
import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices
import org.jetbrains.kotlin.test.services.moduleStructure
import org.jetbrains.kotlinx.library.api.watchdog.WatchdogConfigurationKeys
import org.jetbrains.kotlinx.library.api.watchdog.fir.DependencyDeclaration

/** The directives that replace the plugin options in test data. A path is relative to the test data file. */
object WatchdogTestDirectives : SimpleDirectivesContainer() {
    /** `// INTERNAL_API_ANNOTATIONS: third.party.InternalApi other.lib.Markers.Internal` */
    val INTERNAL_API_ANNOTATIONS by stringDirective(
        description = "Annotation FQ names configured as internal API markers (internalApiAnnotation option)",
    )

    /**
     * `// COMPILE_DEPENDENCY_PATHS: nonTransitiveDependency.deps/models`. A root is a directory
     * with one `.class` file for each class. The checker looks the class up by path, so an empty
     * file is enough.
     */
    val COMPILE_DEPENDENCY_PATHS by stringDirective(
        description = "Compile classpath roots relative to the test data directory (compileDependencyPaths option)",
    )

    /** The roots of [COMPILE_DEPENDENCY_PATHS] that consumers get. */
    val TRANSITIVE_DEPENDENCY_PATHS by stringDirective(
        description = "Transitive classpath roots relative to the test data directory (transitiveDependencyPaths option)",
    )

    /** `// DEPENDENCY_DECLARATIONS: nonTransitiveDependency.deps/models implementation "com.example:models:1.0"` */
    val DEPENDENCY_DECLARATIONS by stringDirective(
        description = "Root, configuration, and notation triples (dependencyDeclarations option)",
    )
}

class WatchdogOptionsConfigurator(testServices: TestServices) : EnvironmentConfigurator(testServices) {
    override val directiveContainers: List<DirectivesContainer>
        get() = listOf(WatchdogTestDirectives)

    override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
        val annotations = module.directives[WatchdogTestDirectives.INTERNAL_API_ANNOTATIONS]
        if (annotations.isNotEmpty()) {
            configuration.put(WatchdogConfigurationKeys.INTERNAL_API_ANNOTATIONS, annotations.toSet())
        }

        val compileRoots = module.directives[WatchdogTestDirectives.COMPILE_DEPENDENCY_PATHS]
        if (compileRoots.isNotEmpty()) {
            configuration.put(WatchdogConfigurationKeys.COMPILE_DEPENDENCY_PATHS, compileRoots.mapTo(linkedSetOf(), ::absolutePath))
            configuration.put(
                WatchdogConfigurationKeys.TRANSITIVE_DEPENDENCY_PATHS,
                module.directives[WatchdogTestDirectives.TRANSITIVE_DEPENDENCY_PATHS].mapTo(linkedSetOf(), ::absolutePath),
            )
            val declarations = module.directives[WatchdogTestDirectives.DEPENDENCY_DECLARATIONS]
            require(declarations.size % 3 == 0) {
                "DEPENDENCY_DECLARATIONS holds root, configuration, and notation triples, got $declarations"
            }
            configuration.put(
                WatchdogConfigurationKeys.DEPENDENCY_DECLARATIONS,
                declarations.chunked(3).associate { (root, dependencyConfiguration, notation) ->
                    absolutePath(root) to DependencyDeclaration(dependencyConfiguration, notation)
                },
            )
        }
    }

    private fun absolutePath(relativePath: String): String {
        val testDataDirectory = testServices.moduleStructure.originalTestDataFiles.first().parentFile
        return File(testDataDirectory, relativePath).absolutePath
    }
}

/** A runner calls it next to `configurePlugin`. */
fun TestConfigurationBuilder.configureWatchdogOptions() {
    useConfigurators(::WatchdogOptionsConfigurator)
}
