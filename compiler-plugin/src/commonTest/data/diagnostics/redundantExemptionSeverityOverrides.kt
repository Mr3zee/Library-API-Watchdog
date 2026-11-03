// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTIC_SEVERITIES: MUTABLE_COLLECTION_PUBLIC_API:error BOOLEAN_PARAMETER_PUBLIC_API:warning KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC:none REDUNDANT_EXEMPTION:none
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -MUTABLE_COLLECTION_PUBLIC_API -BOOLEAN_PARAMETER_PUBLIC_API -MANGLED_JVM_NAME_PUBLIC_API

// A severity other than `none` keeps the check on. `REDUNDANT_EXEMPTION` isn't configurable, so the compiler ignores its override.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public fun neededBuffer(): MutableList<Int> = mutableListOf()

<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
public fun readOnlyView(): List<Int> = emptyList()

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun neededToggle(on: Boolean) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
public fun count(n: Int) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public suspend fun offCheck() {}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public class OffCheckHolder {
    public suspend fun load() {}

    <!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
    public suspend fun fetch() {}
}

@JvmInline
public value class Id(public val raw: Int)

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class MixedHolder {
    public suspend fun load() {}

    public fun id(): Id = Id(1)
}
