@file:Suppress("RedundantVisibilityModifier")

package org.jetbrains.kotlinx.libs.api.watchdog

import com.autonomousapps.kit.GradleBuilder.build
import com.autonomousapps.kit.GradleBuilder.buildAndFail
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KmpTarget
import org.junit.Test

class UpdateBackwardsCompatibilityExemptsTest {
    @Test
    fun fixesEveryFixableDiagnosticAndTheBuildPasses() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(fixableFile, "legacy"))
        }.gradleProject

        val updateResult = build(project.rootDir, UPDATE_TASK)
        assertEquals(TaskOutcome.SUCCESS, updateResult.task(":$UPDATE_TASK")?.outcome)
        assertContains(updateResult.output, "backwards-compatibility exemption(s)")

        val fixedText = project.rootDir.mainSource("legacy").readText()
        val reason = "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY"
        assertTrue(fixedText.startsWith("@file:IntentionallyDefaultFacadeName($reason)"))
        assertContains(fixedText, "@IntentionallyOpen($reason)\npublic open class UnprotectedOpenClass")
        // The markerless @SubclassOptInRequired is replaced, not joined, by the exemption.
        assertContains(fixedText, "@IntentionallyOpen($reason)\npublic open class UnmarkedOptIn")
        assertFalse(fixedText.contains("@SubclassOptInRequired"))
        assertContains(fixedText, "@IntentionallyExhaustive($reason)\npublic enum class UnmarkedEnum")
        assertContains(fixedText, "@IntentionallyFunctionTypeAlias($reason)\npublic typealias UnacknowledgedCallback")
        assertContains(fixedText, "@IntentionallyDataClass($reason)\npublic data class UnmarkedData")
        assertContains(fixedText, "@IntentionallyWithoutToString($reason)\npublic class UnrenderedSession")
        assertContains(fixedText, "@IntentionallyMutableCollection($reason)\npublic fun leakState()")
        assertContains(fixedText, "@IntentionallyPairOrTriple($reason)\npublic fun locateOrigin()")
        assertContains(fixedText, "@IntentionallyBooleanParameter($reason)\npublic fun toggleWork(enabled: Boolean)")
        assertContains(fixedText, "@IntentionallyBooleanParameter($reason)\ncontext(enabled: Boolean)\npublic fun toggleContextWork()")
        assertContains(fixedText, "@IntentionallyNullableBoolean($reason)\npublic fun lastKnownState()")
        assertContains(fixedText, "@IntentionallyRequiredParameterAfterOptional($reason)\n@IntentionallyWithoutJvmOverloads($reason)\npublic fun retryWork",)
        assertContains(fixedText, "@IntentionallyInconsistentParameterOrder($reason)\npublic fun drawShape(y: Int, x: Int, scale: Double)")
        assertContains(fixedText, "@IntentionallyInlinedLogic($reason)\n@Suppress(\"NOTHING_TO_INLINE\")\npublic inline fun squared")
        assertContains(fixedText, "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility\n@DslMarker\n@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)\npublic annotation class NoopTargetDsl")
        assertContains(fixedText, "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility\n@DslMarker\npublic annotation class TargetlessDsl")
        assertContains(fixedText, "@IntentionallyMangledJvmName($reason)\npublic fun findUser")
        assertContains(fixedText, "@IntentionallyNonStaticCompanionApi($reason)\n        public fun instance()")
        assertContains(fixedText, "@IntentionallyNonStaticCompanionApi($reason)\n        public val DEFAULT_LABEL")
        assertContains(fixedText, "@IntentionallyKotlinOnlyApi($reason)\npublic suspend fun refreshState()")
        assertContains(fixedText, "@IntentionallyWithoutJvmOverloads($reason)\npublic fun openPort")

        // The proof of the fixes: the same strict build that would have failed now passes.
        val buildResult = build(project.rootDir, "build")
        buildResult.assertNoWatchdogDiagnostics()
    }

    @Test
    fun secondRunFindsNothingLeftToExempt() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(fixableFile, "legacy"))
        }.gradleProject

        build(project.rootDir, UPDATE_TASK)
        val fixedOnce = project.rootDir.mainSource("legacy").readText()

        val secondRun = build(project.rootDir, UPDATE_TASK)
        assertContains(secondRun.output, "No watchdog diagnostics to exempt.")
        assertEquals(fixedOnce, project.rootDir.mainSource("legacy").readText())
    }

    @Test
    fun disabledDiagnosticsAreNotExempted() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    undocumentedPublicApi = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.NONE
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source("public open class UndocumentedOpen", "legacy"))
        }.gradleProject

        build(project.rootDir, UPDATE_TASK)

        val fixedText = project.rootDir.mainSource("legacy").readText()
        assertFalse(fixedText.contains("@IntentionallyUndocumented"))
        // The other diagnostics are still exempted.
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun demotedWarningsAreExemptedAllTheSame() {
        val project = object : WatchdogProject(
            extraBuildScript = """
                apiWatchdog {
                    openApiWithoutSubclassOptIn = org.jetbrains.kotlinx.libs.api.watchdog.WatchdogSeverity.WARNING
                }
            """.trimIndent(),
        ) {
            override fun sources() = listOf(source("/** A warning-only class. */\npublic open class WarningOnly", "warningOnly"))
        }.gradleProject

        build(project.rootDir, UPDATE_TASK)

        assertContains(
            project.rootDir.mainSource("warningOnly").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class WarningOnly",
        )
    }

    @Test
    fun worksWithoutExplicitApiMode() {
        // The task forces -Xexplicit-api=warning for its analysis, so a library that has not
        // adopted explicit API mode yet can still prepare its exemptions ahead of time.
        val project = object : WatchdogProject(
            explicitApi = false,
        ) {
            override fun sources() = listOf(source("/** An open class. */\npublic open class Open", "open"))
        }.gradleProject

        build(project.rootDir, UPDATE_TASK)

        assertContains(
            project.rootDir.mainSource("open").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Open",
        )
    }

    @Test
    fun missingImportsAreAdded() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(
                source(
                    "/** A bare open class. */\npublic open class Bare",
                    "bare",
                    includeDefaultImports = false,
                )
            )
        }.gradleProject

        build(project.rootDir, UPDATE_TASK)

        val fixedText = project.rootDir.mainSource("bare").readText()
        assertContains(fixedText, "import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason")
        assertContains(fixedText, "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen")
        assertContains(fixedText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")

        val buildResult = build(project.rootDir, "build")
        buildResult.assertNoWatchdogDiagnostics()
    }

    @Test
    fun unfixableDiagnosticsAreReportedForManualAttention() {
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(unfixableFile, "manual"))
        }.gradleProject

        val result = build(project.rootDir, UPDATE_TASK)

        assertContains(result.output, "needs manual attention")
        assertContains(result.output, "DSL_MARKER_NOOP_TYPE_POSITION")
        assertContains(result.output, "UNDOCUMENTED_PUBLIC_API")
    }

    @Test
    fun brokenCompilationFailsBeforeTheFixerAndDoesNotTouchSources() {
        val brokenSource = "internal fun broken() { thisCallDoesNotResolve() }"
        val project = object : WatchdogProject() {
            override fun sources() = listOf(source(brokenSource, "broken"))
        }.gradleProject
        val original = project.rootDir.mainSource("broken").readText()

        val result = buildAndFail(project.rootDir, UPDATE_TASK)

        assertContains(result.output, "thisCallDoesNotResolve")
        assertEquals(null, result.task(":$UPDATE_TASK")?.outcome)
        assertEquals(original, project.rootDir.mainSource("broken").readText())
    }

    @Test
    fun jsOnlyProjectIsFixedThroughItsRegularCompilation() {
        val project = object : WatchdogProject(
            multiplatform = true,
        ) {
            override fun multiplatformTargetsBlock(): String = "kotlin {\n  js { nodejs() }\n}\n"
            override fun sources() = listOf(source("/** A JS class. */\npublic open class JsOnly", "jsOnly"))
        }.gradleProject

        val result = build(project.rootDir, UPDATE_TASK)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$UPDATE_TASK")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":compileKotlinJs")?.outcome)
        assertContains(
            project.rootDir.resolve("src/commonMain/kotlin/test/jsOnly.kt").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public open class JsOnly",
        )
    }

    @Test
    fun nativeOnlyProjectIsFixedThroughItsRegularCompilation() {
        val native = KmpTarget.NATIVE_HOST
        val project = object : WatchdogProject(
            multiplatform = true,
        ) {
            override fun multiplatformTargetsBlock(): String =
                "kotlin {\n  ${native.gradleTargetName}()\n}\n"

            override fun sources() = listOf(source("/** A native class. */\npublic open class NativeOnly", "nativeOnly"))
        }.gradleProject

        val result = build(project.rootDir, UPDATE_TASK)

        assertEquals(TaskOutcome.SUCCESS, result.task(":$UPDATE_TASK")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":${native.compileTaskName}")?.outcome)
        assertContains(
            project.rootDir.resolve("src/commonMain/kotlin/test/nativeOnly.kt").readText(),
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public open class NativeOnly",
        )
    }

    @Test
    fun multiplatformJvmCompilationCoversCommonSources() {
        val project = object : WatchdogProject(
            multiplatform = true,
        ) {
            override fun multiplatformTargetsBlock(): String = "kotlin {\n  jvm()\n}\n"
            override fun sources() = listOf(source(multiplatformFile, "shared"))
        }.gradleProject

        val result = build(project.rootDir, UPDATE_TASK)
        assertEquals(TaskOutcome.SUCCESS, result.task(":$UPDATE_TASK")?.outcome)

        val fixedText = project.rootDir.resolve("src/commonMain/kotlin/test/shared.kt").readText()
        assertContains(
            fixedText,
            "@IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic data class SharedData",
        )

        // The JVM compilation of the fixed sources passes with the watchdog active.
        val compileResult = build(project.rootDir, "compileKotlinJvm")
        compileResult.assertNoWatchdogDiagnostics()
    }

    private fun File.mainSource(fileNameWithoutExtension: String): File =
        resolve("src/main/kotlin/test/$fileNameWithoutExtension.kt")

    private fun BuildResult.assertNoWatchdogDiagnostics() {
        WATCHDOG_MESSAGES.forEach { message ->
            assertFalse(output.contains(message), "Expected no '$message' diagnostic in:\n$output")
        }
    }

    private companion object {
        const val UPDATE_TASK = "updateBackwardsCompatibilityExempts"

        val WATCHDOG_MESSAGES = listOf(
            "can be subclassed outside the library",
            "can be matched exhaustively by users",
            "has no KDoc",
            "abbreviates a function type",
            "bakes its constructor property list",
            "neither declares nor inherits a `toString`",
            "exposes the mutable collection type",
            "exposes the tuple type",
            "takes the Boolean parameter",
            "exposes a nullable Boolean",
            "is required but declared after an optional parameter",
            "appear in the opposite order in another overload",
            "does more than delegate to a non-inline function",
            "compiled JVM name is mangled",
            "still lands in the API surface Java sources see",
            "nested Companion class",
            "compile into the facade class",
            "declares default parameter values",
            "allows the FUNCTION annotation target",
            "declares no explicit @Target",
            "exemption doesn't explain why it is applied",
        )
    }
}

/**
 * One specimen of every diagnostic the fixer can acknowledge automatically. Every declaration is
 * documented because `UNDOCUMENTED_PUBLIC_API` requires manual attention.
 */
@Suppress("RedundantVisibilityModifier", "RedundantSuspendModifier", "MayBeConstant")
@Language("kotlin")
private val fixableFile = """
    /** A deliberately unrestricted base class. */
    public open class UnprotectedOpenClass

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

/** The diagnostics no annotation can acknowledge: they stay for a human to resolve. */
@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val unfixableFile = """
    public class NeedsDocumentation

    /** A DSL marker with tidy targets, misapplied to an inert type position below. */
    @DslMarker
    @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
    public annotation class ScopedDsl

    /** A parameter type carrying a DSL marker that restricts nothing. */
    public fun processTag(tag: @ScopedDsl String) { }
""".trimIndent()

@Suppress("RedundantVisibilityModifier")
@Language("kotlin")
private val multiplatformFile = """
    /**
     * A shared data holder.
     *
     * @param x the only coordinate.
     */
    public data class SharedData(val x: Int)
""".trimIndent()
