@file:Suppress("RedundantVisibilityModifier", "FunctionName")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import com.autonomousapps.kit.gradle.Plugin
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KmpTarget
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KotlinPlugins
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.EXEMPTION_WITHOUT_EXPLANATION
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.UNDOCUMENTED_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Severity.ERROR
import org.jetbrains.kotlinx.library.api.watchdog.Severity.NONE
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

class ExemptExistingApiTest {
    @Test
    fun `exempt task reuses the configuration cache`() {
        val project = jvmProject {
            source("/** An existing open API. */\npublic open class ExistingApi", "existingApi")
        }
        val arguments = arrayOf(EXEMPT_TASK, "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")

        val second = project.build(*arguments)
        second.assertOutputHas("Configuration cache entry reused.")
        second.assertOutputHas("No watchdog errors to exempt.")
    }

    @Test
    fun `fixes every fixable diagnostic and the build passes`() {
        val project = jvmProject {
            buildScript(CONTEXT_PARAMETERS_BUILD_SCRIPT)
            apiWatchdog { severity(ERROR, UNDOCUMENTED_PUBLIC_API) }
            source(fixableFile, "legacy")
        }

        val updateResult = project.build(EXEMPT_TASK)
        updateResult.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        updateResult.assertOutputHas("backwards-compatibility exemption(s) and 2 placeholder KDoc(s)")

        val fixedText = project.mainSource("legacy").readText()
        val reason = "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY"
        assertContains(fixedText, "@file:IntentionallyDefaultFacadeName($reason)")
        assertEquals(0, fixedText.indexOf("@file:IntentionallyDefaultFacadeName"))
        assertContains(fixedText, "$PLACEHOLDER_KDOC\n@IntentionallyOpen($reason)\npublic open class UndocumentedOpenClass {")
        assertContains(fixedText, "    $PLACEHOLDER_KDOC\n    public fun undocumentedMember()")
        assertFalse(fixedText.contains("@IntentionallyUndocumented"))
        assertContains(fixedText, "@IntentionallyOpen($reason)\npublic open class UnprotectedOpenClass")
        assertContains(fixedText, "@IntentionallyOpen($reason)\npublic open class UnmarkedOptIn")
        assertFalse(fixedText.contains("@SubclassOptInRequired"))
        assertContains(fixedText, "@IntentionallyExhaustive($reason)\npublic enum class UnmarkedEnum")
        assertContains(fixedText, "@IntentionallyFunctionTypeAlias($reason)\npublic typealias UnacknowledgedCallback")
        assertContains(fixedText, "@IntentionallyDataClass($reason)\npublic data class UnmarkedData")
        assertContains(fixedText, "@IntentionallyWithoutEqualsHashCodeOrToString($reason)\npublic class UnrenderedSession")
        assertFalse(fixedText.contains("@IntentionallyWithoutEquals("))
        assertFalse(fixedText.contains("@IntentionallyWithoutHashCode("))
        assertFalse(fixedText.contains("@IntentionallyWithoutToString("))
        assertContains(fixedText, "@IntentionallyMutableCollection($reason)\npublic fun leakState()")
        assertContains(fixedText, "@IntentionallyPairOrTriple($reason)\npublic fun locateOrigin()")
        assertContains(fixedText, "@IntentionallyBooleanParameter($reason)\npublic fun toggleWork(enabled: Boolean)")
        assertContains(fixedText, "@IntentionallyBooleanParameter($reason)\ncontext(enabled: Boolean)\npublic fun toggleContextWork()")
        assertContains(fixedText, "@IntentionallyNullableBoolean($reason)\npublic fun lastKnownState()")
        assertContains(fixedText, "@IntentionallyRequiredParameterAfterOptional($reason)\n@IntentionallyWithoutJvmOverloads($reason)\npublic fun retryWork")
        assertContains(fixedText, "@IntentionallyInconsistentParameterOrder($reason)\npublic fun drawShape(y: Int, x: Int, scale: Double)")
        assertContains(fixedText, "@IntentionallyInlinedLogic($reason)\n@Suppress(\"NOTHING_TO_INLINE\")\npublic inline fun squared")
        assertContains(fixedText, "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility\n@DslMarker\n@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)\npublic annotation class NoopTargetDsl")
        assertContains(fixedText, "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility\n@DslMarker\npublic annotation class TargetlessDsl")
        assertContains(fixedText, "@IntentionallyMangledJvmName($reason)\npublic fun findUser")
        assertContains(fixedText, "@IntentionallyNonStaticCompanionApi($reason)\n        public fun instance()")
        assertContains(fixedText, "@IntentionallyNonStaticCompanionApi($reason)\n        public val DEFAULT_LABEL")
        assertContains(fixedText, "@IntentionallyKotlinOnlyApi($reason)\npublic suspend fun refreshState()")
        assertContains(fixedText, "@IntentionallyWithoutJvmOverloads($reason)\npublic fun openPort")

        // The report lists the exemptions the task has just applied.
        val report = project.report().readText()
        assertContains(report, "Applied annotations")
        assertContains(report, "@IntentionallyDataClass (1)")
        assertContains(report, "@IntentionallyOpen (3)")
        assertContains(report, "legacy.kt:")
        assertContains(report, "Every recorded diagnostic was acknowledged automatically.")

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `second run finds nothing left to exempt`() {
        val project = jvmProject {
            buildScript(CONTEXT_PARAMETERS_BUILD_SCRIPT)
            apiWatchdog { severity(ERROR, UNDOCUMENTED_PUBLIC_API) }
            source(fixableFile, "legacy")
        }

        project.build(EXEMPT_TASK)
        val fixedOnce = project.mainSource("legacy").readText()

        project.build(EXEMPT_TASK).assertOutputHas("No watchdog errors to exempt.")
        assertEquals(fixedOnce, project.mainSource("legacy").readText())
    }

    @Test
    fun `report includes exemptions that already existed`() {
        val project = jvmProject {
            source(
                """
                    /** An existing deliberately open API. */
                    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
                    public open class ExistingApi

                    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
                    public fun undocumentedApi(): Unit = Unit

                    /** An intentionally opaque identity object. */
                    @org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString(
                        reason = ExemptionReason.API_DESIGN,
                    )
                    public class OpaqueApi(public val value: Int)
                """,
                "existingApi",
            )
        }
        val sourceFile = project.mainSource("existingApi")
        val original = sourceFile.readText()

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)

        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertContains(report, "Applied annotations")
        assertContains(report, "@IntentionallyOpen (1)")
        assertContains(report, "@IntentionallyUndocumented (1)")
        assertContains(report, "@IntentionallyWithoutEqualsHashCodeOrToString (1)")
        assertContains(report, "existingApi.kt:")
    }

