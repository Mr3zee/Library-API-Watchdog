// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN

package foo.bar

public enum class <!EXHAUSTIVE_PUBLIC_API!>Status<!> {
    ACTIVE,
}

public sealed class <!EXHAUSTIVE_PUBLIC_API!>Shape<!> {
    public class Circle : Shape()
}

public sealed interface <!EXHAUSTIVE_PUBLIC_API!>Event<!> {
    public class Click : Event
}
