// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -COMPANION_API_WITHOUT_JVM_STATIC -MANGLED_JVM_NAME_PUBLIC_API -INLINE_FUNCTION_WITH_LOGIC -FUNCTION_TYPE_ALIAS_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -NOTHING_TO_INLINE

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>fetch<!>(key: String): String = key

public inline fun <reified T> <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>instantiate<!>(): T? = null

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>race<!>(work: suspend () -> Int) {}

public class Config

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>configure<!>(block: Config.() -> Int) {}

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEach<!>(action: (Int) -> Unit) {}

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEachOrNot<!>(action: ((Int) -> Unit)?) {}

public typealias Callback = (Int) -> Unit

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEvent<!>(callback: Callback) {}

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEvents<!>(vararg callbacks: (Int) -> Unit) {}

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>raceAll<!>(vararg work: suspend () -> Int) {}

context(action: (Int) -> Unit)
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEachContextually<!>() {}

context(work: suspend () -> Int)
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>raceContextually<!>() {}

context(block: Config.() -> Int)
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>configureContextually<!>() {}

public fun transformAll(vararg mappers: (Int) -> Int) {}

context(mapper: (Int) -> Int)
public fun transformContextually(): Int = mapper(0)

public open class Loader {
    public open suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>load<!>(key: String): String = key
}

// A function type that returns `Unit` is Kotlin-only. One that returns a value is Java-usable.

public fun transform(mapper: (Int) -> Int): Int = mapper(0)

public fun interface Listener {
    public fun onChange(value: Int)
}

public fun listen(listener: Listener) {}

public inline fun <T> pass(value: T): T = value

@JvmSynthetic
public suspend fun hiddenFetch(key: String): String = key

// `@JvmSynthetic` can't hide an abstract or interface member without a change to its
// implementations. An override isn't reported.

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

// A constructor can't carry `@JvmSynthetic`.
public class Watcher(onChange: () -> Unit)

// A non-suspend signature mangled by a value class is reported by MANGLED_JVM_NAME_PUBLIC_API
// instead. A suspend function is reported here even when mangled. The check skips the members
// of a value class.

@JvmInline
public value class UserId(public val raw: String)

public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>fetchUser<!>(id: UserId): UserId = id

public fun onUserEach(id: UserId, action: (Int) -> Unit) {}

// `@JvmName` or `@JvmExposeBoxed` resolves the mangling, so the Kotlin-only shape is reported here.

@JvmName("onUserEachNamed")
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onUserEachNamed<!>(id: UserId, action: (Int) -> Unit) {}

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onUserEachBoxed<!>(id: UserId, action: (Int) -> Unit) {}

@JvmInline
public value class Wrapped(public val raw: String) {
    public suspend fun refresh(): String = raw
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public suspend fun acknowledgedFetch(key: String): String = key

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class KotlinOnlyService {
    public suspend fun refresh(key: String): String = key

    public fun onEachInside(action: (Int) -> Unit) {}
}

@PublishedApi
internal suspend fun publishedFetch(key: String): String = key

internal suspend fun internalFetch(key: String): String = key

private fun privateEach(action: (Int) -> Unit) {}
