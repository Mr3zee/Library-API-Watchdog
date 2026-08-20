package org.jetbrains.kotlinx.libs.api.watchdog.conventions

import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.Usage
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configures the manual JMH benchmarks in `src/benchmark`. The source set is associated with
 * main so isolated benchmarks can construct internal checkers directly, while whole-compilation
 * benchmarks load the dev kit's multi-version jar through `-Xplugin` like a real compilation.
 */
class BenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val sourceSets = extensions.getByType(SourceSetContainer::class.java)
            val versions = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
            val kotlinVersion = versions.findVersion("kotlin").get().requiredVersion
            val kotlinTargetName =
                "k" + kotlinVersion.replace(".", "").replace("-", "_").lowercase()
            val kotlin = extensions.getByType(KotlinMultiplatformExtension::class.java)
            val kotlinTarget = kotlin.targets.getByName(kotlinTargetName)
            val benchmarkCompilation =
                kotlinTarget.compilations.create("benchmark") {
                    associateWith(kotlinTarget.compilations.getByName("main"))
                    // The dev kit generates PluginInfo in its entry-point compilation. Benchmarks
                    // use that canonical plugin ID when invoking the compiler programmatically.
                    associateWith(kotlinTarget.compilations.getByName("entryPoint"))
                    defaultSourceSet.kotlin.srcDir("src/benchmark/kotlin")
                }
            val benchmarkSourceSet: SourceSet =
                sourceSets.getByName(benchmarkCompilation.defaultSourceSet.name)

            // What the synthetic corpus compiles against: the annotations library plus its
            // transitive dependencies, notably the Kotlin standard library. This is separate
            // from the benchmark's own classpath.
            val benchmarkCorpusClasspath: Configuration = configurations.create("benchmarkCorpusClasspath") {
                isCanBeConsumed = false
                attributes {
                    attribute(
                        Usage.USAGE_ATTRIBUTE,
                        objects.named(Usage::class.java, Usage.JAVA_RUNTIME),
                    )
                    attribute(
                        Category.CATEGORY_ATTRIBUTE,
                        objects.named(Category::class.java, Category.LIBRARY),
                    )
                    attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
                }
            }

            val jmhBytecodeGenerator = configurations.create("jmhBytecodeGenerator")

            dependencies.add(
                benchmarkCompilation.defaultSourceSet.implementationConfigurationName,
                "org.openjdk.jmh:jmh-core:$JMH_VERSION",
            )
            dependencies.add(
                benchmarkCompilation.defaultSourceSet.implementationConfigurationName,
                "org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion",
            )
            dependencies.add("benchmarkCorpusClasspath", dependencies.project(mapOf("path" to ":kotlin-library-api-watchdog-plugin-annotations")))
            dependencies.add("jmhBytecodeGenerator", "org.openjdk.jmh:jmh-generator-bytecode:$JMH_VERSION")

            val benchmarkGeneratedSources = layout.buildDirectory.dir("benchmark-jmh/sources")
            val benchmarkGeneratedResources = layout.buildDirectory.dir("benchmark-jmh/resources")
            val benchmarkGeneratedClasses = layout.buildDirectory.dir("benchmark-jmh/classes")
            val benchmarkKotlinClasses = tasks
                .named(benchmarkCompilation.compileTaskProvider.name, KotlinCompile::class.java)
                .flatMap { it.destinationDirectory }

            val generateBenchmarkJmhSources = tasks.register("generateBenchmarkJmhSources", JavaExec::class.java) {
                description = "Generates the JMH benchmark wrappers from the compiled benchmark classes."
                mainClass.set("org.openjdk.jmh.generators.bytecode.JmhBytecodeGenerator")
                classpath = files(jmhBytecodeGenerator, benchmarkSourceSet.runtimeClasspath)
                inputs.files(benchmarkKotlinClasses)
                outputs.dir(benchmarkGeneratedSources)
                outputs.dir(benchmarkGeneratedResources)
                argumentProviders.add(
                    CommandLineArgumentProvider {
                        listOf(
                            benchmarkKotlinClasses.get().asFile.absolutePath,
                            benchmarkGeneratedSources.get().asFile.absolutePath,
                            benchmarkGeneratedResources.get().asFile.absolutePath,
                            "default",
                        )
                    },
                )
            }

            val compileBenchmarkJmhSources = tasks.register("compileBenchmarkJmhSources", JavaCompile::class.java) {
                description = "Compiles the generated JMH benchmark wrappers."
                dependsOn(generateBenchmarkJmhSources)
                source(benchmarkGeneratedSources)
                classpath = benchmarkSourceSet.runtimeClasspath
                destinationDirectory.set(benchmarkGeneratedClasses)
            }

            /** Passes the plugin jar and the corpus classpath into a benchmark JVM. */
            fun configureWatchdogBenchmarkInputs(task: JavaExec) {
                val pluginJar: Provider<RegularFile> =
                    tasks.named("${kotlinTargetName}Jar", Jar::class.java).flatMap { it.archiveFile }
                val corpusFiles: FileCollection = files(benchmarkCorpusClasspath)
                task.inputs.file(pluginJar)
                task.inputs.files(corpusFiles)
                task.jvmArgumentProviders.add(
                    CommandLineArgumentProvider {
                        listOf(
                            "-Dwatchdog.benchmark.pluginJar=${pluginJar.get().asFile.absolutePath}",
                            "-Dwatchdog.benchmark.corpusClasspath=${corpusFiles.files.joinToString(File.pathSeparator) { it.absolutePath }}",
                        )
                    },
                )
            }

            val benchmarkResultsDirectory = layout.buildDirectory.dir("reports/benchmark")
            tasks.register("benchmark", JavaExec::class.java) {
                group = "benchmark"
                description = "Runs the watchdog JMH benchmarks with the GC (allocation) profiler. " +
                        "Manual only and not wired into check. Filter with -Pbenchmark.include=<regex>, " +
                        "pass extra JMH options with -Pbenchmark.args='...'."
                mainClass.set("org.openjdk.jmh.Main")
                classpath = files(benchmarkGeneratedClasses, benchmarkGeneratedResources) +
                        benchmarkSourceSet.runtimeClasspath
                dependsOn(compileBenchmarkJmhSources)
                configureWatchdogBenchmarkInputs(this)
                val include = providers.gradleProperty("benchmark.include")
                val extraArgs = providers.gradleProperty("benchmark.args")
                var resultsPath: File? = null
                doFirst {
                    resultsPath = benchmarkResultsDirectory.get().file(
                        "results-${resultTimestamp()}.json",
                    ).asFile
                    resultsPath.parentFile.mkdirs()
                }
                argumentProviders.add(
                    CommandLineArgumentProvider {
                        val resultFile = checkNotNull(resultsPath) {
                            "The benchmark results path was not initialized."
                        }
                        buildList {
                            include.orNull?.let { add(it) }
                            addAll(listOf("-prof", "gc"))
                            addAll(listOf("-foe", "true"))
                            addAll(listOf("-rf", "json", "-rff", resultFile.absolutePath))
                            extraArgs.orNull?.let {
                                addAll(it.split(' ').filter(String::isNotEmpty))
                            }
                        }
                    },
                )
            }

            val profileResultsDirectory = layout.buildDirectory.dir("reports/profile")
            tasks.register("profile", JavaExec::class.java) {
                group = "benchmark"
                description = "Profiles one watchdog benchmark subject with Java Flight Recorder. " +
                        "Defaults to a whole compilation with all checkers. Select an isolated checker with " +
                        "-Pprofile.benchmark=isolated -Pprofile.subject=<CheckerName>."
                mainClass.set("org.openjdk.jmh.Main")
                classpath = files(benchmarkGeneratedClasses, benchmarkGeneratedResources) +
                        benchmarkSourceSet.runtimeClasspath
                dependsOn(compileBenchmarkJmhSources)
                configureWatchdogBenchmarkInputs(this)

                val benchmark = providers.gradleProperty("profile.benchmark").orElse("whole")
                val subject = providers.gradleProperty("profile.subject")
                val corpusFiles = providers.gradleProperty("profile.corpusFiles").orElse("200")
                val stackDepth = providers.gradleProperty("profile.stackDepth").orElse("256")
                val extraArgs = providers.gradleProperty("profile.args")
                val resultsPath = profileResultsDirectory.get().asFile
                var startedAt = 0L
                var timestamp: String? = null
                doFirst {
                    startedAt = System.currentTimeMillis()
                    timestamp = resultTimestamp()
                    resultsPath.mkdirs()
                }
                doLast {
                    val resultTimestamp = checkNotNull(timestamp) {
                        "The profile result timestamp was not initialized."
                    }
                    resultsPath.walkTopDown()
                        .filter { it.isFile && it.name == "profile.jfr" && it.lastModified() >= startedAt }
                        .toList()
                        .forEach { profile ->
                            Files.move(
                                profile.toPath(),
                                profile.resolveSibling("profile-$resultTimestamp.jfr").toPath(),
                            )
                        }
                }
                argumentProviders.add(
                    CommandLineArgumentProvider {
                        val (include, parameter, selectedSubject) = when (benchmark.get()) {
                            "whole" -> Triple(
                                "WholeCompilationBenchmark.compile",
                                "mode",
                                subject.orNull ?: "allCheckers",
                            )
                            "isolated" -> Triple(
                                "IsolatedCheckerBenchmark.sweepCorpus",
                                "checker",
                                subject.orNull ?: throw GradleException(
                                    "An isolated profile needs -Pprofile.subject=<CheckerName> " +
                                            "(or -Pprofile.subject=none for the traversal baseline).",
                                ),
                            )
                            else -> throw GradleException(
                                "Unknown profile benchmark '${benchmark.get()}'. Expected 'whole' or 'isolated'.",
                            )
                        }

                        buildList {
                            add(include)
                            addAll(listOf("-p", "$parameter=$selectedSubject"))
                            addAll(listOf("-p", "corpusFiles=${corpusFiles.get()}"))
                            addAll(
                                listOf(
                                    "-prof",
                                    "jfr:dir=${resultsPath.absolutePath};configName=profile;" +
                                            "stackDepth=${stackDepth.get()}",
                                ),
                            )
                            addAll(listOf("-foe", "true"))
                            extraArgs.orNull?.let {
                                addAll(it.split(' ').filter(String::isNotEmpty))
                            }
                        }
                    },
                )
            }

            tasks.register("benchmarkCorpusAudit", JavaExec::class.java) {
                group = "benchmark"
                description = "Compiles the benchmark corpus with all watchdog diagnostics enabled and " +
                        "prints how often each one fired. Set the file count with " +
                        "-Pbenchmark.corpusFiles=<n>."
                mainClass.set("org.jetbrains.kotlinx.libs.api.watchdog.benchmark.CorpusAudit")
                classpath = benchmarkSourceSet.runtimeClasspath
                configureWatchdogBenchmarkInputs(this)
                val corpusFiles = providers.gradleProperty("benchmark.corpusFiles")
                argumentProviders.add(
                    CommandLineArgumentProvider {
                        listOfNotNull(corpusFiles.orNull)
                    },
                )
            }
        }
    }

    private companion object {
        const val JMH_VERSION = "1.37"
        val RESULT_TIMESTAMP_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

        fun resultTimestamp(): String = LocalDateTime.now().format(RESULT_TIMESTAMP_FORMATTER)
    }
}
