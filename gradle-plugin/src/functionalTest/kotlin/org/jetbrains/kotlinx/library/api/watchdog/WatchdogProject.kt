package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.BuildScript
import com.autonomousapps.kit.gradle.Dependency
import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
import java.io.File
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KotlinPlugins
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.junit.Assume.assumeTrue

open class WatchdogProject(
    multiplatform: Boolean = false,
    private val explicitApi: Boolean = true,
    private val extraBuildScript: String = "",
) : AbstractDevKitGradleProject(
    multiplatform = multiplatform,
) {
    override val defaultImports: List<String> = listOf(
        "org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyFunctionTypeAlias",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEquals",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutHashCode",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutToString",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyPairOrTriple",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyRequiredParameterAfterOptional",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInconsistentParameterOrder",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonStaticCompanionApi",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutJvmOverloads",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonTransitiveDependency",
        "org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility",
        "org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker",
    )

    // The watchdog only activates in explicit API mode, so tests enable it unless they
    // specifically exercise the plugin being dormant.
    override fun StringBuilder.onBuildScript() {
        if (explicitApi) appendLine("kotlin { explicitApi() }")
        if (extraBuildScript.isNotBlank()) appendLine(extraBuildScript)
    }

    override val pluginUnderTest: Plugin = Plugin("org.jetbrains.kotlin.library.api-watchdog", pluginUnderTestVersion)

    // The dev kit runtime artifacts are consumed from mavenLocal rather than from included builds.
    override fun repositories(defaults: List<Repository>): Repositories =
        Repositories((defaults + Repository.MAVEN_LOCAL).toMutableList())
}

/** The AGP 8 version the build wires into the Android functional tests, see build.gradle.kts. */
val testAgp8Version: String? = System.getProperty("watchdog.test.agp8Version")

/** The AGP 9 version the build wires into the Android functional tests, see build.gradle.kts. */
val testAgp9Version: String? = System.getProperty("watchdog.test.agp9Version")

/** The Android SDK install the build discovered, or null when the machine has none. */
val testAndroidSdkPath: String? = System.getProperty("watchdog.test.androidHome")

/**
 * The Gradle version AGP 8 test projects are launched with: AGP 8 relies on a Gradle internal
 * API that Gradle 9.6.0 removed, so those projects run on the newest Gradle release line that
 * still supports it. AGP 9 test projects run with the current Gradle version instead.
 */
val agpCompatibleGradle: GradleVersion = GradleVersion.version("9.5.1")

/** AGP 8 refuses AndroidX dependencies without this opt-in, which AGP 9 made the default. */
private val androidGradleProperties: List<String> = listOf("android.useAndroidX=true")

/** Skips the calling test when the environment can't build Android projects. */
fun assumeAndroidBuildEnvironment() {
    assumeTrue(
        "No AGP versions are wired in, see build.gradle.kts",
        testAgp8Version != null && testAgp9Version != null,
    )
    assumeTrue("No Android SDK was found, see build.gradle.kts", testAndroidSdkPath != null)
}

private fun requiredTestAgp8Version(): String =
    requireNotNull(testAgp8Version) { "Android fixtures require assumeAndroidBuildEnvironment()" }

private fun requiredTestAgp9Version(): String =
    requireNotNull(testAgp9Version) { "Android fixtures require assumeAndroidBuildEnvironment()" }

private fun requiredAndroidSdkDir(): String =
    File(requireNotNull(testAndroidSdkPath) { "Android fixtures require assumeAndroidBuildEnvironment()" })
        .invariantSeparatorsPath

/**
 * A [WatchdogProject] whose root module is an Android library. Two AGP setups are supported:
 *
 * - AGP 8 with the Kotlin Android Gradle plugin ([builtInKotlin] = false). Launch builds with
 *   [agpCompatibleGradle].
 * - AGP 9 with AGP's built-in Kotlin support ([builtInKotlin] = true): the Kotlin Android plugin
 *   is refused by AGP 9, and AGP itself drives KGP's compiler plugin integration. The Kotlin
 *   version under test is pinned onto the build classpath with an `apply false` plugin line,
 *   overriding the older KGP that AGP embeds by default. Launch builds with the current Gradle.
 *
 * Main compilations are named after their build variant (`debug`, `release`, or `freeDebug` with
 * a product flavor) and test compilations after the variant plus a suffix (`debugUnitTest`,
 * `debugAndroidTest`). Tests must call [assumeAndroidBuildEnvironment] first.
 *
 * [kotlinScript] is a `kotlin { ... }` configuration block. It lands at the top level of the
 * build script next to the Kotlin Android plugin, and nested inside `android { }` with built-in
 * Kotlin, which is where AGP 9 hosts it. [extraAndroidScript] is placed inside the root's
 * `android { }` block, where build types and product flavors are declared, and
 * [extraBuildScript] at the end of the root build script only.
 */
