// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

// MODULE: common
// FILE: common.kt

package foo.bar

public expect abstract class Credentials

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

public actual typealias <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>Credentials<!> = java.io.InputStream
