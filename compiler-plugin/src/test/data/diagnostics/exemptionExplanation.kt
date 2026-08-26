// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyFunctionTypeAlias
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString
import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

// A bare exemption defaults to reason = OTHER with an empty description, which explains
// nothing: should warn.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen<!>
public open class BareOpenExemption

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive<!>
public enum class BareExhaustiveExemption { ENTRY }

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyUndocumented<!>
public class BareUndocumentedExemption

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyFunctionTypeAlias<!>
public typealias BareAliasExemption = (Int) -> Unit

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyDataClass<!>
public data class BareDataClassExemption(val x: Int)

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyWithoutEqualsHashCodeOrToString<!>
public class BareCombinedStatefulExemption(public val x: Int)

// @InternalAnnotationMarker needs no explanation: the marked annotation class documents the
// internal API surface itself.

@InternalAnnotationMarker
public annotation class UnexplainedMarker

// Spelling the unexplained default out changes nothing: should warn.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(reason = ExemptionReason.OTHER)<!>
public open class ExplicitOtherWithoutDescription

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(ExemptionReason.OTHER, "")<!>
public open class PositionalEmptyDescription

// A whitespace-only description explains nothing either: should warn.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(description = "   ")<!>
public open class BlankDescription

// A specific reason explains the exemption by itself: no warning.

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class ReasonExplainedExemption

@IntentionallyOpen(ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public open class PositionalReasonExemption

// A non-empty description explains the exemption even with the OTHER reason: no warning.

@IntentionallyOpen(description = "extension point for custom transports")
public open class DescriptionExplainedExemption

@IntentionallyOpen(reason = ExemptionReason.OTHER, description = "kept open for mocking")
public open class OtherWithDescriptionExemption

// INTEROP and EXTERNAL_CONTRACT only categorize the exemption - which constraint applies is
// not obvious from the entry alone - so they still require a description.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(reason = ExemptionReason.INTEROP)<!>
public open class InteropWithoutDescription

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive(reason = ExemptionReason.EXTERNAL_CONTRACT)<!>
public enum class ExternalContractWithoutDescription { ENTRY }

@IntentionallyOpen(reason = ExemptionReason.INTEROP, description = "Spring AOP subclasses this.")
public open class InteropWithDescription

@IntentionallyExhaustive(reason = ExemptionReason.EXTERNAL_CONTRACT, description = "Mirrors ISO 4217.")
public enum class ExternalContractWithDescription { ENTRY }

// IGNORE_JAVA_INTEROP categorizes the same way: why this particular declaration gets to ignore
// Java callers is not obvious from the entry alone, so it still requires a description.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.IGNORE_JAVA_INTEROP)<!>
public suspend fun ignoredInteropWithoutDescription(): Int = 0

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.IGNORE_JAVA_INTEROP, description = "Coroutine-first API; Java gets the blocking facade.")
public suspend fun ignoredInteropWithDescription(): Int = 0

// Exemptions on properties from constructor parameters are validated once, on the property.

/** Documented holder. */
public class Holder(
    <!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyUndocumented<!> public val bare: Int,
    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN) public val explained: Int,
)

// Exemptions on type parameters and type usages must explain themselves too. A dedicated type
// checker validates the type-use form, including outside watched public API.

public fun <<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> T : MutableList<Int>> bareBound(source: T): List<Int> = source.toList()

public fun bareTypeUse(): <!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> MutableList<Int> = mutableListOf()

public fun explainedTypeUse(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int> = mutableListOf()

private fun privateBareTypeUse(): <!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> MutableList<Int> = mutableListOf()

public fun localBareTypeUse(): Unit {
    val items: <!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> MutableList<Int> = mutableListOf()
    items.clear()
}

// The check validates the annotation call itself, so it also fires on declarations that are
// not watched public API: non-public declarations and internal API subtrees.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen<!>
internal open class InternalBareExemption

@InternalAnnotationMarker
public annotation class InternalLibApi

@InternalLibApi
<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen<!>
public open class ExemptionInsideInternalApi
