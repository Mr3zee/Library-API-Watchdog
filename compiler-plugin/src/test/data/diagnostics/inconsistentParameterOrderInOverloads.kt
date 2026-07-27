// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyInconsistentParameterOrder

// Overloads disagreeing on the relative order of shared parameter names: no order is preferred
// as canonical, so both members of the pair warn, and reordering either clears both.

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(x: Int, y: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(y: Int, x: Int, scale: Double) {}

// Consistent overloads stay silent - including conversion overloads, where the same parameter
// names deliberately take different types.

public fun move(x: Int, y: Int) {}

public fun move(x: Int, y: Int, z: Int) {}

public fun move(x: Long, y: Long) {}

// Fewer than two shared names can disagree on order.

public fun log(message: String) {}

public fun log(tag: String, code: Int) {}

// Members of one class body are compared with each other.

public class Canvas {
    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(startIndex: Int, endIndex: Int) {}

    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>fill<!>(endIndex: Int, startIndex: Int, color: Long) {}
}

// Constructors of a class are overloads of each other.

public class Rect<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>(width: Int, height: Int)<!> {
    <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>public constructor(height: Int, width: Int, scale: Double) : this(width, height)<!>
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
