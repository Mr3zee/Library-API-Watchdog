// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.CommonKt test.PlatformKt
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// A common declaration is also in the klib dumps, which don't have the facade class. So the rule
// for the facade class applies only to a declaration of the JVM module.

// MODULE: common
// FILE: Common.kt

package test

public fun <!UNDOCUMENTED_PUBLIC_API!>commonFunction<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}

// MODULE: jvm()()(common)
// FILE: Platform.kt

package test

public fun platformFunction(flag: Boolean) {}
