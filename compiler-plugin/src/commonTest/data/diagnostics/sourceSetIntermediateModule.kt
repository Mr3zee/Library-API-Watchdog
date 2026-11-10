// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API
// SOURCE_SETS_CHECKED_ELSEWHERE: common

// The option applies to the named common module only, not to the module between it and the platform module.

// MODULE: common
// FILE: common.kt

package foo.bar

public open class CommonOpen

public fun commonFunction(): Int = 0

// MODULE: intermediate()()(common)
// FILE: intermediate.kt

package foo.bar

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>IntermediateOpen<!>

public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>intermediateFunction<!>(): Int = 0

// MODULE: jvm()()(intermediate)
// FILE: jvm.kt

package foo.bar

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>JvmOpen<!>
