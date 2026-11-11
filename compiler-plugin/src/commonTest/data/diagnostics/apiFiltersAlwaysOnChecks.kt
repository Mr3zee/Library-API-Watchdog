// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.internal.**
// INCLUDED_NAMES: test.api.**
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

// The exemption explanation check and the DSL marker checks run on every declaration, so the
// filters don't turn them off.

// FILE: internal.kt
package test.internal

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

<!EXEMPTION_WITHOUT_EXPLANATION, REDUNDANT_EXEMPTION!>@IntentionallyOpen<!>
public open class Excluded

@DslMarker
public annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>ExcludedDefaultTargetsDsl<!>

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FUNCTION<!>)
public annotation class ExcludedDsl

public open class ExcludedTag

public fun excludedProcess(tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@ExcludedDsl<!> ExcludedTag) {}

// FILE: other.kt
package test.other

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

<!EXEMPTION_WITHOUT_EXPLANATION, REDUNDANT_EXEMPTION!>@IntentionallyOpen<!>
public open class NotIncluded

@DslMarker
public annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>NotIncludedDefaultTargetsDsl<!>

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FUNCTION<!>)
public annotation class NotIncludedDsl

public open class NotIncludedTag

public fun notIncludedProcess(tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@NotIncludedDsl<!> NotIncludedTag) {}
