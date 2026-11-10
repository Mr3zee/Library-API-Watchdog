// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.Excluded test.excludedFunction test.Partly.excludedMember
// INCLUDED_NAMES: test.Excluded test.excludedFunction test.Partly test.IncludedByName test.includedByName
// INCLUDED_ANNOTATIONS: test.PublicApi
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// An `expect` declaration and its `actual` declaration have the same name, so a name rule gives
// both the same verdict. An inclusion annotation on the `expect` declaration includes the `actual`.

// MODULE: common
// FILE: common.kt

package test

/** Includes a declaration. */
@Retention(AnnotationRetention.SOURCE)
public annotation class PublicApi

public expect open class Excluded() {
    public fun member(flag: Boolean = false)
}

public expect fun excludedFunction(flag: Boolean = false)

public expect open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>Partly<!>() {
    public fun excludedMember(flag: Boolean = false)

    public fun <!UNDOCUMENTED_PUBLIC_API!>watchedMember<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false)
}

public expect open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>IncludedByName<!>()

public expect fun <!UNDOCUMENTED_PUBLIC_API!>includedByName<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false)

@PublicApi
public expect open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>IncludedByAnnotation<!>()

@PublicApi
public expect fun <!UNDOCUMENTED_PUBLIC_API!>includedByAnnotation<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false)

public expect open class NotIncluded() {
    public fun member(flag: Boolean = false)
}

public expect fun notIncluded(flag: Boolean = false)

// MODULE: jvm()()(common)
// FILE: jvm.kt

package test

public actual open class Excluded {
    public actual fun member(flag: Boolean) {}
}

public actual fun excludedFunction(flag: Boolean) {}

public actual open class Partly {
    public actual fun excludedMember(flag: Boolean) {}

    public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>watchedMember<!>(flag: Boolean) {}
}

public actual open class IncludedByName

public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>includedByName<!>(flag: Boolean) {}

public actual open class IncludedByAnnotation

public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>includedByAnnotation<!>(flag: Boolean) {}

public actual open class NotIncluded {
    public actual fun member(flag: Boolean) {}
}

public actual fun notIncluded(flag: Boolean) {}
