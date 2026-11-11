// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INCLUDED_NAMES: test.ApiKt
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: Api.kt
// A name rule for the JVM facade class of a file includes the top-level functions and properties.
package test

public fun <!UNDOCUMENTED_PUBLIC_API!>includedFunction<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}

// A class of the file isn't a member of the facade class.
public class NotIncluded {
    public fun member(flag: Boolean) {}
}

// FILE: Other.kt
package test

public fun notIncludedFunction(flag: Boolean) {}
