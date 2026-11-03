// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -NOTHING_TO_INLINE -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -BOOLEAN_PARAMETER_PUBLIC_API -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -COMPANION_API_WITHOUT_JVM_STATIC -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -PAIR_OR_TRIPLE_PUBLIC_API -MUTABLE_COLLECTION_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -MANGLED_JVM_NAME_PUBLIC_API -EXHAUSTIVE_PUBLIC_API -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS

// Shapes of the call syntax, the callee, and the declaration site. The basic shapes are in
// inlineFunction.kt.

package foo.bar

import kotlin.reflect.KClass
import kotlin.reflect.KFunction0

@PublishedApi
internal fun impl(x: Int): Int = x

@PublishedApi
internal fun impl2(a: Int, b: Int): Int = a

@PublishedApi
internal fun ready(): Boolean = true

@PublishedApi
internal fun refreshNoop() {}

@PublishedApi
internal fun transactImpl(work: () -> Unit) {
    work()
}

@PublishedApi
internal fun consume(value: Any?) {}

@PublishedApi
internal var storedLimit: Int = 0

@PublishedApi
internal val storedArray: IntArray = IntArray(1)

@PublishedApi
internal suspend fun awaitImpl(): Int = 1

public inline fun <R> once(block: () -> R): R = block()

public class Scope

@PublishedApi
internal fun scopedImpl(scope: Scope): Int = 0

context(_: Scope)
@PublishedApi
internal fun implicitScopedImpl(): Int = 0

context(scope: Scope)
public inline fun scoped(): Int = scopedImpl(scope)

context(_: Scope)
public inline fun implicitlyScoped(): Int = implicitScopedImpl()

public class Vec {
    public operator fun plus(other: Vec): Vec = this
    public operator fun get(index: Int): Int = index
    public operator fun invoke(): Int = 0
    public operator fun not(): Vec = this
}

public enum class Color { RED }

public object Registry {
    @PublishedApi
    internal fun lookup(color: Color, name: String): Int = 0
}

// An operator or infix member of a built-in type is an instruction, in operator syntax and in
// call syntax.

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>plusByName<!>(x: Int): Int = x.plus(1)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>plusBySyntax<!>(x: Int): Int = x + 1

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>incByName<!>(x: Int): Int = x.inc()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>negateByName<!>(x: Int): Int = x.unaryMinus()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>negateBySyntax<!>(x: Int): Int = -x

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>notByName<!>(b: Boolean): Boolean = b.not()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>notBySyntax<!>(b: Boolean): Boolean = !b

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>charByName<!>(s: String): Char = s.get(0)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>charBySyntax<!>(s: String): Char = s[0]

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>shifted<!>(x: Int): Int = x shl 1

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>compared<!>(x: Int): Int = x.compareTo(1)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>lessThan<!>(a: Int, b: Int): Boolean = a < b

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>equalBySyntax<!>(a: Int, b: Int): Boolean = a == b

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>equalByName<!>(a: Int, b: Int): Boolean = a.equals(b)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>both<!>(a: Boolean, b: Boolean): Boolean = a && b

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>concatenated<!>(x: Int): Unit = consume(x.toString() + "")

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>incrementedText<!>(x: Int): Unit = consume(x.inc().toString())

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>storeFirstBySyntax<!>() {
    storedArray[0] = 1
}

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>storeFirstByName<!>(): Unit = storedArray.set(0, 1)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>addToLimit<!>() {
    storedLimit += 1
}

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>incrementLimit<!>() {
    storedLimit++
}

// A user-defined operator is a regular call.

public inline fun addBySyntax(a: Vec, b: Vec): Vec = a + b

public inline fun addByName(a: Vec, b: Vec): Vec = a.plus(b)

public inline fun indexed(v: Vec): Int = v[0]

public inline fun invoked(v: Vec): Int = v()

public inline fun negated(v: Vec): Vec = !v

public inline fun paired(a: Int, b: Int): Pair<Int, Int> = a to b

public inline fun pairedByName(a: Int, b: Int): Pair<Int, Int> = a.to(b)

public inline fun ranged(a: Int, b: Int): IntRange = a until b

public inline fun widened(x: Int): Long = x.toLong()

// A standard library inline function is a regular call.

public inline fun printed(message: String): Unit = println(message)

public inline fun parsed(s: String): Int = s.toInt()

public inline fun unsigned(x: Int): UInt = x.toUInt()

public inline fun newList(): MutableList<Int> = mutableListOf()

public inline fun newArray(): Array<Int> = arrayOf(1)

public inline fun failed(message: String): Nothing = error(message)

public inline fun required(x: Int?): Int = requireNotNull(x)

public inline fun classOf(x: Any): Class<out Any> = x.javaClass

public inline fun unfinished(): Unit = TODO()

public inline fun ran(work: () -> Unit): Unit = run(work)

public inline fun identity(): Int = impl(1).let { it }

public inline fun locked(): Any = synchronized(Registry) { }

public inline fun <reified T> instances(list: List<Any>): List<T> = list.filterIsInstance<T>()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>thrown<!>(message: String): Nothing = throw IllegalStateException(message)

// An implicit `Unit` isn't logic.

public inline fun bareReturn() {
    return
}

public inline fun unitValue(): Unit = Unit

public inline fun labeledReturn(): Unit = transactImpl { return@transactImpl }

public inline fun emptyLambda(): () -> Unit = {}

public inline fun thinLambda(): () -> Int = { impl(1) }

public inline fun unitLambda(): Unit = transactImpl { impl(1) }

