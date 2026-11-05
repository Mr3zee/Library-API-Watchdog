// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// The `expect` compares the common overloads. Its `actual` compares only the JVM-only overloads. A
// common-only function doesn't see the JVM-only overloads, so only the JVM-only overload reports.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInconsistentParameterOrder

public expect fun pair(first: Int, second: String)

public expect fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>common<!>(first: Int, second: String)

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>common<!>(second: String, first: Int) {}

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)
public expect fun exempt(first: Int, second: String)

public fun commonOnly(first: Int, second: String) {}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

public actual fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>pair<!>(first: Int, second: String) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>pair<!>(second: String, first: Int) {}

public actual fun common(first: Int, second: String) {}

public actual fun exempt(first: Int, second: String) {}

public fun exempt(second: String, first: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>commonOnly<!>(second: String, first: Int) {}
