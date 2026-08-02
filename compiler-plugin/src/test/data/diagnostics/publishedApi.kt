// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_TO_STRING -EXEMPTION_WITHOUT_EXPLANATION -INLINE_FUNCTION_WITH_LOGIC -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen

// A @PublishedApi declaration is internal in sources but part of the published binary API:
// public inline functions expose it to users, so the checks about its binary shape treat it as
// public API. The KDoc check is not one of them: the declaration stays unreferenceable by name,
// so no documentation is required.

@PublishedApi
internal class PublishedClass

@PublishedApi
internal fun publishedFunction() {}

@PublishedApi
internal val publishedProperty: Int = 0

/** Documenting a published declaration anyway is fine. */
@PublishedApi
internal class DocumentedPublishedClass

// The typical shape: published members inside a public class, backing a public inline function.
// The members need no KDoc, the public class and its inline function do.

/** Documented. */
public class PublicOuterWithPublishedMembers {
    @PublishedApi
    internal fun publishedMember() {}

    @PublishedApi
    internal val publishedMemberProperty: Int = 0

    internal fun plainInternalMember() {}

    /** Documented. */
    public inline fun useMembers(block: () -> Unit): Int {
        block()
        publishedMember()
        return publishedMemberProperty
    }
}

// A published secondary constructor needs no KDoc either.

/**
 * Documented.
 *
 * @property value Documented via the class KDoc.
 */
public class WithPublishedConstructor private constructor(public val value: Int) {
    @PublishedApi internal constructor() : this(0)
}

// Members of a published class are published with it, and stay internal in sources with it: no
// KDoc required anywhere inside.

@PublishedApi
internal class PublishedOuter {
    fun undocumentedMember() {}

    private fun privateMember() {}
}

// Published subclassable API is watched by the open-API check.

@PublishedApi
internal open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>PublishedOpenClass<!>

@IntentionallyOpen
@PublishedApi
internal open class AcknowledgedPublishedOpenClass

// Published exhaustively matchable API is watched by the exhaustive-API check.

@PublishedApi
internal enum class <!EXHAUSTIVE_PUBLIC_API!>PublishedEnum<!> {
    ENTRY,
}

@IntentionallyExhaustive
@PublishedApi
internal enum class AcknowledgedPublishedEnum {
    ENTRY,
}

// Without @PublishedApi the same internal shapes stay unwatched.

internal class PlainInternalClass

internal open class PlainInternalOpenClass

internal enum class PlainInternalEnum {
    ENTRY,
}
