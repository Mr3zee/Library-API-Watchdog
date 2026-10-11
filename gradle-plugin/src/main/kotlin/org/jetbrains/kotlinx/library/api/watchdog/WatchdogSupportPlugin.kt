package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
public class WatchdogSupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    /** Enabled once the task graph of the build schedules an exemptions update or report task. */
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
        // The user-facing bucket is declarable only. Each compilation resolves it with its own
        // classpath attributes, see [exemptNonTransitiveDependenciesFor].
        val exemptNonTransitiveDependencies = target.configurations.register(
            EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME,
        ) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
            configuration.description = "Dependencies whose types may appear in the public API " +
                    "although they are not published transitively, see apiWatchdog.exemptNonTransitiveDependencies"
        }
        val extension = target.extensions.create(
            "apiWatchdog",
            WatchdogGradleExtension::class.java,
            exemptNonTransitiveDependencies,
        )
        val collectDiagnostics = target.objects.property(Boolean::class.java).convention(false)
        collectDiagnosticsForExempts = collectDiagnostics
        target.registerBackwardsCompatibilityExemptsTasks(collectDiagnostics)
        target.afterEvaluate { project ->
            if (!project.explicitApiWarningSuppressed() && !project.hasExplicitApiMode()) {
                project.logger.warn(missingExplicitApiWarning(project.path))
            }
            if (extension.suggestAbiValidation.get() && !project.hasAbiValidation()) {
                project.logger.warn(
                    abiValidationSuggestion(project.path, project.kotlinPluginVersion()),
                )
            }
        }
    }

    /**
     * Registers the source update and non-mutating report tasks. [applyToCompilation] wires every
     * main Kotlin compilation into both, so KGP drives JVM, JS, native, Wasm, and metadata analysis.
     */
    private fun Project.registerBackwardsCompatibilityExemptsTasks(
        collectDiagnostics: Property<Boolean>,
    ) {
        val dependencyHandler = dependencies
        val kotlinVersion = provider { kotlinPluginVersion() }
        val fixerDependency = dependencyHandler.create(
            "${info.artifact.groupId}:$FIXER_ARTIFACT_ID:${info.artifact.version}",
        )
        val kotlinCompilerDependency = kotlinVersion.map {
            dependencyHandler.create("org.jetbrains.kotlin:kotlin-compiler:$it")
        }
        val fixerArtifact = configurations.register(
            FIXER_ARTIFACT_CONFIGURATION_NAME,
            Action { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                configuration.isTransitive = false
                configuration.description = "The multi-version watchdog exemption fixer artifact"
                configuration.dependencies.add(fixerDependency)
            },
        )
        val fixerClasspath = configurations.register(
            FIXER_CLASSPATH_CONFIGURATION_NAME,
            Action { configuration ->
                configuration.isCanBeConsumed = false
                configuration.isCanBeResolved = true
                configuration.description = "The Kotlin PSI runtime for the watchdog exemption fixer"
                configuration.dependencies.addLater(kotlinCompilerDependency)
            },
        )

        tasks.register(
            UPDATE_EXEMPTS_TASK_NAME,
            UpdateBackwardsCompatibilityExemptsTask::class.java,
        ) { task ->
            task.group = "api watchdog"
            task.description = "Acknowledges every watchdog diagnostic in the main Kotlin " +
                    "compilation sources with the matching @Intentionally* annotation and the " +
                    "FOR_BACKWARDS_COMPATIBILITY reason"
            task.compilationNames.convention(emptyList())
            task.projectDirectory.set(layout.projectDirectory)
            task.kotlinVersion.set(kotlinVersion)
            task.fixerArtifact.from(fixerArtifact)
            task.fixerClasspath.from(fixerClasspath)
        }
        val reportTask = tasks.register(
            GENERATE_EXEMPTS_REPORT_TASK_NAME,
            GenerateBackwardsCompatibilityExemptsReportTask::class.java,
        ) { task ->
            task.group = "api watchdog"
            task.description =
                "Reports applied and not-applied backwards-compatibility exemptions without changing sources"
            task.compilationNames.convention(emptyList())
            task.projectDirectory.set(layout.projectDirectory)
            @Suppress("UnstableApiUsage")
            task.projectIdentityPath.set(isolated.buildTreePath)
            task.kotlinVersion.set(kotlinVersion)
            task.fixerArtifact.from(fixerArtifact)
            task.fixerClasspath.from(fixerClasspath)
            task.reportFile.convention(
                layout.buildDirectory.file("reports/api-watchdog/backwards-compatibility-exempts.html")
            )
            task.reportDataFile.convention(
                layout.buildDirectory.file("reports/api-watchdog/backwards-compatibility-exempts.data")
            )
        }
        // Only a scheduled update or report task enables collection. Task realization is not a
        // usable signal: IDE sync, `tasks.all { }`, and the `tasks` report realize every task,
        // which would demote the configured severities in builds that never run these tasks.
        // The graph also covers a report task scheduled indirectly through variant resolution.
        // Compile-task inputs and options consume this property lazily during execution.
        val exemptsTaskPaths = listOf(UPDATE_EXEMPTS_TASK_NAME, GENERATE_EXEMPTS_REPORT_TASK_NAME)
            .map { if (path == ":") ":$it" else "$path:$it" }
        gradle.taskGraph.whenReady { graph ->
            collectDiagnostics.set(exemptsTaskPaths.any(graph::hasTask))
        }
        configurations.register(EXEMPTS_REPORT_ELEMENTS_CONFIGURATION) { configuration ->
            configuration.isCanBeConsumed = true
            configuration.isCanBeResolved = false
            configuration.description =
                "The backwards-compatibility exemptions report for this project"
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, EXEMPTS_REPORT_USAGE),
            )
            configuration.attributes.attribute(
                Category.CATEGORY_ATTRIBUTE,
                objects.named(Category::class.java, Category.DOCUMENTATION),
            )
            configuration.outgoing.artifact(reportTask.flatMap { it.reportDataFile }) { artifact ->
                artifact.builtBy(reportTask)
            }
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
        val exemptNonTransitiveDependencies = exemptNonTransitiveDependenciesFor(kotlinCompilation, compileDependencies)
        val reportFile = layout.buildDirectory.file(
            "reports/api-watchdog/diagnostics/${kotlinCompilation.compileKotlinTaskName}.tsv"
        )

        if (isMain) {
            val updateTask = tasks.named(
                UPDATE_EXEMPTS_TASK_NAME,
                UpdateBackwardsCompatibilityExemptsTask::class.java,
            )
            val reportTask = tasks.named(
                GENERATE_EXEMPTS_REPORT_TASK_NAME,
                GenerateBackwardsCompatibilityExemptsReportTask::class.java,
            )
            updateTask.configure { task ->
                task.compilationNames.add(kotlinCompilation.compileKotlinTaskName)
                task.diagnosticReports.from(reportFile)
                task.dependsOn(kotlinCompilation.compileTaskProvider)
            }
            reportTask.configure { task ->
                task.compilationNames.add(kotlinCompilation.compileKotlinTaskName)
                task.sourceFiles.from(kotlinCompilation.allKotlinSourceSets.map { it.kotlin })
                task.diagnosticReports.from(reportFile)
                task.dependsOn(kotlinCompilation.compileTaskProvider)
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

        // Native compile tasks read this option list while they are configured, before the task
        // graph settles whether diagnostics are collected. The set of options is therefore fixed
        // and every collection-dependent value is lazy, so it is computed when the compiler
        // arguments are built. The compiler plugin ignores the blank placeholder values.
        val checkDependencyExposure = extension.publicTypesMustBeTransitiveDependencies.map { it && !collect.get() }
        val compilesForAndroid = compileDependencies.map { kotlinCompilation.compilesForAndroid(it) }
        return providers.provider {
            buildList {
                extension.diagnosticSeverities.forEach { (diagnostic, severity) ->
                    add(
                        SubpluginOption(
                            "diagnosticSeverity",
                            lazy {
                                val configured = severity.get()
                                val effective = if (collect.get() && configured != WatchdogSeverity.NONE) {
                                    WatchdogSeverity.WARNING
                                } else {
                                    configured
                                }
                                "$diagnostic:${effective.name.lowercase()}"
                            },
                        ),
                    )
                }
                extension.annotationIgnoreRules().get().forEach { rule ->
                    add(SubpluginOption("ignoreWhenAnnotated", rule))
                }
                extension.internalApiAnnotations.get().forEach { annotationName ->
                    require(annotationName.isNotBlank() && annotationName.none { it.isWhitespace() || it == ':' }) {
                        "An API Watchdog internal API annotation must be a non-blank fully qualified " +
                                "class name: '$annotationName'"
                    }
                    add(SubpluginOption("internalApiAnnotation", annotationName))
                }
                add(
                    SubpluginOption(
                        "diagnosticsOutputFile",
                        lazy { if (collect.get()) reportFile.get().asFile.path else "" },
                    ),
                )
                add(SubpluginOption("updatingBackwardsCompatibilityExempts", lazy { collect.get().toString() }))
                add(
                    SubpluginOption(
                        "publicTypeWithInternalApi",
                        lazy { (extension.publicTypeWithInternalApi.get() && !collect.get()).toString() },
                    ),
                )
                add(
                    SubpluginOption(
                        "compileDependencyPaths",
                        lazy {
                            if (!checkDependencyExposure.get()) return@lazy ""
                            dependencyFiles(compileDependencies.get(), compilesForAndroid.get()).asPath
                        },
                    ),
                )
                // Exempt dependencies travel as transitive paths: the compiler plugin treats
                // every type found under them as published and needs no option of its own.
                add(
                    SubpluginOption(
                        "transitiveDependencyPaths",
                        lazy {
                            if (!checkDependencyExposure.get()) return@lazy ""
                            val android = compilesForAndroid.get()
                            val files = dependencyFiles(transitiveDependencies.get(), android).files +
                                    dependencyFiles(exemptNonTransitiveDependencies.get(), android).files
                            files.joinToString(File.pathSeparator, transform = File::getPath)
                        },
                    ),
                )
            }
        }
    }

    /**
     * Whether this compilation builds for Android, by the environment its compile classpath
     * requests. The platform type is not enough: the Android target of AGP 8's
     * `com.android.kotlin.multiplatform.library` plugin reports `jvm`, while its compile
     * classpath still asks for Android artifacts and AARs like every other Android compilation.
     */
    private fun KotlinCompilation<*>.compilesForAndroid(compileDependencies: Configuration): Boolean =
        platformType == KotlinPlatformType.androidJvm ||
                compileDependencies.attributes.getAttribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE)?.name ==
                TargetJvmEnvironment.ANDROID

    /**
     * The files of [configuration] as the compiler sees them. An Android library publishes an AAR
     * whose classes reach the compiler through AGP's classes-jar artifact transform, and a
     * project AAR has no artifact at all without that request. Android compilations therefore
     * resolve every configuration through the same transform, which leaves plain jars untouched,
     * so the paths match the ones the compiler reports for a type's origin. Elsewhere the
     * configuration's own artifacts are the compiler's input.
     */
    private fun dependencyFiles(configuration: Configuration, android: Boolean): FileCollection =
        if (android) {
            configuration.incoming.artifactView { view ->
                view.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ANDROID_CLASSES_JAR)
            }.files
        } else {
            configuration
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
        val publishedApi = configurations.named(publishedApiElementsName(kotlinCompilation))
        return registerCompilationClasspathView(
            kotlinCompilation,
            compileDependencies,
            suffix = "TransitiveDependencies",
            description = "Dependencies exposed transitively by ${kotlinCompilation.target.name} API elements",
        ) { configuration ->
            configuration.extendsFrom(publishedApi.get())
        }
    }

    /**
     * Resolves the `apiWatchdogExemptNonTransitive` bucket with the variant attributes of this
     * compilation's compile classpath. The resolved files join the transitive paths handed to the
     * compiler plugin, so the exemption covers the declared dependencies together with everything
     * they pull in, exactly like Gradle resolves them for the compilation itself.
     */
    private fun Project.exemptNonTransitiveDependenciesFor(
        kotlinCompilation: KotlinCompilation<*>,
        compileDependencies: Provider<Configuration>,
    ): NamedDomainObjectProvider<Configuration> {
        val exempt = configurations.named(EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME)
        return registerCompilationClasspathView(
            kotlinCompilation,
            compileDependencies,
            suffix = "ExemptNonTransitiveDependencies",
            description = "Dependencies whose types the public API of ${kotlinCompilation.target.name} may expose " +
                    "although they are not published transitively",
        ) { configuration ->
            configuration.extendsFrom(exempt.get())
        }
    }

    /**
     * Registers a resolvable view of some dependency declarations through the lens of this
     * compilation's compile classpath: the same variant attributes select the same artifacts, and
     * consistent resolution pins every shared module to the version the compilation compiles
     * against. Without it a conflict-upgraded version on the compile classpath would resolve to a
     * different jar here, and the compiler plugin, which matches dependency files by path, would
     * miss it. The pinning also lets an exempt dependency be declared without a version.
     */
    private fun Project.registerCompilationClasspathView(
        kotlinCompilation: KotlinCompilation<*>,
        compileDependencies: Provider<Configuration>,
        suffix: String,
        description: String,
        configure: (Configuration) -> Unit,
    ): NamedDomainObjectProvider<Configuration> {
        val classifier = kotlinCompilation.target.disambiguationClassifier.orEmpty()
        val compilationName = (classifier + kotlinCompilation.name.replaceFirstChar(Char::uppercaseChar))
            .replaceFirstChar(Char::uppercaseChar)
        return configurations.register("apiWatchdog$compilationName$suffix") { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = true
            configuration.description = description
            configure(configuration)
            val compileClasspath = compileDependencies.get()
            configuration.attributes.copyFrom(compileClasspath.attributes)
            @Suppress("UnstableApiUsage")
            configuration.shouldResolveConsistentlyWith(compileClasspath)
        }
    }

    /** The configuration whose dependencies this compilation publishes to consumers. */
    private fun Project.publishedApiElementsName(kotlinCompilation: KotlinCompilation<*>): String {
        // Android publishes one API-elements configuration per variant, and its compilation name
        // is that variant name. The target-level configuration does not carry variant-specific
        // dependencies such as build-type and flavor `api` declarations. Some non-library Android
        // variants have no publishable API elements, so retain the target configuration as a safe
        // fallback for those.
        val androidApiElementsName = "${kotlinCompilation.name}ApiElements"
        return if (
            kotlinCompilation.platformType == KotlinPlatformType.androidJvm &&
            androidApiElementsName in configurations.names
        ) {
            androidApiElementsName
        } else {
            kotlinCompilation.target.apiElementsConfigurationName
        }
    }

    private fun AttributeContainer.copyFrom(source: AttributeContainer) {
        for (key in source.keySet()) {
            @Suppress("UNCHECKED_CAST")
            key as Attribute<Any>
            source.getAttribute(key)?.let { attribute(key, it) }
        }
    }

    internal companion object {
        /** The name of the task that acknowledges existing diagnostics as backwards-compatibility exemptions. */
        private const val UPDATE_EXEMPTS_TASK_NAME = "updateBackwardsCompatibilityExempts"

        /** The non-mutating module report task whose data variant can be aggregated. */
        private const val GENERATE_EXEMPTS_REPORT_TASK_NAME = "generateBackwardsCompatibilityExemptsReport"

        /** AGP's artifact type for the classes jar of an AAR, which its Kotlin compilations consume. */
        private const val ANDROID_CLASSES_JAR = "android-classes-jar"

        /** The declarable bucket behind `apiWatchdog.exemptNonTransitiveDependencies`. */
        internal const val EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME = "apiWatchdogExemptNonTransitive"

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
        private const val FIXER_ARTIFACT_ID = "kotlin-library-api-watchdog-exempts-fixer"

        /** Resolvable runtime used by the exemptions update and report tasks. */
        private const val FIXER_CLASSPATH_CONFIGURATION_NAME = "apiWatchdogExemptsFixerClasspath"

        /** Multi-version fixer artifact resolved separately from its matching compiler runtime. */
        private const val FIXER_ARTIFACT_CONFIGURATION_NAME = "apiWatchdogExemptsFixerArtifact"

        /** The standalone Binary Compatibility Validator's plugin id. */
        private const val BCV_PLUGIN_ID = "org.jetbrains.kotlinx.binary-compatibility-validator"

        /** Tasks the Kotlin Gradle plugin registers only once its ABI validation is activated. */
        private val ABI_VALIDATION_TASK_NAMES = listOf("checkKotlinAbi", "checkLegacyAbi")

        /**
         * Deliberately undocumented escape hatch: `-P` this property to `true` to silence the
         * missing-explicit-API warning should its detection misjudge a project.
         */
        private const val SUPPRESS_EXPLICIT_API_WARNING_PROPERTY =
            "org.jetbrains.kotlin.library.api-watchdog.suppressExplicitApiWarning"

        private fun Project.explicitApiWarningSuppressed(): Boolean =
            providers.gradleProperty(SUPPRESS_EXPLICIT_API_WARNING_PROPERTY)
                .map(String::toBoolean)
                .getOrElse(false)

        /** The version of the Kotlin Gradle plugin applied alongside the watchdog. */
        private fun Project.kotlinPluginVersion(): String =
            plugins.withType(KotlinBasePlugin::class.java)
                .firstOrNull()?.pluginVersion
                ?: error("The Kotlin Gradle plugin must be applied alongside library-api-watchdog")

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
            |Project '$projectPath' applies library-api-watchdog but doesn't enable explicit API mode, so the
            |watchdog registers no checks: there is no declared public API contract to watch. Enable it in
            |the module's build script:
            |
            |    kotlin {
            |        explicitApi()
            |    }
            |
            |The `explicitApiWarning()` variant and the `-Xexplicit-api` compiler flag also count.
        """.trimMargin()

        private fun abiValidationSuggestion(projectPath: String, kotlinVersion: String): String {
            val setupSnippet = if (kotlinVersion.usesLegacyAbiValidationDsl()) {
                """
                    import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

                    kotlin {
                        @OptIn(ExperimentalAbiValidation::class)
                        abiValidation {
                            enabled.set(true)
                        }
                    }
                """.trimIndent()
            } else {
                """
                    import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

                    kotlin {
                        @OptIn(ExperimentalAbiValidation::class)
                        abiValidation()
                    }
                """.trimIndent()
            }.prependIndent("    ")
            return """
            |Project '$projectPath' applies library-api-watchdog but no binary compatibility validation is enabled.
            |The watchdog reviews the shape of new API declarations, while binary compatibility validation
            |compares each build against a committed dump of the released API surface and catches accidental
            |breaking changes to it. Enable the Kotlin Gradle plugin's built-in ABI validation in the module's
            |build script:
            |
            |$setupSnippet
            |
            |See https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html for the check and
            |dump tasks and the full configuration reference. On older Kotlin versions, apply the standalone
            |Binary Compatibility Validator plugin instead: https://github.com/Kotlin/binary-compatibility-validator.
            |
            |Disable this suggestion with `apiWatchdog { suggestAbiValidation = false }`.
            """.trimMargin()
        }

        /** Kotlin 2.2 and 2.3 use an `enabled` property instead of an activating DSL function. */
        private fun String.usesLegacyAbiValidationDsl(): Boolean {
            val components = substringBefore('-').split('.')
            val major = components.getOrNull(0)?.toIntOrNull() ?: return false
            val minor = components.getOrNull(1)?.toIntOrNull() ?: return false
            return major == 2 && minor in 2..3
        }
    }
}
