// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -FINAL_UPPER_BOUND -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

package foo.bar

public fun locate(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!> = 0 to 0

public var bounds: <!PAIR_OR_TRIPLE_PUBLIC_API!>Triple<Int, Int, Int><!> = Triple(0, 0, 0)

public fun plot(point: <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>) { }

public class Anchor(public val position: <!PAIR_OR_TRIPLE_PUBLIC_API!>Triple<Int, Int, Int><!>)

public fun <T : <!PAIR_OR_TRIPLE_PUBLIC_API!>Triple<Int, Int, Int><!>> lastOf(value: T): Int = value.third

public class Grid<T : <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>>
