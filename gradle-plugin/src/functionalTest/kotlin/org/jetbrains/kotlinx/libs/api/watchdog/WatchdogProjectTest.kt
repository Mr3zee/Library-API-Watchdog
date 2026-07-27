package org.jetbrains.kotlinx.libs.api.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language
import org.junit.Test

class WatchdogProjectTest {
    @Test
    fun failsWithErrorsOnUnacknowledgedApiByDefault() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by users")
        result.assertDiagnosticReported("e: ", "has no KDoc")
        result.assertDiagnosticReported("e: ", "abbreviates a function type")
        result.assertDiagnosticReported("e: ", "bakes its constructor property list")
        result.assertDiagnosticReported("e: ", "neither declares nor inherits a `toString`")
        result.assertDiagnosticReported("e: ", "exposes the mutable collection type")
        result.assertDiagnosticReported("e: ", "exposes the tuple type")
        result.assertDiagnosticReported("e: ", "takes the Boolean parameter")
        result.assertDiagnosticReported("e: ", "exposes a nullable Boolean")
        result.assertDiagnosticReported("e: ", "is required but declared after an optional parameter")
        result.assertDiagnosticReported("e: ", "appear in the opposite order in another overload")
        result.assertDiagnosticReported("e: ", "does more than delegate to a non-inline function")
        result.assertDiagnosticReported("e: ", "compiled JVM name is mangled")
        result.assertDiagnosticReported("e: ", "still lands in the API surface Java sources see")
        result.assertDiagnosticReported("e: ", "compiles to an instance method on the nested Companion class")
        result.assertDiagnosticReported("e: ", "compiles to an instance getter on the nested Companion class")
        result.assertDiagnosticReported("e: ", "compile into the facade class")
        result.assertDiagnosticReported("e: ", "declares default parameter values")
        result.assertDiagnosticReported("e: ", "allows the FUNCTION annotation target")
        result.assertDiagnosticReported("e: ", "declares no explicit @Target")
        result.assertDiagnosticReported("e: ", "has no effect on this parameter type")
    }

    @Test
    fun demotedDiagnosticsAreReportedAsWarnings() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    openApiWithoutSubclassOptIn = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    exhaustivePublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    undocumentedPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    functionTypeAliasPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    dataClassPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    statefulClassWithoutToString = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    mutableCollectionPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    pairOrTriplePublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    booleanParameterPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    nullableBooleanPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    requiredParameterAfterOptional = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    inconsistentParameterOrderInOverloads = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    inlineFunctionWithLogic = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    dslMarkerNoopTarget = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    dslMarkerWithoutExplicitTargets = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    dslMarkerNoopTypePosition = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    javaInterop {
                        mangledJvmNamePublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                        kotlinOnlyApiWithoutJvmSynthetic = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                        companionApiWithoutJvmStatic = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                        companionConstantWithoutJvmField = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                        topLevelApiWithoutJvmName = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                        defaultParametersWithoutJvmOverloads = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                    }
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        result.assertDiagnosticReported("w: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("w: ", "can be matched exhaustively by users")
        result.assertDiagnosticReported("w: ", "has no KDoc")
        result.assertDiagnosticReported("w: ", "abbreviates a function type")
        result.assertDiagnosticReported("w: ", "bakes its constructor property list")
        result.assertDiagnosticReported("w: ", "neither declares nor inherits a `toString`")
        result.assertDiagnosticReported("w: ", "exposes the mutable collection type")
        result.assertDiagnosticReported("w: ", "exposes the tuple type")
        result.assertDiagnosticReported("w: ", "takes the Boolean parameter")
        result.assertDiagnosticReported("w: ", "exposes a nullable Boolean")
        result.assertDiagnosticReported("w: ", "is required but declared after an optional parameter")
        result.assertDiagnosticReported("w: ", "appear in the opposite order in another overload")
        result.assertDiagnosticReported("w: ", "does more than delegate to a non-inline function")
        result.assertDiagnosticReported("w: ", "compiled JVM name is mangled")
        result.assertDiagnosticReported("w: ", "still lands in the API surface Java sources see")
        result.assertDiagnosticReported("w: ", "compiles to an instance method on the nested Companion class")
        result.assertDiagnosticReported("w: ", "compiles to an instance getter on the nested Companion class")
        result.assertDiagnosticReported("w: ", "compile into the facade class")
        result.assertDiagnosticReported("w: ", "declares default parameter values")
        result.assertDiagnosticReported("w: ", "allows the FUNCTION annotation target")
        result.assertDiagnosticReported("w: ", "declares no explicit @Target")
        result.assertDiagnosticReported("w: ", "has no effect on this parameter type")
    }

    @Test
    fun severityIsConfiguredPerDiagnostic() {
        // The compiler swallows regular warnings when a compilation fails with errors, so warning
        // reporting is forced to observe the demoted diagnostic next to the remaining errors.
        val project = object : WatchdogProject(
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xreport-all-warnings") } }
                apiWatchdog {
                    undocumentedPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by users")
        result.assertDiagnosticReported("w: ", "has no KDoc")
    }

    @Test
    fun disabledJavaInteropGroupSilencesAllItsDiagnostics() {
        // The off-switch silences every Java-interop diagnostic at once and wins over the
        // individual severities inside the group.
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    javaInterop {
                        enabled = false
                        companionApiWithoutJvmStatic = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.ERROR
                    }
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        // The non-interop diagnostics still fail the build...
        result.assertDiagnosticReported("e: ", "has no KDoc")
        // ...while the whole group is off, the explicitly set severity included.
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("nested Companion class"))
        assertFalse(result.output.contains("compile into the facade class"))
        assertFalse(result.output.contains("declares default parameter values"))
    }

    @Test
    fun disabledDiagnosticsAreNotReported() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    undocumentedPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.NONE
                    statefulClassWithoutToString = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.NONE
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        // The remaining diagnostics still fail the build...
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by users")
        // ...while the disabled ones are not reported at all.
        assertFalse(result.output.contains("has no KDoc"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
    }

    @Test
    fun unexplainedExemptionIsAlwaysAnError() {
        // The only configurable diagnostic the file triggers is demoted, so the remaining error
        // proves EXEMPTION_WITHOUT_EXPLANATION ignores severity configuration: the extension
        // deliberately offers no property for it.
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    undocumentedPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unexplainedExemptionFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "exemption does not explain why it is applied")
    }

    @Test
    fun acknowledgedApiCompilesWithoutDiagnostics() {
        // The source() helper always leads with the package statement, so the file-level facade
        // exemption is prepended by assembling the source file by hand.
        val project = object : WatchdogProject() {
            override fun sources() = listOf(
                Source.kotlin(
                    buildString {
                        appendLine("@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
                        appendLine()
                        appendLine("package test")
                        defaultImports.forEach { appendLine("import $it") }
                        appendLine()
                        appendLine(acknowledgedFile)
                    }
                ).withPath("test", "acknowledged").build()
            )
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("can be subclassed outside the library"))
        assertFalse(result.output.contains("can be matched exhaustively by users"))
        assertFalse(result.output.contains("has no KDoc"))
        assertFalse(result.output.contains("abbreviates a function type"))
        assertFalse(result.output.contains("bakes its constructor property list"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
        assertFalse(result.output.contains("exposes the mutable collection type"))
        assertFalse(result.output.contains("exposes the tuple type"))
        assertFalse(result.output.contains("takes the Boolean parameter"))
        assertFalse(result.output.contains("exposes a nullable Boolean"))
        assertFalse(result.output.contains("is required but declared after an optional parameter"))
        assertFalse(result.output.contains("appear in the opposite order in another overload"))
        assertFalse(result.output.contains("does more than delegate to a non-inline function"))
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("nested Companion class"))
        assertFalse(result.output.contains("compile into the facade class"))
        assertFalse(result.output.contains("declares default parameter values"))
        assertFalse(result.output.contains("DSL marker"))
    }

    @Test
    fun silentWithoutExplicitApiMode() {
        val project = object : WatchdogProject(explicitApi = false) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("can be subclassed outside the library"))
        assertFalse(result.output.contains("can be matched exhaustively by users"))
        assertFalse(result.output.contains("has no KDoc"))
        assertFalse(result.output.contains("abbreviates a function type"))
        assertFalse(result.output.contains("bakes its constructor property list"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
        assertFalse(result.output.contains("exposes the mutable collection type"))
        assertFalse(result.output.contains("exposes the tuple type"))
        assertFalse(result.output.contains("takes the Boolean parameter"))
        assertFalse(result.output.contains("exposes a nullable Boolean"))
        assertFalse(result.output.contains("is required but declared after an optional parameter"))
        assertFalse(result.output.contains("appear in the opposite order in another overload"))
        assertFalse(result.output.contains("does more than delegate to a non-inline function"))
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("nested Companion class"))
        assertFalse(result.output.contains("compile into the facade class"))
        assertFalse(result.output.contains("declares default parameter values"))
        assertFalse(result.output.contains("DSL marker"))
    }

    @Test
    fun internalAnnotationMarkerExemptsAcrossModules() {
        // The marker annotation lives in `:lib`, so the consuming root module reads the
        // @InternalAnnotationMarker meta-annotation from the compiled dependency.
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(markerConsumerFile, "consumer", "test", true, "test.lib.InternalLibApi"))
                    dependencies(implementation(":lib"))
                }
                subproject("lib") {
                    sources(source(markerLibraryFile, "markers", "test.lib"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        // The unmarked control declaration proves the checks ran in the consuming module...
        result.assertDiagnosticReported("e: ", "'WatchedClass' is part of the public API but has no KDoc")
        // ...while declarations marked with the dependency's marker annotation are exempt.
        assertFalse(result.output.contains("InternalOpenClass"))
        assertFalse(result.output.contains("memberOfInternal"))
        assertFalse(result.output.contains("InternalEnum"))
        assertFalse(result.output.contains("INTERNAL_ENTRY"))
        assertFalse(result.output.contains("internalFunction"))
        assertFalse(result.output.contains("can be subclassed outside the library"))
        assertFalse(result.output.contains("can be matched exhaustively by users"))
    }

    @Test
    fun warnsWhenExplicitApiModeIsNotEnabled() {
        // The warning is logged during configuration, so `help` is enough to observe it.
        val project = WatchdogProject(explicitApi = false).gradleProject

        val result = build(project.rootDir, "help")
        assertTrue(result.output.contains("does not enable explicit API mode"))
        assertTrue(result.output.contains("explicitApi()"))
    }

    @Test
    fun explicitApiWarningIsSilentWhenExplicitApiModeIsEnabled() {
        val project = WatchdogProject().gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("does not enable explicit API mode"))
    }

    @Test
    fun explicitApiWarningSeesTheRawCompilerFlag() {
        // The flag lands in the effective free compiler arguments of the compile tasks, which is
        // where the check looks when the DSL setting is absent.
        val project = WatchdogProject(
            explicitApi = false,
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") } }
            """.trimIndent(),
        ).gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("does not enable explicit API mode"))
    }

    @Test
    fun explicitApiWarningCanBeSuppressedWithAGradleProperty() {
        val project = WatchdogProject(explicitApi = false).gradleProject

        val result = build(
            project.rootDir,
            "help",
            "-Porg.jetbrains.kotlinx.libs.api.watchdog.suppressExplicitApiWarning=true",
        )
        assertFalse(result.output.contains("does not enable explicit API mode"))
    }

    @Test
    fun suggestsTapmocWhenItIsNotApplied() {
        // The suggestion is logged during configuration, so `help` is enough to observe it.
        val project = WatchdogProject().gradleProject

        val result = build(project.rootDir, "help")
        assertTrue(result.output.contains("applies libs-api-watchdog but not Tapmoc"))
        assertTrue(result.output.contains("""id("com.gradleup.tapmoc") version "<version>""""))
        assertTrue(result.output.contains("https://github.com/GradleUp/Tapmoc/releases/latest"))
        assertTrue(result.output.contains("https://gradleup.com/tapmoc/"))
        assertTrue(result.output.contains("suggestTapmoc = false"))
    }

    @Test
    fun tapmocSuggestionCanBeDisabled() {
        val project = WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    suggestTapmoc = false
                }
            """.trimIndent(),
        ).gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("applies libs-api-watchdog but not Tapmoc"))
    }

    @Test
    fun tapmocSuggestionIsSilentWhenTapmocIsApplied() {
        // A buildSrc stand-in registers the real Tapmoc plugin id, so the check sees the plugin
        // as applied without the test fetching the actual artifact.
        val project = object : WatchdogProject(
            extraBuildScript = """apply(plugin = "com.gradleup.tapmoc")""",
        ) {
            override fun buildGradleProject(): GradleProject =
                newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
                    .withRootProject {
                        withBuildScript { applyDefaultBuildScript() }
                        withDevKitSettings()
                    }
                    .withBuildSrc {
                        withBuildScript {
                            plugins(Plugin("java-gradle-plugin"))
                            withKotlin(
                                """
                                    gradlePlugin {
                                        plugins {
                                            create("fakeTapmoc") {
                                                id = "com.gradleup.tapmoc"
                                                implementationClass = "test.FakeTapmocPlugin"
                                            }
                                        }
                                    }
                                """.trimIndent()
                            )
                        }
                        sources.add(
                            Source.java(
                                """
                                    package test;

                                    import org.gradle.api.Plugin;
                                    import org.gradle.api.Project;

                                    public class FakeTapmocPlugin implements Plugin<Project> {
                                        @Override
                                        public void apply(Project project) {}
                                    }
                                """.trimIndent()
                            ).withPath("test", "FakeTapmocPlugin").build()
                        )
                    }
                    .write()
        }.gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("applies libs-api-watchdog but not Tapmoc"))
    }

    @Test
    fun suggestsAbiValidationWhenItIsNotEnabled() {
        // The suggestion is logged during configuration, so `help` is enough to observe it.
        val project = WatchdogProject().gradleProject

        val result = build(project.rootDir, "help")
        assertTrue(result.output.contains("but no binary compatibility validation is enabled"))
        assertTrue(result.output.contains("abiValidation()"))
        assertTrue(result.output.contains("https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html"))
        assertTrue(result.output.contains("https://github.com/Kotlin/binary-compatibility-validator"))
        assertTrue(result.output.contains("suggestAbiValidation = false"))
    }

    @Test
    fun abiValidationSuggestionCanBeDisabled() {
        val project = WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    suggestAbiValidation = false
                }
            """.trimIndent(),
        ).gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("but no binary compatibility validation is enabled"))
    }

    @Test
    fun abiValidationSuggestionIsSilentWhenBuiltInAbiValidationIsEnabled() {
        val project = WatchdogProject(
            extraBuildScript = """
                kotlin {
                    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
                    abiValidation()
                }
            """.trimIndent(),
        ).gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("but no binary compatibility validation is enabled"))
    }

    @Test
    fun abiValidationSuggestionIsSilentWhenStandaloneValidatorIsApplied() {
        // A buildSrc stand-in registers the real Binary Compatibility Validator plugin id, so the
        // check sees the plugin as applied without the test fetching the actual artifact.
        val project = object : WatchdogProject(
            extraBuildScript = """apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")""",
        ) {
            override fun buildGradleProject(): GradleProject =
                newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
                    .withRootProject {
                        withBuildScript { applyDefaultBuildScript() }
                        withDevKitSettings()
                    }
                    .withBuildSrc {
                        withBuildScript {
                            plugins(Plugin("java-gradle-plugin"))
                            withKotlin(
                                """
                                    gradlePlugin {
                                        plugins {
                                            create("fakeBcv") {
                                                id = "org.jetbrains.kotlinx.binary-compatibility-validator"
                                                implementationClass = "test.FakeBcvPlugin"
                                            }
                                        }
                                    }
                                """.trimIndent()
                            )
                        }
                        sources.add(
                            Source.java(
                                """
                                    package test;

                                    import org.gradle.api.Plugin;
                                    import org.gradle.api.Project;

                                    public class FakeBcvPlugin implements Plugin<Project> {
                                        @Override
                                        public void apply(Project project) {}
                                    }
                                """.trimIndent()
                            ).withPath("test", "FakeBcvPlugin").build()
                        )
                    }
                    .write()
        }.gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("but no binary compatibility validation is enabled"))
    }

    /** Asserts the message was reported with the given compiler severity prefix (`e: ` or `w: `). */
    private fun BuildResult.assertDiagnosticReported(severityPrefix: String, message: String) {
        assertTrue(
            output.lineSequence().any { it.startsWith(severityPrefix) && message in it },
            "Expected a '$severityPrefix' line containing '$message' in build output:\n$output",
        )
    }
}

