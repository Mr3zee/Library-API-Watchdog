// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: overloads.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInconsistentParameterOrder

// Overloads disagreeing on the relative order of shared parameter names: no order is preferred
// as canonical, so both members of the pair warn, and reordering either clears both.

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(x: Int, y: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(y: Int, x: Int, scale: Double) {}

// Consistent overloads stay silent - including conversion overloads, where the same parameter
// names deliberately take different types.

public fun move(x: Int, y: Int) {}

public fun move(x: Int, y: Int, z: Int) {}

public fun move(x: Long, y: Long) {}

// Parameters unique to either overload may be interleaved without affecting the relative-order
// comparison of the names they share.

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

// Fewer than two shared names can disagree on order.

public fun log(message: String) {}

public fun log(tag: String, code: Int) {}

// Members of one class body are compared with each other.

public class Canvas {
    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(startIndex: Int, endIndex: Int) {}

    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, color: Long) {}
}

// Constructors of a class are overloads of each other.

public class <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>Rect<!>(width: Int, height: Int) {
    public <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>constructor<!>(height: Int, width: Int, scale: Double) : this(width, height)
}

// A member doesn't overload a same-named top-level function.

public class Turtle {
    public fun draw(y: Int, x: Int) {}
}

// An inherited overload is an ordering reference too: users see it side by side with the
// declared ones. Only the subtype's declaration warns - the supertype can see it.

public interface Shape {
    public fun place(x: Int, y: Int) {}
}

public class Widget : Shape {
    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, scale: Double) {}
}

// Overrides never warn - their order is fixed by the overridden declaration - but a new
// overload declared next to one must still follow it.

public class Panel : Shape {
    override fun place(x: Int, y: Int) {}

    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, scale: Double) {}
}

// An exempt overload neither warns nor serves as an ordering reference.

public fun render(x: Int, y: Int) {}

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public fun render(y: Int, x: Int, alpha: Long) {}

public fun render(x: Int, y: Int, scale: Double) {}

// Overloads hidden from users are not compared in either direction.

private fun helper(first: Int, second: Int) {}

public fun helper(second: Int, first: Int, extra: Long) {}

// A @PublishedApi overload is also hidden from source users and is not a comparison reference.

public fun publishedSibling(first: Int, second: Int) {}

@PublishedApi
internal fun publishedSibling(second: Int, first: Int, extra: Long) {}

@PublishedApi
internal fun publishedPair(first: Int, second: Int) {}

@PublishedApi
internal fun publishedPair(second: Int, first: Int, extra: Long) {}

// An extension is called like a member of the type it extends, so the receiver's members are
// overloads of it. Only the extension warns - the class can't see the extensions declared on
// it, and it is the extension that strays from the established order.

public class Grid {
    public fun fill(startIndex: Int, endIndex: Int) {}
}

public fun Grid.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, color: Long) {}

// The receiver's inherited members are references too: users see them on the receiver just the same.

public fun Widget.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, alpha: Long) {}

// A receiver reached through a type alias, a nullable type, or a type parameter bound still
// leads back to the extended class.

public typealias Board = Grid

public fun Board?.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, alpha: Long) {}

public fun <T : Shape> T.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, tint: Int) {}

// A receiver that is no class - an unbounded type parameter - has no members to compare against.

public fun <T> T.fill(endIndex: Int, startIndex: Int, gamma: Int) {}

// FILE: extensions.kt

// An extension is compared with its receiver's members wherever in the module it is declared.

package foo.baz

import foo.bar.Grid

public fun Grid.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, beta: Int) {}
