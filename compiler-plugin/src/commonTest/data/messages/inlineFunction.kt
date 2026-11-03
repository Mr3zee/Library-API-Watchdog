// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -NOTHING_TO_INLINE

package foo.bar

@PublishedApi
internal var storedLimit: Int = 0

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>doubled<!>(value: Int): Int = value + value

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>threshold<!>: Int
    get() = 1 + 1

public inline var <!INLINE_FUNCTION_WITH_LOGIC!>clampedLimit<!>: Int
    get() = storedLimit
    set(value) {
        storedLimit = if (value < 0) 0 else value
    }
