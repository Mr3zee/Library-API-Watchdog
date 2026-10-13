// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

// Message parameters: the facade class name and the callable kinds the file holds.

// FILE: functions.kt
package foo.bar

public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>connect<!>(): Int = 0

public fun disconnect(): Int = 0

// FILE: properties.kt
package foo.bar

public val <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>retries<!>: Int = 3

// FILE: mixed.kt
package foo.bar

public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>open<!>(): Int = 0

public val timeout: Int = 3