open class AndroidLibraryWatchdogProject(
    private val kotlinScript: String = "kotlin { explicitApi() }",
    private val builtInKotlin: Boolean = false,
    private val extraAndroidScript: String = "",
    private val extraBuildScript: String = "",
) : WatchdogProject() {
    override val extraGradleProperties: List<String> = androidGradleProperties

    /** Dependencies of the Android library module, rendered by the test kit. */
    protected open fun androidDependencies(): List<Dependency> = emptyList()

    /** Plain Kotlin/JVM subprojects by path, built with the default build script. */
    protected open fun jvmSubprojects(): Map<String, List<Source>> = emptyMap()

    /** Dependencies of the [jvmSubprojects], by path. */
    protected open fun jvmSubprojectDependencies(): Map<String, List<Dependency>> = emptyMap()

    /**
     * Android library subprojects by path, built with the root's AGP generation and consumed as
     * AARs. Like the JVM ones they apply the watchdog in explicit API mode, which supplies the
     * annotations their sources import.
     */
    protected open fun androidSubprojects(): Map<String, List<Source>> = emptyMap()

    /** Dependencies of the [androidSubprojects], by path. */
    protected open fun androidSubprojectDependencies(): Map<String, List<Dependency>> = emptyMap()

    final override fun buildGradleProject(): GradleProject =
        newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
            .withRootProject {
                sources = this@AndroidLibraryWatchdogProject.sources()
                withBuildScript {
                    plugins(*androidLibraryPlugins(root = true))
                    withKotlin(
                        androidLibraryScript("test.consumer", kotlinScript, extraAndroidScript) +
                                extraBuildScript.ifBlank { "" }
                    )
                    val moduleDependencies = androidDependencies()
                    if (moduleDependencies.isNotEmpty()) dependencies(*moduleDependencies.toTypedArray())
                }
                withDevKitSettings()
                withFile("local.properties", "sdk.dir=${requiredAndroidSdkDir()}")
            }
            .apply {
                jvmSubprojects().forEach { (path, projectSources) ->
                    withSubproject(path) {
                        sources.addAll(projectSources)
                        withBuildScript {
                            // The root's Kotlin plugin ships the whole Kotlin Gradle plugin, so
                            // `org.jetbrains.kotlin.jvm` is already on the build classpath and
                            // must be requested without a version here.
                            plugins(Plugin("org.jetbrains.kotlin.jvm"), pluginUnderTest)
                            withKotlin("kotlin { explicitApi() }\n")
                            jvmSubprojectDependencies()[path]?.takeIf { it.isNotEmpty() }?.let {
                                dependencies(*it.toTypedArray())
                            }
                        }
                    }
                }
                androidSubprojects().forEach { (path, projectSources) ->
                    withSubproject(path) {
                        sources.addAll(projectSources)
                        withBuildScript {
                            plugins(*androidLibraryPlugins(root = false))
                            withKotlin(androidLibraryScript("test.$path", "kotlin { explicitApi() }", extraAndroidScript = ""))
                            androidSubprojectDependencies()[path]?.takeIf { it.isNotEmpty() }?.let {
                                dependencies(*it.toTypedArray())
                            }
                        }
                    }
                }
            }
            .write()

    /**
     * The plugins of an Android library module. Only the root names versions: subprojects find
     * every plugin on the build classpath the root assembled.
     */
    private fun androidLibraryPlugins(root: Boolean): Array<Plugin> = buildList {
        if (builtInKotlin) {
            add(Plugin("com.android.library", requiredTestAgp9Version().takeIf { root }))
            if (root) add(Plugin("org.jetbrains.kotlin.jvm", kotlinVersion ?: "$TEST_COMPILER_VERSION", apply = false))
        } else {
            add(Plugin("com.android.library", requiredTestAgp8Version().takeIf { root }))
            add(Plugin("org.jetbrains.kotlin.android", (kotlinVersion ?: "$TEST_COMPILER_VERSION").takeIf { root }))
        }
        add(pluginUnderTest)
    }.toTypedArray()

    private fun androidLibraryScript(namespace: String, kotlinScript: String, extraAndroidScript: String): String =
        buildString {
            appendLine("android {")
            appendLine("    namespace = \"$namespace\"")
            appendLine("    compileSdk = 36")
            if (extraAndroidScript.isNotBlank()) appendLine(extraAndroidScript.prependIndent("    "))
            if (builtInKotlin) {
                if (kotlinScript.isNotBlank()) appendLine(kotlinScript.prependIndent("    "))
                appendLine("}")
            } else {
                // AGP 8 defaults its Java tasks to 1.8 while Kotlin follows the build JDK, so
                // both sides are pinned to keep KGP's JVM target validation quiet.
                appendLine("    compileOptions {")
                appendLine("        sourceCompatibility = JavaVersion.VERSION_17")
                appendLine("        targetCompatibility = JavaVersion.VERSION_17")
                appendLine("    }")
                appendLine("}")
                appendLine("kotlin {")
                appendLine("    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }")
                appendLine("}")
                if (kotlinScript.isNotBlank()) appendLine(kotlinScript)
            }
        }
}

