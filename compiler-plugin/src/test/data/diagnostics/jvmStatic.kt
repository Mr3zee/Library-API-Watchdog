// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyNonStaticCompanionApi

public class Registry {
    public companion object {
        // Reachable from Java only as Registry.Companion.create(): should warn.
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>create<!>(): Registry = Registry()

        // @JvmStatic compiles the static entry point: no warning.
        @JvmStatic
        public fun createStatic(): Registry = Registry()

        // @JvmSynthetic hides the member from Java on purpose: no warning.
        @JvmSynthetic
        public fun createHidden(): Registry = Registry()

        // suspend members are Kotlin-only sugar with their own diagnostic (muted here).
        public suspend fun createLater(): Registry = Registry()

        // Not visible outside the library: no warning.
        internal fun createInternal(): Registry = Registry()

        // A constant-shaped val is only reachable through the Companion instance getter.
        public val <!COMPANION_CONSTANT_WITHOUT_JVM_FIELD!>DEFAULT_NAME<!>: String = "registry"

        // const, @JvmField, and a @JvmStatic getter expose the value statically: no warning.

        public const val VERSION: Int = 1

        @JvmField
        public val ORIGIN: String = "field"

        @JvmStatic
        public val EXPOSED: String = "static getter"

        @get:JvmStatic
        public val TARGETED: String = "static getter"

        // @get:JvmSynthetic hides the property from Java on purpose: no warning.
        @get:JvmSynthetic
        public val HIDDEN: String = "kotlin only"

        // Custom getters, vars, and delegates expose behavior, not a constant: no warning.

        public val computed: String get() = "computed"

        public var mutable: String = "mutable"

        public val delegated: String by lazy { "lazy" }
    }
}

// Interface companions compile the same way.
public interface Codec {
    public companion object {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>lookup<!>(): Int = 0
    }
}

// An override's Java-facing shape is fixed by the overridden declaration: no warning.

public fun interface Maker {
    public fun make(): Int
}

public class Built {
    public companion object : Maker {
        override fun make(): Int = 0
    }
}

// Acknowledged members: no warning.

public class Acknowledged {
    public companion object {
        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        public fun create(): Acknowledged = Acknowledged()

        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        public val DEFAULT_NAME: String = "acknowledged"
    }
}

// The exemption on the companion object covers every member inside.
public class AcknowledgedCompanion {
    @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
    public companion object {
        public fun create(): AcknowledgedCompanion = AcknowledgedCompanion()

        public val DEFAULT_NAME: String = "acknowledged"
    }
}

// Named objects and plain class members are not companion members: no warning.

public object Singleton {
    public fun make(): Int = 0

    public val NAME: String = "singleton"
}

public class Plain {
    public fun member(): Int = 0

    public val value: Int = 0
}
