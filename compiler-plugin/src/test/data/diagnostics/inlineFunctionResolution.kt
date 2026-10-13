// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -NOTHING_TO_INLINE -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -BOOLEAN_PARAMETER_PUBLIC_API -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -COMPANION_API_WITHOUT_JVM_STATIC -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -PAIR_OR_TRIPLE_PUBLIC_API -MUTABLE_COLLECTION_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -MANGLED_JVM_NAME_PUBLIC_API -EXHAUSTIVE_PUBLIC_API -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -USELESS_CAST -UNCHECKED_CAST -FINAL_UPPER_BOUND

// Shapes where the classification depends on what a call resolves to. The syntax shapes are in
// inlineFunctionShapes.kt.

package foo.bar

import kotlin.reflect.KFunction0
import kotlin.reflect.KProperty0

@PublishedApi
internal fun impl(x: Int): Int = x

@PublishedApi
internal fun impl0(): Int = 0

@PublishedApi
internal fun implAny(x: Any?): Int = 0

@PublishedApi
internal fun runInt(block: () -> Int): Int = block()

@PublishedApi
internal fun useBool(b: Boolean): Int = 0

@PublishedApi
internal fun useVecBlock(block: Vec.() -> Int): Int = 0

@PublishedApi
internal val plainAction: () -> Int = ::impl0

public inline val inlineAction: () -> Int
    get() = ::impl0

public inline val thresholdInline: Int
    get() = impl(1)

public class Vec {
    public operator fun get(index: Int): Int = index
}

public class Grid {
    public inline operator fun <!INLINE_FUNCTION_WITH_LOGIC!>get<!>(index: Int): Int = index + 1
}

public class Op {
    public inline operator fun <!INLINE_FUNCTION_WITH_LOGIC!>invoke<!>(): Int = 1 + 1
}

public class Host {
    public inline fun thinMember(): Int = impl(1)

    public companion object {
        public operator fun invoke(): Host = Host()
    }
}

public class Money(public val amount: Int) : Comparable<Money> {
    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)
}

public enum class Color { RED }

// An implicit `invoke` or an index access resolves to the callee's declaration. An inline getter,
// an inline `invoke`, or an inline `get` copies: should warn. Non-inline ones delegate.

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>invokeInlineAction<!>(): Int = inlineAction()

public inline fun invokePlainAction(): Int = plainAction()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>invokeInlineOperator<!>(op: Op): Int = op()

public inline fun invokeCompanion(): Host = Host()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>indexInlineGet<!>(grid: Grid): Int = grid[0]

// Nullable receivers and casts.

public inline fun String?.nullableReceiver(): Int = implAny(this)

public inline fun String?.<!INLINE_FUNCTION_WITH_LOGIC!>safeCallReceiver<!>(): Int = this?.length ?: 0

public inline fun checkedNotNull(x: Any?): Int = checkNotNull(x).hashCode()

public inline fun <T : Any> <!INLINE_FUNCTION_WITH_LOGIC!>forced<!>(x: T?): T = x!!

public inline fun <reified T> castToReified(x: Any?): Int = implAny(x as T)

// Labeled and implicit `this` in lambdas and anonymous functions. No warning.

public inline fun Vec.labeledThis(): Int = runInt { this@labeledThis[0] }

public inline fun String.labeledThisLength(): Int = runInt { this@labeledThisLength.length }

public inline fun receiverLambda(): Int = useVecBlock { this[0] }

public inline fun anonymousReceiver(): Int = useVecBlock(fun Vec.(): Int = this[0])

// A comparison resolves to `compareTo`. On a `Comparable` it is an interface call, on a built-in
// type it is an instruction.

public inline fun <C : Comparable<C>> genericLess(a: C, b: C): Int = useBool(a < b)

public inline fun <C : Comparable<C>> genericCompareTo(a: C, b: C): Int = a.compareTo(b)

public inline fun moneyLess(a: Money, b: Money): Int = useBool(a < b)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>anyEquals<!>(a: Any, b: Any): Int = useBool(a.equals(b))

// Built-in operators on unsigned types, characters, and arrays: should warn. Collection operators
// are interface calls, and a negative literal is a constant. No warning.

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>unsignedPlus<!>(a: UInt, b: UInt): UInt = a + b

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>charPlus<!>(c: Char): Char = c + 1

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>arrayGet<!>(array: Array<String>): String = array[0]

public inline fun arraySize(array: Array<String>): Int = array.size

public inline fun arrayFirst(array: Array<String>): String = array.first()

public inline fun listGet(list: List<Int>): Int = list[0]

public inline fun mapGet(map: Map<String, Int>): Int? = map["k"]

public inline fun negativeLiteral(): Int = impl(-1)

public inline fun absolute(): Int = kotlin.math.abs(-1)

// A reference to an inline property or an inline member of this library: should warn.

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>inlinePropertyReference<!>(): KProperty0<Int> = ::thresholdInline

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>inlineMemberReference<!>(host: Host): KFunction0<Int> = host::thinMember

public inline fun plainReference(): KFunction0<Int> = ::impl0

public inline fun referenceArgument(x: Int): Int = x.let(::impl)

// Java static calls, varargs, synthetic properties, enum and companion members. No warning.

public inline fun javaStatic(): Int = Integer.valueOf(1)

public inline fun javaVararg(): Int = java.util.Objects.hash(1, 2)

public inline fun javaFormat(): Int = String.format("%d", 1).length

public inline fun javaSynthetic(): Int = StringBuilder().length

public inline fun javaList(): Int = java.util.Arrays.asList(1, 2).size

public inline fun enumOrdinal(): Int = Color.RED.ordinal

public inline fun enumEntries(): Int = Color.entries.size

public inline fun companionReference(): Host.Companion = Host.Companion