/**
 * The shared shape of the multiplatform Android fixtures: a root module with a `jvm()` target and
 * an Android target, plus plain Kotlin/JVM subprojects. Dependencies are declared on the
 * multiplatform source sets, since a multiplatform build script has no top-level
 * `dependencies { }` block for its compilations.
 */
abstract class AbstractKmpAndroidWatchdogProject(
    explicitApi: Boolean,
    extraBuildScript: String,
) : WatchdogProject(
    multiplatform = true,
    explicitApi = explicitApi,
    extraBuildScript = extraBuildScript,
) {
    override val extraGradleProperties: List<String> = androidGradleProperties

    /** Dependencies of the `commonMain` source set. */
    protected open fun commonMainDependencies(): List<Dependency> = emptyList()

    /** Dependencies of the `androidMain` source set. */
    protected open fun androidMainDependencies(): List<Dependency> = emptyList()

    /** Plain Kotlin/JVM subprojects by path, built with the default build script. */
    protected open fun jvmSubprojects(): Map<String, List<Source>> = emptyMap()

    /** Dependencies of the [jvmSubprojects], by path. */
    protected open fun jvmSubprojectDependencies(): Map<String, List<Dependency>> = emptyMap()

    /**
     * [source] for test helpers that describe a fixture from outside an object expression, placed
     * into [sourceSet] instead of `commonMain` when given.
     */
    fun fixtureSource(content: String, name: String, packageName: String, sourceSet: String? = null): Source =
        source(content, name, packageName).let { if (sourceSet == null) it else it.inSourceSet(sourceSet) }

    /** The Android target declaration inside the `kotlin { }` block. */
    protected abstract fun androidTargetBlock(): String

    /** Script text after the `kotlin { }` block, such as a legacy `android { }` block. */
    protected open fun afterKotlinBlock(): String = ""

    /** The AGP plugin providing the Android target. */
    protected abstract fun androidPlugin(): Plugin

    override fun multiplatformTargetsBlock(): String = buildString {
        appendLine("kotlin {")
        appendLine("    jvm()")
        appendLine(androidTargetBlock().trimEnd().prependIndent("    "))
        append(sourceSetDependencies("commonMain", commonMainDependencies()))
        append(sourceSetDependencies("androidMain", androidMainDependencies()))
        appendLine("}")
        append(afterKotlinBlock())
    }

    override fun BuildScript.Builder.applyDefaultBuildScript() {
        plugins(KotlinPlugins.multiplatform(kotlinVersion), androidPlugin(), pluginUnderTest)
        withKotlin(
            buildString {
                onBuildScript()
                append(multiplatformTargetsBlock())
            }
        )
    }

    override fun buildGradleProject(): GradleProject =
        newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
            .withRootProject {
                sources = this@AbstractKmpAndroidWatchdogProject.sources()
                withBuildScript { applyDefaultBuildScript() }
                withDevKitSettings()
                withFile("local.properties", "sdk.dir=${requiredAndroidSdkDir()}")
            }
            .apply {
                jvmSubprojects().forEach { (path, projectSources) ->
                    withSubproject(path) {
                        // The root is multiplatform, so sources default to `commonMain`, which a
                        // Kotlin/JVM subproject doesn't have.
                        sources.addAll(projectSources.map { it.inSourceSet("main") })
                        withBuildScript {
                            // The root's multiplatform plugin ships the whole Kotlin Gradle
                            // plugin, so `org.jetbrains.kotlin.jvm` is already on the build
                            // classpath and must be requested without a version here.
                            plugins(Plugin("org.jetbrains.kotlin.jvm"), pluginUnderTest)
                            withKotlin("kotlin { explicitApi() }\n")
                            jvmSubprojectDependencies()[path]?.takeIf { it.isNotEmpty() }?.let {
                                dependencies(*it.toTypedArray())
                            }
                        }
                    }
                }
            }
            .write()

    private fun sourceSetDependencies(sourceSet: String, dependencies: List<Dependency>): String =
        if (dependencies.isEmpty()) {
            ""
        } else {
            buildString {
                appendLine("    sourceSets.getByName(\"$sourceSet\").dependencies {")
                dependencies.forEach { appendLine("        ${it.renderForSourceSet()}") }
                appendLine("    }")
            }
        }

    /** The test kit renders dependencies only inside a top-level `dependencies` block. */
    private fun Dependency.renderForSourceSet(): String {
        val notation = if (identifier.startsWith(":")) {
            "project(\"$identifier\")"
        } else {
            "\"$identifier${version?.let { ":$it" }.orEmpty()}\""
        }
        return "$configuration($notation)"
    }
}

