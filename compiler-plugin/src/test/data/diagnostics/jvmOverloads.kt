// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -COMPANION_API_WITHOUT_JVM_STATIC -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -MANGLED_JVM_NAME_PUBLIC_API

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWithoutJvmOverloads

// For Java callers the defaults don't exist: should warn.

public fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>connect<!>(host: String, port: Int = 80, timeout: Int = 30) {}

// @JvmOverloads compiles the reduced overloads: no warning.
@JvmOverloads
public fun connectJava(host: String, port: Int = 80) {}

// No defaults, and vararg is not a default: nothing to generate.

public fun send(host: String, port: Int) {}

public fun log(vararg entries: String) {}

// @JvmSynthetic hides the function from Java on purpose: no warning.
@JvmSynthetic
public fun kotlinConnect(host: String, port: Int = 80) {}

// suspend functions are not Java-callable regardless of overloads (muted own diagnostic here).
public suspend fun fetch(host: String, timeout: Int = 30): String = host

// Constructors generate overloads the same way.

public class Connection<!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>(host: String, port: Int = 80)<!> {
    <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>public constructor(port: Int = 443) : this("localhost", port)<!>

    public fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>open<!>(retries: Int = 3) {}

    @JvmOverloads
    public fun openJava(retries: Int = 3) {}
}

public class Tunnel @JvmOverloads constructor(host: String, port: Int = 22)

// @JvmOverloads is not applicable to abstract members, interface members, or annotation class
// constructors: no warning.

public interface Api {
    public fun call(timeout: Int = 30)

    public fun ping(timeout: Int = 30): Int = timeout
}

public abstract class Base {
    public abstract fun run(retries: Int = 3)

    // A concrete member generates overloads like any other function: should warn.
    public fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>walk<!>(steps: Int = 1) {}
}

// An override can't re-declare default values: no warning.
public class Runner : Base() {
    override fun run(retries: Int) {}
}

public annotation class Retry(val count: Int = 3)

// Everything inside a value class is invisible to Java sources anyway: no warning.
@JvmInline
public value class Port(public val number: Int) {
    public fun shifted(by: Int = 1): Int = number + by
}

// Acknowledged declarations: no warning.

@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
public fun acknowledgedConnect(host: String, port: Int = 80) {}

public class Gateway @IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN) constructor(
    host: String,
    port: Int = 80,
)

// Not visible outside the library: no warning.

internal fun internalConnect(host: String, port: Int = 80) {}

private fun privateConnect(host: String, port: Int = 80) {}
