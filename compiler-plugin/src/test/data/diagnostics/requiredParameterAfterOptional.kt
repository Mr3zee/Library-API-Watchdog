// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -FUNCTION_TYPE_ALIAS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import kotlin.reflect.KFunction1
import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyRequiredParameterAfterOptional

// Required parameters declared after optional (defaulted or vararg) ones: should warn.

public fun connect(retries: Int = 3, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String) {}

// Every required parameter behind the first optional one is reported.
public fun configure(timeout: Long = 0L, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>port<!>: Int) {}

// A vararg parameter is optional too: callers can omit it entirely.
public fun tag(vararg values: String, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>name<!>: String) {}

// A function-type parameter that is not last has no trailing-lambda syntax to preserve.
public fun schedule(delay: Long = 0L, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>action<!>: () -> Unit, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>name<!>: String) {}

// A trailing lambda excuses only itself. An earlier required parameter is still reported.
public fun namedSchedule(
    delay: Long = 0L,
    <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>name<!>: String,
    action: () -> Unit,
) {}

// No lambda literal satisfies a KFunction reflection type, so the last position doesn't excuse it.
public fun bind(priority: Int = 0, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>handler<!>: KFunction1<Int, Unit>) {}

// Constructors declare parameter lists too.
public class Server(port: Int = 80, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String) {
    public constructor(port: Int = 80, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>path<!>: String) : this(port, host)
}

// A required function-type or fun interface parameter in the last position keeps
// trailing-lambda call syntax available: no warning.

public fun withTimeout(timeout: Long = 0L, block: () -> Unit) {}

public fun launch(delay: Long = 0L, block: suspend () -> Unit) {}

public fun maybeRun(delay: Long = 0L, block: (() -> Unit)?) {}

public class Scope

public fun inScope(delay: Long = 0L, block: Scope.() -> Unit) {}

public typealias Callback = (Int) -> Unit

public fun onEvent(priority: Int = 0, callback: Callback) {}

public fun interface Listener {
    public fun onChange(value: Int)
}

public fun listen(bufferSize: Int = 16, listener: Listener) {}

public fun listenMaybe(bufferSize: Int = 16, listener: Listener?) {}

// Optional inputs last, or no optional inputs at all: no warning.

public fun wellOrdered(host: String, port: Int = 80, timeout: Long = 30L) {}

public fun allRequired(host: String, port: Int) {}

public fun allOptional(host: String = "localhost", port: Int = 80) {}

// Overrides can't declare defaults, and their parameter order is fixed by the overridden
// declaration, which is reported where it is declared.

public abstract class Transport {
    public abstract fun send(priority: Int = 0, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>payload<!>: String)
}

public class Wire : Transport() {
    override fun send(priority: Int, payload: String) {}
}

// Deliberate legacy order: no warning.

@IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public fun legacyConnect(retries: Int = 3, host: String) {}

public class LegacyServer @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) constructor(port: Int = 80, host: String)

// Not visible outside the library: no warning.

@PublishedApi
internal fun publishedHelper(retries: Int = 3, host: String) {}

internal fun internalHelper(retries: Int = 3, host: String) {}

private fun privateHelper(retries: Int = 3, host: String) {}
