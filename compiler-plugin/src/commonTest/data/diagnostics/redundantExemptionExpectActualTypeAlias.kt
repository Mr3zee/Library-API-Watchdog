// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// The members of the class that an `actual typealias` expands to don't inherit the exemptions of
// the `expect` members, so only the checks that report the `expect` need them.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

internal expect class Internal {
    public fun flag(<!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> on: Boolean)

    public class Nested {
        public fun flag(<!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> on: Boolean)
    }
}

public expect class Public {
    public fun flag(@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN) on: Boolean)

    <!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
    public suspend fun load(): Int
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

internal actual typealias Internal = InternalImpl

internal class InternalImpl {
    public fun flag(on: Boolean) {}

    public class Nested {
        public fun flag(on: Boolean) {}
    }
}

public actual typealias Public = PublicImpl

public class PublicImpl {
    public fun flag(<!BOOLEAN_PARAMETER_PUBLIC_API!>on<!>: Boolean) {}

    public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>load<!>(): Int = 0
}
