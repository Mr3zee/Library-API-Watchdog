// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_TO_STRING -EXEMPTION_WITHOUT_EXPLANATION -INLINE_FUNCTION_WITH_LOGIC -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyUndocumented

// A @PublishedApi declaration is internal in sources but part of the published binary API:
// public inline functions expose it to users, so every check treats it as public API.

// Undocumented published declarations: should warn.

@PublishedApi
internal class <!UNDOCUMENTED_PUBLIC_API!>PublishedClass<!>

@PublishedApi
internal fun <!UNDOCUMENTED_PUBLIC_API!>publishedFunction<!>() {}

@PublishedApi
internal val <!UNDOCUMENTED_PUBLIC_API!>publishedProperty<!>: Int = 0

// Documentation and acknowledgments work on published declarations like on public ones.

/** Documented. */
@PublishedApi
internal class DocumentedPublishedClass

@IntentionallyUndocumented
@PublishedApi
internal class AcknowledgedPublishedClass

// The typical shape: published members inside a public class, backing a public inline function.

/** Documented. */
public class PublicOuterWithPublishedMembers {
    @PublishedApi
    internal fun <!UNDOCUMENTED_PUBLIC_API!>publishedMember<!>() {}

    /** Documented. */
    @PublishedApi
    internal val documentedPublishedMember: Int = 0

    internal fun plainInternalMember() {}

    /** Documented. */
    public inline fun useMembers(block: () -> Unit): Int {
        block()
        publishedMember()
        return documentedPublishedMember
    }
}

// A published secondary constructor is watched like a public one.

/**
 * Documented.
 *
 * @property value Documented via the class KDoc.
 */
public class WithPublishedConstructor private constructor(public val value: Int) {
    <!UNDOCUMENTED_PUBLIC_API!>@PublishedApi internal constructor() : this(0)<!>

    /** Documented published constructor. */
    @PublishedApi internal constructor(a: Int, b: Int) : this(a + b)
}

// Members of a published class are published with it; non-public members stay invisible.

/** Documented. */
@PublishedApi
internal class PublishedOuter {
    fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedMember<!>() {}

    /** Documented. */
    fun documentedMember() {}

    private fun privateMember() {}
}

// Published subclassable API is watched by the open-API check.

/** Documented. */
@PublishedApi
internal open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>PublishedOpenClass<!>

/** Documented. */
@IntentionallyOpen
@PublishedApi
internal open class AcknowledgedPublishedOpenClass

// Published exhaustively matchable API is watched by the exhaustive-API check.

/** Documented. */
@PublishedApi
internal enum class <!EXHAUSTIVE_PUBLIC_API!>PublishedEnum<!> {
    /** Documented. */
    DOCUMENTED_ENTRY,

    <!UNDOCUMENTED_PUBLIC_API!>UNDOCUMENTED_ENTRY<!>,
}

/** Documented. */
@IntentionallyExhaustive
@PublishedApi
internal enum class AcknowledgedPublishedEnum {
    /** Documented. */
    ENTRY,
}

// Without @PublishedApi the same internal shapes stay unwatched.

internal class PlainInternalClass

internal open class PlainInternalOpenClass

internal enum class PlainInternalEnum {
    ENTRY,
}
