// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.HiddenKt test.Renamed test.OtherKt
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: Hidden.kt
// A name rule for the JVM facade class of a file excludes the top-level functions and properties.
package test

public fun hiddenFunction(flag: Boolean) {}

public val hiddenProperty: MutableList<Int> = mutableListOf()

// A class of the file isn't a member of the facade class.
public class <!UNDOCUMENTED_PUBLIC_API!>InHiddenFile<!> {
    public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}
}

// FILE: Named.kt
// `@file:JvmName` gives the name of the facade class.
@file:JvmName("Renamed")

package test

public fun renamedFunction(flag: Boolean) {}

// FILE: Other.kt
// The facade class is `test.Other`, so the rule `test.OtherKt` doesn't match.
@file:JvmName("Other")

package test

public fun <!UNDOCUMENTED_PUBLIC_API!>watchedFunction<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}
