// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -NOTHING_TO_INLINE -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass
import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic

@PublishedApi
internal fun logImpl(tag: String, message: String) {}

@PublishedApi
internal fun logAllImpl(vararg messages: String) {}

@PublishedApi
internal fun length(tag: String): Int = tag.length

@PublishedApi
internal fun lengthOrNull(tag: String): Int? = null

@PublishedApi
internal fun consumeImpl(value: Any?) {}

@PublishedApi
internal fun posImpl(): Int = 1

@PublishedApi
internal fun negImpl(): Int = -1

@PublishedApi
internal fun ready(): Boolean = true

@PublishedApi
internal fun transactImpl(work: () -> Unit) {
    work()
}

@PublishedApi
internal suspend fun awaitImpl(): String = "ready"

@PublishedApi
internal val counter: Int = 0

public object Registry {
    @PublishedApi
    internal fun lookup(name: String, type: KClass<*>): Any? = null
}

// A thin wrapper: one delegating call built from parameter reads, literals, `this`, and
// non-inline calls.

public inline fun log(tag: String, message: String): Unit = logImpl(tag, message)

public inline fun logNamed(tag: String, message: String): Unit = logImpl(message = message, tag = tag)

public inline fun logAll(vararg messages: String): Unit = logAllImpl(*messages)

public inline fun logBlock(tag: String, message: String) {
    logImpl(tag, message)
}

public inline fun lengthBlock(tag: String): Int {
    return length(tag)
}

public inline fun String.selfLength(): Int = length(this)

public inline fun currentCount(): Int = counter

public inline fun fixedProtocolVersion(): Int = 1

public inline fun consumeLength(tag: String): Unit = consumeImpl(length(tag))

public inline fun newBuffer(value: String): Any = StringBuilder(value)

public inline fun noop() {}

// A cast of the result is thin.

public inline fun <reified T> lookup(name: String): T = Registry.lookup(name, T::class) as T

public inline fun <reified T> find(name: String): T? = Registry.lookup(name, T::class) as? T

// A call of the own functional parameter runs caller code. In a suspend caller, the lambda
// inherits the suspend context.

public inline fun <R> once(block: () -> R): R = block()

public suspend fun suspensionPropagatesFromCallSite(): String = once { awaitImpl() }

// A contract doesn't count as a statement.

@OptIn(ExperimentalContracts::class)
public inline fun <R> measured(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return block()
}

// A lambda that only forwards the inlined parameter is thin.

public inline fun transact(crossinline work: () -> Unit): Unit = transactImpl { work() }

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>choose<!>(value: Int): Int = if (value < 0) negImpl() else posImpl()

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>pick<!>(mode: Int): Int = when (mode) {
    0 -> negImpl()
    else -> posImpl()
}

public inline fun <reified T> <!INLINE_FUNCTION_WITH_LOGIC!>lookupOrDefault<!>(name: String, default: T): T =
    Registry.lookup(name, T::class) as? T ?: default

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>lengthOrZero<!>(tag: String?): Int? = tag?.length

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>forcedLength<!>(tag: String): Int = lengthOrNull(tag)!!

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>logTwice<!>(tag: String, message: String) {
    logImpl(tag, message)
    logImpl(tag, message)
}

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>cachedLength<!>(tag: String): Int {
    val cached = length(tag)
    return cached
}

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>doubled<!>(value: Int): Int = value + value

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>greeting<!>(name: String): String = "Hello, $name"

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>guardedLength<!>(tag: String): Int = try {
    length(tag)
} catch (e: Exception) {
    0
}

// A call of an inline function of this library is logic.

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>onceMore<!>(block: () -> Int): Int = once(block)

public inline fun <!INLINE_FUNCTION_WITH_LOGIC!>transactChecked<!>(crossinline work: () -> Unit): Unit = transactImpl {
    if (ready()) work()
}

// A `@PublishedApi` inline declaration is checked too.

