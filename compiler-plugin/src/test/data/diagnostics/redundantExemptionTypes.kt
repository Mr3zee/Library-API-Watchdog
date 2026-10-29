// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -DATA_CLASS_PUBLIC_API -MUTABLE_COLLECTION_PUBLIC_API -NULLABLE_BOOLEAN_PUBLIC_API -PAIR_OR_TRIPLE_PUBLIC_API -BOOLEAN_PARAMETER_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

// Exemptions on types and in unusual annotation syntax.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyPairOrTriple

public fun deep(): Map<String, List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int>>> = emptyMap()

public fun deepView(): Map<String, List<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int>>> = emptyMap()

public fun nullableBuffer(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int>? = null

public fun nullableView(): <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int>? = null

public fun projected(): Array<out <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>> = emptyArray()

public fun starProjected(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<*>> = emptyList()

public fun callback(block: (@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int>) -> Unit) {}

public fun supplier(block: () -> @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int>) {}

public fun receiverBlock(block: <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>.() -> Unit) {}

public fun viewCallback(block: (<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int>) -> Unit) {}

public typealias Buffer = MutableList<Int>

public fun aliasedBuffer(): @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) Buffer = mutableListOf()

public typealias View = List<Int>

public fun aliasedView(): <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> View = emptyList()

public fun pairInsideTriple(): Triple<Int, <!REDUNDANT_EXEMPTION!>@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)<!> Pair<Int, Int>, Int> = Triple(1, 1 to 1, 1)

public fun maybe(): Map<String, @IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN) Boolean?> = emptyMap()

public fun known(): Map<String, <!REDUNDANT_EXEMPTION!>@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)<!> Boolean> = emptyMap()

context(@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) buffer: MutableList<Int>)
public fun fillContext() {}

context(buffer: @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int>)
public fun fillContextType() {}

context(<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> view: List<Int>)
public fun readContext() {}

public fun flags(vararg flags: <!REDUNDANT_EXEMPTION!>@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)<!> Boolean) {}

public fun counts(vararg counts: <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> Int) {}

public fun buffers(vararg buffers: @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableList<Int>) {}

public fun views(vararg views: <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int>) {}

public fun nullableFlags(vararg flags: @IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN) Boolean?) {}

@[<!REDUNDANT_EXEMPTION!>IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!> <!REDUNDANT_EXEMPTION!>IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>]
public class Listed

@[IntentionallyOpen(reason = ExemptionReason.API_DESIGN) <!REDUNDANT_EXEMPTION!>IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>]
public open class ListedOpen

public typealias Opened = IntentionallyOpen

<!REDUNDANT_EXEMPTION!>@Opened(reason = ExemptionReason.API_DESIGN)<!>
public class AliasedExemption

@Opened(reason = ExemptionReason.API_DESIGN)
public open class AliasedNeededExemption

<!REDUNDANT_EXEMPTION!>@org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public class Qualified

@IntentionallyOpen("Subclassing is the extension point.", ExemptionReason.API_DESIGN)
public open class Positional

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen("Subclassing is the extension point.", ExemptionReason.API_DESIGN)<!>
public class PositionalFinal

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen<!>
public class Bare
