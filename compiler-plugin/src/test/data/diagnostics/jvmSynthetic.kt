// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -COMPANION_API_WITHOUT_JVM_STATIC -MANGLED_JVM_NAME_PUBLIC_API -INLINE_FUNCTION_WITH_LOGIC -FUNCTION_TYPE_ALIAS_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -NOTHING_TO_INLINE

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

// Kotlin-only shapes visible to Java sources: should warn.

public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>fetch<!>(key: String): String = key

public inline fun <reified T> <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>instantiate<!>(): T? = null

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>race<!>(work: suspend () -> Int) {}

public class Config

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>configure<!>(block: Config.() -> Int) {}

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEach<!>(action: (Int) -> Unit) {}

// Nullability doesn't matter: the parameter is still a Kotlin function type.
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEachOrNot<!>(action: ((Int) -> Unit)?) {}

// A type alias doesn't change what Java sees: the expansion is a Unit-returning function type.
public typealias Callback = (Int) -> Unit

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEvent<!>(callback: Callback) {}

// A vararg's array is compiler-generated; callers still pass Kotlin function values one by one.
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEvents<!>(vararg callbacks: (Int) -> Unit) {}

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>raceAll<!>(vararg work: suspend () -> Int) {}

// Function types Java can invoke naturally remain accepted in vararg position too.
public fun transformAll(vararg mappers: (Int) -> Int) {}

// An open (non-abstract) member still has a body Java sees: should warn.
public open class Loader {
    public open suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>load<!>(key: String): String = key
}

// Java-usable shapes: no warning.

public fun transform(mapper: (Int) -> Int): Int = mapper(0)

public fun interface Listener {
    public fun onChange(value: Int)
}

public fun listen(listener: Listener) {}

public inline fun <T> pass(value: T): T = value

// @JvmSynthetic already hides the Kotlin-only shape: no warning.
@JvmSynthetic
public suspend fun hiddenFetch(key: String): String = key

// Abstract and interface members can't be hidden without breaking implementations, and an
// override repeats the shape reported on the base declaration: no warning.

public interface Resolver {
    public suspend fun resolve(key: String): String

    public fun forEach(action: (Int) -> Unit) {}
}

public class MapResolver : Resolver {
    override suspend fun resolve(key: String): String = key
}

public abstract class BaseResolver {
    public abstract suspend fun resolveBase(key: String): String
}

// Constructors can't carry @JvmSynthetic: no warning.
public class Watcher(onChange: () -> Unit)

// A signature mangled by a value class is already invisible to Java sources and reported by
// MANGLED_JVM_NAME_PUBLIC_API (muted here), and so is everything inside a value class.

@JvmInline
public value class UserId(public val raw: String)

public suspend fun fetchUser(id: UserId): UserId = id

@JvmInline
public value class Wrapped(public val raw: String) {
    public suspend fun refresh(): String = raw
}

// Acknowledged declarations: no warning.

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public suspend fun acknowledgedFetch(key: String): String = key

// A class-level exemption covers every function inside.
@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class KotlinOnlyService {
    public suspend fun refresh(key: String): String = key

    public fun onEachInside(action: (Int) -> Unit) {}
}

// Not visible outside the library: no warning.

@PublishedApi
internal suspend fun publishedFetch(key: String): String = key

internal suspend fun internalFetch(key: String): String = key

private fun privateEach(action: (Int) -> Unit) {}
