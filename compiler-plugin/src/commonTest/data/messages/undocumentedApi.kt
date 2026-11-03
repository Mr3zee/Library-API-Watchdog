// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

public class <!UNDOCUMENTED_PUBLIC_API!>Undocumented<!>

public interface <!UNDOCUMENTED_PUBLIC_API!>Contract<!>

public object <!UNDOCUMENTED_PUBLIC_API!>Registry<!>

public enum class <!UNDOCUMENTED_PUBLIC_API!>Status<!> {
    ACTIVE,
}

public annotation class <!UNDOCUMENTED_PUBLIC_API!>Marker<!>

public typealias <!UNDOCUMENTED_PUBLIC_API!>Label<!> = String

public fun <!UNDOCUMENTED_PUBLIC_API!>start<!>() {}

public val <!UNDOCUMENTED_PUBLIC_API!>retries<!>: Int = 3

/** A secondary constructor is named after its class. */
public class Server(port: Int) {
    <!UNDOCUMENTED_PUBLIC_API!>public constructor() : this(80)<!>
}
