// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonStaticCompanionApi

public class Registry {
    public companion object {
        // Reachable from Java only as Registry.Companion.create(): should warn.
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>create<!>(): Registry = Registry()

        // @JvmStatic compiles the static entry point: no warning.
        @JvmStatic
        public fun createStatic(): Registry = Registry()

        @JvmStatic
        public suspend fun createStaticLater(): Registry = Registry()

        // @JvmSynthetic hides the member from Java on purpose: no warning.
        @JvmSynthetic
        public fun createHidden(): Registry = Registry()

        // A suspend member can still be placed on the outer class. Its separate Kotlin-only API
        // diagnostic is muted in this test.
        public suspend fun <!COMPANION_API_WITHOUT_JVM_STATIC!>createLater<!>(): Registry = Registry()

        // Not visible outside the library: no warning.
        internal fun createInternal(): Registry = Registry()

        // A constant-shaped val is only reachable through the Companion instance getter.
        public val <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>DEFAULT_NAME<!>: String = "registry"

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

        // Every property shape can expose static accessors with @JvmStatic.

        public val <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>computed<!>: String get() = "computed"

        public var <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>mutable<!>: String = "mutable"

        public val <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>delegated<!>: String by lazy { "lazy" }

        public lateinit var <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>late<!>: String

        public val String.<!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>qualified<!>: String get() = this

        @JvmStatic
        public var staticMutable: String = "static"

        @JvmStatic
        public val staticComputed: String get() = "computed"

        @JvmStatic
        public val staticDelegated: String by lazy { "lazy" }

        @JvmStatic
        public lateinit var staticLate: String

        @JvmStatic
        public val String.staticQualified: String get() = this

        @JvmField
        public var mutableField: String = "field"

        // A var is clean only when every supported Java accessor is static or hidden.

        @get:JvmStatic
        public var <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>staticRead<!>: String = "read"

        @set:JvmStatic
        public var <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>staticWrite<!>: String = "write"

        @get:JvmStatic
        @set:JvmSynthetic
        public var staticReadOnly: String = "read"

        @get:JvmSynthetic
        @set:JvmStatic
        public var staticWriteOnly: String = "write"

        @get:JvmStatic
        public var privatelySet: String = "private"
            private set
    }
}

// Interface companions compile the same way.
public interface Codec {
    public companion object {
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>lookup<!>(): Int = 0
    }
}

// Companion overrides can add a static bridge on the outer class.

public fun interface Maker {
    public fun make(): Int
}

public class Built {
    public companion object : Maker {
        override fun <!COMPANION_API_WITHOUT_JVM_STATIC!>make<!>(): Int = 0
    }
}

public class StaticBuilt {
    public companion object : Maker {
        @JvmStatic
        override fun make(): Int = 0
    }
}

public interface Named {
    public val name: String
}

public class NamedBuilt {
    public companion object : Named {
        override val <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>name<!>: String = "built"
    }
}

public class StaticNamedBuilt {
    public companion object : Named {
        @JvmStatic
        override val name: String = "built"
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

// The exemption on the outer class covers its companion members as well.
@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
public class AcknowledgedOuter {
    public companion object {
        public fun create(): AcknowledgedOuter = AcknowledgedOuter()

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

// @PublishedApi members are binary implementation details, not supported Java API.

public class PublishedMembers {
    public companion object {
        @PublishedApi
        internal fun create(): PublishedMembers = PublishedMembers()

        @PublishedApi
        internal val DEFAULT_NAME: String = "published"
    }
}
