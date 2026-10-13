// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -BOOLEAN_PARAMETER_PUBLIC_API -FINAL_UPPER_BOUND -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

package foo.bar

// Message parameters: the declaration kind and its name.

public fun probe(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

public val lastOutcome: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

public fun tristate(flag: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>) {}

// An unnamed context parameter is named `_` in the message.
context(_: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)
public fun trace() {}

public class Holder(public val checked: <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>)

public class Tri<T : <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!>>
