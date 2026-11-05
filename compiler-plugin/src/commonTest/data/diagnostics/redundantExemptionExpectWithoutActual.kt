// RUN_PIPELINE_TILL: BACKEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -NO_ACTUAL_FOR_EXPECT -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// No `actual` declaration is visible, as in the common compilation and in the IDE on a common module.
// A check that skips `actual` declarations decides an exemption of the `expect` alone. A check that
// reports on `actual` declarations can't decide, so its exemption stays.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
public expect fun count(n: Int)

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public expect fun flag(on: Boolean)

/** Documented. */
<!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
public expect class Documented()

@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
public expect class Undocumented()

// The `actual` can be a data class.
@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public expect class PlainExpect(value: Int) {
    public val value: Int
}

// The `actual` can be a suspend function.
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect fun blocking(): Int

// The `actual` can be open.
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public expect class Final()

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public expect open class Open()
