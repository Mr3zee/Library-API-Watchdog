// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API -DATA_CLASS_PUBLIC_API -FUNCTION_TYPE_ALIAS_PUBLIC_API -MANGLED_JVM_NAME_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -COMPANION_API_WITHOUT_JVM_STATIC -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -DSL_MARKER_NOOP_TARGET -DSL_MARKER_WITHOUT_EXPLICIT_TARGETS

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

public interface Loader {
    <!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
    public suspend fun load()
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public interface Suspending {
    public suspend fun run()
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
@JvmInline
public value class Wrapped(public val raw: Int) {
    public fun twin(): Wrapped = this
}

@JvmInline
public value class Id(public val raw: Int)

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

<!REDUNDANT_EXEMPTION!>@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility<!>
@Target(AnnotationTarget.FUNCTION)
public annotation class PlainAnnotation

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
public annotation class UntargetedMarker

<!REDUNDANT_EXEMPTION!>@IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)<!>
private typealias Handler = (Int) -> Unit

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class OuterIds {
    <!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
    public class Inner {
        public fun id(): Id = Id(1)
    }
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class FromId {
    public constructor(id: Id)
}

public class Registry {
    @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
    public companion object {
        public val default: Registry = Registry()
    }
}

public fun compute(): Int {
    <!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
    data class Point(val x: Int)
    return Point(1).x
}

// FILE: classesOnly.kt
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public class OnlyClass

// FILE: syntheticOnly.kt
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

@JvmSynthetic
public fun hidden(): Int = 1
