// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -MUTABLE_COLLECTION_PUBLIC_API -PAIR_OR_TRIPLE_PUBLIC_API -BOOLEAN_PARAMETER_PUBLIC_API -NULLABLE_BOOLEAN_PUBLIC_API -MANGLED_JVM_NAME_PUBLIC_API -REQUIRED_PARAMETER_AFTER_OPTIONAL -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -FUNCTION_TYPE_ALIAS_PUBLIC_API -INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS

// The probe reads an exemption at every position of a signature. The directive hides the other
// diagnostics, so the markers are about the exemptions only.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyPairOrTriple
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyRequiredParameterAfterOptional
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutJvmOverloads

// The collection check doesn't visit the expanded type of a type alias.

public typealias Buffers = List<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableSet<Int>>

public typealias Views = List<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> Set<Int>>

// The collection check doesn't visit a supertype.

public abstract class BufferSupertype : AbstractList<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>>()

public abstract class ViewSupertype : AbstractList<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int>>()

// A type parameter of a class

public class NeededBox<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) C : MutableCollection<Int>>

public class ViewBox<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> C : Collection<Int>>

// A secondary constructor parameter

public class Sink {
    public constructor(@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) target: MutableList<Int>)

    public constructor(<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> source: List<Int>, limit: Int)
}

// A vararg parameter

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun neededFlags(vararg flags: Boolean) {}

public fun counts(<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> vararg counts: Int) {}

// A property type and a getter. The collection check doesn't report a receiver.

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public val neededItems: MutableList<Int> = mutableListOf()

public val viewItems: <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int> = emptyList()

public val <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>.bufferSize: Int
    get() = size

public val <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int>.viewSize: Int
    get() = size

public fun <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>.bufferDrain() {}

public fun <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> List<Int>.viewDrain() {}

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public val neededComputed: MutableList<Int>
    get() = mutableListOf()

// Use-site targets on a constructor property

public class TargetedHolder(
    @property:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) public val items: MutableList<Int>,
    @param:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) public val others: MutableList<Int>,
    <!REDUNDANT_EXEMPTION!>@property:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> public val views: List<Int>,
    <!REDUNDANT_EXEMPTION!>@param:IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> public val otherViews: List<Int>,
)

// Two exemptions of different checks on one declaration

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
<!REDUNDANT_EXEMPTION!>@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)<!>
public fun neededBufferOnly(): MutableList<Int> = mutableListOf()

// A function-level exemption covers every parameter.

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun allCovered(
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> on: Boolean,
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> off: Boolean,
) {}

// A `@Suppress` hides the diagnostic after the probe, so the exemption next to it isn't redundant.

@Suppress("MUTABLE_COLLECTION_PUBLIC_API")
@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public fun suppressedBuffer(): MutableList<Int> = mutableListOf()

// Constructors

public class Client @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.API_DESIGN) constructor(retries: Int = 3, host: String)

public class Server <!REDUNDANT_EXEMPTION!>@IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.API_DESIGN)<!> constructor(host: String, retries: Int = 3)

public class Pool @IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN) constructor(size: Int = 1)

public class Fixed <!REDUNDANT_EXEMPTION!>@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)<!> constructor(size: Int)
