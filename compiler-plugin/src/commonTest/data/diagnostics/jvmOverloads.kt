// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -COMPANION_API_WITHOUT_JVM_STATIC -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -MANGLED_JVM_NAME_PUBLIC_API

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutJvmOverloads

public fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>connect<!>(host: String, port: Int = 80, timeout: Int = 30) {}

@JvmOverloads
public fun connectJava(host: String, port: Int = 80) {}

public fun send(host: String, port: Int) {}

public fun log(vararg entries: String) {}

@JvmSynthetic
public fun kotlinConnect(host: String, port: Int = 80) {}

// A suspend function isn't Java-callable.
public suspend fun fetch(host: String, timeout: Int = 30): String = host

public class Connection<!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>(host: String, port: Int = 80)<!> {
    <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>public constructor(port: Int = 443) : this("localhost", port)<!>

    public fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>open<!>(retries: Int = 3) {}

    @JvmOverloads
    public fun openJava(retries: Int = 3) {}
}

public class Tunnel @JvmOverloads constructor(host: String, port: Int = 22)

// `@JvmOverloads` isn't applicable to an abstract member, an interface member, or an annotation
// class constructor.

public interface Api {
    public fun call(timeout: Int = 30)

    public fun ping(timeout: Int = 30): Int = timeout
}

public abstract class Base {
    public abstract fun run(retries: Int = 3)

    public fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>walk<!>(steps: Int = 1) {}
}

public class Runner : Base() {
    override fun run(retries: Int) {}
}

public annotation class Retry(val count: Int = 3)

// Java can't see the members of a value class.
@JvmInline
public value class Port(public val number: Int) {
    public fun shifted(by: Int = 1): Int = number + by
}

@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
public fun acknowledgedConnect(host: String, port: Int = 80) {}

public class Gateway @IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN) constructor(
    host: String,
    port: Int = 80,
)

@PublishedApi
internal fun publishedConnect(host: String, port: Int = 80) {}

internal fun internalConnect(host: String, port: Int = 80) {}

private fun privateConnect(host: String, port: Int = 80) {}
