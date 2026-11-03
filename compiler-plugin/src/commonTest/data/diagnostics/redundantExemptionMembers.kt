// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API -DATA_CLASS_PUBLIC_API -MANGLED_JVM_NAME_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -COMPANION_API_WITHOUT_JVM_STATIC -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -INLINE_FUNCTION_WITH_LOGIC -BOOLEAN_PARAMETER_PUBLIC_API -MUTABLE_COLLECTION_PUBLIC_API -NOTHING_TO_INLINE

// Class-level exemptions and the members that need them, and member shapes the checks skip.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonStaticCompanionApi

@JvmInline
public value class Id(public val raw: Int)

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class CompanionIds {
    public companion object {
        public fun id(): Id = Id(1)
    }
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class ObjectIds {
    public object Factory {
        public fun id(): Id = Id(1)
    }
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class PropertyId {
    public val id: Id = Id(1)
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class ConstructorPropertyId(public val id: Id)

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class SecondaryConstructorId {
    public constructor(id: Id)

    public constructor(raw: Int)
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class DeepIds {
    public class Middle {
        public class Inner {
            public fun id(): Id = Id(1)
        }
    }
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public class NamedIds {
    @JvmName("identifier")
    public fun id(): Id = Id(1)
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public class SyntheticIds {
    @JvmSynthetic
    public fun id(): Id = Id(1)
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public class SuspendingIds {
    public suspend fun id(): Id = Id(1)
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public class BoxedIds {
    public fun ids(): List<Id> = emptyList()
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public interface IdSource {
    public fun id(): Id
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public suspend fun suspendId(id: Id) {}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class DeepSuspending {
    public class Middle {
        public class Inner {
            public suspend fun go() {}
        }
    }
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class CompanionSuspending {
    public companion object {
        public suspend fun go() {}
    }
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public class SyntheticSuspending {
    @JvmSynthetic
    public suspend fun go() {}
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public enum class Modes {
    FAST {
        public suspend fun go() {}
    };
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class ReifiedOnly {
    public inline fun <reified T> make(): T? = null
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public class ReifiedMember {
    public inline fun <reified T> make(): T? = null

    public fun plain(): Int = 1
}

@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
public class OuterCompanion {
    public companion object {
        public fun create(): OuterCompanion = OuterCompanion()
    }
}

<!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
public class StaticOuterCompanion {
    public companion object {
        @JvmStatic
        public fun create(): StaticOuterCompanion = StaticOuterCompanion()
    }
}

<!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
public class NoCompanion {
    public fun create(): NoCompanion = NoCompanion()
}

public object Registry {
    <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
    public fun create(): Registry = this

    <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
    public val default: Registry = this
}

public class Props {
    public companion object {
        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        public val plain: Int = 1

        <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
        public const val CONSTANT: Int = 1

        <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
        @JvmField
        public val field: Int = 1

        <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
        @JvmStatic
        public val static: Int = 1

        <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
        public lateinit var late: String

        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
        @get:JvmStatic
        public var half: Int = 1

        <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
        @get:JvmSynthetic
        public val hidden: Int = 1

        <!REDUNDANT_EXEMPTION!>@IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)<!>
        private val secret: Int = 1
    }
}

public class NamedProps {
    @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.API_DESIGN)
    public companion object Factory {
        public fun create(): NamedProps = NamedProps()
    }
}

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline val logicGetter: Int
    get() {
        val base = 1
        return base + 1
    }

<!REDUNDANT_EXEMPTION!>@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)<!>
public inline val thinGetter: Int
    get() = 1

<!REDUNDANT_EXEMPTION!>@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)<!>
public val plainGetter: Int
    get() {
        val base = 1
        return base + 1
    }

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public var logicSetter: Int
    get() = 1
    inline set(value) {
        val doubled = value * 2
        check(doubled >= 0)
    }

<!REDUNDANT_EXEMPTION!>@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)<!>
public fun notInline(): Int {
    val base = 1
    return base + 1
}

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public data object Singleton

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
@JvmInline
public value class Wrapped(public val raw: Int)

@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
public sealed interface Shape

<!REDUNDANT_EXEMPTION!>@IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)<!>
public interface Plugin

public interface Toggler {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun toggle(on: Boolean)

    @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
    public fun buffer(): MutableList<Int>
}

public class Switch : Toggler {
    <!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
    override fun toggle(on: Boolean) {}

    <!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
    override fun buffer(): MutableList<Int> = mutableListOf()
}
