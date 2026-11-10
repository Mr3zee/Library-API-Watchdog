// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// INCLUDED_ANNOTATIONS: test.PublicApi
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// The positions of an inclusion annotation in a multiplatform module:
//
// - On an `expect` declaration or on its file, the annotation includes the `actual` declaration too.
// - On an `actual` declaration or on its platform file, the annotation includes only the `actual`
//   declaration. The `expect` declaration stays outside the filters, so the checks that report a
//   pair on the `expect` declaration don't run: `OPEN_API_WITHOUT_SUBCLASS_OPT_IN`,
//   `UNDOCUMENTED_PUBLIC_API`, and `BOOLEAN_PARAMETER_PUBLIC_API` don't report the pairs of
//   `AnnotatedActual` and `InAnnotatedPlatformFile`.

// MODULE: common
// FILE: common.kt

package test

/** Includes a declaration. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
public annotation class PublicApi

public expect open class AnnotatedActual() {
    public fun member(flag: Boolean = false)
}

public expect fun annotatedActual(flag: Boolean = false)

public expect open class InAnnotatedPlatformFile() {
    public fun member(flag: Boolean = false)
}

public expect fun inAnnotatedPlatformFile(flag: Boolean = false)

@PublicApi
public expect fun <!UNDOCUMENTED_PUBLIC_API!>annotatedExpect<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false)

// FILE: annotatedCommonFile.kt
@file:PublicApi

package test

public expect open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>InAnnotatedCommonFile<!>() {
    public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false)
}

public expect fun <!UNDOCUMENTED_PUBLIC_API!>inAnnotatedCommonFile<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false)

// MODULE: jvm()()(common)
// FILE: jvm.kt

package test

@PublicApi
public actual open class AnnotatedActual {
    public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>member<!>(flag: Boolean) {}
}

@PublicApi
public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>annotatedActual<!>(flag: Boolean) {}

public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>annotatedExpect<!>(flag: Boolean) {}

public actual open class InAnnotatedCommonFile {
    public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>member<!>(flag: Boolean) {}
}

public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>inAnnotatedCommonFile<!>(flag: Boolean) {}

// FILE: annotatedPlatformFile.kt
@file:PublicApi

package test

public actual open class InAnnotatedPlatformFile {
    public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>member<!>(flag: Boolean) {}
}

public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>inAnnotatedPlatformFile<!>(flag: Boolean) {}
