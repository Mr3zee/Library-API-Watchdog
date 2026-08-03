package org.jetbrains.kotlinx.libs.api.watchdog

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
import org.jetbrains.kotlin.compiler.plugin.devkit.test.getTestCompilerVersion
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
        "org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyExhaustive",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyUndocumented",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyFunctionTypeAlias",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDataClass",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWithoutEquals",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWithoutHashCode",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWithoutToString",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyMutableCollection",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyPairOrTriple",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyBooleanParameter",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyNullableBoolean",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyRequiredParameterAfterOptional",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyInconsistentParameterOrder",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyInlinedLogic",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyMangledJvmName",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyKotlinOnlyApi",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyNonStaticCompanionApi",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDefaultFacadeName",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWithoutJvmOverloads",
        "org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility",
        "org.jetbrains.kotlinx.libs.api.watchdog.InternalAnnotationMarker",
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

/** Skips the calling test when the environment cannot build Android projects. */
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
 * Main compilations are named after their build variant (`debug`, `release`) and test
 * compilations after the variant plus a suffix (`debugUnitTest`, `debugAndroidTest`). Tests must
 * call [assumeAndroidBuildEnvironment] first.
 *
 * [kotlinScript] is a `kotlin { ... }` configuration block. It lands at the top level of the
 * build script next to the Kotlin Android plugin, and nested inside `android { }` with built-in
 * Kotlin, which is where AGP 9 hosts it.
 */
open class AndroidLibraryWatchdogProject(
    private val kotlinScript: String = "kotlin { explicitApi() }",
    private val builtInKotlin: Boolean = false,
) : WatchdogProject() {
    /** Dependencies of the Android library module. */
    protected open fun androidDependencies(): List<Dependency> = emptyList()

    /** Plain Kotlin/JVM subprojects by path, built with the default build script. */
    protected open fun jvmSubprojects(): Map<String, List<Source>> = emptyMap()

    final override fun buildGradleProject(): GradleProject =
        newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
            .withRootProject {
                sources = this@AndroidLibraryWatchdogProject.sources()
                withBuildScript {
                    if (builtInKotlin) {
                        plugins(
                            Plugin("com.android.library", requiredTestAgp9Version()),
                            Plugin("org.jetbrains.kotlin.jvm", kotlinVersion ?: getTestCompilerVersion(), apply = false),
                            pluginUnderTest,
                        )
                        withKotlin(
                            buildString {
                                appendLine("android {")
                                appendLine("    namespace = \"test.consumer\"")
                                appendLine("    compileSdk = 36")
                                appendLine(kotlinScript.prependIndent("    "))
                                appendLine("}")
                            }
                        )
                    } else {
                        plugins(
                            Plugin("com.android.library", requiredTestAgp8Version()),
                            Plugin("org.jetbrains.kotlin.android", kotlinVersion ?: getTestCompilerVersion()),
                            pluginUnderTest,
                        )
                        // AGP 8 defaults its Java tasks to 1.8 while Kotlin follows the build
                        // JDK, so both sides are pinned to keep KGP's JVM target validation quiet.
                        withKotlin(
                            """
                                android {
                                    namespace = "test.consumer"
                                    compileSdk = 36
                                    compileOptions {
                                        sourceCompatibility = JavaVersion.VERSION_17
                                        targetCompatibility = JavaVersion.VERSION_17
                                    }
                                }
                                kotlin {
                                    compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }
                                }
                            """.trimIndent() + "\n" + kotlinScript,
                        )
                    }
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
                            // The root's Kotlin Android plugin ships the whole Kotlin Gradle
                            // plugin, so `org.jetbrains.kotlin.jvm` is already on the build
                            // classpath and must be requested without a version here.
                            plugins(Plugin("org.jetbrains.kotlin.jvm"), pluginUnderTest)
                            withKotlin(buildString { onBuildScript() })
                        }
                    }
                }
            }
            .write()
}

/**
 * A multiplatform [WatchdogProject] with an Android target provided by AGP's
 * `com.android.kotlin.multiplatform.library` plugin, from AGP 8 or, with [agp9], from AGP 9.
 * The android target names its main compilation `main`, its unit test compilation `hostTest`,
 * and its instrumented test compilation `deviceTest`. Tests must call
 * [assumeAndroidBuildEnvironment] first; AGP 8 builds are launched with [agpCompatibleGradle]
 * and AGP 9 builds with the current Gradle.
 */
open class KmpAndroidWatchdogProject(
    private val agp9: Boolean = false,
    explicitApi: Boolean = true,
    extraBuildScript: String = "",
) : WatchdogProject(
    multiplatform = true,
    explicitApi = explicitApi,
    extraBuildScript = extraBuildScript,
) {
    // AGP 9 renames the multiplatform target block from `androidLibrary` to `android`.
    override fun multiplatformTargetsBlock(): String = """
        kotlin {
            jvm()
            ${if (agp9) "android" else "androidLibrary"} {
                namespace = "test.lib"
                compileSdk = 36
                minSdk = 24
                withHostTestBuilder {}
            }
        }
    """.trimIndent() + "\n"

    override fun BuildScript.Builder.applyDefaultBuildScript() {
        plugins(
            KotlinPlugins.multiplatform(kotlinVersion),
            Plugin(
                "com.android.kotlin.multiplatform.library",
                if (agp9) requiredTestAgp9Version() else requiredTestAgp8Version(),
            ),
            pluginUnderTest,
        )
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
                sources = this@KmpAndroidWatchdogProject.sources()
                withBuildScript { applyDefaultBuildScript() }
                withDevKitSettings()
                withFile("local.properties", "sdk.dir=${requiredAndroidSdkDir()}")
            }
            .write()
}
