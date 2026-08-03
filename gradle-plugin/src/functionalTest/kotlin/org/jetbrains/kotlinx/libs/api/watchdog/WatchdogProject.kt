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

    override val pluginUnderTest: Plugin = Plugin("org.jetbrains.kotlinx.libs.api.watchdog", pluginUnderTestVersion)

    // The dev kit runtime artifacts are consumed from mavenLocal rather than from included builds.
    override fun repositories(defaults: List<Repository>): Repositories =
        Repositories((defaults + Repository.MAVEN_LOCAL).toMutableList())
}

/** The AGP version the build wires into the Android functional tests, see build.gradle.kts. */
val testAgpVersion: String? = System.getProperty("watchdog.test.agpVersion")

/** The Android SDK install the build discovered, or null when the machine has none. */
val testAndroidSdkPath: String? = System.getProperty("watchdog.test.androidHome")

/**
 * The Gradle version Android test projects are launched with. AGP 8 relies on a Gradle internal
 * API that Gradle 9.6.0 removed, and AGP 9 refuses the Kotlin Android Gradle plugin altogether
 * (its built-in Kotlin support bypasses the compiler plugin integration under test), so Android
 * projects run on the newest Gradle release line that still supports AGP 8.
 */
val agpCompatibleGradle: GradleVersion = GradleVersion.version("9.5.1")

/** Skips the calling test when the environment cannot build Android projects. */
fun assumeAndroidBuildEnvironment() {
    assumeTrue("No AGP version is wired in, see build.gradle.kts", testAgpVersion != null)
    assumeTrue("No Android SDK was found, see build.gradle.kts", testAndroidSdkPath != null)
}

private fun requiredTestAgpVersion(): String =
    requireNotNull(testAgpVersion) { "Android fixtures require assumeAndroidBuildEnvironment()" }

private fun requiredAndroidSdkDir(): String =
    File(requireNotNull(testAndroidSdkPath) { "Android fixtures require assumeAndroidBuildEnvironment()" })
        .invariantSeparatorsPath

/**
 * A [WatchdogProject] whose root module is an Android library built by AGP with the Kotlin
 * Android Gradle plugin. Main compilations are named after their build variant (`debug`,
 * `release`) and test compilations after the variant plus a suffix (`debugUnitTest`,
 * `debugAndroidTest`). Tests must call [assumeAndroidBuildEnvironment] first and launch builds
 * with [agpCompatibleGradle].
 */
open class AndroidLibraryWatchdogProject(
    private val kotlinScript: String = "kotlin { explicitApi() }",
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
                    plugins(
                        Plugin("com.android.library", requiredTestAgpVersion()),
                        Plugin("org.jetbrains.kotlin.android", kotlinVersion ?: getTestCompilerVersion()),
                        pluginUnderTest,
                    )
                    // AGP defaults its Java tasks to 1.8 while Kotlin follows the build JDK, so
                    // both sides are pinned to keep KGP's JVM target validation quiet.
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
 * `com.android.kotlin.multiplatform.library` plugin. The android target names its main
 * compilation `main`, its unit test compilation `hostTest`, and its instrumented test
 * compilation `deviceTest`. Tests must call [assumeAndroidBuildEnvironment] first and launch
 * builds with [agpCompatibleGradle].
 */
open class KmpAndroidWatchdogProject(
    explicitApi: Boolean = true,
    extraBuildScript: String = "",
) : WatchdogProject(
    multiplatform = true,
    explicitApi = explicitApi,
    extraBuildScript = extraBuildScript,
) {
    override fun multiplatformTargetsBlock(): String = """
        kotlin {
            jvm()
            androidLibrary {
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
            Plugin("com.android.kotlin.multiplatform.library", requiredTestAgpVersion()),
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
