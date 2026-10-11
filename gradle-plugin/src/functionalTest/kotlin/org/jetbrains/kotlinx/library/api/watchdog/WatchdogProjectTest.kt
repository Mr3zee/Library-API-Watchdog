package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.Dependency
import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.compileOnly
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
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
        result.assertDiagnosticReported("e: ", "mangles the JVM method name")
        result.assertDiagnosticReported("e: ", "still lands in the API surface Java sources see")
        result.assertDiagnosticReported("e: ", "compiles to an instance method on the nested `Companion` class")
        result.assertDiagnosticReported("e: ", "leaves its getter on the nested companion class")
        result.assertDiagnosticReported("e: ", "Java-visible top-level functions")
        result.assertDiagnosticReported("e: ", "declares default parameter values")
        result.assertDiagnosticReported("e: ", "allows the `FUNCTION` annotation target")
        result.assertDiagnosticReported("e: ", "declares no annotation targets")
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
        result.assertDiagnosticReported("w: ", "mangles the JVM method name")
        result.assertDiagnosticReported("w: ", "still lands in the API surface Java sources see")
        result.assertDiagnosticReported("w: ", "compiles to an instance method on the nested `Companion` class")
        result.assertDiagnosticReported("w: ", "leaves its getter on the nested companion class")
        result.assertDiagnosticReported("w: ", "Java-visible top-level functions")
        result.assertDiagnosticReported("w: ", "declares default parameter values")
        result.assertDiagnosticReported("w: ", "allows the `FUNCTION` annotation target")
        result.assertDiagnosticReported("w: ", "declares no annotation targets")
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
        assertFalse(result.output.contains("has the `value class`"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("The `companion object`"))
        assertFalse(result.output.contains("The facade class"))
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
    fun configuredAnnotationsCanIgnoreOneCheck() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    ignore(
                        WatchdogChecks.STATEFUL_CLASS_WITHOUT_EQUALS,
                        whenAnnotatedWith = listOf(
                            "test.GeneratedValueMembers",
                            "test.AlternateGeneratedValueMembers",
                        ),
                    )
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(annotationIgnoreFile))
        }.gradleProject
        project.rootDir.resolve("build.gradle.kts").prependImport(WATCHDOG_CHECKS_IMPORT)

        val result = buildAndFail(project.rootDir, "build")
        assertFalse(result.hasStatefulDiagnostic("GeneratedState", "an `equals`"))
        assertFalse(result.hasStatefulDiagnostic("AlternateGeneratedState", "an `equals`"))
        assertTrue(result.hasStatefulDiagnostic("GeneratedState", "a `hashCode`"))
        assertTrue(result.hasStatefulDiagnostic("GeneratedState", "a `toString`"))
        assertTrue(result.hasStatefulDiagnostic("RegularState", "an `equals`"))
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
        assertFalse(result.output.contains("has the `value class`"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("The `companion object`"))
        assertFalse(result.output.contains("The facade class"))
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
        assertFalse(result.output.contains("has the `value class`"))
        assertFalse(result.output.contains("still lands in the API surface Java sources see"))
        assertFalse(result.output.contains("The `companion object`"))
        assertFalse(result.output.contains("The facade class"))
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
        // without needing an Android SDK. The SDK-gated Android tests below build the real names.
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
    fun legacyMultiplatformAndroidTargetMainSourcesAreChecked() {
        // The multiplatform plugin's own `androidTarget()` on `com.android.library`, the shape
        // every multiplatform library had before AGP's multiplatform library plugin. Its main
        // compilation is named after the variant, so the plugin sees `debug` instead of `main`.
        assumeAndroidBuildEnvironment()
        val project = object : LegacyKmpAndroidWatchdogProject() {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("androidMain", "AndroidOnlyApi"),
            )
        }.gradleProject

        val result = buildAndFail(agpCompatibleGradle, project.rootDir, "compileDebugKotlinAndroid")
        result.assertDiagnosticReported("e: ", "can be subclassed outside the library without restriction")
        result.assertDiagnosticReported("e: ", "has no `KDoc`")
        result.assertDiagnosticReported("e: ", "exposes a nullable `Boolean`")
    }

    @Test
    fun legacyMultiplatformAndroidTargetTestSourcesAreNotChecked() {
        // The legacy android target names its unit test compilation `debugUnitTest` and compiles
        // the shared test sources along with its own. The raw flag is the strict case here too,
        // see multiplatformTestSourcesAreNotChecked.
        assumeAndroidBuildEnvironment()
        val project = object : LegacyKmpAndroidWatchdogProject(
            explicitApi = false,
            extraBuildScript = """
                kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(
                source(cleanMainFile),
                unexemptedOpenClassSource("commonTest"),
                unexemptedOpenClassSource("androidUnitTest", "HostTestOnlyHelper"),
            )
        }.gradleProject

        val result = build(agpCompatibleGradle, project.rootDir, "compileDebugUnitTestKotlinAndroid")
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
    fun configuredInternalApiAnnotationsExemptLikeTheMarker() {
        // The annotation lives in `:lib` and carries no @InternalAnnotationMarker, standing in
        // for the internal-API annotation of a dependency the consuming module can't annotate.
        // Naming it in `internalApiAnnotations` makes it a marker for the consuming module.
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    internalApiAnnotations.add("test.lib.ThirdPartyInternalApi")
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(
                        source(
                            configuredInternalApiConsumerFile,
                            "consumer",
                            "test",
                            true,
                            "test.lib.ThirdPartyInternalApi",
                            "test.lib.ThirdPartyInternalType",
                        ),
                    )
                    dependencies(implementation(":lib"))
                }
                subproject("lib") {
                    sources(source(configuredInternalApiLibraryFile, "markers", "test.lib"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        // The unmarked control declaration proves the checks ran in the consuming module...
        result.assertDiagnosticReported("e: ", "`WatchedClass` is part of the public API but has no `KDoc`")
        // ...a supported signature exposing the dependency's internal type is reported, naming
        // the configured annotation...
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `test.lib.ThirdPartyInternalType`, but that type is marked as internal API " +
                    "with `@ThirdPartyInternalApi`",
        )
        // ...while declarations carrying the configured annotation are exempt.
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
        result.assertDiagnosticReported("e: ", "The function `protectedModel` publicly exposes")
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
    fun publicTypeFromImplementationDependencyIsAcceptedWhenIntentionallyExempted() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exemptedDependencyTypeFile, "Consumer", "test.consumer"))
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
    fun nonTransitiveDependencyExemptionOnAClassCoversOnlyThatClass() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(partiallyExemptedDependencyTypeFile, "Api", "test.consumer"))
                    dependencies(implementation(":model"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported("e: ", "The function `unexemptedModel` publicly exposes")
        assertFalse(result.output.contains("The function `exemptedModel` publicly exposes"))
        assertFalse(result.output.contains("The supertype of `ExemptedApi` publicly exposes"))
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

    // Android libraries: the dependency collection corner cases of AGP, on both AGP generations.

    @Test
    fun androidPlatformTypesAreNotDependenciesInAndroidLibraries() = androidPlatformTypesAreNotDependenciesCase(agp9 = false)

    @Test
    fun androidPlatformTypesAreNotDependenciesInAndroidLibrariesWithBuiltInKotlin() =
        androidPlatformTypesAreNotDependenciesCase(agp9 = true)

    private fun androidPlatformTypesAreNotDependenciesCase(agp9: Boolean) {
        // `android.jar` reaches the compiler as the platform's boot classpath, not through a
        // Gradle configuration, so platform types are neither transitive nor non-transitive.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(builtInKotlin = agp9) {
            override fun sources() = listOf(source(exposesAndroidPlatformTypeFile, "PlatformFacing", "test.consumer"))
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicTypeFromImplementationAarProjectDependencyIsAnErrorInAndroidLibraries() =
        publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(agp9 = false)

    @Test
    fun publicTypeFromImplementationAarProjectDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(agp9 = true)

    private fun publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(agp9: Boolean) {
        // An Android library module reaches the compiler as the classes jar extracted from its
        // AAR by an artifact transform, not as the AAR its API elements publish. The dependency
        // files handed to the compiler plugin must go through the same transform.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(builtInKotlin = agp9) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(":model"))

            override fun androidSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        buildAgpAndFail(agp9, project.rootDir, "compileDebugKotlin")
            .assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicTypeFromApiAarProjectDependencyIsAcceptedInAndroidLibraries() =
        publicTypeFromApiAarProjectDependencyIsAcceptedCase(agp9 = false)

    @Test
    fun publicTypeFromApiAarProjectDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromApiAarProjectDependencyIsAcceptedCase(agp9 = true)

    private fun publicTypeFromApiAarProjectDependencyIsAcceptedCase(agp9: Boolean) {
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(builtInKotlin = agp9) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(api(":model"))

            override fun androidSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInInAndroidLibraries() =
        exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(agp9 = false)

    @Test
    fun exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInInAndroidLibrariesWithBuiltInKotlin() =
        exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(agp9 = true)

    private fun exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(agp9: Boolean) {
        // The exempt view resolves with the compile classpath's variant attributes, so it picks
        // the same build-type variant of `:facade` as the compilation and follows its `api`
        // edge to the `:model` AAR, which is where the exposed type lives.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(
            builtInKotlin = agp9,
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive(project(":facade"))
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(":facade"))

            override fun androidSubprojects() = mapOf(
                "facade" to emptyList(),
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )

            override fun androidSubprojectDependencies() = mapOf("facade" to listOf(api(":model")))
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicTypeFromImplementationExternalAarDependencyIsAnErrorInAndroidLibraries() =
        publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(agp9 = false)

    @Test
    fun publicTypeFromImplementationExternalAarDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(agp9 = true)

    private fun publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(agp9: Boolean) {
        // A published AAR goes through the same classes-jar transform as a project AAR, but
        // from the Gradle transforms cache rather than a project's build directory.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(builtInKotlin = agp9) {
            override fun sources() = listOf(source(exposesStartupInitializerFile, "StartupHooks", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(STARTUP_RUNTIME))
        }.gradleProject

        buildAgpAndFail(agp9, project.rootDir, "compileDebugKotlin")
            .assertNonTransitiveExposureReported("androidx.startup.Initializer")
    }

    @Test
    fun publicTypeFromApiExternalAarDependencyIsAcceptedInAndroidLibraries() =
        publicTypeFromApiExternalAarDependencyIsAcceptedCase(agp9 = false)

    @Test
    fun publicTypeFromApiExternalAarDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromApiExternalAarDependencyIsAcceptedCase(agp9 = true)

    private fun publicTypeFromApiExternalAarDependencyIsAcceptedCase(agp9: Boolean) {
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(builtInKotlin = agp9) {
            override fun sources() = listOf(source(exposesStartupInitializerFile, "StartupHooks", "test.consumer"))

            override fun androidDependencies() = listOf(api(STARTUP_RUNTIME))
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveExternalAarDependencyIsAcceptedInAndroidLibraries() =
        exemptNonTransitiveExternalAarDependencyIsAcceptedCase(agp9 = false)

    @Test
    fun exemptNonTransitiveExternalAarDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() =
        exemptNonTransitiveExternalAarDependencyIsAcceptedCase(agp9 = true)

    private fun exemptNonTransitiveExternalAarDependencyIsAcceptedCase(agp9: Boolean) {
        // The exemption names the AAR without a version. Consistent resolution supplies the
        // compile classpath's version, and the exempt view must yield the same classes jar.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(
            builtInKotlin = agp9,
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive("${STARTUP_RUNTIME.substringBeforeLast(':')}")
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(exposesStartupInitializerFile, "StartupHooks", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(STARTUP_RUNTIME))
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInAndroidLibraries() =
        multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(agp9 = false)

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInAndroidLibrariesWithBuiltInKotlin() =
        multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(agp9 = true)

    private fun multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(agp9: Boolean) {
        // An Android compilation selects the `-android` AAR variant of a multiplatform library
        // where a JVM one would take `-jvm`. The transitive and exempt views copy the compile
        // classpath's attributes, so they must land on that same AAR: first the unexempted
        // `implementation` dependency is reported, then the version-less exemption covers it.
        assumeAndroidBuildEnvironment()
        val unexempted = object : AndroidLibraryWatchdogProject(builtInKotlin = agp9) {
            override fun sources() = listOf(source(exposesLifecycleTypeFile, "LifecycleHooks", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(LIFECYCLE_RUNTIME))
        }.gradleProject
        buildAgpAndFail(agp9, unexempted.rootDir, "compileDebugKotlin")
            .assertNonTransitiveExposureReported("androidx.lifecycle.LifecycleRegistry")

        val exempted = object : AndroidLibraryWatchdogProject(
            builtInKotlin = agp9,
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive("${LIFECYCLE_RUNTIME.substringBeforeLast(':')}")
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(exposesLifecycleTypeFile, "LifecycleHooks", "test.consumer"))

            override fun androidDependencies() = listOf(implementation(LIFECYCLE_RUNTIME))
        }.gradleProject
        buildAgp(agp9, exempted.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantInAndroidLibraries() =
        buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(agp9 = false)

    @Test
    fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantInAndroidLibrariesWithBuiltInKotlin() =
        buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(agp9 = true)

    private fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(agp9: Boolean) {
        // `debugApi` feeds `debugApiElements` alone, which is why the plugin reads the API
        // elements of the variant a compilation is named after. The release variant reaches the
        // same module only through `releaseImplementation`, so its consumers can't see the type.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(
            builtInKotlin = agp9,
            extraBuildScript = """
                dependencies {
                    "debugApi"(project(":model"))
                    "releaseImplementation"(project(":model"))
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
        buildAgpAndFail(agp9, project.rootDir, "compileReleaseKotlin")
            .assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun flavorApiDependencyIsTransitiveOnlyForItsFlavorInAndroidLibraries() =
        flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(agp9 = false)

    @Test
    fun flavorApiDependencyIsTransitiveOnlyForItsFlavorInAndroidLibrariesWithBuiltInKotlin() =
        flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(agp9 = true)

    private fun flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(agp9: Boolean) {
        // With product flavors the compilation is named after the full variant (`freeDebug`),
        // and so are its API elements, which combine the flavor's and the build type's `api`.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(
            builtInKotlin = agp9,
            extraAndroidScript = """
                flavorDimensions += "tier"
                productFlavors {
                    create("free") { dimension = "tier" }
                    create("paid") { dimension = "tier" }
                }
            """.trimIndent(),
            extraBuildScript = """
                dependencies {
                    "freeApi"(project(":model"))
                    "paidImplementation"(project(":model"))
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileFreeDebugKotlin").assertNoNonTransitiveExposureReported()
        buildAgpAndFail(agp9, project.rootDir, "compilePaidDebugKotlin")
            .assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun compileOnlyDependenciesCanBeExemptedAsAWholeInAndroidLibraries() =
        compileOnlyDependenciesCanBeExemptedAsAWholeCase(agp9 = false)

    @Test
    fun compileOnlyDependenciesCanBeExemptedAsAWholeInAndroidLibrariesWithBuiltInKotlin() =
        compileOnlyDependenciesCanBeExemptedAsAWholeCase(agp9 = true)

    private fun compileOnlyDependenciesCanBeExemptedAsAWholeCase(agp9: Boolean) {
        // AGP's `compileOnly` is a bucket the variant compile classpaths extend from, so the
        // exempt view resolves it through the compilation's own variant.
        assumeAndroidBuildEnvironment()
        val project = object : AndroidLibraryWatchdogProject(
            builtInKotlin = agp9,
            extraBuildScript = """
                apiWatchdog {
                    exemptNonTransitiveDependencies {
                        extendsFrom(configurations.compileOnly.get())
                    }
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun androidDependencies() = listOf(compileOnly(":model"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    // The Android target of a multiplatform project from `com.android.kotlin.multiplatform.library`.

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargets() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(agp9 = false)

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsOnAgp9() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(agp9 = true)

    private fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(agp9: Boolean) {
        // The multiplatform library plugin has no build variants: the compilation is `main`, so
        // the plugin falls back from `mainApiElements` to the target's own API elements.
        assumeAndroidBuildEnvironment()
        val project = object : KmpAndroidWatchdogProject(agp9 = agp9) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun commonMainDependencies() = listOf(implementation(":model"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        buildAgpAndFail(agp9, project.rootDir, "compileAndroidMain")
            .assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargets() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(agp9 = false)

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsOnAgp9() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(agp9 = true)

    private fun publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(agp9: Boolean) {
        assumeAndroidBuildEnvironment()
        val project = object : KmpAndroidWatchdogProject(agp9 = agp9) {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun commonMainDependencies() = listOf(api(":model"))

            override fun jvmSubprojects() = mapOf(
                "model" to listOf(source(dependencyTypeFile, "ExternalModel", "test.model")),
            )
        }.gradleProject

        buildAgp(agp9, project.rootDir, "compileAndroidMain", "compileKotlinJvm").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun androidSourceSetApiDependencyIsAcceptedInMultiplatformAndroidTargets() =
        androidSourceSetDependenciesCase(agp9 = false, legacy = false)

    @Test
    fun androidSourceSetApiDependencyIsAcceptedInMultiplatformAndroidTargetsOnAgp9() =
        androidSourceSetDependenciesCase(agp9 = true, legacy = false)

    @Test
    fun androidSourceSetApiDependencyIsAcceptedInLegacyMultiplatformAndroidTargets() =
        androidSourceSetDependenciesCase(agp9 = false, legacy = true)

    private fun androidSourceSetDependenciesCase(agp9: Boolean, legacy: Boolean) {
        // Dependencies declared on `androidMain` alone reach only the Android target's
        // configurations, and the exposing declaration lives in `androidMain` too.
        assumeAndroidBuildEnvironment()
        val accepted = kmpAndroidProject(
            agp9,
            legacy,
            KmpAndroidSpec(
                sources = { listOf(fixtureSource(exposesDependencyTypeFile, "Consumer", "test.consumer", "androidMain")) },
                androidMainDependencies = listOf(api(":model")),
                jvmSubprojects = modelSubproject,
            ),
        )
        buildAgp(agp9, accepted.rootDir, androidMainCompileTask(legacy)).assertNoNonTransitiveExposureReported()

        val reported = kmpAndroidProject(
            agp9,
            legacy,
            KmpAndroidSpec(
                sources = { listOf(fixtureSource(exposesDependencyTypeFile, "Consumer", "test.consumer", "androidMain")) },
                androidMainDependencies = listOf(implementation(":model")),
                jvmSubprojects = modelSubproject,
            ),
        )
        buildAgpAndFail(agp9, reported.rootDir, androidMainCompileTask(legacy))
            .assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun androidPlatformTypesAreNotDependenciesInMultiplatformAndroidTargets() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(agp9 = false, legacy = false)

    @Test
    fun androidPlatformTypesAreNotDependenciesInMultiplatformAndroidTargetsOnAgp9() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(agp9 = true, legacy = false)

    @Test
    fun androidPlatformTypesAreNotDependenciesInLegacyMultiplatformAndroidTargets() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(agp9 = false, legacy = true)

    private fun androidPlatformTypesAreNotDependenciesInMultiplatformCase(agp9: Boolean, legacy: Boolean) {
        assumeAndroidBuildEnvironment()
        val project = kmpAndroidProject(
            agp9,
            legacy,
            KmpAndroidSpec(
                sources = { listOf(fixtureSource(exposesAndroidPlatformTypeFile, "PlatformFacing", "test.consumer", "androidMain")) },
            ),
        )

        buildAgp(agp9, project.rootDir, androidMainCompileTask(legacy)).assertNoNonTransitiveExposureReported()
    }

    @Test
    fun externalAarDependencyIsCheckedAndExemptableInMultiplatformAndroidTargets() =
        externalAarDependencyInMultiplatformCase(agp9 = false, legacy = false)

    @Test
    fun externalAarDependencyIsCheckedAndExemptableInMultiplatformAndroidTargetsOnAgp9() =
        externalAarDependencyInMultiplatformCase(agp9 = true, legacy = false)

    @Test
    fun externalAarDependencyIsCheckedAndExemptableInLegacyMultiplatformAndroidTargets() =
        externalAarDependencyInMultiplatformCase(agp9 = false, legacy = true)

    private fun externalAarDependencyInMultiplatformCase(agp9: Boolean, legacy: Boolean) {
        // The Android target's compile classpath goes through AGP's classes-jar transform like
        // a plain Android library's, while the exempt bucket is shared with the JVM target.
        assumeAndroidBuildEnvironment()
        val spec = KmpAndroidSpec(
            sources = { listOf(fixtureSource(exposesStartupInitializerFile, "StartupHooks", "test.consumer", "androidMain")) },
            androidMainDependencies = listOf(implementation(STARTUP_RUNTIME)),
        )

        val reported = kmpAndroidProject(agp9, legacy, spec)
        buildAgpAndFail(agp9, reported.rootDir, androidMainCompileTask(legacy))
            .assertNonTransitiveExposureReported("androidx.startup.Initializer")

        val exempted = kmpAndroidProject(agp9, legacy, spec, exemptScript(STARTUP_RUNTIME))
        buildAgp(agp9, exempted.rootDir, androidMainCompileTask(legacy)).assertNoNonTransitiveExposureReported()
    }

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInMultiplatformAndroidTargets() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(agp9 = false, legacy = false)

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInMultiplatformAndroidTargetsOnAgp9() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(agp9 = true, legacy = false)

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInLegacyMultiplatformAndroidTargets() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(agp9 = false, legacy = true)

    private fun multiplatformLibraryAndroidVariantInMultiplatformCase(agp9: Boolean, legacy: Boolean) {
        // Declared on `commonMain`, the library resolves to its `-android` AAR for the Android
        // target and to its `-jvm` jar for the JVM target. Both compilations report the
        // unexempted type, and one version-less exemption covers both variants.
        assumeAndroidBuildEnvironment()
        val spec = KmpAndroidSpec(
            sources = { listOf(fixtureSource(exposesLifecycleTypeFile, "LifecycleHooks", "test.consumer")) },
            commonMainDependencies = listOf(implementation(LIFECYCLE_RUNTIME)),
        )

        val reported = kmpAndroidProject(agp9, legacy, spec)
        val result = buildAgpAndFail(agp9, reported.rootDir, androidMainCompileTask(legacy), "compileKotlinJvm", "--continue")
        result.assertNonTransitiveExposureReported("androidx.lifecycle.LifecycleRegistry")
        assertEquals(TaskOutcome.FAILED, result.task(":${androidMainCompileTask(legacy)}")?.outcome, result.output)
        assertEquals(TaskOutcome.FAILED, result.task(":compileKotlinJvm")?.outcome, result.output)

        val exempted = kmpAndroidProject(agp9, legacy, spec, exemptScript(LIFECYCLE_RUNTIME))
        buildAgp(agp9, exempted.rootDir, androidMainCompileTask(legacy), "compileKotlinJvm")
            .assertNoNonTransitiveExposureReported()
    }

    // The legacy multiplatform `androidTarget()` on `com.android.library`, AGP 8 only.

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInLegacyMultiplatformAndroidTargets() {
        // The legacy android target publishes through AGP's variant-specific API elements
        // (`debugApiElements`) like a plain Android library, while its dependencies are
        // declared on the multiplatform source sets.
        assumeAndroidBuildEnvironment()
        val project = object : LegacyKmpAndroidWatchdogProject() {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun commonMainDependencies() = listOf(implementation(":model"))

            override fun jvmSubprojects() = modelSubproject(this)
        }.gradleProject

        buildAndFail(agpCompatibleGradle, project.rootDir, "compileDebugKotlinAndroid")
            .assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInLegacyMultiplatformAndroidTargets() {
        assumeAndroidBuildEnvironment()
        val project = object : LegacyKmpAndroidWatchdogProject() {
            override fun sources() = listOf(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))

            override fun commonMainDependencies() = listOf(api(":model"))

            override fun jvmSubprojects() = modelSubproject(this)
        }.gradleProject

        build(agpCompatibleGradle, project.rootDir, "compileDebugKotlinAndroid").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantInLegacyMultiplatformAndroidTargets() {
        // The legacy target still has AGP's build-type configurations next to the source-set
        // ones, so `debugApi` scopes the exposure to the `debug` compilation.
        assumeAndroidBuildEnvironment()
        val project = object : LegacyKmpAndroidWatchdogProject(
            extraBuildScript = """
                dependencies {
                    "debugApi"(project(":model"))
                    "releaseImplementation"(project(":model"))
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(fixtureSource(exposesDependencyTypeFile, "Consumer", "test.consumer", "androidMain"))

            override fun jvmSubprojects() = modelSubproject(this)
        }.gradleProject

        build(agpCompatibleGradle, project.rootDir, "compileDebugKotlinAndroid").assertNoNonTransitiveExposureReported()
        buildAndFail(agpCompatibleGradle, project.rootDir, "compileReleaseKotlinAndroid")
            .assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    /** The Android target's main compile task of the multiplatform fixtures. */
    private fun androidMainCompileTask(legacy: Boolean): String =
        if (legacy) "compileDebugKotlinAndroid" else "compileAndroidMain"

    /** A `:model` Kotlin/JVM subproject holding [dependencyTypeFile]. */
    private val modelSubproject: AbstractKmpAndroidWatchdogProject.() -> Map<String, List<Source>> = {
        mapOf("model" to listOf(fixtureSource(dependencyTypeFile, "ExternalModel", "test.model")))
    }

    /** Exempts [dependency] by its version-less coordinates. */
    private fun exemptScript(dependency: String): String = """
        dependencies {
            apiWatchdogExemptNonTransitive("${dependency.substringBeforeLast(':')}")
        }
    """.trimIndent()

    /** What a multiplatform Android case declares, applied to whichever fixture [kmpAndroidProject] picks. */
    private class KmpAndroidSpec(
        val sources: AbstractKmpAndroidWatchdogProject.() -> List<Source>,
        val commonMainDependencies: List<Dependency> = emptyList(),
        val androidMainDependencies: List<Dependency> = emptyList(),
        val jvmSubprojects: AbstractKmpAndroidWatchdogProject.() -> Map<String, List<Source>> = { emptyMap() },
    )

    /** Builds the multiplatform Android fixture selected by [agp9] and [legacy] from [spec]. */
    private fun kmpAndroidProject(
        agp9: Boolean,
        legacy: Boolean,
        spec: KmpAndroidSpec,
        extraBuildScript: String = "",
    ): GradleProject {
        val project = if (legacy) {
            object : LegacyKmpAndroidWatchdogProject(extraBuildScript = extraBuildScript) {
                override fun sources() = spec.sources(this)
                override fun commonMainDependencies() = spec.commonMainDependencies
                override fun androidMainDependencies() = spec.androidMainDependencies
                override fun jvmSubprojects() = spec.jvmSubprojects(this)
            }
        } else {
            object : KmpAndroidWatchdogProject(agp9 = agp9, extraBuildScript = extraBuildScript) {
                override fun sources() = spec.sources(this)
                override fun commonMainDependencies() = spec.commonMainDependencies
                override fun androidMainDependencies() = spec.androidMainDependencies
                override fun jvmSubprojects() = spec.jvmSubprojects(this)
            }
        }
        return project.gradleProject
    }

    @Test
    fun publicDependencyExposureCheckIsDisabledByTheModuleSwitch() {
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
    fun publicTypeFromExemptNonTransitiveProjectDependencyIsAccepted() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive(project(":model"))
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
    fun exemptNonTransitiveDependencyCoversEverythingItPullsIn() {
        // The consumer never names `:model`. It reaches it through the exempt `:facade`, the
        // same way a Kotlin Multiplatform dependency reaches its platform artifact.
        val project = object : WatchdogProject(
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive(project(":facade"))
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(implementation(":facade"))
                }
                subproject("facade") {
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
    fun exemptNonTransitiveDependencyLeavesOtherDependenciesChecked() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive(project(":other"))
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(implementation(":model"), implementation(":other"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
                subproject("other") {
                    sources(source(dependencyTypeFile, "OtherModel", "test.other"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `test.model.ExternalModel`, but the dependency providing that type " +
                    "is not published transitively",
        )
    }

    @Test
    fun exemptNonTransitiveDependenciesExtendFromAWholeConfiguration() {
        // The whole-configuration form is the Gradle plugin case: everything the environment
        // supplies is declared `compileOnly`, and nothing of it is published.
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    exemptNonTransitiveDependencies {
                        extendsFrom(configurations.compileOnly.get())
                    }
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                    dependencies(compileOnly(":model"))
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
    fun exemptNonTransitiveModuleDependencyIsResolvedConsistentlyWithTheCompileClasspath() {
        // The exemption names the multiplatform module without a version and without its JVM
        // artifact. Consistent resolution supplies the compile classpath's version, and the
        // `available-at` redirect brings the `-jvm` artifact that actually holds the classes.
        val project = object : WatchdogProject(
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive("org.jetbrains.kotlinx:kotlinx-coroutines-core")
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesCoroutinesTypeFile, "Jobs", "test.consumer"))
                    dependencies(implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION"))
                }
            }
        }.gradleProject

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun exemptNonTransitiveDependencyFromAVersionCatalog() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive(libs.coroutines)
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesCoroutinesTypeFile, "Jobs", "test.consumer"))
                    dependencies(implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION"))
                }
            }
        }.gradleProject
        project.rootDir.resolve("gradle/libs.versions.toml").apply {
            parentFile.mkdirs()
            writeText(
                """
                    [libraries]
                    coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "$COROUTINES_VERSION" }
                """.trimIndent(),
            )
        }

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun exemptNonTransitiveDependencyFromTheDependencyDeclaration() {
        // The version catalog form adds the dependency through a provider, so the exemption is
        // recorded only when the compile classpath realizes its dependencies. The plugin resolves
        // the transitive view, which is consistent with the compile classpath, before the exempt
        // view, which is what makes this order-independent for users.
        val project = object : WatchdogProject(
            extraBuildScript = """
                dependencies {
                    implementation(libs.coroutines) { exemptNonTransitiveDependency(this) }
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesCoroutinesTypeFile, "Jobs", "test.consumer"))
                }
            }
        }.gradleProject
        project.rootDir.resolve("gradle/libs.versions.toml").apply {
            parentFile.mkdirs()
            writeText(
                """
                    [libraries]
                    coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "$COROUTINES_VERSION" }
                """.trimIndent(),
            )
        }
        project.rootDir.resolve("build.gradle.kts").prependImport(EXEMPT_IMPORT)

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun exemptNonTransitiveDependencyFromAProjectDependencyDeclaration() {
        val project = object : WatchdogProject(
            // The harness appends this script to every project, so keep the self-dependency
            // out of `:model`.
            extraBuildScript = """
                if (project == rootProject) {
                    dependencies {
                        implementation(project(":model")) { exemptNonTransitiveDependency(this) }
                    }
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject
        project.rootDir.resolve("build.gradle.kts").prependImport(EXEMPT_IMPORT)
        project.rootDir.resolve("model/build.gradle.kts").prependImport(EXEMPT_IMPORT)

        val result = build(project.rootDir, "build")
        assertFalse(result.output.contains("not published transitively to consumers"))
    }

    @Test
    fun publicTypeFromUnexemptedModuleDependencyIsAnError() {
        val project = object : WatchdogProject() {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesCoroutinesTypeFile, "Jobs", "test.consumer"))
                    dependencies(implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$COROUTINES_VERSION"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        result.assertDiagnosticReported(
            "e: ",
            "publicly exposes `kotlinx.coroutines.Job`, but the dependency providing that type " +
                    "is not published transitively",
        )
    }

    @Test
    fun exemptNonTransitiveDependenciesDoNotJoinTheCompileClasspath() {
        // The bucket is a marker. A dependency declared only there is not compiled against.
        val project = object : WatchdogProject(
            extraBuildScript = """
                dependencies {
                    apiWatchdogExemptNonTransitive(project(":model"))
                }
            """.trimIndent(),
        ) {
            override fun buildGradleProject() = multiModuleProject {
                root {
                    sources(source(exposesDependencyTypeFile, "Consumer", "test.consumer"))
                }
                subproject("model") {
                    sources(source(dependencyTypeFile, "ExternalModel", "test.model"))
                }
            }
        }.gradleProject

        val result = buildAndFail(project.rootDir, "build")
        assertTrue(result.output.contains("Unresolved reference"), result.output)
    }

    @Test
    fun transitiveDependencyConfigurationsAreNamedAfterTheCompilation() {
        // The JVM plugin's target has no disambiguation classifier, which KGP's own
        // `disambiguatedName` renders as a literal "null".
        val project = object : WatchdogProject(extraBuildScript = printWatchdogConfigurationsTask) {
            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "compileKotlin", "printWatchdogConfigurations", "--continue")
        assertTrue(
            result.output.contains(
                "WATCHDOG_CONFIGURATIONS=[apiWatchdogMainExemptNonTransitiveDependencies, " +
                        "apiWatchdogMainTransitiveDependencies]"
            ),
            result.output,
        )
    }

    @Test
    fun transitiveDependencyConfigurationsAreNamedAfterTheTargetInMultiplatformProjects() {
        val project = object : WatchdogProject(
            multiplatform = true,
            extraBuildScript = printWatchdogConfigurationsTask,
        ) {
            override fun multiplatformTargetsBlock(): String = "kotlin {\n  jvm()\n}\n"

            override fun sources() = listOf(source(unacknowledgedFile))
        }.gradleProject

        val result = buildAndFail(project.rootDir, "compileKotlinJvm", "printWatchdogConfigurations", "--continue")
        assertTrue(result.output.contains("apiWatchdogJvmMainTransitiveDependencies"))
        assertFalse(result.output.contains("apiWatchdogNull"))
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

    /** Launches [tasks] on the Gradle version the AGP generation selected by [agp9] runs on. */
    private fun buildAgp(agp9: Boolean, projectDir: File, vararg tasks: String): BuildResult =
        if (agp9) build(projectDir, *tasks) else build(agpCompatibleGradle, projectDir, *tasks)

    private fun buildAgpAndFail(agp9: Boolean, projectDir: File, vararg tasks: String): BuildResult =
        if (agp9) buildAndFail(projectDir, *tasks) else buildAndFail(agpCompatibleGradle, projectDir, *tasks)

    private fun BuildResult.assertNonTransitiveExposureReported(type: String) =
        assertDiagnosticReported(
            "e: ",
            "publicly exposes `$type`, but the dependency providing that type is not published transitively",
        )

    private fun BuildResult.assertNoNonTransitiveExposureReported() =
        assertFalse(output.contains("not published transitively to consumers"), output)

    private fun BuildResult.hasStatefulDiagnostic(className: String, missingMember: String): Boolean =
        output.lineSequence().any { "`$className`" in it && "inherits $missingMember" in it }

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
private val annotationIgnoreFile = """
    /** Marks classes whose value members are generated. */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    public annotation class GeneratedValueMembers

    /** An alternate annotation that generates the same value members. */
    @Target(AnnotationTarget.CLASS)
    @Retention(AnnotationRetention.SOURCE)
    public annotation class AlternateGeneratedValueMembers

    /** State whose equals, hashCode, and toString implementations are generated. */
    @GeneratedValueMembers
    public class GeneratedState(public val value: Int)

    /** State whose value members are generated under the alternate annotation. */
    @AlternateGeneratedValueMembers
    public class AlternateGeneratedState(public val value: Int)

    /** State with no generated value members. */
    public class RegularState(public val value: Int)
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
private val configuredInternalApiLibraryFile = """
    /** The dependency's own internal-API annotation. It knows nothing about the watchdog. */
    @Target(
        AnnotationTarget.CLASS,
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY,
    )
    public annotation class ThirdPartyInternalApi

    /** A type the dependency marks as internal API. */
    @ThirdPartyInternalApi
    public class ThirdPartyInternalType
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val configuredInternalApiConsumerFile = """
    @ThirdPartyInternalApi
    public open class InternalOpenClass {
        public fun memberOfInternal() {}
    }

    @ThirdPartyInternalApi
    public enum class InternalEnum { INTERNAL_ENTRY }

    @ThirdPartyInternalApi
    public fun internalFunction() {}

    /** Supported entry point leaking the dependency's internal type. */
    public object Api {
        /** Leaks an explicitly unsupported type of the dependency. */
        public fun loadInternal(): ThirdPartyInternalType = ThirdPartyInternalType()
    }

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
private val exemptedDependencyTypeFile = """
    import test.model.ExternalContract
    import test.model.ExternalModel

    /** A public alias whose nested type argument comes from a dependency the host supplies. */
    @IntentionallyNonTransitiveDependency(
        reason = ExemptionReason.INTEROP,
        description = "The host application ships the model module.",
    )
    public typealias ExternalModels = List<ExternalModel>

    /** A documented API owner whose exemption covers its supertype and every member. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    @IntentionallyNonTransitiveDependency(
        reason = ExemptionReason.INTEROP,
        description = "The host application ships the model module.",
    )
    public open class Consumer : ExternalContract {
        /** Returns a model the host application makes available. */
        public fun model(): ExternalModel = ExternalModel()

        /** Returns a dependency type from protected API. */
        protected fun protectedModel(): ExternalModel = ExternalModel()

        /** Uses a dependency type as both a receiver and a nested parameter type. */
        public fun ExternalModel.acceptModels(models: ExternalModels): ExternalModel = this
    }

    /** Exempts single parameters and type usages without touching the declaration. */
    public object Combiner {
        /** Counts [models] after storing [model]. */
        public fun combine(
            @IntentionallyNonTransitiveDependency(
                reason = ExemptionReason.INTEROP,
                description = "The host application ships the model module.",
            )
            model: ExternalModel,
            models: List<@IntentionallyNonTransitiveDependency(
                reason = ExemptionReason.INTEROP,
                description = "The host application ships the model module.",
            ) ExternalModel>,
        ): Int = models.size

        /** Bounds a type parameter by a type the host application supplies. */
        public fun <@IntentionallyNonTransitiveDependency(
            reason = ExemptionReason.INTEROP,
            description = "The host application ships the model module.",
        ) T : ExternalModel> describe(model: T): String = model.toString()
    }
""".trimIndent()

/** A dependency the functional tests resolve from Maven Central as a non-project module. */
private const val COROUTINES_VERSION = "1.10.2"

private const val EXEMPT_IMPORT =
    "org.jetbrains.kotlinx.library.api.watchdog.exemptNonTransitiveDependency"

/** The generated constants object with every diagnostic name, see `generateCheckNames`. */
private const val WATCHDOG_CHECKS_IMPORT = "org.jetbrains.kotlinx.library.api.watchdog.WatchdogChecks"

/** Imports must precede the `plugins` block, and the harness appends extra script text after it. */
private fun File.prependImport(fqName: String) {
    writeText("import $fqName\n" + readText())
}

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val exposesCoroutinesTypeFile = """
    import kotlinx.coroutines.Job

    /** Jobs the host application manages. */
    public object Jobs {
        /** Hands out a job the host application manages. */
        public fun hostJob(): Job = Job()
    }
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val partiallyExemptedDependencyTypeFile = """
    import test.model.ExternalContract
    import test.model.ExternalModel

    /** The exemption on the class covers its supertype and its members, nothing else. */
    @IntentionallyNonTransitiveDependency(
        reason = ExemptionReason.INTEROP,
        description = "The host application ships the model module.",
    )
    public object ExemptedApi : ExternalContract {
        /** Returns a model the host application makes available. */
        public fun exemptedModel(): ExternalModel = ExternalModel()
    }

    /** A sibling declaration without the exemption. */
    public object UnexemptedApi {
        /** Returns a model consumers can't resolve. */
        public fun unexemptedModel(): ExternalModel = ExternalModel()
    }
""".trimIndent()

/**
 * A task listing the watchdog's transitive-dependency configurations. They are registered when
 * KGP hands each compilation to the plugin, so the task runs after the compile tasks.
 */
@Language("kotlin")
private val printWatchdogConfigurationsTask = """
    tasks.register("printWatchdogConfigurations") {
        mustRunAfter(tasks.matching { it.name.startsWith("compileKotlin") })
        doLast {
            val names = configurations.names.filter { it.endsWith("TransitiveDependencies") }.sorted()
            println("WATCHDOG_CONFIGURATIONS=" + names)
        }
    }
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val exposesDependencyTypeFile = """
    import test.model.ExternalContract
    import test.model.ExternalModel

    /** A public alias whose nested type argument comes from a dependency. */
    public typealias ExternalModels = List<ExternalModel>

    /** A documented and otherwise settled API owner. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public open class Consumer : ExternalContract {
        /** Returns a model whose class must be available to consumers. */
        public fun model(): ExternalModel = ExternalModel()

        /** Returns a dependency type from protected API. */
        protected fun protectedModel(): ExternalModel = ExternalModel()

        /** Uses a dependency type as both a receiver and a nested parameter type. */
        public fun ExternalModel.acceptModels(models: ExternalModels): ExternalModel = this
    }
""".trimIndent()

/** A dependency from Google's Maven repository published as an AAR. */
private const val STARTUP_RUNTIME = "androidx.startup:startup-runtime:1.2.0"

/**
 * A multiplatform AndroidX library whose Android variant is an AAR of its own
 * (`lifecycle-runtime-android`), next to a plain JVM variant for desktop consumers. Its
 * `LifecycleRegistry` is compiled into each variant, unlike `Lifecycle` from the plain
 * `lifecycle-common` jar it depends on.
 */
private const val LIFECYCLE_RUNTIME = "androidx.lifecycle:lifecycle-runtime:2.8.7"

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val exposesAndroidPlatformTypeFile = """
    import android.content.Context
    import android.os.Bundle

    /** A documented API owner whose signatures mention the Android platform. */
    public class PlatformFacing {
        /** Names the package of the platform context. */
        public fun describe(context: Context, extras: Bundle?): String = context.packageName
    }
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val exposesStartupInitializerFile = """
    import androidx.startup.Initializer

    /** A documented API owner whose signature mentions a type from an AAR dependency. */
    public class StartupHooks {
        /** Returns the initializer the host runs, if any. */
        public fun initializer(): Initializer<Unit>? = null
    }
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val exposesLifecycleTypeFile = """
    import androidx.lifecycle.LifecycleRegistry

    /** A documented API owner whose signature mentions a type compiled into each platform variant. */
    public class LifecycleHooks {
        /** Returns the lifecycle registry the host drives, if any. */
        public fun registry(): LifecycleRegistry? = null
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
