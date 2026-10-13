// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

// Message parameters: the function name and the parameter name, which is `_` for an unnamed
// context parameter.

public fun doWork(<!BOOLEAN_PARAMETER_PUBLIC_API!>optimizeForSpeed<!>: Boolean) {}

context(<!BOOLEAN_PARAMETER_PUBLIC_API!>_<!>: Boolean)
public fun trace(message: String) {}
