// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -DATA_CLASS_PUBLIC_API -EXHAUSTIVE_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutToString
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEquals
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutHashCode

// Stateful classes relying on Any's equality, hashing, and rendering: should warn three times.

public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>Connection<!>(public val host: String)

public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>Counter<!> {
    private var count: Int = 0

    public fun increment() {
        count++
    }
}

public class Container {
    public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>Nested<!>(public val value: Int)
}

// State declared in an abstract class renders opaquely in every subclass.
public abstract class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>StatefulBase<!>(public val id: Int)

// A lateinit var has a backing field too.
public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>LateBound<!> {
    public lateinit var target: String
}

// @PublishedApi classes are internal in source and therefore exempt.
@PublishedApi
internal class PublishedState(val x: Int)

// Each generated member is checked independently.

public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE!>WithToString<!>(public val host: String) {
    override fun toString(): String = "WithToString(host=$host)"
}

public class <!STATEFUL_CLASS_WITHOUT_TO_STRING!>WithEquality<!>(public val id: Int) {
    override fun equals(other: Any?): Boolean = other is WithEquality && id == other.id
    override fun hashCode(): Int = id
}

// An overload with the same name is not an implementation of Any.equals.
public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>EqualsOverload<!>(public val id: Int) {
    public fun equals(other: String): Boolean = id.toString() == other
}

public open class GeneratedBase(public val id: Int) {
    override fun equals(other: Any?): Boolean = other is GeneratedBase && id == other.id
    override fun hashCode(): Int = id
    override fun toString(): String = "GeneratedBase(id=$id)"
}

// Inherited non-Any implementations cover a subclass, even one adding its own state.
public class GeneratedDerived(id: Int, public val extra: Int) : GeneratedBase(id)

// Deliberate identity equality, identity hashing, and opaque rendering: no warning.

@IntentionallyWithoutEquals
@IntentionallyWithoutHashCode
@IntentionallyWithoutToString
public class MarkedOpaque(public val secret: String)

@IntentionallyWithoutEqualsHashCodeOrToString
public class CombinedMarkedOpaque(public val secret: String)

// No property stores its value in a backing field: no warning.

public class Stateless {
    public val computed: Int
        get() = 42
}

public class DelegatedOnly {
    public val value: String by lazy { "value" }
}

public class NoProperties {
    public fun doWork() {}
}

// The compiler generates toString for data and value classes: no warning here
// (data classes are reported by DATA_CLASS_PUBLIC_API instead).

public data class DataPoint(val x: Int)

@JvmInline
public value class UserId(public val raw: Int)

// Enum entries render their name: no warning.

public enum class Size(public val px: Int) { SMALL(8), LARGE(64) }

// Objects hold constants rather than per-instance state: no warning.

public object Registry {
    public val entries: Int = 0
}

public class WithCompanion {
    public companion object {
        public val Default: WithCompanion = WithCompanion()
    }
}

// Interfaces can't hold backing fields: no warning.

public interface Config {
    public val timeout: Int
}

// Not visible outside the library: no warning.

internal class InternalState(val x: Int)

private class PrivateState(val x: Int)

public class Outer {
    internal class Hidden(val x: Int)
}
