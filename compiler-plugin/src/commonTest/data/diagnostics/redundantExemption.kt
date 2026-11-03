// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API -DATA_CLASS_PUBLIC_API -FUNCTION_TYPE_ALIAS_PUBLIC_API -MUTABLE_COLLECTION_PUBLIC_API -PAIR_OR_TRIPLE_PUBLIC_API -BOOLEAN_PARAMETER_PUBLIC_API -NULLABLE_BOOLEAN_PUBLIC_API -REQUIRED_PARAMETER_AFTER_OPTIONAL -INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS -INLINE_FUNCTION_WITH_LOGIC -MANGLED_JVM_NAME_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -COMPANION_API_WITHOUT_JVM_STATIC -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -DSL_MARKER_NOOP_TARGET -DSL_MARKER_WITHOUT_EXPLICIT_TARGETS -NOTHING_TO_INLINE

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyFunctionTypeAlias
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInconsistentParameterOrder
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonStaticCompanionApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyPairOrTriple
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyRequiredParameterAfterOptional
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEquals
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutJvmOverloads
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class NeededOpen

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public class FinalWithOpenExemption

@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public enum class NeededExhaustive { A }

<!REDUNDANT_EXEMPTION!>@IntentionallyExhaustive(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>
public class NotExhaustive

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public data class NeededData(val x: Int)

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public class NotData(public val x: Int)

@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
public class NeededOpaque(public val token: String)

@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)
<!REDUNDANT_EXEMPTION!>@IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)<!>
public class DoublyOpaque(public val token: String)

<!REDUNDANT_EXEMPTION!>@IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)<!>
public class ProvidesEquals(public val token: String) {
    override fun equals(other: Any?): Boolean = other is ProvidesEquals && other.token == token
}

<!REDUNDANT_EXEMPTION!>@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.API_DESIGN)<!>
public object Stateless

/** Documented. */
<!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(description = "Self-explanatory.")<!>
public class Documented

@IntentionallyUndocumented(description = "Self-explanatory.")
public class Undocumented

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
internal data class HiddenData(val x: Int)

@IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
public typealias NeededCallback = (Int) -> Unit

<!REDUNDANT_EXEMPTION!>@IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)<!>
public typealias Names = List<String>

<!REDUNDANT_EXEMPTION!>@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility<!>
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
public annotation class EffectiveMarker

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
@Target(AnnotationTarget.FUNCTION)
public annotation class NoopMarker

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public fun neededBuffer(): MutableList<Int> = mutableListOf()

<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
public fun readOnlyView(): List<Int> = emptyList()

public fun fill(@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) target: MutableList<Int>) {}

public fun read(<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> source: List<Int>) {}

public fun neededNested(): List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableSet<Int>> = emptyList()

public fun readOnlyNested(): List<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> Set<Int>> = emptyList()

public fun <@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) C : MutableCollection<Int>> fillAll(target: C): C = target

public fun <<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> C : Collection<Int>> readAll(source: C): C = source

public class NeededHolder(@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) public val items: MutableList<Int>)

public class ReadOnlyHolder(<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> public val items: List<Int>)

public class NeededTypedHolder(public val items: List<@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) MutableSet<Int>>)

public class ReadOnlyTypedHolder(public val items: List<<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> Set<Int>>)

<!REDUNDANT_EXEMPTION!>@IntentionallyPairOrTriple(reason = ExemptionReason.API_DESIGN)<!>
public fun origin(): Int = 0

<!REDUNDANT_EXEMPTION!>@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)<!>
public fun known(): Boolean = true

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun doublyToggle(<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> on: Boolean) {}

public fun doublyFill(@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN) target: <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!> MutableList<Int>) {}


@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun neededToggle(enabled: Boolean) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
public fun count(n: Int) {}

public fun mixed(
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> n: Int,
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN) on: Boolean,
) {}

public class Switch(<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!> on: Boolean)

@IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.API_DESIGN)
public fun neededOrder(retries: Int = 3, host: String) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.API_DESIGN)<!>
public fun ordered(host: String, retries: Int = 3) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)<!>
public fun alone(x: Int, y: Int) {}

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)
public fun shape(x: Int, y: Int) {}

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)
public fun shape(y: Int, x: Int, scale: Double) {}

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun neededInline(x: Int): Int = if (x > 0) x else -x

<!REDUNDANT_EXEMPTION!>@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)<!>
public inline fun thin(block: () -> Unit): Unit = block()

@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)
public fun neededDefaults(x: Int = 0) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyWithoutJvmOverloads(reason = ExemptionReason.API_DESIGN)<!>
public fun noDefaults(x: Int) {}

@JvmInline
public value class Id(public val raw: Int)

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public fun neededMangled(id: Id) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public fun plainName(id: Int) {}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public suspend fun neededSuspend() {}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public fun blocking() {}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class NeededIds {
    public fun id(): Id = Id(1)
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class NeededNestedIds {
    public class Inner {
        public fun id(): Id = Id(1)
    }
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public class PlainNames {
    public fun id(): Int = 1

    public class Inner {
        public val raw: Int = 1
    }
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class NeededSuspending {
    public suspend fun load() {}
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public class Blocking {
    public fun load() {}
}

public class NeededCompanion {
    @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
    public companion object {
        public fun create(): NeededCompanion = NeededCompanion()
    }
}

public class StaticCompanion {
    <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
    public companion object {
        @JvmStatic
        public fun create(): StaticCompanion = StaticCompanion()
    }
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class CoveredIds {
    <!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
    public fun id(): Id = Id(1)
}

// FILE: namedFacade.kt
@file:JvmName("Facades")
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public fun facadeMember(): Int = 1

// FILE: defaultFacade.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public fun defaultFacadeMember(): Int = 1
