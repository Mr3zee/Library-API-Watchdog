// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>drawGroup<!>(x: Int, y: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>drawGroup<!>(x: Int, y: Int, scale: Double) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>drawGroup<!>(y: Int, x: Int, color: Long) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>rotateGroup<!>(first: Int, second: Int, third: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>rotateGroup<!>(third: Int, first: Int, second: Int, padding: Long) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>rotateGroup<!>(second: Int, third: Int, first: Int, padding: Double) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>insertGroup<!>(sourcePath: String, destinationPath: String) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>insertGroup<!>(sourcePath: String, mode: Int, destinationPath: String) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>insertGroup<!>(sourcePath: String, mode: Int, flags: Int, destinationPath: String) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>insertGroup<!>(sourcePath: String, destinationPath: String, suffix: Double) {}

public class <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>ConstructorGroup<!>(width: Int, height: Int) {
    public <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>constructor<!>(height: Int, width: Int, scale: Double) : this(width, height)

    public <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>constructor<!>(width: Int, scale: Long, height: Int) : this(width, height)
}

public fun stableGroup(first: Int, second: Int) {}

public fun stableGroup(first: Int, second: Int, third: Int) {}

public fun stableGroup(first: Int, second: Int, third: Int, fourth: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>ordinalPositions<!>(
    p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int, p7: Int,
    p8: Int, p9: Int, p10: Int, p11: Int, p12: Int, p13: Int,
) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>ordinalPositions<!>(
    p13: Int, p12: Int, p11: Int, p10: Int, p9: Int, p8: Int, p7: Int,
    p6: Int, p5: Int, p4: Int, p3: Int, p2: Int, p1: Int, extra: String,
) {}
