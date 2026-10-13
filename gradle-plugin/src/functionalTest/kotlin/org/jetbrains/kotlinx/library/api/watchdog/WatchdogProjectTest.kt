@file:Suppress("RedundantVisibilityModifier")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.api
import com.autonomousapps.kit.gradle.Dependency.Companion.compileOnly
import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome.FAILED
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlinx.library.api.watchdog.AgpVersion.AGP8
import org.jetbrains.kotlinx.library.api.watchdog.AgpVersion.AGP9
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.BOOLEAN_PARAMETER_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.COMPANION_API_WITHOUT_JVM_STATIC
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.EXEMPTION_WITHOUT_EXPLANATION
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.EXHAUSTIVE_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.MUTABLE_COLLECTION_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.NULLABLE_BOOLEAN_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PAIR_OR_TRIPLE_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_EQUALS
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_HASH_CODE
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.STATEFUL_CLASS_WITHOUT_TO_STRING
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.UNDOCUMENTED_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.KmpAndroidTarget.LEGACY
import org.jetbrains.kotlinx.library.api.watchdog.KmpAndroidTarget.LIBRARY_AGP8
import org.jetbrains.kotlinx.library.api.watchdog.KmpAndroidTarget.LIBRARY_AGP9
import org.jetbrains.kotlinx.library.api.watchdog.Severity.ERROR
import org.jetbrains.kotlinx.library.api.watchdog.Severity.NONE
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

class WatchdogProjectTest {
    @Test
    fun failsWithErrorsOnUnacknowledgedApiByDefault() {
        val project = jvmProject {
            source(unacknowledgedFile)
            source(internalApiExposureFile, "internalApiExposure")
        }

        val result = project.buildAndFail("build")
        result.assertErrors(*singleModuleDiagnostics.toTypedArray())
        // The filled-in parameter values.
        result.assertError("Poko (https://github.com/drewhamilton/Poko)")
        result.assertError(
            "Poko (https://mr3zee.github.io/Library-API-Watchdog/checks/" +
                    "stateful-class-without-equals-hashcode-to-string#poko)",
        )
        result.assertError("press `${ideaGenerateShortcut()}`")
        result.assertError("mangles the JVM method name")
        result.assertError("compiles to an instance method of the nested `Companion` class")
        result.assertError("has its getter on the nested companion class")
        result.assertError("Java-visible top-level functions")
        result.assertError("declares the `FUNCTION` annotation target")
        result.assertError("has no effect on this parameter type")
        result.assertError(
            "parameter `first` publicly exposes `test.InternalModel`, but that type is marked " +
                    "as internal API with `@InternalLibApi`",
        )
        result.assertError(
            "parameter `second` publicly exposes `test.OtherInternalModel`, but that type is " +
                    "marked as internal API with `@OtherInternalApi`",
        )
    }

    @Test
    fun demotedDiagnosticsAreReportedAsWarnings() {
        val project = jvmProject {
            apiWatchdog { severityOfEveryCheck(WARNING) }
            source(unacknowledgedFile)
        }

        val result = project.build("build")
        result.assertWarnings(*configurableDiagnostics.toTypedArray())
    }

