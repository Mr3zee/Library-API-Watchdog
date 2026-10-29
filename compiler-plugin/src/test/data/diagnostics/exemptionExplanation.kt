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
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonTransitiveDependency
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString
import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

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

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyNonTransitiveDependency<!>
public class BareNonTransitiveDependencyExemption

// `@InternalAnnotationMarker` isn't an exemption.

@InternalAnnotationMarker
public annotation class UnexplainedMarker

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(reason = ExemptionReason.OTHER)<!>
public open class ExplicitOtherWithoutDescription

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen("", ExemptionReason.OTHER)<!>
public open class PositionalEmptyDescription

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(description = "   ")<!>
public open class BlankDescription

// Only API_DESIGN, FOR_BACKWARDS_COMPATIBILITY, and FOR_DEPRECATION explain an exemption without a description.

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class ReasonExplainedExemption

@IntentionallyOpen("", ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
public open class PositionalReasonExemption

@IntentionallyOpen(reason = ExemptionReason.FOR_DEPRECATION)
public open class DeprecationReasonExemption

@IntentionallyOpen(description = "extension point for custom transports")
public open class DescriptionExplainedExemption

@IntentionallyOpen("kept open for custom transports")
public open class PositionalDescriptionExemption

@IntentionallyOpen(description = "kept open for mocking", reason = ExemptionReason.OTHER)
public open class OtherWithDescriptionExemption

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen(reason = ExemptionReason.INTEROP)<!>
public open class InteropWithoutDescription

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive(reason = ExemptionReason.EXTERNAL_CONTRACT)<!>
public enum class ExternalContractWithoutDescription { ENTRY }

@IntentionallyOpen(description = "Spring AOP subclasses this.", reason = ExemptionReason.INTEROP)
public open class InteropWithDescription

@IntentionallyExhaustive(description = "Mirrors ISO 4217.", reason = ExemptionReason.EXTERNAL_CONTRACT)
public enum class ExternalContractWithDescription { ENTRY }

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.IGNORE_JAVA_INTEROP)<!>
public suspend fun ignoredInteropWithoutDescription(): Int = 0

@IntentionallyKotlinOnlyApi(description = "Coroutine-first API. Java gets the blocking facade.", reason = ExemptionReason.IGNORE_JAVA_INTEROP)
public suspend fun ignoredInteropWithDescription(): Int = 0

// A constructor property is reported once.

/** Documented holder. */
public class Holder(
    <!EXEMPTION_WITHOUT_EXPLANATION, REDUNDANT_EXEMPTION!>@IntentionallyUndocumented<!> public val bare: Int,
    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!> public val explained: Int,
)

// Every annotation use is checked, in non-public declarations and internal API too.

public fun <<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> T : MutableList<Int>> bareBound(source: T): List<Int> = source.toList()

public fun bareTypeUse(): <!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyMutableCollection<!> MutableList<Int> = mutableListOf()

public fun explainedTypeUse(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int> = mutableListOf()

private fun privateBareTypeUse(): <!EXEMPTION_WITHOUT_EXPLANATION, REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection<!> MutableList<Int> = mutableListOf()

public fun localBareTypeUse(): Unit {
    val items: <!EXEMPTION_WITHOUT_EXPLANATION, REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection<!> MutableList<Int> = mutableListOf()
    items.clear()
}

<!EXEMPTION_WITHOUT_EXPLANATION, REDUNDANT_EXEMPTION!>@IntentionallyOpen<!>
internal open class InternalBareExemption

@InternalAnnotationMarker
public annotation class InternalLibApi

@InternalLibApi
<!EXEMPTION_WITHOUT_EXPLANATION, REDUNDANT_EXEMPTION!>@IntentionallyOpen<!>
public open class ExemptionInsideInternalApi
