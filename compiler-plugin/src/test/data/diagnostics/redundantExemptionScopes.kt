// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API -DATA_CLASS_PUBLIC_API -FUNCTION_TYPE_ALIAS_PUBLIC_API -MANGLED_JVM_NAME_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -COMPANION_API_WITHOUT_JVM_STATIC -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -DSL_MARKER_NOOP_TARGET -DSL_MARKER_WITHOUT_EXPLICIT_TARGETS

// A check skips some declarations by their kind or position. An exemption on them is redundant.
// The directive hides the other diagnostics, so the markers are about the exemptions only.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyFunctionTypeAlias
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonStaticCompanionApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility

// The Kotlin-only check skips interface members.

public interface Loader {
    <!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
    public suspend fun load()
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public interface Suspending {
    public suspend fun run()
}

// `@JvmName` doesn't apply inside a value class, so the mangling check skips its members.

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
@JvmInline
public value class Wrapped(public val raw: Int) {
    public fun twin(): Wrapped = this
}

@JvmInline
public value class Id(public val raw: Int)

// The open check skips a class with `@SubclassOptInRequired`, a sealed class, an enum, and an
// object. It reports an interface, an abstract class, and a `fun interface`.

@RequiresOptIn
public annotation class Experimental

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
@SubclassOptInRequired(Experimental::class)
public open class OptInBase

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public sealed class Shape

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public enum class Color { RED }

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public object Singleton

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public interface Plugin

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public abstract class Base

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public fun interface Callback {
    public fun call()
}

// The undocumented check skips an override and a primary constructor.

public abstract class Documented {
    /** Runs. */
    public abstract fun run()
}

public class Runner : Documented() {
    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(description = "The supertype documents it.")<!>
    override fun run() {}
}

/** A holder. */
public class Holder <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(description = "The class KDoc covers it.")<!> constructor(public val x: Int)

// The DSL marker check skips an annotation without `@DslMarker`. The exemption covers a marker
// without explicit targets.

<!REDUNDANT_EXEMPTION!>@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility<!>
@Target(AnnotationTarget.FUNCTION)
public annotation class PlainAnnotation

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
public annotation class UntargetedMarker

// A private type alias isn't public API.

<!REDUNDANT_EXEMPTION!>@IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)<!>
private typealias Handler = (Int) -> Unit

// The outer class covers a nested class, so the same exemption on the nested class is redundant.

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class OuterIds {
    <!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
    public class Inner {
        public fun id(): Id = Id(1)
    }
}

// A class-level exemption covers a secondary constructor.

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class FromId {
    public constructor(id: Id)
}

// A companion-level exemption that only a property needs.

public class Registry {
    @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
    public companion object {
        public val default: Registry = Registry()
    }
}

// The probe skips a local declaration.

public fun compute(): Int {
    @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
    data class Point(val x: Int)
    return Point(1).x
}

// FILE: classesOnly.kt
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

// A file without a top-level callable has no facade class.
public class OnlyClass

// FILE: syntheticOnly.kt
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

// Java doesn't see a `@JvmSynthetic` callable, so the facade name doesn't matter.
@JvmSynthetic
public fun hidden(): Int = 1
