// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// INCLUDED_ANNOTATIONS: test.PublicApi
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -BOOLEAN_PARAMETER_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// The redundancy check reads an exemption of an `expect` declaration through its `actual`
// declaration. The file and the enclosing classes of the `actual` declaration decide whether the
// checks see the `actual` declaration.

// MODULE: common
// FILE: common.kt

package test

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutJvmOverloads

/** Includes a declaration. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
public annotation class PublicApi

// The `actual` declaration is in a file with the inclusion annotation.
@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
public expect fun encode(v: Int, compact: Boolean = false)

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect class Loader() {
    public suspend fun load(): Int
}

// The class two levels above the `actual` member has the inclusion annotation.
public expect class Outer() {
    public class Inner() {
        @IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
        public fun encode(v: Int, compact: Boolean = false)
    }
}

// No inclusion rule matches the `actual` declaration.
<!REDUNDANT_EXEMPTION!>@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)<!>
public expect fun decode(v: Int, compact: Boolean = false)

// MODULE: jvm()()(common)
// FILE: annotatedFile.kt
@file:PublicApi

package test

public actual fun encode(v: Int, compact: Boolean) {}

public actual class Loader {
    public actual suspend fun load(): Int = 0
}

// FILE: jvm.kt

package test

@PublicApi
public actual class Outer {
    public actual class Inner {
        public actual fun encode(v: Int, compact: Boolean) {}
    }
}

public actual fun decode(v: Int, compact: Boolean) {}
