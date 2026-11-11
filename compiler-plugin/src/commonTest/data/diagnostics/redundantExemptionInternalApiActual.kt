// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -BOOLEAN_PARAMETER_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// An internal API annotation on the file or on an enclosing class of an `actual` declaration
// removes the `actual` declaration from the checks. An exemption of the `expect` declaration that
// only the `actual` declaration reads is then redundant.

// MODULE: common
// FILE: common.kt

package test

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutJvmOverloads

/** Marks internal API. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
public annotation class Hidden

<!REDUNDANT_EXEMPTION!>@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)<!>
public expect fun encode(v: Int, compact: Boolean = false)

public expect class Outer() {
    public class Inner() {
        <!REDUNDANT_EXEMPTION!>@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)<!>
        public fun encode(v: Int, compact: Boolean = false)
    }
}

@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
public expect fun decode(v: Int, compact: Boolean = false)

// MODULE: jvm()()(common)
// FILE: hiddenFile.kt
@file:Hidden

package test

public actual fun encode(v: Int, compact: Boolean) {}

// FILE: jvm.kt

package test

@Hidden
public actual class Outer {
    public actual class Inner {
        public actual fun encode(v: Int, compact: Boolean) {}
    }
}

public actual fun decode(v: Int, compact: Boolean) {}
