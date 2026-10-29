// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -DATA_CLASS_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -BOOLEAN_PARAMETER_PUBLIC_API

// The probe skips an `expect` and an `actual` declaration. The checks split between them, and
// the exemption on the `expect` also serves the `actual`.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

// No check needs these exemptions, but the declarations are `expect`.

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public expect class PlainExpect(value: Int) {
    public val value: Int
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect fun blocking(): Int

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public expect fun count(n: Int)

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

public actual class PlainExpect public actual constructor(public actual val value: Int)

// The `expect` has the same exemption, but the `actual` isn't probed either.
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public actual fun blocking(): Int = 1

public actual fun count(n: Int) {}

// A declaration without an `expect` is probed as usual.
<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
public fun platformCount(n: Int) {}
