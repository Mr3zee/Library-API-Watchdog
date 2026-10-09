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

/** An unrelated annotation of the dependency: not configured, so it exempts nothing. */
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

// Declarations carrying a configured annotation are internal API: no public API check fires on
// them, nor on anything nested in them.

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

// A configured annotation applied through a type alias is still recognized.

/** Alias of the configured annotation. */
public typealias AliasedInternalApi = ThirdPartyInternalApi

@AliasedInternalApi
public class InternalThroughAlias

// An annotation that is not configured exempts nothing.

@ThirdPartyOrdinary
public class <!UNDOCUMENTED_PUBLIC_API!>WatchedDespiteAnnotated<!>

// Unmarked declarations in the same module are still watched.

/** Documented, but open for unrestricted subclassing: still reported. */
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>WatchedOpenClass<!>

/** Documented, but exhaustively matchable: still reported. */
public enum class <!EXHAUSTIVE_PUBLIC_API!>WatchedEnum<!> {
    /** Documented entry. */
    ENTRY,
}

// A supported public signature exposing a type marked with a configured annotation is reported
// like one exposing a type marked through @InternalAnnotationMarker, and the message names the
// configured annotation.

/** Leaks the dependency's internal type. */
public fun leakedReturn(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>ThirdPartyInternalType<!> = ThirdPartyInternalType()

/** Leaks a locally declared type marked with a configured annotation. */
public fun leakedParameter(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalThroughNestedAnnotation<!>) {}

// The exposing declaration is itself internal API, so nothing is reported.

@ThirdPartyInternalApi
public fun internalApiFunction(value: ThirdPartyInternalType): ThirdPartyInternalType = value

/** Supported types in the same positions are accepted. */
public fun supported(value: ThirdPartySupportedType): ThirdPartySupportedType = value
