// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -NOTHING_TO_INLINE

// The shared contract owns source-shape checks. Platform implementations own checks of bodies,
// generated members, and JVM exposure, inheriting exemptions from their matched expect.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyPairOrTriple
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutJvmOverloads

// Contract diagnostics are reported once, on the expect declaration.

public expect fun reported(
    <!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean,
): <!NULLABLE_BOOLEAN_PUBLIC_API, PAIR_OR_TRIPLE_PUBLIC_API!>Pair<String, Boolean?><!>

// Contract exemptions live on expect and need not be repeated by actual declarations.

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)
public expect fun exemptContract(flag: Boolean): Pair<String, Boolean?>

// Implementation-side exemptions are inherited from expect.

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public expect class ExemptData(value: Int) {
    public val value: Int
}

public expect class ReportedData(value: Int) {
    public val value: Int
}

@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
public expect class ExemptState(value: Int) {
    public val value: Int
}

public expect class ReportedState(value: Int) {
    public val value: Int
}

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public expect inline fun exemptInline(value: Int): Int

public expect inline fun reportedInline(value: Int): Int

// JVM checks run only on actual declarations and inherit expect-side exemptions. The actual
// default-parameter check must recover the default value from this expect signature.

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect suspend fun exemptSuspend(): Int

public expect suspend fun reportedSuspend(): Int

@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
public expect fun exemptDefault(value: Int = 1): Int

public expect fun reportedDefault(value: Int = 1): Int

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

public actual fun reported(flag: Boolean): Pair<String, Boolean?> = flag.toString() to flag

public actual fun exemptContract(flag: Boolean): Pair<String, Boolean?> = flag.toString() to flag

// A declaration added only by an actual source set is still checked as a source contract.
public fun platformOnly(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean): Int = if (flag) 1 else 0

public actual data class ExemptData public actual constructor(public actual val value: Int)

public actual data class <!DATA_CLASS_PUBLIC_API!>ReportedData<!> public actual constructor(
    public actual val value: Int,
)

public actual class ExemptState public actual constructor(public actual val value: Int)

public actual class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>ReportedState<!> public actual constructor(
    public actual val value: Int,
)

public actual inline fun exemptInline(value: Int): Int = value + 1

public actual inline fun <!INLINE_FUNCTION_WITH_LOGIC!>reportedInline<!>(value: Int): Int = value + 1

public actual suspend fun exemptSuspend(): Int = 1

public actual suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>reportedSuspend<!>(): Int = 1

public actual fun exemptDefault(value: Int): Int = value

public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>reportedDefault<!>(value: Int): Int = value
