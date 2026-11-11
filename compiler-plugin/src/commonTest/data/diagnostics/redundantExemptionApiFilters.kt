// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION: the later phase is named BACKEND before Kotlin 2.5 and CODEGEN after it
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.internal.** test.api.Hidden
// INCLUDED_NAMES: test.internal.** test.api.Hidden test.api.Watched test.api.Shell.member
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// An exemption on a declaration outside the filters is redundant, because no check reports the
// declaration without it.

// FILE: internal.kt
package test.internal

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public open class ExcludedByPackage {
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    public fun member(flag: Boolean) {}
}

// FILE: api.kt
package test.api

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public open class Hidden {
    public fun member(<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> flag: Boolean) {}
}

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Watched {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun member(flag: Boolean) {}
}

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public data class NotIncluded(val value: Int)

// The class is watched through its included member.
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Shell {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun member(flag: Boolean) {}

    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    public fun otherMember(flag: Boolean) {}
}
