package org.jetbrains.kotlinx.libs.api.watchdog

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.compiler.plugin.devkit.DevKitSupportPlugin
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

@Suppress("unused") // Used via reflection.
public class WatchdogSupportPlugin : DevKitSupportPlugin(PluginInfo.PLUGIN_INFO) {
    override fun apply(target: Project) {
        val extension = target.extensions.create("apiWatchdog", WatchdogGradleExtension::class.java)
        target.registerUpdateBackwardsCompatibilityExemptsTask()
        target.afterEvaluate { project ->
            if (!project.explicitApiWarningSuppressed() && !project.hasExplicitApiMode()) {
                project.logger.warn(missingExplicitApiWarning(project.path))
            }
            if (extension.suggestTapmoc.get() && TAPMOC_PLUGIN_IDS.none(project.pluginManager::hasPlugin)) {
                project.logger.warn(tapmocSuggestion(project.path))
            }
            if (extension.suggestAbiValidation.get() && !project.hasAbiValidation()) {
                project.logger.warn(abiValidationSuggestion(project.path))
            }
        }
    }

    /**
     * Registers [UpdateBackwardsCompatibilityExemptsTask] over every main JVM compilation. The
     * fixer runs on a classpath resolved here: the `exempts-fixer` artifact published next to
     * this plugin, plus the Build Tools API implementation matching the project's Kotlin Gradle
     * plugin version, so the analysis sees the same compiler the build uses.
     */
    private fun Project.registerUpdateBackwardsCompatibilityExemptsTask() {
        val fixerClasspath = configurations.detachedConfiguration(
            dependencies.create("${info.artifact.groupId}:$FIXER_ARTIFACT_ID:${info.artifact.version}"),
        )
        fixerClasspath.dependencies.addLater(provider {
            val kotlinVersion = plugins.withType(KotlinBasePlugin::class.java).firstOrNull()?.pluginVersion
                ?: error("The Kotlin Gradle plugin must be applied alongside libs-api-watchdog")
            dependencies.create("org.jetbrains.kotlin:kotlin-build-tools-impl:$kotlinVersion")
        })

        tasks.register(UPDATE_EXEMPTS_TASK_NAME, UpdateBackwardsCompatibilityExemptsTask::class.java) { task ->
            task.group = "api watchdog"
            task.description = "Acknowledges every watchdog diagnostic in the main JVM " +
                    "compilation sources with the matching @Intentionally* annotation and the " +
                    "FOR_BACKWARDS_COMPATIBILITY reason"
            task.pluginId.set(info.id)
            task.projectDirectory.set(layout.projectDirectory.asFile)
            task.fixerClasspath.from(fixerClasspath)
            task.compilations.addAll(provider { collectCompilationInputs() })
        }
    }

    private fun Project.collectCompilationInputs(): List<WatchdogCompilationInput> =
        tasks.withType(KotlinJvmCompile::class.java)
            .filter { it.sourceSetName.getOrElse("") == KotlinCompilation.MAIN_COMPILATION_NAME }
            .map { compileTask ->
                objects.newInstance(WatchdogCompilationInput::class.java).apply {
                    compilationName.set(compileTask.name)
                    sources.from(compileTask.sources)
                    libraries.from(compileTask.libraries)
                    pluginClasspath.from(compileTask.pluginClasspath)
                    friendPaths.from(compileTask.friendPaths)
                    compilerArgs.set(renderCompilerArgs(compileTask))
                }
            }

    /** The compilation's settings as CLI arguments, mirroring what the compile task passes. */
    private fun renderCompilerArgs(compileTask: KotlinJvmCompile): List<String> = buildList {
        val options = compileTask.compilerOptions
        // The module name only affects the mangled names of internal members; it may have no
        // value yet at this point, and the analysis works without it.
        options.moduleName.orNull?.let {
            add("-module-name")
            add(it)
        }
        options.jvmTarget.orNull?.let {
            add("-jvm-target")
            add(it.target)
        }
        options.languageVersion.orNull?.let {
            add("-language-version")
            add(it.version)
        }
        options.apiVersion.orNull?.let {
            add("-api-version")
            add(it.version)
        }
        options.optIn.getOrElse(emptyList()).forEach { add("-opt-in=$it") }
        if (options.progressiveMode.getOrElse(false)) add("-progressive")
        if (options.javaParameters.getOrElse(false)) add("-java-parameters")
        addAll(options.freeCompilerArgs.getOrElse(emptyList()))
        if (compileTask.multiPlatformEnabled.getOrElse(false)) add("-Xmulti-platform")
        compileTask.pluginOptions.getOrElse(emptyList()).forEach { config ->
            config.allOptions().forEach { (pluginId, pluginOptions) ->
                pluginOptions.forEach { option ->
                    add("-P")
                    add("plugin:$pluginId:${option.key}=${option.value}")
                }
            }
        }
    }

