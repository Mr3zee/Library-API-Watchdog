package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.junit.Test

class WatchdogProjectTest {
    @Test
    fun failsWithErrorsOnUnacknowledgedApiByDefault() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(
                source(unacknowledgedFile),
                source(internalApiExposureFile, "internalApiExposure"),
            )
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by users")
        result.assertDiagnosticReported("e: ", "has no `KDoc`")
        result.assertDiagnosticReported("e: ", "abbreviates a function type")
        result.assertDiagnosticReported("e: ", "bakes its constructor property list")
        result.assertDiagnosticReported("e: ", "Poko (https://github.com/drewhamilton/Poko)")
        result.assertDiagnosticReported("e: ", "neither declares nor inherits an `equals`")
        result.assertDiagnosticReported("e: ", "neither declares nor inherits a `hashCode`")
        result.assertDiagnosticReported("e: ", "neither declares nor inherits a `toString`")
        result.assertDiagnosticReported(
            "e: ",
            "Poko (https://mr3zee.github.io/Library-API-Watchdog/checks/" +
                    "stateful-class-without-equals-hashcode-to-string#poko)",
        )
        result.assertDiagnosticReported("e: ", "press `${ideaGenerateShortcut()}`")
        result.assertDiagnosticReported("e: ", "exposes the mutable collection type")
        result.assertDiagnosticReported("e: ", "exposes the tuple type")
        result.assertDiagnosticReported("e: ", "takes the `Boolean` parameter")
        result.assertDiagnosticReported("e: ", "exposes a nullable `Boolean`")
        result.assertDiagnosticReported("e: ", "is required but declared after an optional parameter")
        result.assertDiagnosticReported("e: ", "appear in the opposite relative order in another overload")
        result.assertDiagnosticReported("e: ", "contains logic beyond a single thin delegating statement")
        result.assertDiagnosticReported("e: ", "compiled JVM name is mangled")
        result.assertDiagnosticReported("e: ", "still lands in the API surface Java sources see")
        result.assertDiagnosticReported("e: ", "compiles to an instance method on the nested `Companion` class")
        result.assertDiagnosticReported("e: ", "leaves its getter on the nested `Companion` class")
        result.assertDiagnosticReported("e: ", "compile into the facade class")
        result.assertDiagnosticReported("e: ", "declares default parameter values")
        result.assertDiagnosticReported("e: ", "allows the `FUNCTION` annotation target")
        result.assertDiagnosticReported("e: ", "declares no explicit `@Target`")
        result.assertDiagnosticReported("e: ", "has no effect on this parameter type")
        result.assertDiagnosticReported(
            "e: ",
            "parameter `first` publicly exposes `test.InternalModel`, but that type is marked " +
                    "as internal API with `@InternalLibApi`",
        )
        result.assertDiagnosticReported(
            "e: ",
            "parameter `second` publicly exposes `test.OtherInternalModel`, but that type is " +
                    "marked as internal API with `@OtherInternalApi`",
        )
    }

    @Test
    fun demotedDiagnosticsAreReportedAsWarnings() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    openApiWithoutSubclassOptIn = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    exhaustivePublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    undocumentedPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    functionTypeAliasPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    dataClassPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    statefulClassWithoutEquals = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    statefulClassWithoutHashCode = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    statefulClassWithoutToString = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    mutableCollectionPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    pairOrTriplePublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    booleanParameterPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    nullableBooleanPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    requiredParameterAfterOptional = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    inconsistentParameterOrderInOverloads = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    inlineFunctionWithLogic = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    dslMarkerNoopTarget = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    dslMarkerWithoutExplicitTargets = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    dslMarkerNoopTypePosition = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    javaInterop {
                        mangledJvmNamePublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                        kotlinOnlyApiWithoutJvmSynthetic = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                        companionApiWithoutJvmStatic = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                        companionPropertyWithoutStaticAccess = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                        topLevelApiWithoutJvmName = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                        defaultParametersWithoutJvmOverloads = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    }
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = build(project.rootDir, "build")
        result.assertDiagnosticReported("w: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("w: ", "can be matched exhaustively by users")
        result.assertDiagnosticReported("w: ", "has no `KDoc`")
        result.assertDiagnosticReported("w: ", "abbreviates a function type")
        result.assertDiagnosticReported("w: ", "bakes its constructor property list")
        result.assertDiagnosticReported("w: ", "neither declares nor inherits an `equals`")
        result.assertDiagnosticReported("w: ", "neither declares nor inherits a `hashCode`")
        result.assertDiagnosticReported("w: ", "neither declares nor inherits a `toString`")
        result.assertDiagnosticReported("w: ", "exposes the mutable collection type")
        result.assertDiagnosticReported("w: ", "exposes the tuple type")
        result.assertDiagnosticReported("w: ", "takes the `Boolean` parameter")
        result.assertDiagnosticReported("w: ", "exposes a nullable `Boolean`")
        result.assertDiagnosticReported("w: ", "is required but declared after an optional parameter")
        result.assertDiagnosticReported("w: ", "appear in the opposite relative order in another overload")
        result.assertDiagnosticReported("w: ", "contains logic beyond a single thin delegating statement")
        result.assertDiagnosticReported("w: ", "compiled JVM name is mangled")
        result.assertDiagnosticReported("w: ", "still lands in the API surface Java sources see")
        result.assertDiagnosticReported("w: ", "compiles to an instance method on the nested `Companion` class")
        result.assertDiagnosticReported("w: ", "leaves its getter on the nested `Companion` class")
        result.assertDiagnosticReported("w: ", "compile into the facade class")
        result.assertDiagnosticReported("w: ", "declares default parameter values")
        result.assertDiagnosticReported("w: ", "allows the `FUNCTION` annotation target")
        result.assertDiagnosticReported("w: ", "declares no explicit `@Target`")
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
                    undocumentedPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "can be matched exhaustively by users")
        result.assertDiagnosticReported("w: ", "has no `KDoc`")
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
                        companionApiWithoutJvmStatic = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.ERROR
                    }
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        // The non-interop diagnostics still fail the build...
        result.assertDiagnosticReported("e: ", "has no `KDoc`")
        // ...while the whole group is off, the explicitly set severity included.
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("nested `Companion` class"))
        assertFalse(result.output.contains("compile into the facade class"))
        assertFalse(result.output.contains("declares default parameter values"))
    }

    @Test
    fun disabledDiagnosticsAreNotReported() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    undocumentedPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.NONE
                    statefulClassWithoutEquals = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.NONE
                    statefulClassWithoutHashCode = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.NONE
                    statefulClassWithoutToString = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.NONE
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
        assertFalse(result.output.contains("has no `KDoc`"))
        assertFalse(result.output.contains("neither declares nor inherits an `equals`"))
        assertFalse(result.output.contains("neither declares nor inherits a `hashCode`"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
    }

    @Test
    fun unexplainedExemptionIsAlwaysAnError() {
        // The checker that honors this type-use exemption is disabled, so the remaining error
        // proves EXEMPTION_WITHOUT_EXPLANATION is enforced independently and ignores severity
        // configuration: the extension deliberately offers no property for it.
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    mutableCollectionPublicApi = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.NONE
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(unexplainedExemptionFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "exemption doesn't explain why it is applied")
    }

    @Test
    fun internalApiExposureIsAlwaysAnError() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(internalApiExposureFile, "internalApiExposure"))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "marked as internal API with `@InternalLibApi`")
    }

    @Test
    fun internalApiExposureCheckCanOnlyBeDisabledAsAWhole() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    publicTypeWithInternalApi = false
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(internalApiExposureFile, "internalApiExposure"))
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("marked as internal API"))
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
        assertFalse(result.output.contains("has no `KDoc`"))
        assertFalse(result.output.contains("abbreviates a function type"))
        assertFalse(result.output.contains("bakes its constructor property list"))
        assertFalse(result.output.contains("neither declares nor inherits an `equals`"))
        assertFalse(result.output.contains("neither declares nor inherits a `hashCode`"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
        assertFalse(result.output.contains("exposes the mutable collection type"))
        assertFalse(result.output.contains("exposes the tuple type"))
        assertFalse(result.output.contains("takes the `Boolean` parameter"))
        assertFalse(result.output.contains("exposes a nullable `Boolean`"))
        assertFalse(result.output.contains("is required but declared after an optional parameter"))
        assertFalse(result.output.contains("appear in the opposite relative order in another overload"))
        assertFalse(result.output.contains("contains logic beyond a single thin delegating statement"))
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("nested `Companion` class"))
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
        assertFalse(result.output.contains("has no `KDoc`"))
        assertFalse(result.output.contains("abbreviates a function type"))
        assertFalse(result.output.contains("bakes its constructor property list"))
        assertFalse(result.output.contains("neither declares nor inherits an `equals`"))
        assertFalse(result.output.contains("neither declares nor inherits a `hashCode`"))
        assertFalse(result.output.contains("neither declares nor inherits a `toString`"))
        assertFalse(result.output.contains("exposes the mutable collection type"))
        assertFalse(result.output.contains("exposes the tuple type"))
        assertFalse(result.output.contains("takes the `Boolean` parameter"))
        assertFalse(result.output.contains("exposes a nullable `Boolean`"))
        assertFalse(result.output.contains("is required but declared after an optional parameter"))
        assertFalse(result.output.contains("appear in the opposite relative order in another overload"))
        assertFalse(result.output.contains("contains logic beyond a single thin delegating statement"))
        assertFalse(result.output.contains("compiled JVM name is mangled"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("nested `Companion` class"))
        assertFalse(result.output.contains("compile into the facade class"))
        assertFalse(result.output.contains("declares default parameter values"))
        assertFalse(result.output.contains("DSL marker"))
    }

    @Test
    fun testSourcesAreNotChecked() {
        // Test sources are not published, so they carry no API contract to watch. The same
        // declaration in main sources fails the build, see failsWithErrorsOnUnacknowledgedApiByDefault.
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(cleanMainFile), testOnlySource())
        }.gradleProject

        val result = build(project.rootDir, "compileTestKotlin")
        result.assertNoTestSourceDiagnostics()
    }

    @Test
    fun testSourcesAreNotCheckedWhenExplicitApiModeIsForcedOnEveryCompilation() {
        // A raw compiler flag reaches test compilations too, unlike `kotlin { explicitApi() }`,
        // which the Kotlin Gradle plugin deliberately keeps off for them.
        val project = object : WatchdogProject(
            explicitApi = false,
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(cleanMainFile), testOnlySource())
        }.gradleProject

        val result = build(project.rootDir, "compileTestKotlin")
        result.assertNoTestSourceDiagnostics()
    }

    @Test
    fun multiplatformTestSourcesAreNotChecked() {
        // Every multiplatform target compiles the shared `commonTest` source set along with its
        // own, so the exclusion has to hold for both. The raw flag is the strict case here too.
        val project = object : WatchdogProject(
            multiplatform = true,
            explicitApi = false,
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }
            """.trimIndent(),
        ) {
            override fun multiplatformTargetsBlock(): String = "kotlin {\n  jvm()\n}\n"

            override fun sources() = listOf(
                source(cleanMainFile),
                testOnlySource("commonTest"),
                testOnlySource("jvmTest", "PlatformTestOnlyHelper"),
            )
        }.gradleProject

        val result = build(project.rootDir, "compileTestKotlinJvm")
        result.assertNoTestSourceDiagnostics()
    }

    @Test
    fun expectOwnsContractChecksAndExemptions() {
        val project = object : WatchdogProject(multiplatform = true) {
            override fun multiplatformTargetsBlock(): String = "kotlin { jvm() }"

            override fun sources() = listOf(
                Source.kotlin(
                    """
                        package test

                        import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                        import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
                        import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean
                        import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyPairOrTriple

                        /** The shared contract owns its source-shape exemptions. */
                        @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
                        @IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
                        @IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
                        public expect fun shared(flag: Boolean): Pair<String, Boolean?>

                        /** An unexempt shared contract is reported from common source. */
                        public expect fun reported(flag: Boolean): Pair<String, Boolean?>
                    """.trimIndent(),
                ).withSourceSet("commonMain").withPath("test", "CommonApi").build(),
                Source.kotlin(
                    """
                        @file:org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName(
                            reason = org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason.API_DESIGN,
                        )

                        package test

                        public actual fun shared(flag: Boolean): Pair<String, Boolean?> =
                            flag.toString() to flag

                        public actual fun reported(flag: Boolean): Pair<String, Boolean?> =
                            flag.toString() to flag

                        /** Platform-only declarations remain part of the checked source API. */
                        public fun platformOnly(flag: Boolean): Int = if (flag) 1 else 0
                    """.trimIndent(),
                ).withSourceSet("jvmMain").withPath("test", "JvmApi").build(),
            )
        }.gradleProject

        val result = buildAndFail(project.rootDir, "compileKotlinJvm")
        val booleanReports = result.output.lineSequence()
            .filter { ".kt:" in it && "takes the `Boolean` parameter" in it }
            .toList()
        val nullableBooleanReports = result.output.lineSequence()
            .filter { ".kt:" in it && "exposes a nullable `Boolean`" in it }
            .toList()
        val tupleReports = result.output.lineSequence()
            .filter { ".kt:" in it && "exposes the tuple type" in it }
            .toList()
        assertEquals(2, booleanReports.size, result.output)
        assertEquals(1, booleanReports.count { "CommonApi.kt" in it }, result.output)
        assertEquals(1, booleanReports.count { "JvmApi.kt" in it }, result.output)
        assertEquals(1, nullableBooleanReports.size, result.output)
        assertTrue(nullableBooleanReports.single().contains("CommonApi.kt"), result.output)
        assertEquals(1, tupleReports.size, result.output)
        assertTrue(tupleReports.single().contains("CommonApi.kt"), result.output)
    }

    @Test
    fun jvmChecksReportActualOnceUsingExpectSignature() {
        val project = object : WatchdogProject(
            multiplatform = true,
            extraBuildScript = """
                apiWatchdog {
                    javaInterop {
                        kotlinOnlyApiWithoutJvmSynthetic = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                        defaultParametersWithoutJvmOverloads = org.jetbrains.kotlinx.library.api.watchdog.WatchdogSeverity.WARNING
                    }
                }
            """.trimIndent(),
        ) {
            override fun multiplatformTargetsBlock(): String = "kotlin { jvm() }"

            override fun sources() = listOf(
                Source.kotlin(
                    """
                        package test

                        /** A Kotlin-only shared contract. */
                        public expect suspend fun suspended(): Int

                        /** A shared contract with a default argument. */
                        public expect fun defaulted(value: Int = 1): Int
                    """.trimIndent(),
                ).withSourceSet("commonMain").withPath("test", "CommonApi").build(),
                Source.kotlin(
                    """
                        @file:org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName(
                            reason = org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason.API_DESIGN,
                        )

                        package test

                        public actual suspend fun suspended(): Int = 1

                        public actual fun defaulted(value: Int): Int = value
                    """.trimIndent(),
                ).withSourceSet("jvmMain").withPath("test", "JvmApi").build(),
            )
        }.gradleProject

        val result = build(project.rootDir, "compileKotlinJvm")
        val kotlinOnlyReports = result.output.lineSequence()
            .filter { "still lands in the API surface Java sources see" in it }
            .toList()
        val defaultReports = result.output.lineSequence()
            .filter { "declares default parameter values" in it }
            .toList()
        assertEquals(1, kotlinOnlyReports.size, result.output)
        assertEquals(1, defaultReports.size, result.output)
        assertTrue(kotlinOnlyReports.single().contains("JvmApi.kt"), result.output)
        assertTrue(defaultReports.single().contains("JvmApi.kt"), result.output)
        assertFalse(result.output.contains("CommonApiKt"), result.output)
    }

    @Test
    fun implementationChecksHonorExpectExemptions() {
        val project = object : WatchdogProject(multiplatform = true) {
            override fun multiplatformTargetsBlock(): String = "kotlin { jvm() }"

            override fun sources() = listOf(
                Source.kotlin(
                    """
                        package test

                        import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                        import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
                        import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
                        import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString

                        /**
                         * A platform data holder.
                         *
                         * @property value its value.
                         */
                        @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
                        public expect class Model(value: Int) {
                            public val value: Int
                        }

                        /**
                         * A platform state holder.
                         *
                         * @property value its value.
                         */
                        @IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
                        public expect class Stateful(value: Int) {
                            public val value: Int
                        }

                        /** Platform logic deliberately inlined into callers. */
                        @IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
                        public expect inline fun increment(value: Int): Int
                    """.trimIndent(),
                ).withSourceSet("commonMain").withPath("test", "CommonApi").build(),
                Source.kotlin(
                    """
                        @file:org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName(
                            reason = org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason.API_DESIGN,
                        )

                        package test

                        public actual data class Model public actual constructor(public actual val value: Int)

                        public actual class Stateful public actual constructor(public actual val value: Int)

                        public actual inline fun increment(value: Int): Int = value + 1
                    """.trimIndent(),
                ).withSourceSet("jvmMain").withPath("test", "JvmApi").build(),
            )
        }.gradleProject

        val result = build(project.rootDir, "compileKotlinJvm")
        assertFalse(result.output.contains("bakes its constructor property list"), result.output)
        assertFalse(result.output.contains("neither declares nor inherits"), result.output)
        assertFalse(result.output.contains("contains logic beyond"), result.output)
    }

    @Test
    fun compilationsNamedAfterATestVariantAreNotChecked() {
        // Only the Kotlin/JVM and multiplatform targets have a compilation literally called
        // `test`. Android names them after the variant (`debugUnitTest`, `debugAndroidTest`), the
        // Android target of a multiplatform project uses `hostTest` and `deviceTest`, and custom
        // compilations follow the same shape. A locally declared one stands in for all of them
        // without needing an Android SDK; the SDK-gated Android tests below build the real names.
        val project = object : WatchdogProject(
            explicitApi = false,
            extraBuildScript = """
                kotlin {
                    compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") }
                    target.compilations.create("integrationTest") {
                        associateWith(target.compilations.getByName("main"))
                    }
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(cleanMainFile), unexemptedOpenClassSource("integrationTest"))
        }.gradleProject

        val result = build(project.rootDir, "compileIntegrationTestKotlin")
        result.assertNoTestSourceDiagnostics()
    }

    @Test
    fun androidTestSourceCompilationsAreNotChecked() {
        // The real Android names for the stand-in above: unit tests compile as `debugUnitTest`
        // and instrumented tests as `debugAndroidTest`. The raw flag reaches both because
        // project-level compiler options apply to every compilation, so only the plugin's own
        // exclusion keeps them silent. publicTypeFromImplementationDependencyIsAnErrorInAndroidLibraries
        // proves the main compilations of the same project shape are checked.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(
            kotlinScript = """kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }""",
        ) {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("test"),
                unexemptedOpenClassSource("androidTest", "DeviceTestOnlyHelper"),
            )
        }.gradleProject

        val result = build(
            agpCompatibleGradle,
            project.rootDir,
            "compileDebugUnitTestKotlin",
            "compileDebugAndroidTestKotlin",
        )
        result.assertNoTestSourceDiagnostics()
    }

    @Test
    fun androidTestSourceCompilationsAreNotCheckedWithBuiltInKotlin() {
        // The same test compilation names under AGP 9, where AGP's built-in Kotlin support
        // drives the compiler plugin integration instead of the Kotlin Android plugin.
        // publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin
        // proves the main compilations of the same project shape are checked.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(
            kotlinScript = """kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }""",
            builtInKotlin = true,
        ) {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("test"),
                unexemptedOpenClassSource("androidTest", "DeviceTestOnlyHelper"),
            )
        }.gradleProject

        val result = build(
            project.rootDir,
            "compileDebugUnitTestKotlin",
            "compileDebugAndroidTestKotlin",
        )
        result.assertNoTestSourceDiagnostics()
    }

    @Test
    fun multiplatformAndroidTargetMainSourcesAreChecked() {
        // The control for multiplatformAndroidTargetTestSourcesAreNotChecked: the android
        // target's `main` compilation of the same project shape reports its diagnostics.
        assumeAndroidBuildEnvironment()
        val project = object : KmpAndroidWatchdogProject() {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("androidMain", "AndroidOnlyApi"),
            )
        }.gradleProject

        val result = buildAndFail(agpCompatibleGradle, project.rootDir, "compileAndroidMain")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "has no `KDoc`")
        result.assertDiagnosticReported("e: ", "exposes a nullable `Boolean`")
    }

    @Test
    fun multiplatformAndroidTargetTestSourcesAreNotChecked() {
        // The android target of a multiplatform project names its unit test compilation
        // `hostTest` and compiles the shared test sources along with its own. The raw flag is
        // the strict case here too, see multiplatformTestSourcesAreNotChecked.
        assumeAndroidBuildEnvironment()
        val project = object : KmpAndroidWatchdogProject(
            explicitApi = false,
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("commonTest"),
                unexemptedOpenClassSource("androidHostTest", "HostTestOnlyHelper"),
            )
        }.gradleProject

        val result = build(agpCompatibleGradle, project.rootDir, "compileAndroidHostTest")
        result.assertNoTestSourceDiagnostics()
    }

    @Test
    fun multiplatformAndroidTargetMainSourcesAreCheckedOnAgp9() {
        // The control for multiplatformAndroidTargetTestSourcesAreNotCheckedOnAgp9, on AGP 9's
        // `android` target block.
        assumeAndroidBuildEnvironment()
        val project = object : KmpAndroidWatchdogProject(agp9 = true) {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("androidMain", "AndroidOnlyApi"),
            )
        }.gradleProject

        val result = buildAndFail(project.rootDir, "compileAndroidMain")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "has no `KDoc`")
        result.assertDiagnosticReported("e: ", "exposes a nullable `Boolean`")
    }

    @Test
    fun multiplatformAndroidTargetTestSourcesAreNotCheckedOnAgp9() {
        // The `hostTest` exclusion holds on AGP 9's multiplatform android target too.
        assumeAndroidBuildEnvironment()
        val project = object : KmpAndroidWatchdogProject(
            agp9 = true,
            explicitApi = false,
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("commonTest"),
                unexemptedOpenClassSource("androidHostTest", "HostTestOnlyHelper"),
            )
        }.gradleProject

        val result = build(project.rootDir, "compileAndroidHostTest")
        result.assertNoTestSourceDiagnostics()
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
        result.assertDiagnosticReported("e: ", "`WatchedClass` is part of the public API but has no `KDoc`")
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
    fun publicTypeFromImplementationDependencyIsAnError() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(implementation(":model"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `test.model.ExternalModel`, but the dependency providing that type " +
                    "is not published transitively",
        )
        result.assertDiagnosticReported("e: ", "The type alias `ExternalModels` publicly exposes")
        result.assertDiagnosticReported("e: ", "The supertype of `Consumer` publicly exposes")
        result.assertDiagnosticReported("e: ", "The function receiver `acceptModels` publicly exposes")
        result.assertDiagnosticReported("e: ", "The parameter `models` publicly exposes")
    }

    @Test
    fun publicTypeFromApiDependencyIsAccepted() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(api(":model"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformProjects() {
        // The JVM target consumes the dependency as a jar or classes directory while the JS
        // target consumes it as a klib, so both artifact classification paths are exercised.
        // `--continue` lets the second compilation run after the first one fails.
        val project = object : WatchdogProject(multiplatform = true) {
            override fun multiplatformTargetsBlock(): String = "kotlin {\n  jvm()\n  js { nodejs() }\n}\n"

            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(implementation(":model"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "compileKotlinJvm", "compileKotlinJs", "--continue")
        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlinJvm")?.outcome)
        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlinJs")?.outcome)
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `test.model.ExternalModel`, but the dependency providing that type " +
                    "is not published transitively",
        )
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInMultiplatformProjects() {
        // The api dependency lands in `commonMainApi`, so every target's API elements inherit it.
        val project = object : WatchdogProject(multiplatform = true) {
            override fun multiplatformTargetsBlock(): String = "kotlin {\n  jvm()\n  js { nodejs() }\n}\n"

            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(api(":model"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject

        val result = build(project.rootDir, "compileKotlinJvm", "compileKotlinJs")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun publicJavaTypeFromImplementationDependencyIsAnError() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesJavaDependencyTypeFile, "JavaConsumer", "test.consumer"))
                    dependencies(implementation(":javaModel"))
                }
                subproject("javaModel") {
                    sources(
                        Source.java(javaDependencyTypeFile)
                            .withPath("test/model", "ExternalJavaModel")
                            .build(),
                    )
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `test.model.ExternalJavaModel`, but the dependency providing that type " +
                    "is not published transitively",
        )
    }

    @Test
    fun publicJavaTypeFromApiDependencyIsAccepted() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesJavaDependencyTypeFile, "JavaConsumer", "test.consumer"))
                    dependencies(api(":javaModel"))
                }
                subproject("javaModel") {
                    sources(
                        Source.java(javaDependencyTypeFile)
                            .withPath("test/model", "ExternalJavaModel")
                            .build(),
                    )
                }
            }
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibraries() {
        // Android resolves the transitive dependencies against the variant-specific API elements
        // configuration (`debugApiElements`), the branch the plugin reserves for androidJvm.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject() {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(":model"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        val result = buildAndFail(agpCompatibleGradle, project.rootDir, "compileDebugKotlin")
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `test.model.ExternalModel`, but the dependency providing that type " +
                    "is not published transitively",
        )
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInAndroidLibraries() {
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject() {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(api(":model"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        val result = build(agpCompatibleGradle, project.rootDir, "compileDebugKotlin")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin() {
        // The AGP 9 counterpart of the test above: built-in Kotlin support applies the compiler
        // plugin and supplies the same variant-specific dependency configurations.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(builtInKotlin = true) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(":model"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        val result = buildAndFail(project.rootDir, "compileDebugKotlin")
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `test.model.ExternalModel`, but the dependency providing that type " +
                    "is not published transitively",
        )
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() {
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(builtInKotlin = true) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(api(":model"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        val result = build(project.rootDir, "compileDebugKotlin")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun publicDependencyExposureCheckCanOnlyBeDisabledAsAWhole() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    publicTypesMustBeTransitiveDependencies = false
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(implementation(":model"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun warnsWhenExplicitApiModeIsNotEnabled() {
        // The warning is logged during configuration, so `help` is enough to observe it.
        val project = WatchdogProject(explicitApi = false).gradleProject

        val result = build(project.rootDir, "help")
        assertTrue(result.output.contains("doesn't enable explicit API mode"))
        assertTrue(result.output.contains("explicitApi()"))
    }

    @Test
    fun explicitApiWarningIsSilentWhenExplicitApiModeIsEnabled() {
        val project = WatchdogProject().gradleProject

        val result = build(project.rootDir, "help")
        assertFalse(result.output.contains("doesn't enable explicit API mode"))
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
        assertFalse(result.output.contains("doesn't enable explicit API mode"))
    }

    @Test
    fun explicitApiWarningCanBeSuppressedWithAGradleProperty() {
        val project = WatchdogProject(explicitApi = false).gradleProject

        val result = build(
            project.rootDir,
            "help",
            "-Porg.jetbrains.kotlin.library.api-watchdog.suppressExplicitApiWarning=true",
        )
        assertFalse(result.output.contains("doesn't enable explicit API mode"))
    }

    @Test
    fun suggestsAbiValidationWhenItIsNotEnabled() {
        // The suggestion is logged during configuration, so `help` is enough to observe it.
        val project = WatchdogProject().gradleProject

        val result = build(project.rootDir, "help")
        val testCompilerVersion = TEST_COMPILER_VERSION
        val legacyDsl = testCompilerVersion.major <= 2 && testCompilerVersion.minor < 4
        assertTrue(result.output.contains("but no binary compatibility validation is enabled"))
        assertEquals(
            legacyDsl,
            result.output.contains("abiValidation {"),
            "The warning should display the ABI validation setup for the tested KGP version",
        )
        assertEquals(
            !legacyDsl,
            result.output.contains("abiValidation()"),
            "The warning should display the ABI validation setup for the tested KGP version",
        )
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
        val testCompilerVersion = TEST_COMPILER_VERSION
        val abiValidationCall = if (testCompilerVersion.major <= 2 && testCompilerVersion.minor < 4) {
            "abiValidation { enabled.set(true) }"
        } else {
            "abiValidation()"
        }
        val project = WatchdogProject(
            extraBuildScript = """
                kotlin {
                    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
                    $abiValidationCall
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
                                    import org.jspecify.annotations.NonNull;

                                    public class FakeBcvPlugin implements Plugin<Project> {
                                        @Override
                                        public void apply(@NonNull Project project) {}
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

    /**
     * The test source set counterpart of [source], carrying the unwatched declarations.
     * [className] keeps the file names apart when several test source sets take part in one build.
     */
    private fun testOnlySource(sourceSet: String = "test", className: String = "TestOnlyHelper"): Source =
        Source.kotlin(
            buildString {
                appendLine("package test")
                appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen")
                appendLine()
                appendLine(testOnlyFile(className))
            }
        ).withSourceSet(sourceSet).withPath("test", className).build()

    /**
     * [testOnlySource] without the exemption annotation, placeable into any source set. The plugin
     * adds the annotations dependency only to the compilations it applies to, so this fixture
     * doesn't depend on whether a test compilation inherits that dependency from the main one it
     * is associated with.
     */
    private fun unexemptedOpenClassSource(sourceSet: String, className: String = "TestOnlyHelper"): Source =
        Source.kotlin(
            buildString {
                appendLine("package test")
                appendLine()
                appendLine(unexemptedTestOnlyFile(className))
            }
        ).withSourceSet(sourceSet).withPath("test", className).build()

    /** Asserts nothing in [testOnlyFile] was reported by any checker. */
    private fun BuildResult.assertNoTestSourceDiagnostics() {
        assertFalse(output.contains("can be subclassed outside the library"))
        assertFalse(output.contains("has no `KDoc`"))
        assertFalse(output.contains("exposes a nullable `Boolean`"))
        assertFalse(output.contains("exemption doesn't explain why it is applied"))
        assertFalse(output.contains("TestOnlyHelper"))
    }

    /** Asserts the message was reported with the given compiler severity prefix (`e: ` or `w: `). */
    private fun BuildResult.assertDiagnosticReported(severityPrefix: String, message: String) {
        assertTrue(
            output.lineSequence().any {
                (it.startsWith(severityPrefix) || " $severityPrefix" in it) && message in it
            },
            "Expected a '$severityPrefix' line containing '$message' in build output:\n$output",
        )
    }

    private fun ideaGenerateShortcut(): String =
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) "⌘N" else "Alt+Insert"
}

/** A main-source declaration no checker has anything to say about. */
@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val cleanMainFile = """
    /** A documented, final, closed declaration. */
    public class Settled {
        /** Returns the only supported mode. */
        public fun mode(): Int = 0
    }
""".trimIndent()

/**
 * A test-source declaration several checkers would report if they ran on test sources. The bare
 * exemption also covers the one diagnostic no configuration can demote,
 * `EXEMPTION_WITHOUT_EXPLANATION`, and proves the annotations stay resolvable from test sources.
 */
@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private fun testOnlyFile(className: String) = """
    @IntentionallyOpen
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

/** [testOnlyFile] stripped of the exemption, so that it needs nothing on the compile classpath. */
@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private fun unexemptedTestOnlyFile(className: String) = """
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

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
    public fun processTag(tag: @ScopedDsl UnprotectedOpenClass) { }

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

    /** A function whose default parameter values don't exist for Java callers. */
    public fun openPort(host: String, port: Int = 8080) {}
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val unexplainedExemptionFile = """
    /** Returns a deliberately mutable result. */
    public fun unexplainedExemption(): @IntentionallyMutableCollection MutableList<Int> = mutableListOf()
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

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val internalApiExposureFile = """
    /** Marks declarations that are public only for technical reasons. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
    public annotation class InternalLibApi

    /** Another internal API with a different annotation. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS)
    public annotation class OtherInternalApi

    @InternalLibApi
    public class InternalModel

    @OtherInternalApi
    public class OtherInternalModel

    /** Supported entry points. */
    public object Api {
        /** Leaks an explicitly unsupported type. */
        public fun loadModel(): InternalModel = InternalModel()

        /** Leaks types governed by different internal API annotations. */
        public fun useModels(first: InternalModel, second: OtherInternalModel) {}
    }
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val dependencyTypeFile = """
    /** A model supplied by another module. */
    public class ExternalModel

    /** A contract supplied by another module. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public interface ExternalContract
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val exposesDependencyTypeFile = """
    import test.model.ExternalContract
    import test.model.ExternalModel

    /** A public alias whose nested type argument comes from a dependency. */
    public typealias ExternalModels = List<ExternalModel>

    /** A documented and otherwise settled API owner. */
    public class Consumer : ExternalContract {
        /** Returns a model whose class must be available to consumers. */
        public fun model(): ExternalModel = ExternalModel()

        /** Uses a dependency type as both a receiver and a nested parameter type. */
        public fun ExternalModel.acceptModels(models: ExternalModels): ExternalModel = this
    }
""".trimIndent()

@Language("java")
private val javaDependencyTypeFile = """
    package test.model;

    public final class ExternalJavaModel {}
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val exposesJavaDependencyTypeFile = """
    import test.model.ExternalJavaModel

    /** A documented and otherwise settled API owner. */
    public class JavaConsumer {
        /** Returns a Java model whose class must be available to consumers. */
        public fun model(): ExternalJavaModel = ExternalJavaModel()
    }
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
    @IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)
    @IntentionallyWithoutHashCode(reason = ExemptionReason.API_DESIGN)
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
    @IntentionallyKotlinOnlyApi(reason = ExemptionReason.IGNORE_JAVA_INTEROP, description = "Coroutine-first API. Java is served by a blocking facade.")
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
