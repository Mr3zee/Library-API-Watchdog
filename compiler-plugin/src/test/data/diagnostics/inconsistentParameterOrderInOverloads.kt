// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: overloads.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInconsistentParameterOrder

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(x: Int, y: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(y: Int, x: Int, scale: Double) {}

public fun move(x: Int, y: Int) {}

public fun move(x: Int, y: Int, z: Int) {}

public fun move(x: Long, y: Long) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>arrange<!>(
    prefix: String,
    first: Int,
    middle: Long,
    second: Int,
) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>arrange<!>(
    second: Int,
    suffix: String,
    first: Int,
    scale: Double,
) {}

public fun align(first: Int, separator: String, second: Int) {}

public fun align(prefix: String, first: Long, second: Long, suffix: Double) {}

public fun log(message: String) {}

public fun log(tag: String, code: Int) {}

public class Canvas {
    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(startIndex: Int, endIndex: Int) {}

    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, color: Long) {}
}

public class <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>Rect<!>(width: Int, height: Int) {
    public <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>constructor<!>(height: Int, width: Int, scale: Double) : this(width, height)
}

// A member doesn't overload a top-level function with the same name.

public class Turtle {
    public fun draw(y: Int, x: Int) {}
}

// An inherited overload is a reference. Only the declaration in the subtype is reported.

public interface Shape {
    public fun place(x: Int, y: Int) {}
}

public class Widget : Shape {
    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, scale: Double) {}
}

// An override isn't reported.

public class Panel : Shape {
    override fun place(x: Int, y: Int) {}

    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, scale: Double) {}
}

// An exempt overload isn't a reference.

public fun render(x: Int, y: Int) {}

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public fun render(y: Int, x: Int, alpha: Long) {}

public fun render(x: Int, y: Int, scale: Double) {}

// A non-public or `@PublishedApi` overload isn't a reference.

private fun helper(first: Int, second: Int) {}

public fun helper(second: Int, first: Int, extra: Long) {}

public fun publishedSibling(first: Int, second: Int) {}

@PublishedApi
internal fun publishedSibling(second: Int, first: Int, extra: Long) {}

@PublishedApi
internal fun publishedPair(first: Int, second: Int) {}

@PublishedApi
internal fun publishedPair(second: Int, first: Int, extra: Long) {}

// The members of the receiver are overloads of an extension. Only the extension is reported.

public class Grid {
    public fun fill(startIndex: Int, endIndex: Int) {}
}

public fun Grid.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, color: Long) {}

public fun Widget.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, alpha: Long) {}

public typealias Board = Grid

public fun Board?.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, alpha: Long) {}

public fun <T : Shape> T.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, tint: Int) {}

public fun <T> T.fill(endIndex: Int, startIndex: Int, gamma: Int) {}

// FILE: extensions.kt

package foo.baz

import foo.bar.Grid

public fun Grid.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, beta: Int) {}
