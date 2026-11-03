// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

/** Flags declarations that are public for technical reasons but aren't supported API. */
@InternalAnnotationMarker
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class InternalLibApi

@InternalLibApi
public open class InternalOpenClass {
    public fun memberOfInternal() {}

    public val propertyOfInternal: Int = 0

    public class NestedInInternal

    public enum class NestedEnumInInternal { ENTRY }
}

@InternalLibApi
public interface InternalInterface

@InternalLibApi
public enum class InternalEnum { ENTRY }

@InternalLibApi
public sealed class InternalSealed

@InternalLibApi
public typealias InternalCallback = () -> Unit

@InternalLibApi
public fun internalFunction() {}

@InternalLibApi
public val internalProperty: Int = 0

/** Alias of the internal API marker. */
public typealias AliasedInternalLibApi = InternalLibApi

@AliasedInternalLibApi
public class InternalThroughAlias

@InternalAnnotationMarker
public annotation class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedMarker<!>

/** An ordinary annotation. */
public annotation class OrdinaryAnnotation

@OrdinaryAnnotation
public class <!UNDOCUMENTED_PUBLIC_API!>WatchedDespiteAnnotated<!>

/** Documented, but open for unrestricted subclassing: still reported. */
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>WatchedOpenClass<!>

/** Documented, but exhaustively matchable: still reported. */
public enum class <!EXHAUSTIVE_PUBLIC_API!>WatchedEnum<!> {
    /** Documented entry. */
    ENTRY,
}

/** Documented, but abbreviates a function type: still reported. */
public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>WatchedCallback<!> = () -> Unit