    @Test
    fun severityIsConfiguredPerDiagnostic() {
        // The compiler swallows regular warnings when a compilation fails with errors, so warning
        // reporting is forced to observe the demoted diagnostic next to the remaining errors.
        val project = jvmProject {
            buildScript("""kotlin { compilerOptions { freeCompilerArgs.add("-Xreport-all-warnings") } }""")
            apiWatchdog { severity(WARNING, UNDOCUMENTED_PUBLIC_API) }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
        result.assertWarnings(UNDOCUMENTED_PUBLIC_API)
    }

    @Test
    fun disabledJavaInteropGroupSilencesAllItsDiagnostics() {
        // The off-switch silences every Java-interop diagnostic at once and wins over the
        // individual severities inside the group.
        val project = jvmProject {
            apiWatchdog {
                javaInterop {
                    enabled = false
                    severity(ERROR, COMPANION_API_WITHOUT_JVM_STATIC)
                }
            }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        // The non-interop diagnostics still fail the build...
        result.assertErrors(UNDOCUMENTED_PUBLIC_API)
        // ...while the whole group is off, the explicitly set severity included.
        result.assertNotReported(*Diagnostic.entries.filter { it.javaInterop }.toTypedArray())
    }

    @Test
    fun disabledDiagnosticsAreNotReported() {
        val project = jvmProject {
            apiWatchdog {
                severity(
                    NONE,
                    UNDOCUMENTED_PUBLIC_API,
                    STATEFUL_CLASS_WITHOUT_EQUALS,
                    STATEFUL_CLASS_WITHOUT_HASH_CODE,
                    STATEFUL_CLASS_WITHOUT_TO_STRING,
                )
            }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        // The remaining diagnostics still fail the build...
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
        // ...while the disabled ones are not reported at all.
        result.assertNotReported(
            UNDOCUMENTED_PUBLIC_API,
            STATEFUL_CLASS_WITHOUT_EQUALS,
            STATEFUL_CLASS_WITHOUT_HASH_CODE,
            STATEFUL_CLASS_WITHOUT_TO_STRING,
        )
    }

    @Test
    fun configuredAnnotationsCanIgnoreOneCheck() {
        val project = jvmProject {
            apiWatchdog {
                ignore(
                    STATEFUL_CLASS_WITHOUT_EQUALS,
                    "test.GeneratedValueMembers",
                    "test.AlternateGeneratedValueMembers",
                )
            }
            source(annotationIgnoreFile)
        }

        val result = project.buildAndFail("build")
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
        val project = jvmProject {
            apiWatchdog { severity(NONE, MUTABLE_COLLECTION_PUBLIC_API) }
            source(unexplainedExemptionFile)
        }

        project.buildAndFail("build").assertErrors(EXEMPTION_WITHOUT_EXPLANATION)
    }

    @Test
    fun internalApiExposureIsAlwaysAnError() {
        val project = jvmProject { source(internalApiExposureFile, "internalApiExposure") }

        project.buildAndFail("build").assertError("marked as internal API with `@InternalLibApi`")
    }

    @Test
    fun internalApiExposureCheckCanOnlyBeDisabledAsAWhole() {
        val project = jvmProject {
            apiWatchdog { publicTypeWithInternalApi = false }
            source(internalApiExposureFile, "internalApiExposure")
        }

        project.build("build").assertNotReported(PUBLIC_TYPE_WITH_INTERNAL_API)
    }

    @Test
    fun acknowledgedApiCompilesWithoutDiagnostics() {
        val project = jvmProject {
            source(
                acknowledgedFile,
                "acknowledged",
                fileAnnotation = "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
            )
        }

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun silentWithoutExplicitApiMode() {
        val project = jvmProject {
            explicitApi = false
            source(unacknowledgedFile)
        }

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun testSourcesAreNotChecked() {
        // Test sources are not published, so they carry no API contract to watch. The same
        // declaration in main sources fails the build, see failsWithErrorsOnUnacknowledgedApiByDefault.
        val project = jvmProject {
            source(cleanMainFile)
            testOnlySource()
        }

        project.build("compileTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun testSourcesAreNotCheckedWhenExplicitApiModeIsForcedOnEveryCompilation() {
        // A raw compiler flag reaches test compilations too, unlike `kotlin { explicitApi() }`,
        // which the Kotlin Gradle plugin deliberately keeps off for them.
        val project = jvmProject {
            explicitApi = false
            buildScript(EXPLICIT_API_FLAG)
            source(cleanMainFile)
            testOnlySource()
        }

        project.build("compileTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun multiplatformTestSourcesAreNotChecked() {
        // Every multiplatform target compiles the shared `commonTest` source set along with its
        // own, so the exclusion has to hold for both. The raw flag is the strict case here too.
        val project = kmpProject {
            explicitApi = false
            buildScript(EXPLICIT_API_FLAG)
            source(cleanMainFile)
            testOnlySource("commonTest")
            testOnlySource("jvmTest", "PlatformTestOnlyHelper")
        }

        project.build("compileTestKotlinJvm").assertNoTestSourceDiagnostics()
    }

    @Test
    fun expectOwnsContractChecksAndExemptions() {
        val project = kmpProject {
            kotlinFile(
                "CommonApi",
                text = """
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
                """,
            )
            kotlinFile(
                "JvmApi",
                "jvmMain",
                """
                    $DEFAULT_FACADE_NAME_FILE_ANNOTATION

                    package test

                    public actual fun shared(flag: Boolean): Pair<String, Boolean?> =
                        flag.toString() to flag

                    public actual fun reported(flag: Boolean): Pair<String, Boolean?> =
                        flag.toString() to flag

                    /** Platform-only declarations remain part of the checked source API. */
                    public fun platformOnly(flag: Boolean): Int = if (flag) 1 else 0
                """,
            )
        }

        val result = project.buildAndFail("compileKotlinJvm")
        result.assertReportedIn(BOOLEAN_PARAMETER_PUBLIC_API, "CommonApi.kt", "JvmApi.kt")
        result.assertReportedIn(NULLABLE_BOOLEAN_PUBLIC_API, "CommonApi.kt")
        result.assertReportedIn(PAIR_OR_TRIPLE_PUBLIC_API, "CommonApi.kt")
    }

    @Test
    fun jvmChecksReportActualOnceUsingExpectSignature() {
        val project = kmpProject {
            apiWatchdog {
                severity(WARNING, KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC, DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS)
            }
            kotlinFile(
                "CommonApi",
                text = """
                    package test

                    /** A Kotlin-only shared contract. */
                    public expect suspend fun suspended(): Int

                    /** A shared contract with a default argument. */
                    public expect fun defaulted(value: Int = 1): Int
                """,
            )
            kotlinFile(
                "JvmApi",
                "jvmMain",
                """
                    $DEFAULT_FACADE_NAME_FILE_ANNOTATION

                    package test

                    public actual suspend fun suspended(): Int = 1

                    public actual fun defaulted(value: Int): Int = value
                """,
            )
        }

        val result = project.build("compileKotlinJvm")
        result.assertReportedIn(KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC, "JvmApi.kt")
        result.assertReportedIn(DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS, "JvmApi.kt")
        result.assertOutputLacks("CommonApiKt")
    }

    @Test
    fun implementationChecksHonorExpectExemptions() {
        val project = kmpProject {
            kotlinFile(
                "CommonApi",
                text = """
                    package test

                    import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                    import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
                    import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
                    import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString

                    /** A platform data holder. */
                    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
                    public expect class Model(value: Int) {
                        /** Its value. */
                        public val value: Int
                    }

                    /** A platform state holder. */
                    @IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
                    public expect class Stateful(value: Int) {
                        /** Its value. */
                        public val value: Int
                    }

                    /** Platform logic deliberately inlined into callers. */
                    @IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
                    public expect inline fun increment(value: Int): Int
                """,
            )
            kotlinFile(
                "JvmApi",
                "jvmMain",
                """
                    $DEFAULT_FACADE_NAME_FILE_ANNOTATION

                    package test

                    public actual data class Model public actual constructor(public actual val value: Int)

                    public actual class Stateful public actual constructor(public actual val value: Int)

                    public actual inline fun increment(value: Int): Int = value + 1
                """,
            )
        }

        project.build("compileKotlinJvm").assertNotReported(
            Diagnostic.DATA_CLASS_PUBLIC_API,
            STATEFUL_CLASS_WITHOUT_EQUALS,
            STATEFUL_CLASS_WITHOUT_HASH_CODE,
            STATEFUL_CLASS_WITHOUT_TO_STRING,
            Diagnostic.INLINE_FUNCTION_WITH_LOGIC,
        )
    }

    @Test
    fun compilationsNamedAfterATestVariantAreNotChecked() {
        // Only the Kotlin/JVM and multiplatform targets have a compilation literally called
        // `test`. Android names them after the variant (`debugUnitTest`, `debugAndroidTest`), the
        // Android target of a multiplatform project uses `hostTest` and `deviceTest`, and custom
        // compilations follow the same shape. A locally declared one stands in for all of them
        // without needing an Android SDK. The SDK-gated Android tests below build the real names.
        val project = jvmProject {
            explicitApi = false
            buildScript(
                """
                    kotlin {
                        compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") }
                        target.compilations.create("integrationTest") {
                            associateWith(target.compilations.getByName("main"))
                        }
                    }
                """
            )
            source(cleanMainFile)
            unexemptedOpenClassSource("integrationTest")
        }

        project.build("compileIntegrationTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun androidTestSourceCompilationsAreNotChecked() = androidTestSourceCompilationsCase(AGP8)

    @Test
    fun androidTestSourceCompilationsAreNotCheckedWithBuiltInKotlin() = androidTestSourceCompilationsCase(AGP9)

    private fun androidTestSourceCompilationsCase(agp: AgpVersion) {
        // The real Android names for the stand-in above: unit tests compile as `debugUnitTest`
        // and instrumented tests as `debugAndroidTest`. The raw flag reaches both because
        // project-level compiler options apply to every compilation, so only the plugin's own
        // exclusion keeps them silent. publicTypeFromImplementationDependencyIsAnErrorInAndroidLibraries
        // proves the main compilations of the same project shape are checked.
        val project = androidLibrary(agp) {
            explicitApi = false
            kotlin("""compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") }""")
            source(cleanMainFile)
            unexemptedOpenClassSource("test")
            unexemptedOpenClassSource("androidTest", "DeviceTestOnlyHelper")
        }

        project.build("compileDebugUnitTestKotlin", "compileDebugAndroidTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun multiplatformAndroidTargetMainSourcesAreChecked() = multiplatformAndroidTargetMainSourcesCase(LIBRARY_AGP8)

    @Test
    fun multiplatformAndroidTargetMainSourcesAreCheckedOnAgp9() = multiplatformAndroidTargetMainSourcesCase(LIBRARY_AGP9)

    @Test
    fun legacyMultiplatformAndroidTargetMainSourcesAreChecked() = multiplatformAndroidTargetMainSourcesCase(LEGACY)

    private fun multiplatformAndroidTargetMainSourcesCase(target: KmpAndroidTarget) {
        // The control for the test-source cases below: the Android target's main compilation of
        // the same project shape reports its diagnostics. The legacy target names it after the
        // variant, so the plugin sees `debug` instead of `main`.
        val project = kmpAndroidProject(target) {
            source(cleanMainFile)
            unexemptedOpenClassSource("androidMain", "AndroidOnlyApi")
        }

        project.buildAndFail(target.mainCompileTask)
            .assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API, NULLABLE_BOOLEAN_PUBLIC_API)
    }

    @Test
    fun multiplatformAndroidTargetTestSourcesAreNotChecked() = multiplatformAndroidTargetTestSourcesCase(LIBRARY_AGP8)

    @Test
    fun multiplatformAndroidTargetTestSourcesAreNotCheckedOnAgp9() = multiplatformAndroidTargetTestSourcesCase(LIBRARY_AGP9)

    @Test
    fun legacyMultiplatformAndroidTargetTestSourcesAreNotChecked() = multiplatformAndroidTargetTestSourcesCase(LEGACY)

    private fun multiplatformAndroidTargetTestSourcesCase(target: KmpAndroidTarget) {
        // The Android target names its unit test compilation `hostTest`, or `debugUnitTest` for
        // the legacy target, and compiles the shared test sources along with its own. The raw
        // flag is the strict case here too, see multiplatformTestSourcesAreNotChecked.
        val project = kmpAndroidProject(target) {
            explicitApi = false
            buildScript(EXPLICIT_API_FLAG)
            source(cleanMainFile)
            unexemptedOpenClassSource("commonTest")
            unexemptedOpenClassSource(target.hostTestSourceSet, "HostTestOnlyHelper")
        }

        project.build(target.hostTestCompileTask).assertNoTestSourceDiagnostics()
    }

    @Test
    fun internalAnnotationMarkerExemptsAcrossModules() {
        // The marker annotation lives in `:lib`, so the consuming root module reads the
        // @InternalAnnotationMarker meta-annotation from the compiled dependency.
        val project = jvmProject {
            source(markerConsumerFile, "consumer", imports = listOf("test.lib.InternalLibApi"))
            dependencies(implementation(":lib"))
            subproject("lib") { source(markerLibraryFile, "markers", "test.lib") }
        }

        val result = project.buildAndFail("build")
        // The unmarked control declaration proves the checks ran in the consuming module...
        result.assertError("`WatchedClass` has no `KDoc`")
        // ...while declarations marked with the dependency's marker annotation are exempt.
        result.assertOutputLacks("InternalOpenClass")
        result.assertOutputLacks("memberOfInternal")
        result.assertOutputLacks("InternalEnum")
        result.assertOutputLacks("INTERNAL_ENTRY")
        result.assertOutputLacks("internalFunction")
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
    }

    @Test
    fun configuredInternalApiAnnotationsExemptLikeTheMarker() {
        // The annotation lives in `:lib` and carries no @InternalAnnotationMarker, standing in
        // for the internal-API annotation of a dependency the consuming module can't annotate.
        // Naming it in `internalApiAnnotations` makes it a marker for the consuming module.
        val project = jvmProject {
            apiWatchdog { internalApiAnnotations("test.lib.ThirdPartyInternalApi") }
            source(
                configuredInternalApiConsumerFile,
                "consumer",
                imports = listOf("test.lib.ThirdPartyInternalApi", "test.lib.ThirdPartyInternalType"),
            )
            dependencies(implementation(":lib"))
            subproject("lib") { source(configuredInternalApiLibraryFile, "markers", "test.lib") }
        }

        val result = project.buildAndFail("build")
        // The unmarked control declaration proves the checks ran in the consuming module...
        result.assertError("`WatchedClass` has no `KDoc`")
        // ...a supported signature exposing the dependency's internal type is reported, naming
        // the configured annotation...
        result.assertError(
            "publicly exposes `test.lib.ThirdPartyInternalType`, but that type is marked as internal API " +
                    "with `@ThirdPartyInternalApi`",
        )
        // ...while declarations carrying the configured annotation are exempt.
        result.assertOutputLacks("InternalOpenClass")
        result.assertOutputLacks("memberOfInternal")
        result.assertOutputLacks("InternalEnum")
        result.assertOutputLacks("INTERNAL_ENTRY")
        result.assertOutputLacks("internalFunction")
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
    }

    @Test
    fun publicTypeFromImplementationDependencyIsAnError() {
        val project = jvmProject {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        val result = project.buildAndFail("build")
        result.assertNonTransitiveExposureReported("test.model.ExternalModel")
        result.assertOutputHas(
            "        implementation(project(\":model\")) {\n            exemptNonTransitiveDependency(this)\n        }"
        )
        result.assertError("The type alias `ExternalModels` publicly exposes")
        result.assertError("The supertype of `Consumer` publicly exposes")
        result.assertError("The function receiver `acceptModels` publicly exposes")
        result.assertError("The parameter `models` publicly exposes")
        result.assertError("The function `protectedModel` publicly exposes")
    }

    @Test
    fun publicTypeFromApiDependencyIsAccepted() {
        val project = jvmProject {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicTypeFromImplementationDependencyIsAcceptedWhenIntentionallyExempted() {
        val project = jvmProject {
            source(exemptedDependencyTypeFile, "Consumer", "test.consumer")
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun nonTransitiveDependencyExemptionOnAClassCoversOnlyThatClass() {
        val project = jvmProject {
            source(partiallyExemptedDependencyTypeFile, "Api", "test.consumer")
            dependencies(implementation(":model"))
            modelSubproject()
        }

        val result = project.buildAndFail("build")
        result.assertError("The function `unexemptedModel` publicly exposes")
        result.assertOutputLacks("The function `exemptedModel` publicly exposes")
        result.assertOutputLacks("The supertype of `ExemptedApi` publicly exposes")
    }

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformProjects() {
        // The JVM target consumes the dependency as a jar or classes directory while the JS
        // target consumes it as a klib, so both artifact classification paths are exercised.
        // `--continue` lets the second compilation run after the first one fails.
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        val result = project.buildAndFail("compileKotlinJvm", "compileKotlinJs", "--continue")
        result.assertTaskOutcome(":compileKotlinJvm", FAILED)
        result.assertTaskOutcome(":compileKotlinJs", FAILED)
        result.assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInMultiplatformProjects() {
        // The api dependency lands in `commonMainApi`, so every target's API elements inherit it.
        val project = kmpProject(JVM_AND_JS_TARGETS) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build("compileKotlinJvm", "compileKotlinJs").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicJavaTypeFromImplementationDependencyIsAnError() {
        val project = jvmProject {
            source(exposesJavaDependencyTypeFile, "JavaConsumer", "test.consumer")
            dependencies(implementation(":javaModel"))
            subproject("javaModel") { javaFile("ExternalJavaModel", javaDependencyTypeFile) }
        }

        project.buildAndFail("build").assertNonTransitiveExposureReported("test.model.ExternalJavaModel")
    }

    @Test
    fun publicJavaTypeFromApiDependencyIsAccepted() {
        val project = jvmProject {
            source(exposesJavaDependencyTypeFile, "JavaConsumer", "test.consumer")
            dependencies(api(":javaModel"))
            subproject("javaModel") { javaFile("ExternalJavaModel", javaDependencyTypeFile) }
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    // Android libraries: the dependency collection corner cases of AGP, on both AGP generations.

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibraries() =
        publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(AGP8)

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(AGP9)

    private fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(agp: AgpVersion) {
        // Android resolves the transitive dependencies against the variant-specific API elements
        // configuration (`debugApiElements`), the branch the plugin reserves for androidJvm. With
        // AGP 9 the built-in Kotlin support applies the compiler plugin and supplies the same
        // variant-specific dependency configurations.
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject(jvm = true)
        }

        project.buildAndFail("compileDebugKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInAndroidLibraries() =
        publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesCase(AGP8)

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesCase(AGP9)

    private fun publicTypeFromApiDependencyIsAcceptedInAndroidLibrariesCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun androidPlatformTypesAreNotDependenciesInAndroidLibraries() = androidPlatformTypesAreNotDependenciesCase(AGP8)

    @Test
    fun androidPlatformTypesAreNotDependenciesInAndroidLibrariesWithBuiltInKotlin() =
        androidPlatformTypesAreNotDependenciesCase(AGP9)

    private fun androidPlatformTypesAreNotDependenciesCase(agp: AgpVersion) {
        // `android.jar` reaches the compiler as the platform's boot classpath, not through a
        // Gradle configuration, so platform types are neither transitive nor non-transitive.
        val project = androidLibrary(agp) {
            source(exposesAndroidPlatformTypeFile, "PlatformFacing", "test.consumer")
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicTypeFromImplementationAarProjectDependencyIsAnErrorInAndroidLibraries() =
        publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(AGP8)

    @Test
    fun publicTypeFromImplementationAarProjectDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(AGP9)

    private fun publicTypeFromImplementationAarProjectDependencyIsAnErrorCase(agp: AgpVersion) {
        // An Android library module reaches the compiler as the classes jar extracted from its
        // AAR by an artifact transform, not as the AAR its API elements publish. The dependency
        // files handed to the compiler plugin must go through the same transform.
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.buildAndFail("compileDebugKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicTypeFromApiAarProjectDependencyIsAcceptedInAndroidLibraries() =
        publicTypeFromApiAarProjectDependencyIsAcceptedCase(AGP8)

    @Test
    fun publicTypeFromApiAarProjectDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromApiAarProjectDependencyIsAcceptedCase(AGP9)

    private fun publicTypeFromApiAarProjectDependencyIsAcceptedCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInInAndroidLibraries() =
        exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(AGP8)

    @Test
    fun exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInInAndroidLibrariesWithBuiltInKotlin() =
        exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(AGP9)

    private fun exemptNonTransitiveAarProjectDependencyCoversTheAarsItPullsInCase(agp: AgpVersion) {
        // The exempt view resolves with the compile classpath's variant attributes, so it picks
        // the same build-type variant of `:facade` as the compilation and follows its `api`
        // edge to the `:model` AAR, which is where the exposed type lives.
        val project = androidLibrary(agp) {
            buildScript(exemptScript("project(\":facade\")"))
            consumerSource()
            dependencies(implementation(":facade"))
            subproject("facade") { dependencies(api(":model")) }
            modelSubproject()
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicTypeFromImplementationExternalAarDependencyIsAnErrorInAndroidLibraries() =
        publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(AGP8)

    @Test
    fun publicTypeFromImplementationExternalAarDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(AGP9)

    private fun publicTypeFromImplementationExternalAarDependencyIsAnErrorCase(agp: AgpVersion) {
        // A published AAR goes through the same classes-jar transform as a project AAR, but
        // from the Gradle transforms cache rather than a project's build directory.
        val project = androidLibrary(agp) {
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer")
            dependencies(implementation(STARTUP_RUNTIME))
        }

        project.buildAndFail("compileDebugKotlin").assertNonTransitiveExposureReported("androidx.startup.Initializer")
    }

    @Test
    fun publicTypeFromApiExternalAarDependencyIsAcceptedInAndroidLibraries() =
        publicTypeFromApiExternalAarDependencyIsAcceptedCase(AGP8)

    @Test
    fun publicTypeFromApiExternalAarDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromApiExternalAarDependencyIsAcceptedCase(AGP9)

    private fun publicTypeFromApiExternalAarDependencyIsAcceptedCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer")
            dependencies(api(STARTUP_RUNTIME))
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveExternalAarDependencyIsAcceptedInAndroidLibraries() =
        exemptNonTransitiveExternalAarDependencyIsAcceptedCase(AGP8)

    @Test
    fun exemptNonTransitiveExternalAarDependencyIsAcceptedInAndroidLibrariesWithBuiltInKotlin() =
        exemptNonTransitiveExternalAarDependencyIsAcceptedCase(AGP9)

    private fun exemptNonTransitiveExternalAarDependencyIsAcceptedCase(agp: AgpVersion) {
        // The exemption names the AAR without a version. Consistent resolution supplies the
        // compile classpath's version, and the exempt view must yield the same classes jar.
        val project = androidLibrary(agp) {
            buildScript(exemptScript(STARTUP_RUNTIME))
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer")
            dependencies(implementation(STARTUP_RUNTIME))
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInAndroidLibraries() =
        multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(AGP8)

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInAndroidLibrariesWithBuiltInKotlin() =
        multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(AGP9)

    private fun multiplatformLibraryIsMatchedThroughItsAndroidVariantCase(agp: AgpVersion) {
        // An Android compilation selects the `-android` AAR variant of a multiplatform library
        // where a JVM one would take `-jvm`. The transitive and exempt views copy the compile
        // classpath's attributes, so they must land on that same AAR: first the unexempted
        // `implementation` dependency is reported, then the version-less exemption covers it.
        val lifecycleHooks: AndroidLibrarySpec.() -> Unit = {
            source(exposesLifecycleTypeFile, "LifecycleHooks", "test.consumer")
            dependencies(implementation(LIFECYCLE_RUNTIME))
        }

        androidLibrary(agp, lifecycleHooks)
            .buildAndFail("compileDebugKotlin")
            .assertNonTransitiveExposureReported("androidx.lifecycle.LifecycleRegistry")

        androidLibrary(agp) { lifecycleHooks(); buildScript(exemptScript(LIFECYCLE_RUNTIME)) }
            .build("compileDebugKotlin")
            .assertNoNonTransitiveExposureReported()
    }

    @Test
    fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantInAndroidLibraries() =
        buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(AGP8)

    @Test
    fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantInAndroidLibrariesWithBuiltInKotlin() =
        buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(AGP9)

    private fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantCase(agp: AgpVersion) {
        // `debugApi` feeds `debugApiElements` alone, which is why the plugin reads the API
        // elements of the variant a compilation is named after. The release variant reaches the
        // same module only through `releaseImplementation`, so its consumers can't see the type.
        val project = androidLibrary(agp) {
            consumerSource()
            dependencies("debug", api(":model"))
            dependencies("release", implementation(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
        project.buildAndFail("compileReleaseKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun flavorApiDependencyIsTransitiveOnlyForItsFlavorInAndroidLibraries() =
        flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(AGP8)

    @Test
    fun flavorApiDependencyIsTransitiveOnlyForItsFlavorInAndroidLibrariesWithBuiltInKotlin() =
        flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(AGP9)

    private fun flavorApiDependencyIsTransitiveOnlyForItsFlavorCase(agp: AgpVersion) {
        // With product flavors the compilation is named after the full variant (`freeDebug`),
        // and so are its API elements, which combine the flavor's and the build type's `api`.
        val project = androidLibrary(agp) {
            android(
                """
                    flavorDimensions += "tier"
                    productFlavors {
                        create("free") { dimension = "tier" }
                        create("paid") { dimension = "tier" }
                    }
                """
            )
            consumerSource()
            dependencies("free", api(":model"))
            dependencies("paid", implementation(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileFreeDebugKotlin").assertNoNonTransitiveExposureReported()
        project.buildAndFail("compilePaidDebugKotlin").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun compileOnlyDependenciesCanBeExemptedAsAWholeInAndroidLibraries() =
        compileOnlyDependenciesCanBeExemptedAsAWholeCase(AGP8)

    @Test
    fun compileOnlyDependenciesCanBeExemptedAsAWholeInAndroidLibrariesWithBuiltInKotlin() =
        compileOnlyDependenciesCanBeExemptedAsAWholeCase(AGP9)

    private fun compileOnlyDependenciesCanBeExemptedAsAWholeCase(agp: AgpVersion) {
        // AGP's `compileOnly` is a bucket the variant compile classpaths extend from, so the
        // exempt view resolves it through the compilation's own variant.
        val project = androidLibrary(agp) {
            apiWatchdog { exemptNonTransitiveDependencies("extendsFrom(configurations.compileOnly.get())") }
            consumerSource()
            dependencies(compileOnly(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

    // The Android target of a multiplatform project.

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargets() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(LIBRARY_AGP8)

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsOnAgp9() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(LIBRARY_AGP9)

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInLegacyMultiplatformAndroidTargets() =
        publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(LEGACY)

    private fun publicTypeFromImplementationDependencyIsAnErrorInMultiplatformAndroidTargetsCase(target: KmpAndroidTarget) {
        // The multiplatform library plugin has no build variants: the compilation is `main`, so
        // the plugin falls back from `mainApiElements` to the target's own API elements. The
        // legacy target publishes through AGP's variant-specific API elements
        // (`debugApiElements`) like a plain Android library, while its dependencies are declared
        // on the multiplatform source sets.
        val project = kmpAndroidProject(target) {
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.buildAndFail(target.mainCompileTask).assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargets() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(LIBRARY_AGP8)

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsOnAgp9() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(LIBRARY_AGP9)

    @Test
    fun publicTypeFromApiDependencyIsAcceptedInLegacyMultiplatformAndroidTargets() =
        publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(LEGACY)

    private fun publicTypeFromApiDependencyIsAcceptedInMultiplatformAndroidTargetsCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            consumerSource()
            dependencies(api(":model"))
            modelSubproject()
        }

        project.build(target.mainCompileTask, "compileKotlinJvm").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun androidSourceSetApiDependencyIsAcceptedInMultiplatformAndroidTargets() =
        androidSourceSetDependenciesCase(LIBRARY_AGP8)

    @Test
    fun androidSourceSetApiDependencyIsAcceptedInMultiplatformAndroidTargetsOnAgp9() =
        androidSourceSetDependenciesCase(LIBRARY_AGP9)

    @Test
    fun androidSourceSetApiDependencyIsAcceptedInLegacyMultiplatformAndroidTargets() =
        androidSourceSetDependenciesCase(LEGACY)

    private fun androidSourceSetDependenciesCase(target: KmpAndroidTarget) {
        // Dependencies declared on `androidMain` alone reach only the Android target's
        // configurations, and the exposing declaration lives in `androidMain` too.
        kmpAndroidProject(target) {
            consumerSource("androidMain")
            dependencies("androidMain", api(":model"))
            modelSubproject()
        }.build(target.mainCompileTask).assertNoNonTransitiveExposureReported()

        kmpAndroidProject(target) {
            consumerSource("androidMain")
            dependencies("androidMain", implementation(":model"))
            modelSubproject()
        }.buildAndFail(target.mainCompileTask).assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun androidPlatformTypesAreNotDependenciesInMultiplatformAndroidTargets() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(LIBRARY_AGP8)

    @Test
    fun androidPlatformTypesAreNotDependenciesInMultiplatformAndroidTargetsOnAgp9() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(LIBRARY_AGP9)

    @Test
    fun androidPlatformTypesAreNotDependenciesInLegacyMultiplatformAndroidTargets() =
        androidPlatformTypesAreNotDependenciesInMultiplatformCase(LEGACY)

    private fun androidPlatformTypesAreNotDependenciesInMultiplatformCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            source(exposesAndroidPlatformTypeFile, "PlatformFacing", "test.consumer", "androidMain")
        }

        project.build(target.mainCompileTask).assertNoNonTransitiveExposureReported()
    }

    @Test
    fun externalAarDependencyIsCheckedAndExemptableInMultiplatformAndroidTargets() =
        externalAarDependencyInMultiplatformCase(LIBRARY_AGP8)

    @Test
    fun externalAarDependencyIsCheckedAndExemptableInMultiplatformAndroidTargetsOnAgp9() =
        externalAarDependencyInMultiplatformCase(LIBRARY_AGP9)

    @Test
    fun externalAarDependencyIsCheckedAndExemptableInLegacyMultiplatformAndroidTargets() =
        externalAarDependencyInMultiplatformCase(LEGACY)

    private fun externalAarDependencyInMultiplatformCase(target: KmpAndroidTarget) {
        // The Android target's compile classpath goes through AGP's classes-jar transform like
        // a plain Android library's, while the exempt bucket is shared with the JVM target.
        val startupHooks: ProjectSpec.() -> Unit = {
            source(exposesStartupInitializerFile, "StartupHooks", "test.consumer", "androidMain")
            dependencies("androidMain", implementation(STARTUP_RUNTIME))
        }

        kmpAndroidProject(target, startupHooks)
            .buildAndFail(target.mainCompileTask)
            .assertNonTransitiveExposureReported("androidx.startup.Initializer")

        kmpAndroidProject(target) { startupHooks(); buildScript(exemptScript(STARTUP_RUNTIME)) }
            .build(target.mainCompileTask)
            .assertNoNonTransitiveExposureReported()
    }

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInMultiplatformAndroidTargets() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(LIBRARY_AGP8)

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInMultiplatformAndroidTargetsOnAgp9() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(LIBRARY_AGP9)

    @Test
    fun multiplatformLibraryIsMatchedThroughItsAndroidVariantInLegacyMultiplatformAndroidTargets() =
        multiplatformLibraryAndroidVariantInMultiplatformCase(LEGACY)

    private fun multiplatformLibraryAndroidVariantInMultiplatformCase(target: KmpAndroidTarget) {
        // Declared on `commonMain`, the library resolves to its `-android` AAR for the Android
        // target and to its `-jvm` jar for the JVM target. Both compilations report the
        // unexempted type, and one version-less exemption covers both variants.
        val lifecycleHooks: ProjectSpec.() -> Unit = {
            source(exposesLifecycleTypeFile, "LifecycleHooks", "test.consumer")
            dependencies(implementation(LIFECYCLE_RUNTIME))
        }

        val result = kmpAndroidProject(target, lifecycleHooks)
            .buildAndFail(target.mainCompileTask, "compileKotlinJvm", "--continue")
        result.assertNonTransitiveExposureReported("androidx.lifecycle.LifecycleRegistry")
        result.assertTaskOutcome(":${target.mainCompileTask}", FAILED)
        result.assertTaskOutcome(":compileKotlinJvm", FAILED)

        kmpAndroidProject(target) { lifecycleHooks(); buildScript(exemptScript(LIFECYCLE_RUNTIME)) }
            .build(target.mainCompileTask, "compileKotlinJvm")
            .assertNoNonTransitiveExposureReported()
    }

    @Test
    fun buildTypeApiDependencyIsTransitiveOnlyForItsVariantInLegacyMultiplatformAndroidTargets() {
        // The legacy target still has AGP's build-type configurations next to the source-set
        // ones, so `debugApi` scopes the exposure to the `debug` compilation.
        val project = kmpAndroidProject(LEGACY) {
            consumerSource("androidMain")
            dependencies("debug", api(":model"))
            dependencies("release", implementation(":model"))
            modelSubproject()
        }

        project.build("compileDebugKotlinAndroid").assertNoNonTransitiveExposureReported()
        project.buildAndFail("compileReleaseKotlinAndroid").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun publicDependencyExposureCheckIsDisabledByTheModuleSwitch() {
        val project = jvmProject {
            apiWatchdog { publicTypesMustBeTransitiveDependencies = false }
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicTypeFromExemptNonTransitiveProjectDependencyIsAccepted() {
        val project = jvmProject {
            buildScript(exemptScript("project(\":model\")"))
            consumerSource()
            dependencies(implementation(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveDependencyCoversEverythingItPullsIn() {
        // The consumer never names `:model`. It reaches it through the exempt `:facade`, the
        // same way a Kotlin Multiplatform dependency reaches its platform artifact.
        val project = jvmProject {
            buildScript(exemptScript("project(\":facade\")"))
            consumerSource()
            dependencies(implementation(":facade"))
            subproject("facade") { dependencies(api(":model")) }
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveDependencyLeavesOtherDependenciesChecked() {
        val project = jvmProject {
            buildScript(exemptScript("project(\":other\")"))
            consumerSource()
            dependencies(implementation(":model"), implementation(":other"))
            modelSubproject()
            subproject("other") { source(dependencyTypeFile, "OtherModel", "test.other") }
        }

        project.buildAndFail("build").assertNonTransitiveExposureReported("test.model.ExternalModel")
    }

    @Test
    fun exemptNonTransitiveDependenciesExtendFromAWholeConfiguration() {
        // The whole-configuration form is the Gradle plugin case: everything the environment
        // supplies is declared `compileOnly`, and nothing of it is published.
        val project = jvmProject {
            apiWatchdog { exemptNonTransitiveDependencies("extendsFrom(configurations.compileOnly.get())") }
            consumerSource()
            dependencies(compileOnly(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveModuleDependencyIsResolvedConsistentlyWithTheCompileClasspath() {
        // The exemption names the multiplatform module without a version and without its JVM
        // artifact. Consistent resolution supplies the compile classpath's version, and the
        // `available-at` redirect brings the `-jvm` artifact that actually holds the classes.
        val project = jvmProject {
            buildScript(exemptScript(COROUTINES))
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
            dependencies(implementation(COROUTINES))
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveDependencyFromAVersionCatalog() {
        val project = jvmProject {
            buildScript("dependencies { apiWatchdogExemptNonTransitive(libs.coroutines) }")
            versionCatalog(COROUTINES_CATALOG)
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
            dependencies(implementation(COROUTINES))
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveDependencyFromTheDependencyDeclaration() {
        // The version catalog form adds the dependency through a provider, so the exemption is
        // recorded only when the compile classpath realizes its dependencies. The plugin resolves
        // the transitive view, which is consistent with the compile classpath, before the exempt
        // view, which is what makes this order-independent for users.
        val project = jvmProject {
            imports(EXEMPT_IMPORT)
            buildScript("dependencies { implementation(libs.coroutines) { exemptNonTransitiveDependency(this) } }")
            versionCatalog(COROUTINES_CATALOG)
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveDependencyFromAProjectDependencyDeclaration() {
        val project = jvmProject {
            imports(EXEMPT_IMPORT)
            buildScript("dependencies { implementation(project(\":model\")) { exemptNonTransitiveDependency(this) } }")
            consumerSource()
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun publicTypeFromUnexemptedModuleDependencyIsAnError() {
        val project = jvmProject {
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
            dependencies(implementation(COROUTINES))
        }

        val result = project.buildAndFail("build")
        result.assertNonTransitiveExposureReported("kotlinx.coroutines.Job")
        // The type is in the platform artifact, but the snippet names the declared dependency.
        result.assertOutputHas(
            "        implementation(\"$COROUTINES\") {\n            exemptNonTransitiveDependency(this)\n        }"
        )
    }

    @Test
    fun exemptNonTransitiveDependenciesDoNotJoinTheCompileClasspath() {
        // The bucket is a marker. A dependency declared only there is not compiled against.
        val project = jvmProject {
            buildScript(exemptScript("project(\":model\")"))
            consumerSource()
            modelSubproject()
        }

        project.buildAndFail("build").assertOutputHas("Unresolved reference")
    }

    @Test
    fun transitiveDependencyConfigurationsAreNamedAfterTheCompilation() {
        // The JVM plugin's target has no disambiguation classifier, which KGP's own
        // `disambiguatedName` renders as a literal "null".
        val project = jvmProject {
            buildScript(printWatchdogConfigurationsTask)
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("compileKotlin", "printWatchdogConfigurations", "--continue")
        result.assertOutputHas(
            "WATCHDOG_CONFIGURATIONS=[apiWatchdogMainExemptNonTransitiveDependencies, " +
                    "apiWatchdogMainTransitiveDependencies]"
        )
    }

    @Test
    fun transitiveDependencyConfigurationsAreNamedAfterTheTargetInMultiplatformProjects() {
        val project = kmpProject {
            buildScript(printWatchdogConfigurationsTask)
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("compileKotlinJvm", "printWatchdogConfigurations", "--continue")
        result.assertOutputHas("apiWatchdogJvmMainTransitiveDependencies")
        result.assertOutputLacks("apiWatchdogNull")
    }

    @Test
    fun warnsWhenExplicitApiModeIsNotEnabled() {
        // The warning is logged during configuration, so `help` is enough to observe it.
        val project = jvmProject { explicitApi = false }

        val result = project.build("help")
        result.assertOutputHas("doesn't enable explicit API mode")
        result.assertOutputHas("explicitApi()")
    }

    @Test
    fun explicitApiWarningIsSilentWhenExplicitApiModeIsEnabled() {
        jvmProject().build("help").assertOutputLacks("doesn't enable explicit API mode")
    }

    @Test
    fun explicitApiWarningSeesTheRawCompilerFlag() {
        // The flag lands in the effective free compiler arguments of the compile tasks, which is
        // where the check looks when the DSL setting is absent.
        val project = jvmProject {
            explicitApi = false
            buildScript("""kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=strict") } }""")
        }

        project.build("help").assertOutputLacks("doesn't enable explicit API mode")
    }

    @Test
    fun explicitApiWarningCanBeSuppressedWithAGradleProperty() {
        val project = jvmProject { explicitApi = false }

        project.build("help", "-Porg.jetbrains.kotlin.library.api-watchdog.suppressExplicitApiWarning=true")
            .assertOutputLacks("doesn't enable explicit API mode")
    }

    @Test
    fun suggestsAbiValidationWhenItIsNotEnabled() {
        // The suggestion is logged during configuration, so `help` is enough to observe it.
        val result = jvmProject().build("help")

        result.assertOutputHas("but no binary compatibility validation is enabled")
        assertEquals(
            legacyAbiValidationDsl,
            result.output.contains("abiValidation {"),
            "The warning should display the ABI validation setup for the tested KGP version",
        )
        assertEquals(
            !legacyAbiValidationDsl,
            result.output.contains("abiValidation()"),
            "The warning should display the ABI validation setup for the tested KGP version",
        )
        result.assertOutputHas("https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html")
        result.assertOutputHas("https://github.com/Kotlin/binary-compatibility-validator")
        result.assertOutputHas("suggestAbiValidation = false")
    }

    @Test
    fun abiValidationSuggestionCanBeDisabled() {
        val project = jvmProject {
            apiWatchdog { suggestAbiValidation = false }
        }

        project.build("help").assertOutputLacks("but no binary compatibility validation is enabled")
    }

    @Test
    fun abiValidationSuggestionIsSilentWhenBuiltInAbiValidationIsEnabled() {
        val abiValidationCall = if (legacyAbiValidationDsl) "abiValidation { enabled.set(true) }" else "abiValidation()"
        val project = jvmProject {
            buildScript(
                """
                    kotlin {
                        @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
                        $abiValidationCall
                    }
                """
            )
        }

        project.build("help").assertOutputLacks("but no binary compatibility validation is enabled")
    }

    @Test
    fun abiValidationSuggestionIsSilentWhenStandaloneValidatorIsApplied() {
        // A buildSrc stand-in registers the real Binary Compatibility Validator plugin id, so the
        // check sees the plugin as applied without the test fetching the actual artifact.
        val project = jvmProject {
            buildScript("""apply(plugin = "org.jetbrains.kotlinx.binary-compatibility-validator")""")
            buildSrc {
                plugins(Plugin.javaGradle)
                buildScript(
                    """
                        gradlePlugin {
                            plugins {
                                create("fakeBcv") {
                                    id = "org.jetbrains.kotlinx.binary-compatibility-validator"
                                    implementationClass = "test.FakeBcvPlugin"
                                }
                            }
                        }
                    """
                )
                javaFile(
                    "FakeBcvPlugin",
                    """
                        package test;

                        import org.gradle.api.Plugin;
                        import org.gradle.api.Project;
                        import org.jspecify.annotations.NonNull;

                        public class FakeBcvPlugin implements Plugin<Project> {
                            @Override
                            public void apply(@NonNull Project project) {}
                        }
                    """,
                )
            }
        }

        project.build("help").assertOutputLacks("but no binary compatibility validation is enabled")
    }

    /**
     * The test source set counterpart of [ModuleSpec.source], carrying the unwatched declarations.
     * [className] keeps the file names apart when several test source sets take part in one build.
     */
    private fun ModuleSpec.testOnlySource(sourceSet: String = "test", className: String = "TestOnlyHelper") {
        source(testOnlyFile(className), className, sourceSet = sourceSet)
    }

    /**
     * [testOnlySource] without the exemption annotation, placeable into any source set. The plugin
     * adds the annotations dependency only to the compilations it applies to, so this fixture
     * doesn't depend on whether a test compilation inherits that dependency from the main one it
     * is associated with.
     */
    private fun ModuleSpec.unexemptedOpenClassSource(sourceSet: String, className: String = "TestOnlyHelper") {
        source(unexemptedTestOnlyFile(className), className, sourceSet = sourceSet, defaultImports = false)
    }

    /** Asserts nothing in [testOnlyFile] was reported by any checker. */
    private fun BuildResult.assertNoTestSourceDiagnostics() {
        assertNotReported(
            OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            UNDOCUMENTED_PUBLIC_API,
            NULLABLE_BOOLEAN_PUBLIC_API,
            EXEMPTION_WITHOUT_EXPLANATION,
        )
        assertOutputLacks("TestOnlyHelper")
    }

    private fun BuildResult.assertNonTransitiveExposureReported(type: String) {
        assertError("publicly exposes `$type`, but the dependency with that type isn't published transitively")
    }

    private fun BuildResult.assertNoNonTransitiveExposureReported() {
        assertNotReported(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY)
    }

    private fun BuildResult.hasStatefulDiagnostic(className: String, missingMember: String): Boolean =
        output.lineSequence().any { "`$className`" in it && "inherits $missingMember" in it }

    /** Exempts [dependency] by its version-less coordinates, or a `project(...)` notation as is. */
    private fun exemptScript(dependency: String): String {
        val notation = if (dependency.startsWith("project(")) dependency else "\"${dependency.substringBeforeLast(':')}\""
        return "dependencies { apiWatchdogExemptNonTransitive($notation) }"
    }

    private fun ideaGenerateShortcut(): String =
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) "⌘N" else "Alt+Insert"

    /** KGP before 2.4 configures ABI validation through a block instead of a call. */
    private val legacyAbiValidationDsl: Boolean
        get() = TEST_COMPILER_VERSION.major <= 2 && TEST_COMPILER_VERSION.minor < 4
}

private const val EXPLICIT_API_FLAG = """kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }"""

private const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"

private const val DEFAULT_FACADE_NAME_FILE_ANNOTATION =
    "@file:org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName(" +
            "reason = org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason.API_DESIGN)"

private const val EXEMPT_IMPORT = "org.jetbrains.kotlinx.library.api.watchdog.exemptNonTransitiveDependency"

/** A dependency the functional tests resolve from Maven Central as a non-project module. */
private const val COROUTINES = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"

private const val COROUTINES_CATALOG = """
    [libraries]
    coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
"""

/** A dependency from Google's Maven repository published as an AAR. */
private const val STARTUP_RUNTIME = "androidx.startup:startup-runtime:1.2.0"

/**
 * A multiplatform AndroidX library whose Android variant is an AAR of its own
 * (`lifecycle-runtime-android`), next to a plain JVM variant for desktop consumers. Its
 * `LifecycleRegistry` is compiled into each variant, unlike `Lifecycle` from the plain
 * `lifecycle-common` jar it depends on.
 */
private const val LIFECYCLE_RUNTIME = "androidx.lifecycle:lifecycle-runtime:2.8.7"

/**
 * A test-source declaration several checkers would report if they ran on test sources. The bare
 * exemption also covers the one diagnostic no configuration can demote,
 * `EXEMPTION_WITHOUT_EXPLANATION`, and proves the annotations stay resolvable from test sources.
 */
@Language("kotlin")
private fun testOnlyFile(className: String) = """
    @IntentionallyOpen
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

/** [testOnlyFile] stripped of the exemption, so that it needs nothing on the compile classpath. */
@Language("kotlin")
private fun unexemptedTestOnlyFile(className: String) = """
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

/** The diagnostics [unacknowledgedFile] and [internalApiExposureFile] report together. */
private val singleModuleDiagnostics: List<Diagnostic> =
    Diagnostic.entries - setOf(PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY, EXEMPTION_WITHOUT_EXPLANATION)

/** One specimen of every configurable diagnostic. */
@Suppress("RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
private val unacknowledgedFile = """
    public open class UnprotectedOpenClass

    /** A base class whose subclass opt-in lists no markers and so restricts nothing. */
    @SubclassOptInRequired
    public open class UnmarkedOptIn

    public enum class UnmarkedEnum { A, B }

    /** An unacknowledged function type alias. */
    public typealias UnacknowledgedCallback = (Int) -> Unit

    /** An unacknowledged data class. */
    public data class UnmarkedData(
        /** The only coordinate. */
        val x: Int,
    )

    /** A stateful session relying on the opaque default toString. */
    public class UnrenderedSession(
        /** The session identifier. */
        public val id: Int,
    )

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

    /** A user handle compiled to its underlying type. */
    @JvmInline
    public value class UserHandle(
        /** The raw handle value. */
        public val raw: String,
    )

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

@Language("kotlin")
private val unexplainedExemptionFile = """
    /** Returns a deliberately mutable result. */
    public fun unexplainedExemption(): @IntentionallyMutableCollection MutableList<Int> = mutableListOf()
""".trimIndent()

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

@Language("kotlin")
private val exposesCoroutinesTypeFile = """
    import kotlinx.coroutines.Job

    /** Jobs the host application manages. */
    public object Jobs {
        /** Hands out a job the host application manages. */
        public fun hostJob(): Job = Job()
    }
""".trimIndent()

/**
 * A task listing the watchdog's transitive-dependency configurations. They are registered when
 * KGP hands each compilation to the plugin, so the task runs after the compile tasks.
 */
@Language("kts")
private val printWatchdogConfigurationsTask = """
    tasks.register("printWatchdogConfigurations") {
        mustRunAfter(tasks.matching { it.name.startsWith("compileKotlin") })
        doLast {
            val names = configurations.names.filter { it.endsWith("TransitiveDependencies") }.sorted()
            println("WATCHDOG_CONFIGURATIONS=" + names)
        }
    }
""".trimIndent()

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

@Language("kotlin")
private val exposesStartupInitializerFile = """
    import androidx.startup.Initializer

    /** A documented API owner whose signature mentions a type from an AAR dependency. */
    public class StartupHooks {
        /** Returns the initializer the host runs, if any. */
        public fun initializer(): Initializer<Unit>? = null
    }
""".trimIndent()

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

@Language("kotlin")
private val exposesJavaDependencyTypeFile = """
    import test.model.ExternalJavaModel

    /** A documented and otherwise settled API owner. */
    public class JavaConsumer {
        /** Returns a Java model whose class must be available to consumers. */
        public fun model(): ExternalJavaModel = ExternalJavaModel()
    }
""".trimIndent()

/** One acknowledged specimen of every diagnostic in [unacknowledgedFile]. */
@Suppress("RedundantSuspendModifier", "MayBeConstant")
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

    /** A deliberately stable data holder. */
    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
    public data class DeliberateData(
        /** The only coordinate. */
        val x: Int,
    )

    /** A deliberately opaque credentials holder. */
    @IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)
    @IntentionallyWithoutHashCode(reason = ExemptionReason.API_DESIGN)
    @IntentionallyWithoutToString(description = "The token must not leak into logs.", reason = ExemptionReason.API_DESIGN)
    public class OpaqueCredentials(
        /** The secret that must not leak into logs. */
        public val token: String,
    )

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
    @IntentionallyNullableBoolean(description = "Mirrors the wire format's optional flag.", reason = ExemptionReason.EXTERNAL_CONTRACT)
    public fun consentState(): Boolean? = null

    /** A legacy signature keeping its required parameter behind an optional one. */
    @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    @IntentionallyWithoutJvmOverloads(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
    public fun legacyRetry(retries: Int = 3, host: String) {}

    /** A deliberately Kotlin-only refresher left visible to Java. */
    @IntentionallyKotlinOnlyApi(description = "Coroutine-first API. Java is served by a blocking facade.", reason = ExemptionReason.IGNORE_JAVA_INTEROP)
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

    /** An account handle compiled to its underlying type. */
    @JvmInline
    public value class AccountHandle(
        /** The raw handle value. */
        public val raw: String,
    )

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
