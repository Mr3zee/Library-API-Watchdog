// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INTERNAL_API_ANNOTATIONS: third.party.ThirdPartyInternalApi third.party.Markers.NestedInternalApi
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// MODULE: lib
// FILE: markers.kt
package third.party

/** The dependency's own internal-API annotation. It has no @InternalAnnotationMarker. */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class ThirdPartyInternalApi

/** Holder of a nested annotation, which the option names as `third.party.Markers.NestedInternalApi`. */
public object Markers {
    /** A nested internal-API annotation. */
    @Target(AnnotationTarget.CLASS)
    public annotation class NestedInternalApi
}

/** An unrelated annotation of the dependency: not configured, so it doesn't exempt anything. */
@Target(AnnotationTarget.CLASS)
public annotation class ThirdPartyOrdinary

@ThirdPartyInternalApi
public class ThirdPartyInternalType

/** A supported type of the dependency used as the negative control. */
public class ThirdPartySupportedType

// MODULE: main(lib)
// FILE: usage.kt
package consumer

import third.party.Markers
import third.party.ThirdPartyInternalApi
import third.party.ThirdPartyInternalType
import third.party.ThirdPartyOrdinary
import third.party.ThirdPartySupportedType

@ThirdPartyInternalApi
public open class InternalOpenClass {
    public fun memberOfInternal() {}

    public val propertyOfInternal: Int = 0

    public class NestedInInternal

    public enum class NestedEnumInInternal { ENTRY }
}

@ThirdPartyInternalApi
public enum class InternalEnum { ENTRY }

@ThirdPartyInternalApi
public typealias InternalCallback = () -> Unit

@ThirdPartyInternalApi
public fun internalFunction() {}

@ThirdPartyInternalApi
public val internalProperty: Int = 0

@Markers.NestedInternalApi
public open class InternalThroughNestedAnnotation

/** Alias of the configured annotation. */
public typealias AliasedInternalApi = ThirdPartyInternalApi

@AliasedInternalApi
public class InternalThroughAlias

@ThirdPartyOrdinary
public class <!UNDOCUMENTED_PUBLIC_API!>WatchedDespiteAnnotated<!>

/** Documented, but open for unrestricted subclassing: still reported. */
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>WatchedOpenClass<!>

/** Documented, but exhaustively matchable: still reported. */
public enum class <!EXHAUSTIVE_PUBLIC_API!>WatchedEnum<!> {
    /** Documented entry. */
    ENTRY,
}

/** Leaks the dependency's internal type. */
public fun leakedReturn(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>ThirdPartyInternalType<!> = ThirdPartyInternalType()

/** Leaks a locally declared type marked with a configured annotation. */
public fun leakedParameter(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalThroughNestedAnnotation<!>) {}

@ThirdPartyInternalApi
public fun internalApiFunction(value: ThirdPartyInternalType): ThirdPartyInternalType = value

/** Supported types in the same positions are accepted. */
public fun supported(value: ThirdPartySupportedType): ThirdPartySupportedType = value
