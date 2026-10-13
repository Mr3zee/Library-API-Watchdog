// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass

public data class <!DATA_CLASS_PUBLIC_API!>Coordinates<!>(val x: Int, val y: Int)

public class Container {
    public data class <!DATA_CLASS_PUBLIC_API!>Nested<!>(val value: Int)
}

// `@PublishedApi` makes an internal declaration binary API.
@PublishedApi
internal data class <!DATA_CLASS_PUBLIC_API!>PublishedPoint<!>(val x: Int)

@IntentionallyDataClass
public data class MarkedData(val x: Int)

public class RegularClass(public val x: Int)

// A data object has no constructor properties, `copy`, or `componentN`.
public data object DataObject

internal data class InternalData(val x: Int)

private data class PrivateData(val x: Int)

public class Outer {
    internal data class Hidden(val x: Int)
}
