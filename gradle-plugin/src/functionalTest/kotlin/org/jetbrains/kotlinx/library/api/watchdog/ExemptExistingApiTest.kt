@file:Suppress("RedundantVisibilityModifier")

package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.gradle.Dependency.Companion.implementation
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.gradle.testkit.runner.TaskOutcome.SUCCESS
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KmpTarget
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.EXEMPTION_WITHOUT_EXPLANATION
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.PUBLIC_TYPE_WITH_INTERNAL_API
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.UNDOCUMENTED_PUBLIC_API
import org.jetbrains.kotlinx.library.api.watchdog.Severity.NONE
import org.jetbrains.kotlinx.library.api.watchdog.Severity.WARNING
import org.junit.Test

class ExemptExistingApiTest {
    @Test
    fun exemptTaskReusesTheConfigurationCache() {
        val project = jvmProject {
            source("/** An existing open API. */\npublic open class ExistingApi", "existingApi")
        }
        val arguments = arrayOf(EXEMPT_TASK, "--configuration-cache", "--configuration-cache-problems=fail")

        project.build(*arguments).assertOutputHas("Configuration cache entry stored.")

        val second = project.build(*arguments)
        second.assertOutputHas("Configuration cache entry reused.")
        second.assertOutputHas("No watchdog diagnostics to exempt.")
    }

    @Test
    fun fixesEveryFixableDiagnosticAndTheBuildPasses() {
        val project = jvmProject {
            buildScript(CONTEXT_PARAMETERS_BUILD_SCRIPT)
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
    fun secondRunFindsNothingLeftToExempt() {
        val project = jvmProject {
            buildScript(CONTEXT_PARAMETERS_BUILD_SCRIPT)
            source(fixableFile, "legacy")
        }

        project.build(EXEMPT_TASK)
        val fixedOnce = project.mainSource("legacy").readText()

        project.build(EXEMPT_TASK).assertOutputHas("No watchdog diagnostics to exempt.")
        assertEquals(fixedOnce, project.mainSource("legacy").readText())
    }

    @Test
    fun reportIncludesExemptionsThatAlreadyExisted() {
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
    fun reportShowsApplicableAnnotationsWithoutUpdatingSources() {
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
    fun reportShowsThePlaceholderKdocWithoutUpdatingSources() {
        val project = jvmProject { source("public class Undocumented", "undocumented") }
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
    fun sarifReportListsTheDiagnosticsWithRootRelativeLocations() {
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
    fun reportsBlockSelectsTheFormatsAndTheFiles() {
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
    fun disabledDiagnosticsAreNotExempted() {
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
    fun demotedWarningsAreExemptedAllTheSame() {
        val project = jvmProject {
            apiWatchdog { severity(WARNING, OPEN_API_WITHOUT_SUBCLASS_OPT_IN) }
            source("/** A warning-only class. */\npublic open class WarningOnly", "warningOnly")
        }

        project.build(EXEMPT_TASK)

        assertContains(
            project.mainSource("warningOnly").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class WarningOnly",
        )
    }

    @Test
    fun worksWithoutExplicitApiMode() {
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
    fun missingImportsAreAdded() {
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
    fun unfixableDiagnosticsAreReportedForManualAttention() {
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
    fun brokenCompilationFailsBeforeTheFixerAndDoesNotTouchSources() {
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
    fun unfixableAlwaysErrorDiagnosticsAreDisabledOnlyDuringTheUpdate() {
        val project = jvmProject {
            source(alwaysErrorDiagnosticsFile, "alwaysErrors", "test.consumer")
            dependencies(implementation(":model"))
            modelSubproject()
        }
        val sourceFile = project.file("src/main/kotlin/test/consumer/alwaysErrors.kt")
        val original = sourceFile.readText()

        val updateResult = project.build(EXEMPT_TASK)

        updateResult.assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)
        updateResult.assertOutputHas("No watchdog diagnostics to exempt.")
        assertEquals(original, sourceFile.readText())

        project.buildAndFail("compileKotlin").assertErrors(
            EXEMPTION_WITHOUT_EXPLANATION,
            PUBLIC_TYPE_WITH_INTERNAL_API,
            PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
        )
    }

    @Test
    fun jsOnlyProjectIsFixedThroughItsRegularCompilation() {
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
    fun nativeOnlyProjectIsFixedThroughItsRegularCompilation() {
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
    fun multiplatformJvmCompilationCoversCommonSources() {
        val project = kmpProject { source(multiplatformFile, "shared") }

        project.build(EXEMPT_TASK).assertTaskOutcome(":$EXEMPT_TASK", SUCCESS)

        assertContains(
            project.file("src/commonMain/kotlin/test/shared.kt").readText(),
            "@IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic data class SharedData",
        )

        project.build("compileKotlinJvm").assertNoWatchdogDiagnostics()
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
