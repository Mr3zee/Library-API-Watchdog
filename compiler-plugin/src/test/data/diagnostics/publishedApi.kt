// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -EXEMPTION_WITHOUT_EXPLANATION -INLINE_FUNCTION_WITH_LOGIC -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

// `@PublishedApi` doesn't make a declaration callable in source, so source-API checks skip it.

@PublishedApi
internal class PublishedClass

@PublishedApi
internal fun publishedFunction() {}

@PublishedApi
internal val publishedProperty: Int = 0

/** Documenting a published declaration anyway is fine. */
@PublishedApi
internal class DocumentedPublishedClass

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

/** Documented. */
public class WithPublishedConstructor private constructor(
    /** Documented. */
    public val value: Int,
) {
    @PublishedApi internal constructor() : this(0)
}

@PublishedApi
internal class PublishedOuter {
    fun undocumentedMember() {}

    private fun privateMember() {}
}

@PublishedApi
internal open class PublishedOpenClass

@IntentionallyOpen
@PublishedApi
internal open class AcknowledgedPublishedOpenClass

@SubclassOptInRequired()
@PublishedApi
internal open class PublishedOpenClassWithEmptyOptIn

@PublishedApi
internal enum class PublishedEnum {
    ENTRY,
}

@IntentionallyExhaustive
@PublishedApi
internal enum class AcknowledgedPublishedEnum {
    ENTRY,
}

internal class PlainInternalClass

internal open class PlainInternalOpenClass

internal enum class PlainInternalEnum {
    ENTRY,
}
