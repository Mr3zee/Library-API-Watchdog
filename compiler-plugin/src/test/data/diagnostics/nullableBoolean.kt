// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -BOOLEAN_PARAMETER_PUBLIC_API -DATA_CLASS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean

public fun probe(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

public fun tristate(flag: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>) {}

context(state: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)
public fun contextualFunction(): String = state.toString()

context(state: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)
public val contextualProperty: String get() = state.toString()

public val lastOutcome: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

public var cachedDecision: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

// Unlike BOOLEAN_PARAMETER_PUBLIC_API, the check covers constructors.

public class Holder(public val checked: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)

public class Sink(initial: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)

// `@PublishedApi` doesn't make a declaration callable in source.
@PublishedApi
internal fun published(): Boolean? = null

public fun outcomes(): <!NULLABLE_BOOLEAN_PUBLIC_API!>List<Boolean?><!> = emptyList()

public typealias MaybeFlag = Boolean?

public fun aliased(): <!NULLABLE_BOOLEAN_PUBLIC_API!>MaybeFlag<!> = null

public typealias Flag = Boolean

public fun aliasedNullable(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Flag?<!> = null

public fun onDecision(callback: <!NULLABLE_BOOLEAN_PUBLIC_API!>(Boolean?) -> Unit<!>) { }

public fun decider(): <!NULLABLE_BOOLEAN_PUBLIC_API!>() -> Boolean?<!> = { null }

public fun varargFlags(vararg flags: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>) { }

public fun <T : <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>> narrow(value: T): T = value

public class Tri<T : <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>>

public fun <@IntentionallyNullableBoolean C : Boolean?> pick(value: C): C = value

public fun snapshots(): List<@IntentionallyNullableBoolean Boolean?> = emptyList()

public fun buffered(): @IntentionallyNullableBoolean Boolean? = null

// An extension receiver isn't reported.

public fun Boolean?.orFalse(): Boolean = this ?: false

// An override isn't reported.

public interface Probe {
    public fun read(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>
}

public class DefaultProbe : Probe {
    override fun read(): Boolean? = null
}

@IntentionallyNullableBoolean
public fun legacyProbe(): Boolean? = null

public fun acknowledgedParameter(@IntentionallyNullableBoolean flag: Boolean?) {}

@IntentionallyNullableBoolean
context(state: Boolean?)
public fun acknowledgedContext(): String = state.toString()

context(@IntentionallyNullableBoolean state: Boolean?)
public fun acknowledgedContextParameter(): String = state.toString()

public class Settings(@IntentionallyNullableBoolean public val checked: Boolean?)

@IntentionallyNullableBoolean
public val acknowledgedOutcome: Boolean? = null

public fun isReady(): Boolean = true

public class Session {
    public var active: Boolean = false
}

internal fun internalProbe(): Boolean? = null

private val privateOutcome: Boolean? = null

public fun outer() {
    fun local(flag: Boolean?) = flag
    local(null)
}
