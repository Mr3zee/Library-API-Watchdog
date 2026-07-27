// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_TO_STRING -EXHAUSTIVE_PUBLIC_API -DATA_CLASS_PUBLIC_API -NULLABLE_BOOLEAN_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyBooleanParameter

// Boolean parameters in public functions: should warn.

public fun doWork(<!BOOLEAN_PARAMETER_PUBLIC_API!>optimizeForSpeed<!>: Boolean) {}

public fun mixed(<!BOOLEAN_PARAMETER_PUBLIC_API!>first<!>: Boolean, count: Int, <!BOOLEAN_PARAMETER_PUBLIC_API!>second<!>: Boolean) {}

// A default value does not make the positional call site any clearer.
public fun withDefault(<!BOOLEAN_PARAMETER_PUBLIC_API!>eager<!>: Boolean = false) {}

// A nullable Boolean is a three-state flag: still opaque at the call site.
public fun tristate(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean?) {}

// A type alias does not change what users pass.
public typealias Flag = Boolean

public fun aliased(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Flag) {}

// A vararg Boolean parameter takes the same positional true/false arguments.
public fun varargFlags(vararg <!BOOLEAN_PARAMETER_PUBLIC_API!>flags<!>: Boolean) {}

// Protected members are part of the public API surface.
public abstract class Base {
    protected fun guarded(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}
}

// @PublishedApi declarations belong to the published binary API.
@PublishedApi
internal fun published(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}

// Operator conventions do not exempt: `bits.set(0, true)` is as opaque as any other call.
public class Bits {
    public operator fun set(index: Int, <!BOOLEAN_PARAMETER_PUBLIC_API!>value<!>: Boolean) {}
}

// The Boolean parameter is reported once, on the declaration that introduces it: overrides
// only repeat the fixed signature.

public interface Togglable {
    public fun toggle(<!BOOLEAN_PARAMETER_PUBLIC_API!>state<!>: Boolean)
}

public class Switch : Togglable {
    override fun toggle(state: Boolean) {}
}

// Constructors are not checked: a construction site stores data in the named type rather than
// switching an operation mode.

public class Widget(visible: Boolean) {
    public constructor(visible: Boolean, tag: String) : this(visible)

    // A member function still switches behavior: should warn.
    public fun refresh(<!BOOLEAN_PARAMETER_PUBLIC_API!>force<!>: Boolean) {}
}

public class Config(public val enabled: Boolean)

public data class DataFlags(val debug: Boolean)

public enum class Mode(public val fast: Boolean) { QUICK(true), FULL(false) }

public annotation class Feature(val enabled: Boolean)

// Constructor functions - factories named after the type they create - share the constructor
// call shape: no warning.

public fun Widget(visible: Boolean, scale: Int): Widget = Widget(visible)

// A factory that may fail still constructs the named type.
public fun Config(enabled: Boolean, validate: Boolean): Config? = if (validate) Config(enabled) else null

// The call site reads the alias name, so a factory named after a type alias counts too. The
// parameter list must differ from Widget's constructors, which the alias re-exposes as Toggle.
public typealias Toggle = Widget

public fun Toggle(on: Boolean, brightness: Int): Toggle = Widget(on)

// A factory whose name does not match the returned type reads as an ordinary call: should warn.
public fun createWidget(<!BOOLEAN_PARAMETER_PUBLIC_API!>visible<!>: Boolean): Widget = Widget(visible)

// Acknowledged Boolean parameters: no warning.

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun acknowledgedSignature(enabled: Boolean, verbose: Boolean) {}

public fun acknowledgedParameter(
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN) accepted: Boolean,
    <!BOOLEAN_PARAMETER_PUBLIC_API!>rejected<!>: Boolean,
) {}

// Booleans in result and property positions are not arguments: no warning.

public fun isReady(): Boolean = true

public class Session {
    public var active: Boolean = false
}

// A function type taking or returning Boolean is not a Boolean parameter itself.
public fun filter(items: List<String>, predicate: (String) -> Boolean): List<String> = items.filter(predicate)

// An extension receiver is not a positional argument.
public fun Boolean.negated(): Boolean = !this

// Not visible outside the library: no warning.

internal fun internalToggle(flag: Boolean) {}

private fun privateToggle(flag: Boolean) {}

public fun outer() {
    fun local(flag: Boolean) = flag
    local(true)
}
