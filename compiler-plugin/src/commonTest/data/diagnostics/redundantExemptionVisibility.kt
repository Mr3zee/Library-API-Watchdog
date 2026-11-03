// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INTERNAL_API_ANNOTATIONS: foo.bar.ConfiguredInternal
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -MUTABLE_COLLECTION_PUBLIC_API -BOOLEAN_PARAMETER_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -MANGLED_JVM_NAME_PUBLIC_API -DATA_CLASS_PUBLIC_API -PUBLIC_TYPE_WITH_INTERNAL_API -DEPRECATION

// A declaration that isn't public API isn't checked, so every exemption on it is redundant. `protected` is
// public API in a final class too. A `@PublishedApi internal` declaration is binary API only, and the
// source-shape checks skip it.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
public annotation class MarkedInternal

public annotation class ConfiguredInternal

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
internal fun internalToggle(on: Boolean) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
private fun privateToggle(on: Boolean) {}

public class FinalHost {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    protected fun protectedInFinal(on: Boolean) {}

    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    private fun privateMember(on: Boolean) {}

    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun publicMember(on: Boolean) {}
}

public open class OpenHost {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    protected fun protectedInOpen(on: Boolean) {}
}

public abstract class AbstractHost {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    protected abstract fun protectedInAbstract(on: Boolean)
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
@PublishedApi
internal fun publishedBuffer(): MutableList<Int> = mutableListOf()

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
@PublishedApi
internal fun publishedToggle(on: Boolean) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
@MarkedInternal
public fun markedToggle(on: Boolean) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
@ConfiguredInternal
public fun configuredToggle(on: Boolean) {}

@MarkedInternal
public class MarkedHost {
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    public fun toggle(on: Boolean) {}

    <!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
    public data class Nested(val x: Int)
}

internal class InternalHost {
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    public fun toggle(on: Boolean) {}

    <!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
    public data class Nested(val x: Int)

    public class Deeper {
        <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
        public fun toggle(on: Boolean) {}
    }
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class HiddenSuspending {
    internal suspend fun load() {}

    private suspend fun fetch() {}

    protected suspend fun sync() {}
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
internal class InternalSuspending {
    public suspend fun load() {}
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
@MarkedInternal
public class MarkedSuspending {
    public suspend fun load() {}
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public class PublishedSuspending {
    @PublishedApi
    internal suspend fun load() {}
}

public fun compute(): Int {
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    fun local(on: Boolean): Int = if (on) 1 else 0

    val anonymous = object {
        <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
        fun toggle(on: Boolean): Int = if (on) 1 else 0
    }
    return local(true) + anonymous.toggle(false)
}

@Deprecated("Use compute.", level = DeprecationLevel.HIDDEN)
@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun hiddenToggle(on: Boolean) {}

@Suppress("REDUNDANT_EXEMPTION")
@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun suppressedCount(n: Int) {}
