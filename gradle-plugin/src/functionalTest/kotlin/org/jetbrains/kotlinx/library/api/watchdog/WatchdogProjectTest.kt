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
import org.gradle.testkit.runner.TaskOutcome.SKIPPED
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
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME
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
            buildScript(REPORT_ALL_WARNINGS_FLAG)
            source(unacknowledgedFile)
            source(internalApiExposureFile, "internalApiExposure")
        }

        val result = project.buildAndFail("build")
        result.assertErrors(*(singleModuleDiagnostics - UNDOCUMENTED_PUBLIC_API).toTypedArray())
        result.assertWarnings(UNDOCUMENTED_PUBLIC_API)
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
        val project = jvmProject {
            buildScript(REPORT_ALL_WARNINGS_FLAG)
            apiWatchdog {
                severity(WARNING, OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
                severity(ERROR, UNDOCUMENTED_PUBLIC_API)
            }
            source(unacknowledgedFile)
        }

        val result = project.buildAndFail("build")
        result.assertErrors(UNDOCUMENTED_PUBLIC_API, EXHAUSTIVE_PUBLIC_API)
        result.assertWarnings(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
    }

    @Test
    fun disabledJavaInteropGroupSilencesAllItsDiagnostics() {
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
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN)
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
        result.assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
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
    fun ignoreRejectsAnAlwaysErrorCheck() {
        val project = jvmProject {
            apiWatchdog { ignore(EXEMPTION_WITHOUT_EXPLANATION, "test.GeneratedValueMembers") }
            source(annotationIgnoreFile)
        }

        project.buildAndFail("build").assertOutputHas(
            "API Watchdog check 'EXEMPTION_WITHOUT_EXPLANATION' is always an error and accepts no ignore rule"
        )
    }

    @Test
    fun unexplainedExemptionIsAlwaysAnError() {
        // The checker that reads the exemption is off, so the error can't come from it.
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
    fun undocumentedClassPropertiesAreSkippedByDefault() {
        val result = holderProject(reportUndocumentedClassProperties = null).build("build")

        result.assertWarnings(UNDOCUMENTED_PUBLIC_API)
        result.assertOutputHas("The public function `undocumentedFunction` has no `KDoc`")
        result.assertOutputHas("The public property `undocumentedTopLevelProperty` has no `KDoc`")
        result.assertOutputLacks("`value` has no `KDoc`")
    }

    @Test
    fun undocumentedClassPropertiesCanBeReported() {
        val result = holderProject(reportUndocumentedClassProperties = true).build("build")

        result.assertOutputHas("The public property `value` has no `KDoc`")
    }

    private fun holderProject(reportUndocumentedClassProperties: Boolean?) = jvmProject {
        apiWatchdog {
            this.reportUndocumentedClassProperties = reportUndocumentedClassProperties
            severity(NONE, OPEN_API_WITHOUT_SUBCLASS_OPT_IN, TOP_LEVEL_API_WITHOUT_JVM_NAME)
        }
        source(
            """
            /**
             * Documented.
             *
             * @property value Documented through the class KDoc.
             */
            public interface Holder {
                public val value: Int

                public fun undocumentedFunction(): Int
            }

            public val undocumentedTopLevelProperty: Int = 0
            """,
            "holder",
        )
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
    fun disabledWatchdogAddsNoCompilerPluginAndSkipsItsTasks() {
        val project = jvmProject {
            explicitApi = false
            apiWatchdog { enabled = false }
            source(unacknowledgedFile)
            source(
                acknowledgedFile,
                "acknowledged",
                fileAnnotation = "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
            )
            buildScript(
                """
                    tasks.compileKotlin {
                        doLast { println("PLUGIN_CLASSPATH=" + pluginClasspath.files.joinToString { it.name }) }
                    }
                """
            )
        }

        val result = project.build("build", "exemptExistingApi", "generateApiWatchdogReport")
        result.assertNoWatchdogDiagnostics()
        result.assertOutputHas("PLUGIN_CLASSPATH=")
        result.assertOutputLacks("watchdog-compiler-plugin")
        result.assertOutputLacks("doesn't enable explicit API mode")
        result.assertOutputLacks("but no binary compatibility validation is enabled")
        result.assertTaskOutcome(":exemptExistingApi", SKIPPED)
        result.assertTaskOutcome(":generateApiWatchdogReport", SKIPPED)
    }

    @Test
    fun disabledWatchdogKeepsExemptionAnnotationsInMultiplatformProject() {
        val project = kmpProject {
            apiWatchdog { enabled = false }
            source(unacknowledgedFile)
            source(
                acknowledgedFile,
                "acknowledged",
                fileAnnotation = "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
            )
        }

        val result = project.build("build", "generateApiWatchdogReport")
        result.assertNoWatchdogDiagnostics()
        result.assertTaskOutcome(":generateApiWatchdogReport", SKIPPED)
    }

    @Test
    fun testSourcesAreNotChecked() {
        val project = jvmProject {
            source(cleanMainFile)
            testOnlySource()
        }

        project.build("compileTestKotlin").assertNoTestSourceDiagnostics()
    }

    @Test
    fun testSourcesAreNotCheckedWhenExplicitApiModeIsForcedOnEveryCompilation() {
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
        // A local compilation has the shape of the Android names (`debugUnitTest`, `hostTest`)
        // and needs no Android SDK.
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
        val project = kmpAndroidProject(target) {
            source(cleanMainFile)
            unexemptedOpenClassSource("androidMain", "AndroidOnlyApi")
        }

        project.buildAndFail(target.mainCompileTask)
            .assertErrors(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, NULLABLE_BOOLEAN_PUBLIC_API)
    }

    @Test
    fun multiplatformAndroidTargetTestSourcesAreNotChecked() = multiplatformAndroidTargetTestSourcesCase(LIBRARY_AGP8)

    @Test
    fun multiplatformAndroidTargetTestSourcesAreNotCheckedOnAgp9() = multiplatformAndroidTargetTestSourcesCase(LIBRARY_AGP9)

    @Test
    fun legacyMultiplatformAndroidTargetTestSourcesAreNotChecked() = multiplatformAndroidTargetTestSourcesCase(LEGACY)

    private fun multiplatformAndroidTargetTestSourcesCase(target: KmpAndroidTarget) {
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
        val project = jvmProject {
            source(markerConsumerFile, "consumer", imports = listOf("test.lib.InternalLibApi"))
            dependencies(implementation(":lib"))
            subproject("lib") { source(markerLibraryFile, "markers", "test.lib") }
        }

        val result = project.build("build")
        result.assertWarning("`WatchedClass` has no `KDoc`")
        result.assertOutputLacks("InternalOpenClass")
        result.assertOutputLacks("memberOfInternal")
        result.assertOutputLacks("InternalEnum")
        result.assertOutputLacks("INTERNAL_ENTRY")
        result.assertOutputLacks("internalFunction")
        result.assertNotReported(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, EXHAUSTIVE_PUBLIC_API)
    }

    @Test
    fun configuredInternalApiAnnotationsExemptLikeTheMarker() {
        val project = jvmProject {
            buildScript(REPORT_ALL_WARNINGS_FLAG)
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
        result.assertWarning("`WatchedClass` has no `KDoc`")
        result.assertError(
            "publicly exposes `test.lib.ThirdPartyInternalType`, but that type is marked as internal API " +
                    "with `@ThirdPartyInternalApi`",
        )
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
            "        implementation(project(\":model\"))\n        apiWatchdogExemptNonTransitive(project(\":model\"))"
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
        // The JVM target reads a jar and the JS target a klib. `--continue` runs the second
        // compilation after the first one fails.
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

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibraries() =
        publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(AGP8)

    @Test
    fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesWithBuiltInKotlin() =
        publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(AGP9)

    private fun publicTypeFromImplementationDependencyIsAnErrorInAndroidLibrariesCase(agp: AgpVersion) {
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
        // `android.jar` is the boot classpath, not a Gradle dependency.
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
        // A published AAR comes from the Gradle transforms cache, not from a build directory.
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
        // An Android compilation selects the `-android` variant of the library, a JVM one `-jvm`.
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
        val project = androidLibrary(agp) {
            buildScript(EXEMPT_COMPILE_ONLY_SCRIPT)
            consumerSource()
            dependencies(compileOnly(":model"))
            modelSubproject(jvm = true)
        }

        project.build("compileDebugKotlin").assertNoNonTransitiveExposureReported()
    }

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
        val project = jvmProject {
            buildScript(EXEMPT_COMPILE_ONLY_SCRIPT)
            consumerSource()
            dependencies(compileOnly(":model"))
            modelSubproject()
        }

        project.build("build").assertNoNonTransitiveExposureReported()
    }

    @Test
    fun exemptNonTransitiveModuleDependencyIsResolvedConsistentlyWithTheCompileClasspath() {
        // The `available-at` redirect of the module brings the `-jvm` artifact that holds the classes.
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
    fun publicTypeFromUnexemptedModuleDependencyIsAnError() {
        val project = jvmProject {
            source(exposesCoroutinesTypeFile, "Jobs", "test.consumer")
            dependencies(implementation(COROUTINES))
        }

        val result = project.buildAndFail("build")
        result.assertNonTransitiveExposureReported("kotlinx.coroutines.Job")
        // The snippet names the declared dependency, not the platform artifact that holds the type.
        result.assertOutputHas(
            "        implementation(\"$COROUTINES\")\n        apiWatchdogExemptNonTransitive(\"$COROUTINES\")"
        )
    }

    @Test
    fun exemptNonTransitiveDependenciesDoNotJoinTheCompileClasspath() {
        val project = jvmProject {
            buildScript(exemptScript("project(\":model\")"))
            consumerSource()
            modelSubproject()
        }

        project.buildAndFail("build").assertOutputHas("Unresolved reference")
    }

    @Test
    fun transitiveDependencyConfigurationsAreNamedAfterTheCompilation() {
        // KGP renders the missing disambiguation classifier of the JVM target as a literal "null".
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
        // The buildSrc plugin registers the real plugin id, so the test doesn't fetch the real plugin.
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

    /** [testOnlyFile] in a test source set. */
    private fun ModuleSpec.testOnlySource(sourceSet: String = "test", className: String = "TestOnlyHelper") {
        source(testOnlyFile(className), className, sourceSet = sourceSet)
    }

    /** [unexemptedTestOnlyFile] in any source set. It needs no annotations dependency. */
    private fun ModuleSpec.unexemptedOpenClassSource(sourceSet: String, className: String = "TestOnlyHelper") {
        source(unexemptedTestOnlyFile(className), className, sourceSet = sourceSet, defaultImports = false)
    }

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

    /** Exempts [dependency] without its version. */
    private fun exemptScript(dependency: String): String {
        val notation = if (dependency.startsWith("project(")) dependency else "\"${dependency.substringBeforeLast(':')}\""
        return "dependencies { apiWatchdogExemptNonTransitive($notation) }"
    }

    private fun ideaGenerateShortcut(): String =
        if (System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) "⌘N" else "Alt+Insert"

    private val legacyAbiValidationDsl: Boolean
        get() = TEST_COMPILER_VERSION.major <= 2 && TEST_COMPILER_VERSION.minor < 4
}

private const val EXPLICIT_API_FLAG = """kotlin { compilerOptions { freeCompilerArgs.add("-Xexplicit-api=warning") } }"""

/** The compiler omits warnings when the compilation fails. This flag reports them. */
private const val REPORT_ALL_WARNINGS_FLAG =
    """kotlin { compilerOptions { freeCompilerArgs.add("-Xreport-all-warnings") } }"""

private const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"

private const val DEFAULT_FACADE_NAME_FILE_ANNOTATION =
    "@file:org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName(" +
            "reason = org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason.API_DESIGN)"

private const val EXEMPT_COMPILE_ONLY_SCRIPT =
    "configurations.apiWatchdogExemptNonTransitive { extendsFrom(configurations.compileOnly.get()) }"

private const val COROUTINES = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2"

private const val COROUTINES_CATALOG = """
    [libraries]
    coroutines = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version = "1.10.2" }
"""

/** An AAR from the Google Maven repository. */
private const val STARTUP_RUNTIME = "androidx.startup:startup-runtime:1.2.0"

/**
 * A multiplatform library with an AAR variant and a JVM variant. `LifecycleRegistry` is in each
 * variant, unlike `Lifecycle` from the `lifecycle-common` jar.
 */
private const val LIFECYCLE_RUNTIME = "androidx.lifecycle:lifecycle-runtime:2.8.7"

/** A declaration that several checkers report, with a bare exemption for `EXEMPTION_WITHOUT_EXPLANATION`. */
@Language("kotlin")
private fun testOnlyFile(className: String) = """
    @IntentionallyOpen
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

/** [testOnlyFile] without the exemption. */
@Language("kotlin")
private fun unexemptedTestOnlyFile(className: String) = """
    public open class $className {
        public fun flag(): Boolean? = null
    }
""".trimIndent()

/** The diagnostics of [unacknowledgedFile] and [internalApiExposureFile]. */
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

/** The configurations are registered per compilation, so the task runs after the compile tasks. */
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

/** [unacknowledgedFile] with every diagnostic exempted. */
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