    @Test
    fun `report shows applicable annotations and leaves sources unchanged`() {
        val project = jvmProject {
            source("/** A legacy open API. */\npublic open class LegacyApi", "legacyApi")
        }
        val sourceFile = project.mainSource("legacyApi")
        val original = sourceFile.readText()

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)

        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertContains(report, "@IntentionallyOpen (1)")
        assertContains(report, "exemptExistingApi can apply it.")
    }

    @Test
    fun `report shows the placeholder KDoc and leaves sources unchanged`() {
        val project = jvmProject {
            apiWatchdog { severity(ERROR, UNDOCUMENTED_PUBLIC_API) }
            source("public class Undocumented", "undocumented")
        }
        val sourceFile = project.mainSource("undocumented")
        val original = sourceFile.readText()

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)

        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertContains(report, "No automatic annotation (1)")
        assertContains(report, "UNDOCUMENTED_PUBLIC_API")
        assertContains(report, "exemptExistingApi can add a placeholder KDoc.")
    }

    @Test
    fun `SARIF report lists the diagnostics with root-relative locations`() {
        val project = jvmProject {
            subproject("lib") {
                apiWatchdog { severity(WARNING, UNDOCUMENTED_PUBLIC_API) }
                source("public open class LegacyApi", "legacyApi")
            }
        }

        project.build(":lib:$REPORT_TASK").assertTaskOutcome(":lib:$REPORT_TASK", SUCCESS)

        val sarif = project.file("lib/build/reports/api-watchdog/report.sarif").readText()
        assertContains(sarif, "\"version\": \"2.1.0\"")
        assertContains(sarif, "\"id\": \"OPEN_API_WITHOUT_SUBCLASS_OPT_IN\"")
        assertContains(sarif, "\"helpUri\": \"${OPEN_API_WITHOUT_SUBCLASS_OPT_IN.docsUrl}\"")
        assertContains(sarif, "\"ruleId\": \"UNDOCUMENTED_PUBLIC_API\"")
        assertContains(sarif, "\"level\": \"error\"")
        assertContains(sarif, "\"level\": \"warning\"")
        assertContains(sarif, "\"uri\": \"lib/src/main/kotlin/test/legacyApi.kt\"")
        assertContains(sarif, "\"startLine\": ")
        assertContains(sarif, "Open API without subclass opt-in. Exemption: @IntentionallyOpen. exemptExistingApi can apply it.")
    }

    @Test
    fun `reports block selects the formats and the files`() {
        val project = jvmProject {
            apiWatchdog { reports { html = false; sarifFile = "scan/watchdog.sarif" } }
            source("/** A legacy open API. */\npublic open class LegacyApi", "legacyApi")
        }

        val result = project.build(REPORT_TASK)

        result.assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        assertFalse(project.report().exists(), "HTML report is off")
        assertFalse(project.file("build/reports/api-watchdog/report.sarif").exists(), "SARIF report moved")
        assertContains(project.file("build/scan/watchdog.sarif").readText(), "OPEN_API_WITHOUT_SUBCLASS_OPT_IN")
        result.assertOutputLacks("API Watchdog report: ")
        result.assertOutputHas("API Watchdog SARIF report: ")

        val sarifOff = jvmProject {
            apiWatchdog { reports { sarif = false } }
            source("/** A legacy open API. */\npublic open class LegacyApi", "legacyApi")
        }

        sarifOff.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        assertFalse(sarifOff.file("build/reports/api-watchdog/report.sarif").exists(), "SARIF report is off")
        assertContains(sarifOff.report().readText(), "@IntentionallyOpen (1)")
    }

    @Test
    fun `disabled diagnostics aren't exempted`() {
        val project = jvmProject {
            apiWatchdog { severity(NONE, UNDOCUMENTED_PUBLIC_API) }
            source("public open class UndocumentedOpen", "legacy")
        }

        project.build(EXEMPT_TASK)

        val fixedText = project.mainSource("legacy").readText()
        assertFalse(fixedText.contains("@IntentionallyUndocumented"))
        assertFalse(fixedText.contains(PLACEHOLDER_KDOC))
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun `warnings stay and the report lists them`() {
        val project = jvmProject {
            apiWatchdog { severity(WARNING, OPEN_API_WITHOUT_SUBCLASS_OPT_IN) }
            source("/** A warning-only class. */\npublic open class WarningOnly", "warningOnly")
        }
        val sourceFile = project.mainSource("warningOnly")
        val original = sourceFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertOutputHas("No watchdog errors to exempt.")
        result.assertOutputHas("1 warning(s) stay, because the task exempts errors only.")
        result.assertOutputLacks("needs manual attention")
        assertEquals(original, sourceFile.readText())
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertContains(report, "@IntentionallyOpen (1)")
        assertContains(report, "exemptExistingApi exempts errors only.")
    }

    @Test
    fun `the default undocumented warning gets no placeholder KDoc`() {
        val project = jvmProject { source("public open class UndocumentedOpen", "legacy") }

        project.build(EXEMPT_TASK)

        val fixedText = project.mainSource("legacy").readText()
        assertFalse(fixedText.contains(PLACEHOLDER_KDOC))
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
        assertContains(project.report().readText(), "exemptExistingApi exempts errors only.")
    }

    @Test
    fun `works without explicit API mode`() {
        // The task adds `-Xexplicit-api=warning` itself.
        val project = jvmProject {
            explicitApi = false
            source("/** An open class. */\npublic open class Open", "open")
        }

        project.build(EXEMPT_TASK)

        assertContains(
            project.mainSource("open").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Open",
        )
    }

    @Test
    fun `missing imports are added`() {
        val project = jvmProject {
            source("/** A bare open class. */\npublic open class Bare", "bare", defaultImports = false)
        }

        project.build(EXEMPT_TASK)

        val fixedText = project.mainSource("bare").readText()
        assertContains(fixedText, "import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason")
        assertContains(fixedText, "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen")
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")

        project.build("build").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `unfixable diagnostics are reported for manual attention`() {
        val project = jvmProject { source(unfixableFile, "manual") }

        project.build(REPORT_TASK).assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertContains(report, "No automatic annotation")
        assertContains(report, "DSL_MARKER_NOOP_TYPE_POSITION")

        val result = project.build(EXEMPT_TASK)

        result.assertOutputHas("needs manual attention")
        result.assertOutputHas("DSL_MARKER_NOOP_TYPE_POSITION")
    }

    @Test
    fun `broken compilation fails before the fixer and doesn't touch sources`() {
        val project = jvmProject {
            source("internal fun broken() { thisCallDoesNotResolve() }", "broken")
        }
        val original = project.mainSource("broken").readText()

        val result = project.buildAndFail(EXEMPT_TASK)

        result.assertOutputHas("thisCallDoesNotResolve")
        result.assertTaskOutcome(":$EXEMPT_TASK", null)
        assertEquals(original, project.mainSource("broken").readText())
    }

    @Test
    fun `unfixable always-error diagnostics are disabled only during the update`() {
        val project = jvmProject {
            source(alwaysErrorDiagnosticsFile, "alwaysErrors", "test.consumer")
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val sourceFile = project.file("src/main/kotlin/test/consumer/alwaysErrors.kt")
        val original = sourceFile.readText()

        val updateResult = project.build(EXEMPT_TASK)

        updateResult.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        updateResult.assertOutputHas("No watchdog errors to exempt.")
        assertEquals(original, sourceFile.readText())

        project.buildAndFail("compileKotlin").assertErrors(
            EXEMPTION_WITHOUT_EXPLANATION,
            PUBLIC_TYPE_WITH_INTERNAL_API,
            PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
        )
    }

    @Test
    fun `JS-only project is fixed through its regular compilation`() {
        val project = kmpProject("js { nodejs() }") {
            source("/** A JS class. */\npublic open class JsOnly", "jsOnly")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJs", SUCCESS)
        assertContains(
            project.file("src/commonMain/kotlin/test/jsOnly.kt").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class JsOnly",
        )
    }

    @Test
    fun `native-only project is fixed through its regular compilation`() {
        val native = KmpTarget.NATIVE_HOST
        val project = kmpProject("${native.gradleTargetName}()") {
            source("/** A native class. */\npublic open class NativeOnly", "nativeOnly")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":${native.compileTaskName}", SUCCESS)
        assertContains(
            project.file("src/commonMain/kotlin/test/nativeOnly.kt").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class NativeOnly",
        )
    }

    @Test
    fun `multiplatform JVM compilation covers common sources`() {
        val project = kmpProject { source(multiplatformFile, "shared") }

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        assertContains(
            project.file("src/commonMain/kotlin/test/shared.kt").readText(),
            "@IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic data class SharedData",
        )

        project.build("compileKotlinJvm").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `Android library is fixed through both variants`() = androidLibraryIsFixedThroughBothVariantsCase(AgpVersion.AGP8)

    @Test
    fun `Android library is fixed through both variants on AGP 9`() = androidLibraryIsFixedThroughBothVariantsCase(AgpVersion.AGP9)

    private fun androidLibraryIsFixedThroughBothVariantsCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            source(openClass("AndroidApi"), "androidApi")
            source(openClass("DebugOnlyApi"), "debugOnlyApi", sourceSet = "debug")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileDebugKotlin", SUCCESS)
        result.assertTaskOutcome(":compileReleaseKotlin", SUCCESS)
        result.assertOutputHas("2 backwards-compatibility exemption(s)")
        project.assertExempted("src/main/kotlin/test/androidApi.kt", "AndroidApi")
        project.assertExempted("src/debug/kotlin/test/debugOnlyApi.kt", "DebugOnlyApi")
        assertContains(project.report().readText(), "@IntentionallyOpen (2)")

        project.build("compileDebugKotlin", "compileReleaseKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `multiplatform Android target is fixed`() = multiplatformAndroidTargetIsFixedCase(KmpAndroidTarget.LIBRARY_AGP8)

    @Test
    fun `multiplatform Android target is fixed on AGP 9`() = multiplatformAndroidTargetIsFixedCase(KmpAndroidTarget.LIBRARY_AGP9)

    @Test
    fun `legacy multiplatform Android target is fixed`() = multiplatformAndroidTargetIsFixedCase(KmpAndroidTarget.LEGACY)

    private fun multiplatformAndroidTargetIsFixedCase(target: KmpAndroidTarget) {
        val project = kmpAndroidProject(target) {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("AndroidOnlyApi"), "androidOnlyApi", sourceSet = "androidMain")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":${target.mainCompileTask}", SUCCESS)
        if (target == KmpAndroidTarget.LEGACY) {
            result.assertTaskOutcome(":compileReleaseKotlinAndroid", SUCCESS)
        }
        // The JVM and Android compilations both report the common declaration. It gets one exemption.
        result.assertOutputHas("2 backwards-compatibility exemption(s)")
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        project.assertExempted("src/androidMain/kotlin/test/androidOnlyApi.kt", "AndroidOnlyApi")

        project.build(target.mainCompileTask).assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom JVM compilations have no explicit API mode and aren't fixed`() {
        val project = jvmProject {
            buildScript(
                """
                    sourceSets.create("extra")
                    kotlin.target.compilations.create("benchmark")
                    kotlin.target.compilations.create("integrationTest") {
                        associateWith(kotlin.target.compilations.getByName("main"))
                    }
                """
            )
            source(openClass("MainApi"), "mainApi")
            source(openClass("ExtraApi"), "extraApi", sourceSet = "extra")
            source(openClass("BenchmarkApi"), "benchmarkApi", sourceSet = "benchmark")
            source(openClass("IntegrationHelper"), "integrationHelper", sourceSet = "integrationTest")
        }
        val customFiles = listOf(
            "src/extra/kotlin/test/extraApi.kt",
            "src/benchmark/kotlin/test/benchmarkApi.kt",
            "src/integrationTest/kotlin/test/integrationHelper.kt",
        ).map(project::file)
        val customTexts = customFiles.map { it.readText() }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlin", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlin", null)
        result.assertTaskOutcome(":compileBenchmarkKotlin", null)
        result.assertTaskOutcome(":compileIntegrationTestKotlin", null)
        result.assertOutputHas("1 backwards-compatibility exemption(s) in 1 file(s)")
        project.assertExempted("src/main/kotlin/test/mainApi.kt", "MainApi")
        assertEquals(customTexts, customFiles.map { it.readText() })

        project.build("compileExtraKotlin", "compileBenchmarkKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `raw explicit API flag makes custom JVM compilations fixed`() {
        val project = jvmProject {
            explicitApi = false
            buildScript(
                """
                    kotlin { compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict") }
                    sourceSets.create("extra")
                    kotlin.target.compilations.create("integrationTest") {
                        associateWith(kotlin.target.compilations.getByName("main"))
                    }
                """
            )
            source(openClass("ExtraApi"), "extraApi", sourceSet = "extra")
            source(openClass("IntegrationHelper"), "integrationHelper", sourceSet = "integrationTest")
        }
        val integrationFile = project.file("src/integrationTest/kotlin/test/integrationHelper.kt")
        val integrationText = integrationFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlin", SUCCESS)
        result.assertTaskOutcome(":compileIntegrationTestKotlin", null)
        project.assertExempted("src/extra/kotlin/test/extraApi.kt", "ExtraApi")
        assertEquals(integrationText, integrationFile.readText())

        project.build("compileExtraKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom multiplatform compilation has no explicit API mode and isn't fixed`() {
        val project = kmpProject("jvm { compilations.create(\"extra\") }") {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("ExtraApi"), "extraApi", sourceSet = "jvmExtra")
        }
        val extraFile = project.file("src/jvmExtra/kotlin/test/extraApi.kt")
        val extraText = extraFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlinJvm", null)
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        assertEquals(extraText, extraFile.readText())

        project.build("compileExtraKotlinJvm").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `report task covers the same compilations as the exempt task`() {
        val project = jvmProject {
            buildScript("""sourceSets.create("extra")""")
            source(openClass("MainApi"), "mainApi")
            source(openClass("ExtraApi"), "extraApi", sourceSet = "extra")
        }

        val result = project.build(REPORT_TASK)

        result.assertTaskOutcome(":$REPORT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlin", SUCCESS)
        result.assertTaskOutcome(":compileExtraKotlin", null)
        val report = project.report().readText()
        assertContains(report, "Not applied")
        assertContains(report, "mainApi.kt:")
        assertFalse(report.contains("extraApi.kt:"))
    }

    @Test
    fun `Android flavors and custom build types are fixed`() = androidFlavorsAndCustomBuildTypesCase(AgpVersion.AGP8)

    @Test
    fun `Android flavors and custom build types are fixed on AGP 9`() = androidFlavorsAndCustomBuildTypesCase(AgpVersion.AGP9)

    private fun androidFlavorsAndCustomBuildTypesCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            android(
                """
                    flavorDimensions += "tier"
                    productFlavors {
                        create("free") { dimension = "tier" }
                        create("paid") { dimension = "tier" }
                    }
                    buildTypes { create("staging") }
                """
            )
            source(openClass("MainApi"), "mainApi")
            source(openClass("FreeApi"), "freeApi", sourceSet = "free")
            source(openClass("StagingApi"), "stagingApi", sourceSet = "staging")
            source(openClass("PaidReleaseApi"), "paidReleaseApi", sourceSet = "paidRelease")
            source(openClass("UnitTestHelper"), "unitTestHelper", sourceSet = "test")
            source(openClass("DeviceTestHelper"), "deviceTestHelper", sourceSet = "androidTest")
        }
        val testFiles = listOf("src/test/kotlin/test/unitTestHelper.kt", "src/androidTest/kotlin/test/deviceTestHelper.kt")
            .map(project::file)
        val testTexts = testFiles.map { it.readText() }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        listOf("FreeDebug", "FreeRelease", "FreeStaging", "PaidDebug", "PaidRelease", "PaidStaging").forEach {
            result.assertTaskOutcome(":compile${it}Kotlin", SUCCESS)
        }
        result.assertTaskOutcome(":compileFreeDebugUnitTestKotlin", null)
        result.assertTaskOutcome(":compileFreeDebugAndroidTestKotlin", null)
        result.assertOutputHas("4 backwards-compatibility exemption(s) in 4 file(s)")
        project.assertExempted("src/main/kotlin/test/mainApi.kt", "MainApi")
        project.assertExempted("src/free/kotlin/test/freeApi.kt", "FreeApi")
        project.assertExempted("src/staging/kotlin/test/stagingApi.kt", "StagingApi")
        project.assertExempted("src/paidRelease/kotlin/test/paidReleaseApi.kt", "PaidReleaseApi")
        assertEquals(testTexts, testFiles.map { it.readText() })
        assertContains(project.report().readText(), "@IntentionallyOpen (4)")

        project.build("compileFreeStagingKotlin", "compilePaidReleaseKotlin").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `disabled Android variant is skipped`() = disabledAndroidVariantIsSkippedCase(AgpVersion.AGP8)

    @Test
    fun `disabled Android variant is skipped on AGP 9`() = disabledAndroidVariantIsSkippedCase(AgpVersion.AGP9)

    private fun disabledAndroidVariantIsSkippedCase(agp: AgpVersion) {
        val project = androidLibrary(agp) {
            buildScript(
                """
                    androidComponents {
                        beforeVariants(selector().withBuildType("release")) { it.enable = false }
                    }
                """
            )
            source(openClass("AndroidApi"), "androidApi")
            source(openClass("ReleaseOnlyApi"), "releaseOnlyApi", sourceSet = "release")
        }
        val releaseFile = project.file("src/release/kotlin/test/releaseOnlyApi.kt")
        val releaseText = releaseFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileDebugKotlin", SUCCESS)
        result.assertTaskOutcome(":compileReleaseKotlin", null)
        project.assertExempted("src/main/kotlin/test/androidApi.kt", "AndroidApi")
        assertEquals(releaseText, releaseFile.readText())
    }

    @Test
    fun `legacy multiplatform Android target flavors are fixed`() {
        val project = kmpAndroidProject(KmpAndroidTarget.LEGACY) {
            buildScript(
                """
                    android {
                        flavorDimensions += "tier"
                        productFlavors {
                            create("free") { dimension = "tier" }
                            create("paid") { dimension = "tier" }
                        }
                    }
                """
            )
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("AndroidApi"), "androidApi", sourceSet = "androidMain")
            source(openClass("FreeApi"), "freeApi", sourceSet = "androidFree")
            source(openClass("PaidDebugApi"), "paidDebugApi", sourceSet = "androidPaidDebug")
            source(openClass("UnitTestHelper"), "unitTestHelper", sourceSet = "androidUnitTest")
        }
        val testFile = project.file("src/androidUnitTest/kotlin/test/unitTestHelper.kt")
        val testText = testFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        listOf("FreeDebug", "FreeRelease", "PaidDebug", "PaidRelease").forEach {
            result.assertTaskOutcome(":compile${it}KotlinAndroid", SUCCESS)
        }
        result.assertTaskOutcome(":compileFreeDebugUnitTestKotlinAndroid", null)
        result.assertOutputHas("4 backwards-compatibility exemption(s) in 4 file(s)")
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        project.assertExempted("src/androidMain/kotlin/test/androidApi.kt", "AndroidApi")
        project.assertExempted("src/androidFree/kotlin/test/freeApi.kt", "FreeApi")
        project.assertExempted("src/androidPaidDebug/kotlin/test/paidDebugApi.kt", "PaidDebugApi")
        assertEquals(testText, testFile.readText())

        project.build("compilePaidDebugKotlinAndroid").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom intermediate source set is fixed once`() {
        val project = kmpProject(
            """
                jvm()
                js { nodejs() }
                sourceSets {
                    val jvmAndJs by creating { dependsOn(commonMain.get()) }
                    jvmMain.get().dependsOn(jvmAndJs)
                    jsMain.get().dependsOn(jvmAndJs)
                }
            """
        ) {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("JvmAndJsApi"), "jvmAndJsApi", sourceSet = "jvmAndJs")
            source(openClass("JvmOnlyApi"), "jvmOnlyApi", sourceSet = "jvmMain")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJvm", SUCCESS)
        result.assertTaskOutcome(":compileKotlinJs", SUCCESS)
        // Three compilations report the common declaration and two the intermediate one.
        result.assertOutputHas("3 backwards-compatibility exemption(s) in 3 file(s)")
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        project.assertExempted("src/jvmAndJs/kotlin/test/jvmAndJsApi.kt", "JvmAndJsApi")
        project.assertExempted("src/jvmMain/kotlin/test/jvmOnlyApi.kt", "JvmOnlyApi")

        project.build("compileKotlinJvm", "compileKotlinJs").assertNoWatchdogDiagnostics()
    }

    @Test
    fun `custom target name is fixed`() {
        val project = kmpProject("jvm(\"desktop\")") {
            source(openClass("DesktopApi"), "desktopApi", sourceSet = "desktopMain")
        }

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileKotlinDesktop", SUCCESS)
        project.assertExempted("src/desktopMain/kotlin/test/desktopApi.kt", "DesktopApi")
    }

    @Test
    fun `test-named multiplatform compilation isn't fixed`() {
        val project = kmpProject(
            """
                jvm {
                    val main = compilations.getByName("main")
                    compilations.create("integrationTest") { associateWith(main) }
                }
            """
        ) {
            source(openClass("SharedApi"), "sharedApi")
            source(openClass("IntegrationHelper"), "integrationHelper", sourceSet = "jvmIntegrationTest")
        }
        val testFile = project.file("src/jvmIntegrationTest/kotlin/test/integrationHelper.kt")
        val testText = testFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileIntegrationTestKotlinJvm", null)
        project.assertExempted("src/commonMain/kotlin/test/sharedApi.kt", "SharedApi")
        assertEquals(testText, testFile.readText())
    }

    @Test
    fun `test fixtures have no explicit API mode and aren't fixed`() {
        val project = jvmProject {
            plugins(
                KotlinPlugins.jvm("$TEST_COMPILER_VERSION"),
                Plugin("java-test-fixtures"),
                Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion),
            )
            buildScript("kotlin { explicitApi() }")
            source(openClass("MainApi"), "mainApi")
            source(openClass("Fixture"), "fixture", sourceSet = "testFixtures")
        }
        val fixtureFile = project.file("src/testFixtures/kotlin/test/fixture.kt")
        val fixtureText = fixtureFile.readText()

        val result = project.build(EXEMPT_TASK)

        result.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        result.assertTaskOutcome(":compileTestFixturesKotlin", null)
        project.assertExempted("src/main/kotlin/test/mainApi.kt", "MainApi")
        assertEquals(fixtureText, fixtureFile.readText())

        project.build("compileTestFixturesKotlin").assertNoWatchdogDiagnostics()
    }

    private fun openClass(name: String): String = "/** An existing open API. */\npublic open class $name"

    private fun WatchdogBuild.assertExempted(path: String, className: String) {
        assertContains(
            file(path).readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class $className",
        )
    }

    private fun WatchdogBuild.mainSource(fileNameWithoutExtension: String): File =
        file("src/main/kotlin/test/$fileNameWithoutExtension.kt")

    private fun WatchdogBuild.report(): File =
        file("build/reports/api-watchdog/report.html")

    private companion object {
        const val EXEMPT_TASK = "exemptExistingApi"
        const val REPORT_TASK = "generateApiWatchdogReport"
        const val PLACEHOLDER_KDOC = "/** TODO: Provide kDocs for this declaration */"
        const val CONTEXT_PARAMETERS_BUILD_SCRIPT = """kotlin { compilerOptions.freeCompilerArgs.add("-Xcontext-parameters") }"""
    }
}

/** One specimen of every diagnostic that the fixer resolves. Two declarations are undocumented. */
@Suppress("RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
private val fixableFile = """
    /** A deliberately unrestricted base class. */
    public open class UnprotectedOpenClass

    public open class UndocumentedOpenClass {
        public fun undocumentedMember() {}
    }

    /** A base class whose subclass opt-in lists no markers and so restricts nothing. */
    @SubclassOptInRequired
    public open class UnmarkedOptIn

    /** A deliberately matchable enum. */
    public enum class UnmarkedEnum {
        /** The first entry. */
        A,

        /** The second entry. */
        B,
    }

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

    /** A function switched by a flag the call site puts into scope. */
    context(enabled: Boolean)
    public fun toggleContextWork() {}

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

/** The diagnostics without an exemption annotation. */
@Language("kotlin")
private val unfixableFile = """
    /** A DSL marker with tidy targets, misapplied to an inert type position below. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class ScopedDsl

    /** A parameter type carrying a DSL marker that restricts nothing. */
    public fun processTag(tag: @ScopedDsl String) { }
""".trimIndent()

/** The always-error diagnostics, which the task skips. */
@Language("kotlin")
private val alwaysErrorDiagnosticsFile = """
    import test.model.ExternalModel

    /** Marks declarations that are public only for technical reasons. */
    @InternalAnnotationMarker
    @Target(AnnotationTarget.CLASS)
    public annotation class InternalLibApi

    @InternalLibApi
    public class InternalModel

    /** Existing open API with an invalid, unfixable exemption. */
    @IntentionallyOpen
    public open class ExistingOpenApi

    /** Public entry points that expose unsupported and non-transitive types. */
    public object Api {
        /** Leaks an explicitly unsupported type. */
        public fun loadInternalModel(): InternalModel = InternalModel()

        /** Leaks a type consumers can't obtain transitively. */
        public fun loadExternalModel(): ExternalModel = ExternalModel()

        /** Carries an invalid type-use exemption handled by the dedicated type checker. */
        public fun loadMutable(): @IntentionallyMutableCollection MutableList<String> = mutableListOf()
    }
""".trimIndent()

@Language("kotlin")
private val multiplatformFile = """
    /** A shared data holder. */
    public data class SharedData(
        /** The only coordinate. */
        val x: Int,
    )
""".trimIndent()
