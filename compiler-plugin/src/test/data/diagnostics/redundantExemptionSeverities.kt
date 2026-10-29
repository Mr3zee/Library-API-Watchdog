// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTIC_SEVERITIES: STATEFUL_CLASS_WITHOUT_EQUALS:none OPEN_API_WITHOUT_SUBCLASS_OPT_IN:none COMPANION_API_WITHOUT_JVM_STATIC:none DSL_MARKER_NOOP_TARGET:none DATA_CLASS_PUBLIC_API:none MUTABLE_COLLECTION_PUBLIC_API:warning
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -SUBCLASS_OPT_IN_WITHOUT_MARKERS -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -DSL_MARKER_WITHOUT_EXPLICIT_TARGETS -MUTABLE_COLLECTION_PUBLIC_API

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonStaticCompanionApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEquals
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility

@IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)
public class NeededWithoutEquals(public val token: String) {
    override fun hashCode(): Int = token.hashCode()
    override fun toString(): String = token
}

<!REDUNDANT_EXEMPTION!>@IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)<!>
public class ProvidesEquals(public val token: String) {
    override fun equals(other: Any?): Boolean = other is ProvidesEquals && other.token == token
    override fun hashCode(): Int = token.hashCode()
    override fun toString(): String = token
}

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class NeededOpen

<!REDUNDANT_EXEMPTION!>@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)<!>
public class FinalWithOpenExemption

public class Factory {
    public companion object {
        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        public fun create(): Factory = Factory()

        <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
        @JvmStatic
        public fun createStatic(): Factory = Factory()
    }
}

public class NeededFactory {
    @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
    public companion object {
        public fun create(): NeededFactory = NeededFactory()
    }
}

public class StaticFactory {
    <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
    public companion object {
        @JvmStatic
        public fun create(): StaticFactory = StaticFactory()
    }
}

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
@Target(AnnotationTarget.FUNCTION)
public annotation class NoopMarker

<!REDUNDANT_EXEMPTION!>@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility<!>
@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class EffectiveMarker

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public class NotData

<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
public fun readOnlyView(): List<Int> = emptyList()

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public fun neededBuffer(): MutableList<Int> = mutableListOf()