public inline fun expressionFunction(): Unit = transactImpl(fun(): Unit = refreshNoop())

public inline fun blockFunction(): Unit = transactImpl(fun() { refreshNoop() })

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>destructured<!>(): (Pair<Int, Int>) -> Int = { (a, b) -> impl2(a, b) }

// The compiler copies a parameter default value into every call site that omits the argument.

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>branchingDefault<!>(x: Int = if (ready()) 1 else 0): Int = impl(x)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>branchingLambdaDefault<!>(noinline block: () -> Unit = { if (ready()) refreshNoop() }): Unit = transactImpl(block)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedDefault<!>(x: Int = 1 + 1): Int = impl(x)

public inline fun delegatingDefault(x: Int = impl(1)): Int = impl(x)

public inline fun emptyLambdaDefault(noinline block: () -> Unit = {}): Unit = transactImpl(block)

// A reference to an inline function of this library copies its body.

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>onceReference<!>(): (() -> Int) -> Int = ::once

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>onceAction<!>: (() -> Int) -> Int
    get() = ::once

public inline fun refreshReference(): () -> Unit = ::refreshNoop

public inline fun readyReference(): KFunction0<Boolean> = ::ready

public inline fun classReference(x: Int): KClass<out Int> = x::class

public inline fun <reified T : Any> javaClassOf(): Class<T> = T::class.java

public inline fun <reified T> isInstance(value: Any?): Boolean = value is T

public inline fun <reified T> cast(value: Any?): T = value as T

public inline fun <reified T> safeCast(value: Any?): T? = value as? T

public class Host {
    public inline fun thinMember(): Int = impl(1)

    public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>branchingMember<!>(): Int = if (ready()) 1 else 0

    public inline val thinProperty: Int
        get() = impl(2)

    public inline val <!INLINE_FUNCTION_WITH_LOGIC!>computedProperty<!>: Int
        get() = 1 + 1

    public companion object {
        public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedCompanion<!>(): Int = 1 + 1

        public inline val <!INLINE_FUNCTION_WITH_LOGIC!>computedCompanionProperty<!>: Int
            get() = 2 + 2
    }

    public object Nested {
        public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedNested<!>(): Int = 3 + 3
    }

    public inner class Inner {
        public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedInner<!>(): Int = 6 + 6
    }
}

public object Singleton {
    public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedObject<!>(): Int = 4 + 4
}

public interface Contract {
    public companion object {
        public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedInterfaceCompanion<!>(): Int = 5 + 5
    }
}

@JvmInline
public value class Boxed(public val value: Int) {
    public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedValueMember<!>(): Int = value + 1

    public inline fun thinValueMember(): Int = impl(value)
}

public inline fun Host.thinExtension(): Int = impl(1)

public inline fun Host.<!INLINE_FUNCTION_WITH_LOGIC!>relayingExtension<!>(): Int = this.thinMember()

public inline val Host.<!INLINE_FUNCTION_WITH_LOGIC!>relayedProperty<!>: Int
    get() = this.thinMember()

public inline val Host.<!INLINE_FUNCTION_WITH_LOGIC!>relayedBranching<!>: Int
    get() = branchingMember()

public suspend inline fun awaited(): Int = awaitImpl()

public suspend inline fun awaitedBlock(block: suspend () -> Int): Int = block()

public suspend inline fun <!INLINE_FUNCTION_WITH_LOGIC!>awaitedPlusOne<!>(): Int = awaitImpl() + 1

public inline fun deeplyNested(crossinline work: () -> Unit): Unit =
    transactImpl { transactImpl { transactImpl { work() } } }

public inline fun namedLambda(crossinline work: () -> Unit): Unit = transactImpl(work = { work() })

public inline fun forwarded(noinline work: () -> Unit): Unit = transactImpl(work)

public inline fun invokedByName(work: () -> Unit): Unit = work.invoke()

public inline fun castReceiver(x: Any): Int = (x as String).length

public inline fun qualifiedArguments(): Int = Registry.lookup(Color.RED, Color.RED.name)

public inline fun chained(): Int = impl(1).toString().length

public inline fun nestedCalls(): Int = impl(impl(impl(1)))

public inline fun constant(): Int = Int.MAX_VALUE

public inline fun qualifier(): Any = Registry

public inline fun trailingSemicolon() {
    impl(1);
}

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>branchingNestedLambda<!>(crossinline work: () -> Unit): Unit =
    transactImpl { transactImpl { if (ready()) work() } }

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedReceiver<!>(): Int = (1 + 1)::toString.hashCode()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>computedArgument<!>(): Int = impl(x = 1 + 1)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>objectArgument<!>(): Unit = consume(object {})

public inline var <!INLINE_FUNCTION_WITH_LOGIC!>plusByNameSetter<!>: Int
    get() = storedLimit
    set(value) {
        storedLimit = value.plus(1)
    }

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>firstBySyntax<!>: Int
    get() = storedArray[0]

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>firstByName<!>: Int
    get() = storedArray.get(0)

public inline val referenceHash: Int
    get() = ::ready.hashCode()

public var thinInlineGetter: Int
    inline get() = storedLimit
    set(value) {
        storedLimit = if (value < 0) 0 else value
    }

public val thinAccessorMarkedInline: Int
    inline get() = impl(1)

public inline var expressionSetter: Int
    get() = impl(1)
    set(value) = consume(value)

public inline var bareReturnSetter: Int
    get() = impl(1)
    set(value) {
        return
    }
