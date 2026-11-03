// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -FUNCTION_TYPE_ALIAS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import kotlin.reflect.KFunction1
import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyRequiredParameterAfterOptional

public fun connect(retries: Int = 3, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String) {}

public fun configure(timeout: Long = 0L, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>port<!>: Int) {}

// A vararg parameter is optional.
public fun tag(vararg values: String, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>name<!>: String) {}

public fun schedule(delay: Long = 0L, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>action<!>: () -> Unit, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>name<!>: String) {}

public fun namedSchedule(
    delay: Long = 0L,
    <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>name<!>: String,
    action: () -> Unit,
) {}

// No lambda literal satisfies a `KFunction` type.
public fun bind(priority: Int = 0, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>handler<!>: KFunction1<Int, Unit>) {}

public class Server(port: Int = 80, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String) {
    public constructor(port: Int = 80, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>path<!>: String) : this(port, host)
}

// A required function-type or fun interface parameter in the last position keeps the
// trailing-lambda syntax.

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

public fun wellOrdered(host: String, port: Int = 80, timeout: Long = 30L) {}

public fun allRequired(host: String, port: Int) {}

public fun allOptional(host: String = "localhost", port: Int = 80) {}

// An override isn't reported.

public abstract class Transport {
    public abstract fun send(priority: Int = 0, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>payload<!>: String)
}

public class Wire : Transport() {
    override fun send(priority: Int, payload: String) {}
}

@IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public fun legacyConnect(retries: Int = 3, host: String) {}

public class LegacyServer @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) constructor(port: Int = 80, host: String)

@PublishedApi
internal fun publishedHelper(retries: Int = 3, host: String) {}

internal fun internalHelper(retries: Int = 3, host: String) {}

private fun privateHelper(retries: Int = 3, host: String) {}
