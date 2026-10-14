package org.jetbrains.kotlinx.library.api.watchdog

import java.io.File
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
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
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetsContainer
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused")
public class WatchdogSupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    private lateinit var collectDiagnosticsForExempts: Property<Boolean>
    private lateinit var enabled: Property<Boolean>

    /**
     * KGP calls it after `afterEvaluate`, so `apiWatchdog.enabled` is final here. A raw
     * `-Xexplicit-api` flag also applies to test compilations.
     */
    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
        enabled.get() && !kotlinCompilation.isTestCompilation()

    override fun apply(target: Project) {
        target.configurations.register(EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME) { configuration ->
            configuration.isCanBeConsumed = false
            configuration.isCanBeResolved = false
            configuration.description = "Dependencies whose types the public API can expose " +
                    "although they aren't published transitively"
        }
        val extension = target.extensions.create(
            "apiWatchdog",
            WatchdogGradleExtension::class.java,
        )
        enabled = extension.enabled
        extension.reports.html.convention(true)
        extension.reports.htmlFile.convention(target.layout.buildDirectory.file(REPORT_FILE))
        extension.reports.sarif.convention(true)
        extension.reports.sarifFile.convention(target.layout.buildDirectory.file(SARIF_REPORT_FILE))
        val collectDiagnostics = target.objects.property(Boolean::class.java).convention(false)
        collectDiagnosticsForExempts = collectDiagnostics
        target.registerExemptionTasks(extension, collectDiagnostics)
        target.afterEvaluate { project ->
            if (!extension.enabled.get()) {
                project.addExemptionAnnotationsWithoutChecks()
                return@afterEvaluate
            }
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

    /** KGP adds the annotations library only through [applyToCompilation], which a disabled watchdog never reaches. */
    private fun Project.addExemptionAnnotationsWithoutChecks() {
        val compilations = when (val kotlin = extensions.findByName("kotlin")) {
            is KotlinJvmExtension -> kotlin.target.compilations
            is KotlinAndroidExtension -> kotlin.target.compilations
            is KotlinTargetsContainer -> kotlin.targets.flatMap { it.compilations }
            else -> return
        }
        compilations.filterNot { it.isTestCompilation() }.forEach { compilation ->
            compilation.defaultSourceSet.dependencies {
                info.companionLibraryCoordinates.forEach(::implementation)
            }
        }
    }

    private fun Project.registerExemptionTasks(
        extension: WatchdogGradleExtension,
        collectDiagnostics: Property<Boolean>,
    ) {
        val enabled = extension.enabled
        val dependencyHandler = dependencies
        val kotlinVersion = provider { kotlinPluginVersion() }
        @Suppress("UnstableApiUsage")
        val rootDirectory = isolated.rootProject.projectDirectory.asFile
        val projectDirectoryFromRoot = layout.projectDirectory.asFile
            .relativeToOrSelf(rootDirectory).invariantSeparatorsPath
        val diagnosticSeverities = extension.diagnosticSeverities.entries.fold(
            objects.mapProperty(String::class.java, WatchdogSeverity::class.java),
        ) { map, (diagnostic, severity) -> map.apply { put(diagnostic.name, severity) } }
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
                configuration.description = "The multi-version artifact of the watchdog exemption fixer"
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

        fun <T : ApiWatchdogReportTask> registerReportTask(
            name: String,
            type: Class<T>,
            description: String,
        ): TaskProvider<T> = tasks.register(name, type) { task ->
            task.group = "api watchdog"
            task.onlyIf(DISABLED_TASK_REASON) { enabled.get() }
            task.description = description
            task.compilationNames.convention(emptyList())
            task.projectDirectory.set(layout.projectDirectory)
            @Suppress("UnstableApiUsage")
            task.projectIdentityPath.set(isolated.buildTreePath)
            task.projectDirectoryFromRoot.set(projectDirectoryFromRoot)
            task.diagnosticSeverities.set(diagnosticSeverities)
            task.kotlinVersion.set(kotlinVersion)
            task.fixerArtifact.from(fixerArtifact)
            task.fixerClasspath.from(fixerClasspath)
            task.reports.html.convention(extension.reports.html)
            task.reports.htmlFile.convention(extension.reports.htmlFile)
            task.reports.sarif.convention(extension.reports.sarif)
            task.reports.sarifFile.convention(extension.reports.sarifFile)
            task.reportDataFile.convention(layout.buildDirectory.file(REPORT_DATA_FILE))
        }
        registerReportTask(
            EXEMPT_EXISTING_API_TASK_NAME,
            ExemptExistingApiTask::class.java,
            "Adds the FOR_BACKWARDS_COMPATIBILITY exemption to every declaration with a watchdog " +
                    "error in the main Kotlin sources and writes the API Watchdog reports",
        )
        val reportTask = registerReportTask(
            GENERATE_REPORT_TASK_NAME,
            GenerateApiWatchdogReportTask::class.java,
            "Writes the API Watchdog reports: the applied exemptions and the diagnostics without one. " +
                    "Changes no source",
        )
        // IDE sync realizes every task, so task realization can't be the signal.
        val exemptsTaskPaths = listOf(EXEMPT_EXISTING_API_TASK_NAME, GENERATE_REPORT_TASK_NAME)
            .map { if (path == ":") ":$it" else "$path:$it" }
        gradle.taskGraph.whenReady { graph ->
            collectDiagnostics.set(exemptsTaskPaths.any(graph::hasTask))
        }
        configurations.register(API_WATCHDOG_REPORT_ELEMENTS_CONFIGURATION) { configuration ->
            configuration.isCanBeConsumed = true
            configuration.isCanBeResolved = false
            configuration.description =
                "The API Watchdog report of this project"
            configuration.attributes.attribute(
                Usage.USAGE_ATTRIBUTE,
                objects.named(Usage::class.java, API_WATCHDOG_REPORT_USAGE),
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
            val exemptTask = tasks.named(
                EXEMPT_EXISTING_API_TASK_NAME,
                ExemptExistingApiTask::class.java,
            )
            val reportTask = tasks.named(
                GENERATE_REPORT_TASK_NAME,
                GenerateApiWatchdogReportTask::class.java,
            )
            fun ApiWatchdogReportTask.addCompilation() {
                compilationNames.add(kotlinCompilation.compileKotlinTaskName)
                sourceFiles.from(kotlinCompilation.allKotlinSourceSets.map { it.kotlin })
                diagnosticReports.from(reportFile)
                dependsOn(kotlinCompilation.compileTaskProvider)
            }
            exemptTask.configure { task -> task.addCompilation() }
            reportTask.configure { task ->
                // Both tasks write the same report files.
                task.mustRunAfter(exemptTask)
                task.addCompilation()
            }

            kotlinCompilation.compileTaskProvider.configure { task ->
                task.inputs.property("apiWatchdog.collectDiagnosticsForExempts", collect)
                // The report isn't a declared output because JVM compile tasks prepare every
                // extra output as a directory. So collection mode always runs and skips the
                // build cache.
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

        // Native compile tasks read the option list at configuration time, before the task graph
        // is ready. So the set of options is fixed and each value that depends on collection is
        // lazy. The compiler plugin ignores a blank value.
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
                                "${diagnostic.name}:${effective.name.lowercase()}"
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
                add(SubpluginOption("exemptingExistingApi", lazy { collect.get().toString() }))
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
                // The compiler plugin has no option for exempt dependencies, so they go into the
                // transitive paths.
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
                add(
                    SubpluginOption(
                        "dependencyDeclarations",
                        lazy {
                            if (!checkDependencyExposure.get()) return@lazy ""
                            dependencyDeclarations(compileDependencies.get(), compilesForAndroid.get())
                        },
                    ),
                )
            }
        }
    }

    /**
     * The build script declaration behind each artifact of [configuration], as
     * tab-separated `path`, `configuration`, `notation` triples. The declaration is the nearest
     * declared dependency on the resolution path to the component of the artifact.
     */
    private fun dependencyDeclarations(configuration: Configuration, android: Boolean): String {
        val declarations = HashMap<ComponentIdentifier, String>()
        val queue = ArrayDeque<Pair<ResolvedComponentResult, String>>()
        for (dependency in configuration.incoming.resolutionResult.root.dependencies) {
            if (dependency !is ResolvedDependencyResult) continue
            val declaration = declarationOf(dependency.requested, configuration) ?: continue
            queue.add(dependency.selected to declaration)
        }
        while (queue.isNotEmpty()) {
            val (component, declaration) = queue.removeFirst()
            if (declarations.putIfAbsent(component.id, declaration) != null) continue
            for (dependency in component.dependencies) {
                if (dependency is ResolvedDependencyResult) queue.add(dependency.selected to declaration)
            }
        }
        return dependencyArtifacts(configuration, android).artifacts.asSequence()
            .mapNotNull { artifact ->
                declarations[artifact.id.componentIdentifier]?.let { "${artifact.file.path}\t$it" }
            }
            .joinToString("\t")
    }

    /** The declaration of [requested] as a tab-separated configuration name and notation. */
    private fun declarationOf(requested: ComponentSelector, configuration: Configuration): String? {
        val notation: String
        val isDeclaredIn: (Dependency) -> Boolean
        when (requested) {
            is ProjectComponentSelector -> {
                notation = "project(\"${requested.projectPath}\")"
                isDeclaredIn = { it is ProjectDependency && it.path == requested.projectPath }
            }
            is ModuleComponentSelector -> {
                val version = requested.version.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty()
                notation = "\"${requested.group}:${requested.module}$version\""
                isDeclaredIn = {
                    it is ModuleDependency && it.group == requested.group && it.name == requested.module
                }
            }
            else -> return null
        }
        val declaringConfiguration = configuration.hierarchy
            .firstOrNull { it.dependencies.any(isDeclaredIn) }
            ?.name
            ?: "implementation"
        return "$declaringConfiguration\t$notation"
    }

    /** The `com.android.kotlin.multiplatform.library` target of AGP 8 reports platform type `jvm`. */
    private fun KotlinCompilation<*>.compilesForAndroid(compileDependencies: Configuration): Boolean =
        platformType == KotlinPlatformType.androidJvm ||
                compileDependencies.attributes.getAttribute(TARGET_JVM_ENVIRONMENT_ATTRIBUTE)?.name ==
                TargetJvmEnvironment.ANDROID

    /**
     * The files of [configuration] as the compiler sees them. Without the `android-classes-jar`
     * view, a project AAR has no artifact. The view leaves a plain jar as it is.
     */
    private fun dependencyFiles(configuration: Configuration, android: Boolean): FileCollection =
        if (android) dependencyArtifacts(configuration, android).artifactFiles else configuration

    private fun dependencyArtifacts(configuration: Configuration, android: Boolean): ArtifactCollection =
        if (android) {
            configuration.incoming.artifactView { view ->
                view.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ANDROID_CLASSES_JAR)
            }.artifacts
        } else {
            configuration.incoming.artifacts
        }

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

    private fun Project.exemptNonTransitiveDependenciesFor(
        kotlinCompilation: KotlinCompilation<*>,
        compileDependencies: Provider<Configuration>,
    ): NamedDomainObjectProvider<Configuration> {
        val exempt = configurations.named(EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME)
        return registerCompilationClasspathView(
            kotlinCompilation,
            compileDependencies,
            suffix = "ExemptNonTransitiveDependencies",
            description = "Dependencies whose types the public API of ${kotlinCompilation.target.name} can expose " +
                    "although they aren't published transitively",
        ) { configuration ->
            configuration.extendsFrom(exempt.get())
        }
    }

    /**
     * The compiler plugin matches dependency files by path, so a conflict-upgraded version on the
     * compile classpath must resolve to the same jar here.
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

    private fun Project.publishedApiElementsName(kotlinCompilation: KotlinCompilation<*>): String {
        // The target-level configuration of Android lacks the build-type and flavor `api`
        // dependencies. A non-library Android variant has no API elements of its own.
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
        private const val EXEMPT_EXISTING_API_TASK_NAME = "exemptExistingApi"
        private const val GENERATE_REPORT_TASK_NAME = "generateApiWatchdogReport"
        private const val REPORT_FILE = "reports/api-watchdog/report.html"
        private const val SARIF_REPORT_FILE = "reports/api-watchdog/report.sarif"
        private const val REPORT_DATA_FILE = "reports/api-watchdog/report.data"
        private const val ANDROID_CLASSES_JAR = "android-classes-jar"
        internal const val EXEMPT_NON_TRANSITIVE_CONFIGURATION_NAME = "apiWatchdogExemptNonTransitive"

        private const val TEST_COMPILATION_NAME_SUFFIX = "Test"

        /**
         * Android names a test compilation after its variant, so only the suffix is common. Test
         * fixtures are published with the library, so they aren't test compilations.
         */
        private fun KotlinCompilation<*>.isTestCompilation(): Boolean =
            name == KotlinCompilation.TEST_COMPILATION_NAME || name.endsWith(TEST_COMPILATION_NAME_SUFFIX)

        private const val FIXER_ARTIFACT_ID = "kotlin-library-api-watchdog-exempts-fixer"
        private const val FIXER_CLASSPATH_CONFIGURATION_NAME = "apiWatchdogExemptsFixerClasspath"
        private const val FIXER_ARTIFACT_CONFIGURATION_NAME = "apiWatchdogExemptsFixerArtifact"
        private const val BCV_PLUGIN_ID = "org.jetbrains.kotlinx.binary-compatibility-validator"

        private val ABI_VALIDATION_TASK_NAMES = listOf("checkKotlinAbi", "checkLegacyAbi")

        private const val DISABLED_TASK_REASON = "apiWatchdog.enabled is true"

        /** Undocumented. `true` silences the warning when the explicit API detection misjudges a project. */
        private const val SUPPRESS_EXPLICIT_API_WARNING_PROPERTY =
            "org.jetbrains.kotlin.library.api-watchdog.suppressExplicitApiWarning"

        private fun Project.explicitApiWarningSuppressed(): Boolean =
            providers.gradleProperty(SUPPRESS_EXPLICIT_API_WARNING_PROPERTY)
                .map(String::toBoolean)
                .getOrElse(false)

        private fun Project.kotlinPluginVersion(): String =
            plugins.withType(KotlinBasePlugin::class.java)
                .firstOrNull()?.pluginVersion
                ?: error("Apply the Kotlin Gradle plugin together with library-api-watchdog")

        /** Reads the extension, not the compile tasks, so the tasks stay unrealized. */
        private fun Project.hasExplicitApiMode(): Boolean {
            val kotlin = extensions.findByName("kotlin") as? KotlinBaseExtension ?: return false
            val mode = kotlin.explicitApi
            if (mode != null && mode != ExplicitApiMode.Disabled) return true
            val compilerOptions = kotlin as? HasConfigurableKotlinCompilerOptions<*> ?: return false
            return compilerOptions.compilerOptions.freeCompilerArgs.orNull.orEmpty().any {
                it.startsWith("-Xexplicit-api=") && it != "-Xexplicit-api=disable"
            }
        }

        private fun Project.hasAbiValidation(): Boolean {
            if (pluginManager.hasPlugin(BCV_PLUGIN_ID)) return true
            val kotlin = extensions.findByName("kotlin") as? ExtensionAware ?: return false
            // Kotlin 2.2 and 2.3 opt in with the `enabled` flag of the `abiValidation`
            // sub-extension. The compiled-against API deprecates the flag for removal, so
            // reflection reads it.
            val legacyAbiValidation = kotlin.extensions.findByName("abiValidation")
            if (legacyAbiValidation != null) {
                val enabled = runCatching {
                    legacyAbiValidation.javaClass.getMethod("getEnabled").invoke(legacyAbiValidation)
                }.getOrNull()
                return (enabled as? Provider<*>)?.orNull == true
            }
            // The `abiValidation` getter of Kotlin 2.4 and later activates ABI validation, so
            // don't call it. The registered tasks show the activation.
            return ABI_VALIDATION_TASK_NAMES.any(tasks.names::contains)
        }

        private fun missingExplicitApiWarning(projectPath: String): String = """
            |Project '$projectPath' applies library-api-watchdog but doesn't enable explicit API mode, so the
            |watchdog registers no checks. Enable it in the build script of the module:
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
            |The watchdog checks the shape of new API. Binary compatibility validation compares each build with
            |a committed dump of the released API and catches a breaking change. Enable the ABI validation of
            |the Kotlin Gradle plugin in the build script of the module:
            |
            |$setupSnippet
            |
            |Configuration reference: https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html
            |On an older Kotlin version, apply the Binary Compatibility Validator plugin instead:
            |https://github.com/Kotlin/binary-compatibility-validator
            |
            |Disable this suggestion with `apiWatchdog { suggestAbiValidation = false }`.
            """.trimMargin()
        }

        private fun String.usesLegacyAbiValidationDsl(): Boolean {
            val components = substringBefore('-').split('.')
            val major = components.getOrNull(0)?.toIntOrNull() ?: return false
            val minor = components.getOrNull(1)?.toIntOrNull() ?: return false
            return major == 2 && minor in 2..3
        }
    }
}
