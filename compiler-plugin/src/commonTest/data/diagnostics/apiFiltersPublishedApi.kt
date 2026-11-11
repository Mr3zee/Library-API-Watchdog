// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.excluded.** test.included.*.excludedMember
// INCLUDED_NAMES: test.excluded.** test.included.publishedFunction test.included.PublishedData test.included.*.publishedMember test.included.*.excludedMember
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -NOTHING_TO_INLINE

// The filters apply to a `@PublishedApi internal` declaration as to a public declaration. The
// checks of the binary API report the included ones.

// FILE: excluded.kt
package test.excluded

@PublishedApi
internal inline fun publishedFunction(): Int {
    var i = 0
    i += 1
    return i
}

@PublishedApi
internal data class PublishedData(val value: Int)

// FILE: included.kt
package test.included

@PublishedApi
internal inline fun <!INLINE_FUNCTION_WITH_LOGIC!>publishedFunction<!>(): Int {
    var i = 0
    i += 1
    return i
}

@PublishedApi
internal inline fun notIncluded(): Int {
    var i = 0
    i += 1
    return i
}

@PublishedApi
internal data class <!DATA_CLASS_PUBLIC_API!>PublishedData<!>(val value: Int)

@PublishedApi
internal data class NotIncludedData(val value: Int)

// An included `@PublishedApi internal` member includes its class.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>Shell<!> {
    @PublishedApi
    internal inline fun <!INLINE_FUNCTION_WITH_LOGIC!>publishedMember<!>(): Int {
        var i = 0
        i += 1
        return i
    }

    @PublishedApi
    internal inline fun excludedMember(): Int {
        var i = 0
        i += 1
        return i
    }

    public fun other(flag: Boolean) {}
}

// An excluded member doesn't include its class.
public open class ExcludedShell {
    @PublishedApi
    internal inline fun excludedMember(): Int {
        var i = 0
        i += 1
        return i
    }
}
