// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -EXHAUSTIVE_PUBLIC_API -DATA_CLASS_PUBLIC_API -NULLABLE_BOOLEAN_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter

public fun doWork(<!BOOLEAN_PARAMETER_PUBLIC_API!>optimizeForSpeed<!>: Boolean) {}

public fun mixed(<!BOOLEAN_PARAMETER_PUBLIC_API!>first<!>: Boolean, count: Int, <!BOOLEAN_PARAMETER_PUBLIC_API!>second<!>: Boolean) {}

public fun withDefault(<!BOOLEAN_PARAMETER_PUBLIC_API!>eager<!>: Boolean = false) {}

public fun tristate(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean?) {}

public typealias Flag = Boolean

public fun aliased(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Flag) {}

public fun varargFlags(vararg <!BOOLEAN_PARAMETER_PUBLIC_API!>flags<!>: Boolean) {}

context(<!BOOLEAN_PARAMETER_PUBLIC_API!>verbose<!>: Boolean)
public fun logLine(message: String) {}

context(<!BOOLEAN_PARAMETER_PUBLIC_API!>_<!>: Boolean)
public fun trace(message: String) {}

public class Logger

context(logger: Logger)
public fun logNamed(message: String) {}

public abstract class Base {
    protected fun guarded(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}
}

// `@PublishedApi` doesn't make a declaration callable in source.
@PublishedApi
internal fun published(flag: Boolean) {}

public class Bits {
    public operator fun set(index: Int, <!BOOLEAN_PARAMETER_PUBLIC_API!>value<!>: Boolean) {}
}

// An override isn't reported again.

public interface Togglable {
    public fun toggle(<!BOOLEAN_PARAMETER_PUBLIC_API!>state<!>: Boolean)
}

public class Switch : Togglable {
    override fun toggle(state: Boolean) {}
}

// Constructors aren't checked.

public class Widget(visible: Boolean) {
    public constructor(visible: Boolean, tag: String) : this(visible)

    public fun refresh(<!BOOLEAN_PARAMETER_PUBLIC_API!>force<!>: Boolean) {}
}

public class Config(public val enabled: Boolean)

public data class DataFlags(val debug: Boolean)

public enum class Mode(public val fast: Boolean) { QUICK(true), FULL(false) }

public annotation class Feature(val enabled: Boolean)

// A factory named after the type it returns counts as a constructor.

public fun Widget(visible: Boolean, scale: Int): Widget = Widget(visible)

// A nullable return type still matches.
public fun Config(enabled: Boolean, validate: Boolean): Config? = if (validate) Config(enabled) else null

// A factory named after a type alias counts too. Its parameter list must differ from the
// constructors of `Widget`, because the alias re-exposes them.
public typealias Toggle = Widget

public fun Toggle(on: Boolean, brightness: Int): Toggle = Widget(on)

public fun createWidget(<!BOOLEAN_PARAMETER_PUBLIC_API!>visible<!>: Boolean): Widget = Widget(visible)

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun acknowledgedSignature(enabled: Boolean, verbose: Boolean) {}

public fun acknowledgedParameter(
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN) accepted: Boolean,
    <!BOOLEAN_PARAMETER_PUBLIC_API!>rejected<!>: Boolean,
) {}

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
context(verbose: Boolean)
public fun acknowledgedContext(message: String) {}

context(@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN) verbose: Boolean)
public fun acknowledgedContextParameter(message: String) {}

public fun isReady(): Boolean = true

public class Session {
    public var active: Boolean = false
}

public fun filter(items: List<String>, predicate: (String) -> Boolean): List<String> = items.filter(predicate)

public fun Boolean.negated(): Boolean = !this

internal fun internalToggle(flag: Boolean) {}

private fun privateToggle(flag: Boolean) {}

public fun outer() {
    fun local(flag: Boolean) = flag
    local(true)
}