/**
 * A multiplatform [WatchdogProject] with an Android target provided by AGP's
 * `com.android.kotlin.multiplatform.library` plugin, from AGP 8 or, with [agp9], from AGP 9.
 * The android target names its main compilation `main`, its unit test compilation `hostTest`,
 * and its instrumented test compilation `deviceTest`, and it has no build variants. Tests must
 * call [assumeAndroidBuildEnvironment] first. AGP 8 builds are launched with
 * [agpCompatibleGradle] and AGP 9 builds with the current Gradle.
 */
open class KmpAndroidWatchdogProject(
    private val agp9: Boolean = false,
    explicitApi: Boolean = true,
    extraBuildScript: String = "",
) : AbstractKmpAndroidWatchdogProject(explicitApi, extraBuildScript) {
    // AGP 9 renames the multiplatform target block from `androidLibrary` to `android`.
    override fun androidTargetBlock(): String = """
        ${if (agp9) "android" else "androidLibrary"} {
            namespace = "test.lib"
            compileSdk = 36
            minSdk = 24
            withHostTestBuilder {}
        }
    """.trimIndent()

    override fun androidPlugin(): Plugin = Plugin(
        "com.android.kotlin.multiplatform.library",
        if (agp9) requiredTestAgp9Version() else requiredTestAgp8Version(),
    )
}

/**
 * A multiplatform [WatchdogProject] whose Android target is the Kotlin Multiplatform plugin's own
 * `androidTarget()` on top of `com.android.library`: the setup every multiplatform library used
 * before AGP's `com.android.kotlin.multiplatform.library` plugin existed, and one AGP 9 no longer
 * accepts. The target names its compilations after the build variant (`debug`, `release`), its
 * unit test compilations `debugUnitTest` and `releaseUnitTest`, and its instrumented test
 * compilation `debugAndroidTest`. Its compile tasks carry the target name, as in
 * `compileDebugKotlinAndroid`. Tests must call [assumeAndroidBuildEnvironment] first and launch
 * builds with [agpCompatibleGradle].
 */
open class LegacyKmpAndroidWatchdogProject(
    explicitApi: Boolean = true,
    extraBuildScript: String = "",
) : AbstractKmpAndroidWatchdogProject(explicitApi, extraBuildScript) {
    override fun androidTargetBlock(): String = """
        androidTarget {
            compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
        }
    """.trimIndent()

    // AGP 8 defaults its Java tasks to 1.8 while Kotlin follows the build JDK, so both sides are
    // pinned to keep KGP's JVM target validation quiet, as in AndroidLibraryWatchdogProject.
    override fun afterKotlinBlock(): String = """
        android {
            namespace = "test.lib"
            compileSdk = 36
            defaultConfig { minSdk = 24 }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    """.trimIndent() + "\n"

    override fun androidPlugin(): Plugin = Plugin("com.android.library", requiredTestAgp8Version())
}

/** The same file in another source set. */
private fun Source.inSourceSet(sourceSet: String): Source =
    Source(sourceType, name, path, source, sourceSet, forceLanguage)
