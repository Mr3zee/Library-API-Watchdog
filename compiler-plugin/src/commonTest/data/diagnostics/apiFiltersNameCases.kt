// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INCLUDED_NAMES: test.Levels test.Holder.Factory test.Registry.Companion test.facadeFunction
// INCLUDED_ANNOTATIONS: test.PublicApi
// EXCLUDED_NAMES: test.Levels.HIGH test.ApiFiltersNameCasesKt
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// Name rules on an enum entry, on a companion object, and on the JVM facade class of the file.

package test

/** Includes a declaration. */
public annotation class PublicApi

// An excluded enum entry doesn't exclude the enum class.
public enum class <!EXHAUSTIVE_PUBLIC_API, UNDOCUMENTED_PUBLIC_API!>Levels<!> {
    HIGH,
    LOW,
}

// A named companion object that a name rule includes doesn't include the enclosing class.
public open class Holder {
    public companion object <!UNDOCUMENTED_PUBLIC_API!>Factory<!> {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC, UNDOCUMENTED_PUBLIC_API!>create<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean): Holder = Holder()
    }

    public fun member(flag: Boolean) {}
}

// The same for a default-named companion object.
public open class Registry {
    public companion object {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC, UNDOCUMENTED_PUBLIC_API!>create<!>(): Registry = Registry()
    }
}

// A companion object with an inclusion annotation doesn't include the enclosing class.
public open class Annotated {
    @PublicApi
    public companion object {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC, UNDOCUMENTED_PUBLIC_API!>create<!>(): Annotated = Annotated()
    }
}

// A name rule doesn't match the JVM facade class of the file.
public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME, UNDOCUMENTED_PUBLIC_API!>facadeFunction<!>() {}
