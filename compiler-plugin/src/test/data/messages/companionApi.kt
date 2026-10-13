// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

package foo.bar

// Message parameters: the outer class, the member name, the companion class name, and for a
// property the instance accessors (getter, setter, or both) with the matching fix.

public class Registry {
    public companion object {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>create<!>(): Registry = Registry()

        public val <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>DEFAULT_NAME<!>: String = "registry"

        public var <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>mutable<!>: String = "mutable"

        @get:JvmStatic
        public var <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>staticRead<!>: String = "read"
    }
}

public class Config {
    public companion object Factory {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>create<!>(): Config = Config()

        public val <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>DEFAULT_LABEL<!>: String = "config"
    }
}
