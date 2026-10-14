// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(x: Int, y: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>draw<!>(y: Int, x: Int, scale: Double) {}

public class <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>Rect<!>(width: Int, height: Int) {
    public <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>constructor<!>(height: Int, width: Int, scale: Double) : this(width, height)
}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>insertMiddle<!>(a: Int, b: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>insertMiddle<!>(a: Int, c: Int, b: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>rotate<!>(a: Int, b: Int, c: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>rotate<!>(c: Int, a: Int, b: Int, d: Int) {}

public class <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>MiddleConstructor<!>(a: Int, b: Int) {
    public <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>constructor<!>(a: Int, c: Int, b: Int) : this(a, b)
}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>scramble<!>(a: Int, b: String, c: Long, d: Double, e: Char, f: Float) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>scramble<!>(e: Char, c: Long, a: Int, extra: Short, f: Float, b: String, d: Double) {}