@Suppress("RedundantVisibilityModifier", "RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
private val unacknowledgedFile = """
    public open class UnprotectedOpenClass

    public enum class UnmarkedEnum { A, B }

    /** An unacknowledged function type alias. */
    public typealias UnacknowledgedCallback = (Int) -> Unit

    /**
     * An unacknowledged data class.
     *
     * @param x the only coordinate.
     */
    public data class UnmarkedData(val x: Int)

    /**
     * A stateful session relying on the opaque default toString.
     *
     * @param id the session identifier.
     */
    public class UnrenderedSession(public val id: Int)

    /** A function handing out the library's mutable state. */
    public fun leakState(): MutableList<String> = mutableListOf()

    /** A function pairing coordinates without naming them. */
    public fun locateOrigin(): Pair<Int, Int> = 0 to 0

    /** A function switched by an opaque positional flag. */
    public fun toggleWork(enabled: Boolean) {}

    /** A query returning a silent three-state flag. */
    public fun lastKnownState(): Boolean? = null

    /** A function declaring a required parameter after an optional one. */
    public fun retryWork(retries: Int = 3, host: String) {}

    /** An overload setting the parameter order convention. */
    public fun drawShape(x: Int, y: Int) {}

    /** An overload breaking the parameter order convention. */
    public fun drawShape(y: Int, x: Int, scale: Double) {}

    /** An inline function computing inline instead of delegating. */
    @Suppress("NOTHING_TO_INLINE")
    public inline fun squared(value: Int): Int = value * value

    /** A DSL marker with a target on which it has no effect. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class NoopTargetDsl

    /** A DSL marker left with the default target set. */
    @DslMarker
    public annotation class TargetlessDsl

    /** A DSL marker with tidy targets, misapplied to an inert type position below. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class ScopedDsl

    /** A parameter type carrying a DSL marker that restricts nothing. */
    public fun processTag(tag: @ScopedDsl UnprotectedOpenClass): Unit = Unit

    /**
     * A user handle compiled to its underlying type.
     *
     * @param raw the raw handle value.
     */
    @JvmInline
    public value class UserHandle(public val raw: String)

    /** A lookup whose JVM name is mangled by the value class parameter. */
    public fun findUser(handle: UserHandle) {}

    /** A coordinator whose companion members hide behind the Companion instance for Java. */
    public class Coordinator {
        /** The companion holding the factory and the default label. */
        public companion object {
            /** A factory Java callers reach only through the Companion instance. */
            public fun instance(): Coordinator = Coordinator()

            /** A constant Java callers read only through the Companion instance getter. */
            public val DEFAULT_LABEL: String = "coordinator"
        }
    }

    /** A suspend function left visible to Java sources. */
    public suspend fun refreshState(): Int = 0

    /** A function whose default parameter values do not exist for Java callers. */
    public fun openPort(host: String, port: Int = 8080) {}
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val unexplainedExemptionFile = """
    @IntentionallyUndocumented
    public class UnexplainedExemption
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val markerLibraryFile = """
    /** Flags declarations that are public for technical reasons but are not supported API. */
    @InternalAnnotationMarker
    @Target(
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
    )
    public annotation class InternalLibApi
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val markerConsumerFile = """
    @InternalLibApi
    public open class InternalOpenClass {
        public fun memberOfInternal() {}
    }

    @InternalLibApi
    public enum class InternalEnum { INTERNAL_ENTRY }

    @InternalLibApi
    public fun internalFunction() {}

    public class WatchedClass
""".trimIndent()

@Suppress("RedundantVisibilityModifier", "RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
private val acknowledgedFile = """
    /** A deliberately open class. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public open class DeliberatelyOpenClass

    /** A deliberately exhaustive enum. */
    @IntentionallyExhaustive(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public enum class MarkedEnum {
        /** The first entry. */
        A,

        /** The second entry. */
        B,
    }

    @IntentionallyUndocumented(description = "Self-explanatory.")
    public class DeliberatelyUndocumentedClass

    /** A deliberate function type alias. */
    @IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
    public typealias DeliberateCallback = (Int) -> Unit

    /**
     * A deliberately stable data holder.
     *
     * @param x the only coordinate.
     */
    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
    public data class DeliberateData(val x: Int)

    /**
     * A deliberately opaque credentials holder.
     *
     * @param token the secret that must not leak into logs.
     */
    @IntentionallyWithoutToString(reason = ExemptionReason.API_DESIGN, description = "The token must not leak into logs.")
    public class OpaqueCredentials(public val token: String)

    /** A deliberately shared mutable buffer. */
    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public fun sharedBuffer(): MutableList<String> = mutableListOf()

    /** Deliberately shared mutable batches, acknowledged on the type usage. */
    public fun sharedBatches(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<String>> = emptyList()

    /** A deliberately exposed coordinate pair. */
    @IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
    public fun originPoint(): Pair<Int, Int> = 0 to 0

    /** A deliberately Boolean-switched toggle. */
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun setEnabled(enabled: Boolean) {}

    /** A deliberately three-state query result. */
    @IntentionallyNullableBoolean(reason = ExemptionReason.EXTERNAL_CONTRACT, description = "Mirrors the wire format's optional flag.")
    public fun consentState(): Boolean? = null

    /** A legacy signature keeping its required parameter behind an optional one. */
    @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    @IntentionallyWithoutJvmOverloads(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public fun legacyRetry(retries: Int = 3, host: String) {}

    /** A deliberately Kotlin-only refresher left visible to Java. */
    @IntentionallyKotlinOnlyApi(reason = ExemptionReason.IGNORE_JAVA_INTEROP, description = "Coroutine-first API; Java is served by a blocking facade.")
    public suspend fun refreshAccounts(): Int = 0

    /** A holder whose companion deliberately serves Java callers through the instance. */
    public class KotlinFacingCoordinator {
        /** The companion acknowledged as companion-instance-only for Java callers. */
        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        public companion object {
            /** A factory reached through the Companion instance. */
            public fun instance(): KotlinFacingCoordinator = KotlinFacingCoordinator()

            /** A constant read through the Companion instance getter. */
            public val DEFAULT_LABEL: String = "coordinator"
        }
    }

    /** An overload setting the parameter order convention. */
    public fun renderShape(x: Int, y: Int) {}

    /** An overload with a deliberately different parameter order. */
    @IntentionallyInconsistentParameterOrder(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public fun renderShape(y: Int, x: Int, alpha: Long) {}

    /** A deliberately inlined fast path. */
    @IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
    @Suppress("NOTHING_TO_INLINE")
    public inline fun cubed(value: Int): Int = value * value * value

    /**
     * An account handle compiled to its underlying type.
     *
     * @param raw the raw handle value.
     */
    @JvmInline
    public value class AccountHandle(public val raw: String)

    /** A deliberately Kotlin-only lookup. */
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
    public fun findAccount(handle: AccountHandle) {}

    /** A DSL marker with only effective targets. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class TidyDsl

    /** A legacy DSL marker whose wrong target set is kept for backwards compatibility. */
    @IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class LegacyDsl
""".trimIndent()
