// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -FINAL_UPPER_BOUND -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyPairOrTriple

public fun locate(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!> = 0 to 0

public fun dimensions(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Triple<Int, Int, Int><!> = Triple(0, 0, 0)

public fun plot(point: <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>) { }

public val origin: <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!> = 0 to 0

public var bounds: <!PAIR_OR_TRIPLE_PUBLIC_API!>Triple<Int, Int, Int><!> = Triple(0, 0, 0)

public class Anchor(public val position: <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>)

public class Plotter(seed: <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>)

// `@PublishedApi` doesn't make a declaration callable in source.
@PublishedApi
internal fun corner(): Pair<Int, Int> = 0 to 0

public fun edges(): <!PAIR_OR_TRIPLE_PUBLIC_API!>List<Pair<Int, Int>><!> = emptyList()

public typealias Point = Pair<Int, Int>

public fun aliased(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Point<!> = 0 to 0

public fun onMove(callback: <!PAIR_OR_TRIPLE_PUBLIC_API!>(Pair<Int, Int>) -> Unit<!>) { }

public fun pointFactory(): <!PAIR_OR_TRIPLE_PUBLIC_API!>() -> Pair<Int, Int><!> = { 0 to 0 }

public fun path(vararg points: <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>) { }

public fun <T : <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>> firstOf(value: T): Int = value.first

public fun <T> lastOf(value: T): Int where T : <!PAIR_OR_TRIPLE_PUBLIC_API!>Triple<Int, Int, Int><!> = value.third

public class Grid<T : <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>>

public fun <@IntentionallyPairOrTriple P : Pair<Int, Int>> render(value: P): Int = value.first

@IntentionallyPairOrTriple
public fun <T : Pair<Int, Int>> renderExempt(value: T): Int = value.first

public fun corners(): List<@IntentionallyPairOrTriple Pair<Int, Int>> = emptyList()

public fun anchorPoint(): @IntentionallyPairOrTriple Pair<Int, Int> = 0 to 0

// An extension receiver isn't reported.

public fun Pair<Int, Int>.manhattanLength(): Int = first + second

// An override isn't reported.

public interface Positioned {
    public fun position(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!>
}

public class Dot : Positioned {
    override fun position(): Pair<Int, Int> = 0 to 0
}

@IntentionallyPairOrTriple
public fun rawLocation(): Pair<Int, Int> = 0 to 0

public fun draw(@IntentionallyPairOrTriple at: Pair<Int, Int>) { }

public class Route(@IntentionallyPairOrTriple public val waypoint: Pair<Int, Int>)

public class Coordinates(public val x: Int, public val y: Int)

public fun locateNamed(): Coordinates = Coordinates(0, 0)

internal fun scratchPoint(): Pair<Int, Int> = 0 to 0

private val hidden: Pair<Int, Int> = 0 to 0