@PublishedApi
internal inline fun <!INLINE_FUNCTION_WITH_LOGIC!>publishedWithLogic<!>(value: Int): Int {
    val squared = value * value
    return squared
}

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun clamped(value: Int): Int = if (value < 0) 0 else value

internal inline fun internalChoose(value: Int): Int = if (value < 0) negImpl() else posImpl()

private inline fun privateChoose(value: Int): Int = if (value < 0) negImpl() else posImpl()

public fun regularChoose(value: Int): Int = if (value < 0) negImpl() else posImpl()

public val computedInLibrary: Int
    get() = negImpl() + posImpl()

@PublishedApi
internal var storedLimit: Int = 0

@PublishedApi
internal var storedTag: String = ""

@PublishedApi
internal val storedTagOrNull: String? = null

@PublishedApi
internal fun refreshNoop() {}

public inline var limit: Int
    get() = storedLimit
    set(value) {
        storedLimit = value
    }

public inline val currentLength: Int
    get() = length("current")

public inline val blockLength: Int
    get() {
        return length("block")
    }

public inline val String.selfLen: Int
    get() = length(this)

public inline val refresher: () -> Unit
    get() = ::refreshNoop

public inline val anyLength: Int
    get() = Registry.lookup("length", Int::class) as Int

public inline var tagged: String
    get() = storedTag
    set(value) = logImpl("tag", value)

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>threshold<!>: Int
    get() = 1 + 1

public val <!INLINE_FUNCTION_WITH_LOGIC!>accessorMarkedInline<!>: Int
    inline get() = 2 + 2

public inline var <!INLINE_FUNCTION_WITH_LOGIC!>clampedLimit<!>: Int
    get() = storedLimit
    set(value) {
        storedLimit = if (value < 0) 0 else value
    }

// Only the inline setter is reported.

public var <!INLINE_FUNCTION_WITH_LOGIC!>mixedAccessors<!>: Int
    get() = negImpl() + posImpl()
    inline set(value) {
        storedLimit = if (value < 0) 0 else value
    }

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>statusSign<!>: Int
    get() = if (ready()) posImpl() else negImpl()

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>modeSign<!>: Int
    get() = when (storedLimit) {
        0 -> negImpl()
        else -> posImpl()
    }

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>knownLength<!>: Int
    get() = lengthOrNull("known") ?: 0

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>forcedKnownLength<!>: Int
    get() = lengthOrNull("known")!!

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>taggedLength<!>: Int?
    get() = storedTagOrNull?.length

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>loggedLength<!>: Int
    get() {
        logImpl("length", "read")
        return length("logged")
    }

public inline var <!INLINE_FUNCTION_WITH_LOGIC!>doubleLogged<!>: String
    get() = storedTag
    set(value) {
        logImpl("first", value)
        logImpl("second", value)
    }

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>cachedThreshold<!>: Int
    get() {
        val cached = length("cached")
        return cached
    }

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>limitLine<!>: String
    get() = "limit: $storedLimit"

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>guardedThreshold<!>: Int
    get() = try {
        length("guarded")
    } catch (e: Exception) {
        0
    }

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>onceThreshold<!>: Int
    get() = once { 1 }

// A read or a write through an inline accessor is a call of it.

public inline val <!INLINE_FUNCTION_WITH_LOGIC!>relayedThreshold<!>: Int
    get() = threshold

public inline var <!INLINE_FUNCTION_WITH_LOGIC!>relayedLimit<!>: Int
    get() = storedLimit
    set(value) {
        clampedLimit = value
    }

@PublishedApi
internal inline val <!INLINE_FUNCTION_WITH_LOGIC!>publishedThreshold<!>: Int
    get() = 5 + 5

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline val fastThreshold: Int
    get() = 3 + 3

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline var acknowledgedClamped: Int
    get() = if (storedLimit < 0) 0 else storedLimit
    set(value) {
        storedLimit = if (value < 0) 0 else value
    }

internal inline var internalClamped: Int
    get() = storedLimit
    set(value) {
        storedLimit = if (value < 0) 0 else value
    }

private inline val hiddenThreshold: Int
    get() = 4 + 4
