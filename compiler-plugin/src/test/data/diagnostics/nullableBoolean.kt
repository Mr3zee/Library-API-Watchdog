// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_TO_STRING -BOOLEAN_PARAMETER_PUBLIC_API -DATA_CLASS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyNullableBoolean

// Nullable Booleans in public signatures: should warn.

public fun probe(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

public fun tristate(flag: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>) {}

public val lastOutcome: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

public var cachedDecision: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

// Unlike plain Boolean parameters, constructors are checked too: a stored three-state flag is
// as opaque to its readers as a passed one.

public class Holder(public val checked: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)

public class Sink(initial: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)

// @PublishedApi declarations belong to the published binary API.
@PublishedApi
internal fun published(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

// The nullable Boolean may hide in a type argument, behind a type alias, or in a function type.

public fun outcomes(): <!NULLABLE_BOOLEAN_PUBLIC_API!>List<Boolean?><!> = emptyList()

public typealias MaybeFlag = Boolean?

public fun aliased(): <!NULLABLE_BOOLEAN_PUBLIC_API!>MaybeFlag<!> = null

public typealias Flag = Boolean

public fun aliasedNullable(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Flag?<!> = null

public fun onDecision(callback: <!NULLABLE_BOOLEAN_PUBLIC_API!>(Boolean?) -> Unit<!>): Unit = Unit

public fun decider(): <!NULLABLE_BOOLEAN_PUBLIC_API!>() -> Boolean?<!> = { null }

// A vararg parameter still passes the three-state values one by one.

public fun varargFlags(vararg flags: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>): Unit = Unit

// A nullable bound constrains every instantiation of the type parameter to the same
// three-state values as a direct mention of the bound.

public fun <T : <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>> narrow(value: T): T = value

public class Tri<T : <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>>

// ...unless the type parameter (or its owner) acknowledges the nullable bound.

public fun <@IntentionallyNullableBoolean C : Boolean?> pick(value: C): C = value

// A type-use exemption covers the annotated type and everything nested in it.

public fun snapshots(): List<@IntentionallyNullableBoolean Boolean?> = emptyList()

public fun buffered(): @IntentionallyNullableBoolean Boolean? = null

// Extensions on nullable Booleans provide functionality for values the user already holds -
// typically remedial helpers: no warning on the receiver.

public fun Boolean?.orFalse(): Boolean = this ?: false

// Overrides repeat the signature fixed by the overridden declaration, which is reported instead.

public interface Probe {
    public fun read(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>
}

public class DefaultProbe : Probe {
    override fun read(): Boolean? = null
}

// Deliberately nullable Booleans: no warning.

@IntentionallyNullableBoolean
public fun legacyProbe(): Boolean? = null

public fun acknowledgedParameter(@IntentionallyNullableBoolean flag: Boolean?) {}

public class Settings(@IntentionallyNullableBoolean public val checked: Boolean?)

@IntentionallyNullableBoolean
public val acknowledgedOutcome: Boolean? = null

// Non-nullable Booleans are two honest states: no warning.

public fun isReady(): Boolean = true

public class Session {
    public var active: Boolean = false
}

// Not visible outside the library: no warning.

internal fun internalProbe(): Boolean? = null

private val privateOutcome: Boolean? = null

public fun outer() {
    fun local(flag: Boolean?) = flag
    local(null)
}
