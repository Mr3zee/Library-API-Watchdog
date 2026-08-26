// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -NOTHING_TO_INLINE

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

/**
 * A value class used to exercise JVM name mangling.
 *
 * @property value wrapped identifier.
 */
@JvmInline
public value class UserId(public val value: String)

/** Marks declarations that have no supported API contract. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class InternalApi

@InternalApi
public class InternalType

/** Anchors the file-facade diagnostic, whose top-level scope cannot be protected. */
public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>facadeAnchor<!>(): Int = 0

/** Hosts protected declarations for every declaration-level API check. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class ProtectedApi {
    /** An unrestricted protected subclassing point. */
    protected open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>OpenType<!>

    /** A protected subclassing point whose opt-in annotation restricts nothing. */
    <!SUBCLASS_OPT_IN_WITHOUT_MARKERS!>@SubclassOptInRequired<!>
    protected open class EmptyOptIn

    /** A protected exhaustively matchable type. */
    protected enum class <!EXHAUSTIVE_PUBLIC_API!>Status<!> {
        /** The active state. */
        ACTIVE,
    }

    /** A protected alias that erases to a function type. */
    protected typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>Callback<!> = () -> Unit

    /**
     * A protected data class.
     *
     * @property x coordinate.
     */
    protected data class <!DATA_CLASS_PUBLIC_API!>Point<!>(public val x: Int)

    /**
     * A protected stateful class.
     *
     * @property value stored state.
     */
    protected class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>Stateful<!>(
        public val value: Int,
    )

    protected fun <!UNDOCUMENTED_PUBLIC_API!>undocumented<!>(): Unit {}

    /** Exposes mutable state from protected API. */
    protected fun mutableValues(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<String><!> = mutableListOf()

    /** Exposes an unnamed tuple from protected API. */
    protected fun coordinates(): <!PAIR_OR_TRIPLE_PUBLIC_API!>Pair<Int, Int><!> = 0 to 0

    /** Exposes a nullable Boolean from protected API. */
    protected fun decision(): <!NULLABLE_BOOLEAN_PUBLIC_API!>Boolean?<!> = null

    /** Takes a Boolean in protected API. */
    protected fun booleanArgument(<!BOOLEAN_PARAMETER_PUBLIC_API!>enabled<!>: Boolean): Unit {}

    /** Places a required parameter behind an optional one in protected API. */
    @JvmOverloads
    protected fun request(retries: Int = 3, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String): Unit {}

    /** Establishes one protected overload order. */
    protected fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>move<!>(x: Int, y: Int): Unit {}

    /** Reverses the protected overload order. */
    protected fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>move<!>(y: Int, x: Int, scale: Long): Unit {}

    /** Freezes logic into protected callers. */
    protected inline fun <!INLINE_FUNCTION_WITH_LOGIC!>doubled<!>(value: Int): Int = value + value

    /** Produces a mangled protected JVM name. */
    protected fun <!MANGLED_JVM_NAME_PUBLIC_API!>take<!>(id: UserId): Unit {}

    /** Exposes a Kotlin-only protected JVM method. */
    protected suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>load<!>(): String = "loaded"

    /** Omits Java overloads from protected API. */
    protected fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>connect<!>(port: Int = 80): Unit {}

    /** Exposes an unsupported type from protected API. */
    protected fun internalType(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()

    /** Hosts companion members whose effective visibility is protected. */
    protected class CompanionHost {
        /** Protected-effective companion API. */
        public companion object {
            /** Has no static Java entry point. */
            public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>create<!>(): CompanionHost = CompanionHost()

            /** Has no static Java accessor. */
            public val <!COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS!>instanceValue<!>: Int = 1
        }
    }
}

// A written public modifier inside an internal container is not effective public API.
internal class InternalContainer {
    public data class HiddenData(public val value: Int)

    public fun hiddenBoolean(enabled: Boolean): Unit {}
}
