package org.jetbrains.kotlinx.libs.api.watchdog

import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion

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
