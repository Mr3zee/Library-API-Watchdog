package org.jetbrains.kotlinx.libs.api.watchdog

import java.io.File
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
public class WatchdogSupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    /** Enabled lazily when Gradle realizes the update task. Never exposed as user configuration. */
    private lateinit var collectDiagnosticsForExempts: Property<Boolean>

    /**
     * Test sources are never published, so they carry no API contract to watch. The Kotlin Gradle
     * plugin already keeps explicit API mode off for test compilations, which is enough for the
     * usual `kotlin { explicitApi() }` setup, but a raw `-Xexplicit-api` flag added to every
     * compilation would otherwise turn the checks on there too. Skipping the compiler plugin for
     * test compilations makes the exclusion hold whichever way explicit API mode is enabled.
     */
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        !kotlinCompilation.isTestCompilation()

    override fun apply(target: Project) {
        val extension = target.extensions.create("apiWatchdog", WatchdogGradleExtension::class.java)
        val collectDiagnostics = target.objects.property(Boolean::class.java).convention(false)
        collectDiagnosticsForExempts = collectDiagnostics
        target.registerUpdateBackwardsCompatibilityExemptsTask(collectDiagnostics)
        target.afterEvaluate { project ->
            if (!project.explicitApiWarningSuppressed() && !project.hasExplicitApiMode()) {
                project.logger.warn(missingExplicitApiWarning(project.path))
            }
            if (extension.suggestAbiValidation.get() && !project.hasAbiValidation()) {
                project.logger.warn(abiValidationSuggestion(project.path))
            }
        }
    }

    /**
     * Registers the fixer task. [applyToCompilation] wires every main Kotlin compilation into it,
     * so KGP itself drives JVM, JS, native, Wasm, and metadata analysis. The fixer classpath uses
     * the compiler embeddable matching the project's Kotlin Gradle plugin for PSI parsing only.
     */
    private fun Project.registerUpdateBackwardsCompatibilityExemptsTask(
        collectDiagnostics: Property<Boolean>,
    ): TaskProvider<UpdateBackwardsCompatibilityExemptsTask> {
        val dependencyHandler = dependencies
        val kotlinCompilerDependency = provider {
            val kotlinVersion = plugins.withType(KotlinBasePlugin::class.java)
                .firstOrNull()?.pluginVersion
                ?: error("The Kotlin Gradle plugin must be applied alongside libs-api-watchdog")
            dependencyHandler.create("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
        }
        val fixerClasspath = configurations.register(
            FIXER_CLASSPATH_CONFIGURATION_NAME,
            Action { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                configuration.description = "The watchdog exemption fixer and its Kotlin PSI runtime"
                configuration.dependencies.add(
                    dependencyHandler.create(
                        "${info.artifact.groupId}:$FIXER_ARTIFACT_ID:${info.artifact.version}",
                    ),
                )
                configuration.dependencies.addLater(kotlinCompilerDependency)
            },
        )

        return tasks.register(UPDATE_EXEMPTS_TASK_NAME, UpdateBackwardsCompatibilityExemptsTask::class.java) { task ->
            // Realizing the task enables collection. Compile-task inputs and options
            // consume this property lazily during task-graph construction and execution.
            collectDiagnostics.set(true)
            task.group = "api watchdog"
            task.description = "Acknowledges every watchdog diagnostic in the main Kotlin " +
                    "compilation sources with the matching @Intentionally* annotation and the " +
                    "FOR_BACKWARDS_COMPATIBILITY reason"
            task.compilationNames.convention(emptyList())
            task.projectDirectory.set(layout.projectDirectory)
            task.fixerClasspath.from(fixerClasspath)
        }
    }

    override fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val extension = extensions.getByType(WatchdogGradleExtension::class.java)
        val isMain = kotlinCompilation.name == KotlinCompilation.MAIN_COMPILATION_NAME
        val collect = collectDiagnosticsForExempts.map { it && isMain }
        val compileDependencies = configurations.named(kotlinCompilation.compileDependencyConfigurationName)
        val transitiveDependencies = transitiveDependenciesFor(kotlinCompilation, compileDependencies)
        val reportFile = layout.buildDirectory.file(
            "reports/api-watchdog/diagnostics/${kotlinCompilation.compileKotlinTaskName}.tsv"
        )

        if (isMain) {
            val updateTask = tasks.named(
                UPDATE_EXEMPTS_TASK_NAME,
                UpdateBackwardsCompatibilityExemptsTask::class.java,
            )
            updateTask.configure { task ->
                task.compilationNames.add(kotlinCompilation.compileKotlinTaskName)
                task.diagnosticReports.from(provider {
                    if (collect.get()) reportFile.get().asFile else emptyList<File>()
                })
                task.dependsOn(provider {
                    if (collect.get()) kotlinCompilation.compileTaskProvider else emptyList<Any>()
                })
            }

            kotlinCompilation.compileTaskProvider.configure { task ->
                task.inputs.property("apiWatchdog.collectDiagnosticsForExempts", collect)
                // The report deliberately stays outside the task's declared outputs. JVM compile
                // tasks prepare every extra output as a directory, while the compiler plugin
                // needs a file. Collection mode always executes and bypasses the build cache so
                // an internal report can never be stale or absent after an up-to-date/cache hit.
                task.outputs.upToDateWhen { !collect.get() }
                task.outputs.doNotCacheIf("watchdog diagnostic collection is enabled") {
                    collect.get()
                }
                task.compilerOptions.freeCompilerArgs.addAll(collect.map {
                    if (it) listOf("-Xexplicit-api=warning") else emptyList()
                })
                task.doFirst {
                    if (collect.get()) {
                        reportFile.get().asFile.apply {
                            parentFile.mkdirs()
                            writeText("")
                        }
                    }
                }
            }
        }

        return providers.provider {
            buildList {
                extension.diagnosticSeverities().forEach { (diagnostic, severity) ->
                    val configured = severity.get()
                    val effective = if (collect.get() && configured != WatchdogSeverity.NONE) {
                        WatchdogSeverity.WARNING
                    } else {
                        configured
                    }
                    add(SubpluginOption("diagnosticSeverity", "$diagnostic:${effective.name.lowercase()}"))
                }
                if (collect.get()) {
                    add(FilesSubpluginOption("diagnosticsOutputFile", listOf(reportFile.get().asFile)))
                }
                if (extension.publicTypesMustBeTransitiveDependencies.get()) {
                    add(
                        SubpluginOption(
                            "compileDependencyPaths",
                            compileDependencies.get().asPath,
                        ),
                    )
                    add(SubpluginOption("transitiveDependencyPaths", transitiveDependencies.get().asPath))
                }
            }
        }
    }

    /**
     * Resolves the dependencies inherited by this target's published API elements with the same
     * variant attributes as its compile classpath. Extending from the outgoing configuration
     * preserves source-set hierarchies, `api` transitives, project dependencies and
     * `compileOnlyApi` where the platform supports it.
     */
    private fun Project.transitiveDependenciesFor(
        kotlinCompilation: KotlinCompilation<*>,
        compileDependencies: Provider<Configuration>,
    ): NamedDomainObjectProvider<Configuration> {
        // Android publishes one API-elements configuration per variant, and its compilation name
        // is that variant name. The target-level configuration does not carry variant-specific
        // dependencies such as build-type and flavor `api` declarations. Some non-library Android
        // variants have no publishable API elements, so retain the target configuration as a safe
        // fallback for those.
        val androidApiElementsName = "${kotlinCompilation.name}ApiElements"
        val publishedApiName = if (
            kotlinCompilation.platformType == KotlinPlatformType.androidJvm &&
            androidApiElementsName in configurations.names
        ) {
            androidApiElementsName
        } else {
            kotlinCompilation.target.apiElementsConfigurationName
        }
        val publishedApi = configurations.named(publishedApiName)
        return configurations.register(
            "apiWatchdog${kotlinCompilation.disambiguatedName.replaceFirstChar(Char::uppercaseChar)}TransitiveDependencies",
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description =
                "Dependencies exposed transitively by ${kotlinCompilation.target.name} API elements"
            configuration.extendsFrom(publishedApi.get())
            configuration.attributes.copyFrom(compileDependencies.get().attributes)
        }
    }

    private fun AttributeContainer.copyFrom(source: AttributeContainer) {
        for (key in source.keySet()) {
            @Suppress("UNCHECKED_CAST")
            key as Attribute<Any>
            source.getAttribute(key)?.let { attribute(key, it) }
        }
    }

    private companion object {
        /** The name of the task that acknowledges existing diagnostics as backwards-compatibility exemptions. */
        private const val UPDATE_EXEMPTS_TASK_NAME = "updateBackwardsCompatibilityExempts"

        /**
         * The suffix every test compilation that isn't simply called `test` carries. Android
         * compilations are named after their variant (`debugUnitTest`, `debugAndroidTest`,
         * `releaseScreenshotTest`), and the Android target of a multiplatform project names them
         * `hostTest` and `deviceTest`, so there is no single name to recognize them by.
         */
        private const val TEST_COMPILATION_NAME_SUFFIX = "Test"

        /**
         * Whether the compilation builds test sources. Test fixtures are deliberately not treated
         * as tests: they are published alongside the library, so their API is worth watching.
         */
        private fun KotlinCompilation<*>.isTestCompilation(): Boolean =
            name == KotlinCompilation.TEST_COMPILATION_NAME || name.endsWith(TEST_COMPILATION_NAME_SUFFIX)

        /** The artifact carrying the standalone fixer tool, published next to this plugin. */
        private const val FIXER_ARTIFACT_ID = "exempts-fixer"

        /** Resolvable runtime used only by [UpdateBackwardsCompatibilityExemptsTask]. */
        private const val FIXER_CLASSPATH_CONFIGURATION_NAME = "apiWatchdogExemptsFixerClasspath"

        /** The standalone Binary Compatibility Validator's plugin id. */
        private const val BCV_PLUGIN_ID = "org.jetbrains.kotlinx.binary-compatibility-validator"

        /** Tasks the Kotlin Gradle plugin registers only once its ABI validation is activated. */
        private val ABI_VALIDATION_TASK_NAMES = listOf("checkKotlinAbi", "checkLegacyAbi")

        /**
         * Deliberately undocumented escape hatch: `-P` this property to `true` to silence the
         * missing-explicit-API warning should its detection misjudge a project.
         */
        private const val SUPPRESS_EXPLICIT_API_WARNING_PROPERTY =
            "org.jetbrains.kotlinx.libs.api.watchdog.suppressExplicitApiWarning"

        private fun Project.explicitApiWarningSuppressed(): Boolean =
            providers.gradleProperty(SUPPRESS_EXPLICIT_API_WARNING_PROPERTY)
                .map(String::toBoolean)
                .getOrElse(false)

        /**
         * Whether explicit API mode (strict or warning) is enabled through the `kotlin` DSL or a
         * raw flag in its top-level compiler options. Inspecting the extension keeps compile tasks
         * unrealized during configuration.
         */
        private fun Project.hasExplicitApiMode(): Boolean {
            val kotlin = extensions.findByName("kotlin") as? KotlinBaseExtension ?: return false
            val mode = kotlin.explicitApi
            if (mode != null && mode != ExplicitApiMode.Disabled) return true
            val compilerOptions = kotlin as? HasConfigurableKotlinCompilerOptions<*> ?: return false
            return compilerOptions.compilerOptions.freeCompilerArgs.orNull.orEmpty().any {
                it.startsWith("-Xexplicit-api=") && it != "-Xexplicit-api=disable"
            }
        }

        /**
         * Whether binary compatibility validation is configured in this isolated project: the
         * standalone Binary Compatibility Validator plugin or Kotlin's built-in ABI validation.
         */
        private fun Project.hasAbiValidation(): Boolean {
            if (pluginManager.hasPlugin(BCV_PLUGIN_ID)) return true
            val kotlin = extensions.findByName("kotlin") as? ExtensionAware ?: return false
            // Kotlin 2.2 and 2.3 register the DSL as a `kotlin` sub-extension whose `enabled`
            // flag is the opt-in. The flag is deprecated for removal in the API compiled
            // against, so it is read reflectively.
            val legacyAbiValidation = kotlin.extensions.findByName("abiValidation")
            if (legacyAbiValidation != null) {
                val enabled = runCatching {
                    legacyAbiValidation.javaClass.getMethod("getEnabled").invoke(legacyAbiValidation)
                }.getOrNull()
                return (enabled as? Provider<*>)?.orNull == true
            }
            // Kotlin 2.4+ activates ABI validation the moment the `abiValidation` DSL property
            // is touched (its getter has that side effect, so it must not be called here).
            // Activation is observed through the tasks it registers instead.
            return ABI_VALIDATION_TASK_NAMES.any(tasks.names::contains)
        }

        private fun missingExplicitApiWarning(projectPath: String): String = """
            |Project '$projectPath' applies libs-api-watchdog but doesn't enable explicit API mode, so the
            |watchdog registers no checks: there is no declared public API contract to watch. Enable it in
            |the module's build script:
            |
            |    kotlin {
            |        explicitApi()
            |    }
            |
            |The `explicitApiWarning()` variant and the `-Xexplicit-api` compiler flag also count.
        """.trimMargin()

        private fun abiValidationSuggestion(projectPath: String): String = """
            |Project '$projectPath' applies libs-api-watchdog but no binary compatibility validation is enabled.
            |The watchdog reviews the shape of new API declarations, while binary compatibility validation
            |compares each build against a committed dump of the released API surface and catches accidental
            |breaking changes to it. Enable the Kotlin Gradle plugin's built-in ABI validation in the
            |module's build script (on Kotlin 2.2 and 2.3, write `abiValidation { enabled.set(true) }`
            |instead):
            |
            |    import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
            |
            |    kotlin {
            |        @OptIn(ExperimentalAbiValidation::class)
            |        abiValidation()
            |    }
            |
            |See https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html for the check and
            |dump tasks and the full configuration reference. On older Kotlin versions, apply the standalone
            |Binary Compatibility Validator plugin instead: https://github.com/Kotlin/binary-compatibility-validator.
            |
            |Disable this suggestion with `apiWatchdog { suggestAbiValidation = false }`.
        """.trimMargin()
    }
}
