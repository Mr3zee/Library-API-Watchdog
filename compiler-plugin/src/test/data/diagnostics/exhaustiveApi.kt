// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive

// Unacknowledged exhaustive API: should warn.

public enum class <!EXHAUSTIVE_PUBLIC_API!>UnmarkedEnum<!> {
    A,
    B,
}

public sealed class <!EXHAUSTIVE_PUBLIC_API!>UnmarkedSealedClass<!> {
    public class Left : UnmarkedSealedClass()

    public class Right : UnmarkedSealedClass()

    // A non-final member of a sealed hierarchy is itself unrestricted subclassable API.
    public abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>Middle<!> : UnmarkedSealedClass()
}

public sealed interface <!EXHAUSTIVE_PUBLIC_API!>UnmarkedSealedInterface<!> {
    public class Impl : UnmarkedSealedInterface
}

// Deliberately exhaustive: no warning.

@IntentionallyExhaustive
public enum class MarkedEnum {
    A,
    B,
}

@IntentionallyExhaustive
public sealed class MarkedSealedClass {
    public class Only : MarkedSealedClass()
}

@IntentionallyExhaustive
public sealed interface MarkedSealedInterface {
    public class Impl : MarkedSealedInterface
}

// Not visible outside the library: no warning.

internal enum class InternalEnum {
    A,
}

private sealed class PrivateSealedClass {
    class Only : PrivateSealedClass()
}