    override fun Project.applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val extension = extensions.getByType(WatchdogGradleExtension::class.java)
        return providers.provider {
            extension.diagnosticSeverities().map { (diagnostic, severity) ->
                SubpluginOption("diagnosticSeverity", "$diagnostic:${severity.get().name.lowercase()}")
            }
        }
    }

    private companion object {
        /** The name of the task that acknowledges existing diagnostics as backwards-compatibility exemptions. */
        private const val UPDATE_EXEMPTS_TASK_NAME = "updateBackwardsCompatibilityExempts"

        /** The artifact carrying the standalone fixer tool, published next to this plugin. */
        private const val FIXER_ARTIFACT_ID = "exempts-fixer"

        /** Tapmoc's plugin id, plus the id of its former incarnation, CompatPatrouille. */
        private val TAPMOC_PLUGIN_IDS = listOf("com.gradleup.tapmoc", "com.gradleup.compat.patrouille")

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
         * Whether explicit API mode (strict or warning) is enabled: through the `kotlin` DSL, or
         * through a raw `-Xexplicit-api` flag in the effective free compiler arguments of any
         * Kotlin compile task. The DSL check comes first so that the common case does not force
         * task realization.
         */
        private fun Project.hasExplicitApiMode(): Boolean {
            val kotlin = extensions.findByName("kotlin") as? KotlinBaseExtension ?: return false
            val mode = kotlin.explicitApi
            if (mode != null && mode != ExplicitApiMode.Disabled) return true
            return tasks.withType(KotlinCompilationTask::class.java).any { task ->
                task.compilerOptions.freeCompilerArgs.orNull.orEmpty()
                    .any { it.startsWith("-Xexplicit-api=") && it != "-Xexplicit-api=disable" }
            }
        }

        /**
         * Whether some binary compatibility validation guards this project: the standalone
         * Binary Compatibility Validator plugin (it covers subprojects when applied to a parent
         * project) or the Kotlin Gradle plugin's built-in ABI validation.
         */
        private fun Project.hasAbiValidation(): Boolean {
            val standaloneApplied = generateSequence(this) { it.parent }
                .any { it.pluginManager.hasPlugin(BCV_PLUGIN_ID) }
            if (standaloneApplied) return true
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
            // is touched (its getter has that side effect, so it must not be called here);
            // activation is observed through the tasks it registers instead.
            return ABI_VALIDATION_TASK_NAMES.any(tasks.names::contains)
        }

        private fun missingExplicitApiWarning(projectPath: String): String = """
            |Project '$projectPath' applies libs-api-watchdog but does not enable explicit API mode, so the
            |watchdog registers no checks: there is no declared public API contract to watch. Enable it in
            |the module's build script:
            |
            |    kotlin {
            |        explicitApi()
            |    }
            |
            |The `explicitApiWarning()` variant and the `-Xexplicit-api` compiler flag also count.
        """.trimMargin()

        private fun tapmocSuggestion(projectPath: String): String = """
            |Project '$projectPath' applies libs-api-watchdog but not Tapmoc. The watchdog guards the shape of
            |the public API, while Tapmoc pins the Java and Kotlin compatibility levels the artifacts are
            |built against, keeping them consumable from the oldest JDK and Kotlin versions the library
            |supports. Enable it in the module's build script, replacing <version> with the latest release
            |(https://github.com/GradleUp/Tapmoc/releases/latest):
            |
            |    plugins {
            |        id("com.gradleup.tapmoc") version "<version>"
            |    }
            |
            |    tapmoc {
            |        java(17)        // oldest supported Java release
            |        kotlin("2.1.0") // oldest supported Kotlin version
            |    }
            |
            |See https://gradleup.com/tapmoc/ for the full configuration reference.
            |
            |Disable this suggestion with `apiWatchdog { suggestTapmoc = false }`.
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
