// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

// @InternalAnnotationMarker turns an annotation class into an internal API marker: declarations
// carrying the marked annotation offer no compatibility contract despite being public, so no
// public API check fires on them or on anything nested in them.

/** Flags declarations that are public for technical reasons but are not supported API. */
@InternalAnnotationMarker
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.TYPEALIAS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class InternalLibApi

// No open, exhaustive, undocumented, or function type alias warnings on marked declarations,
// including their members and nested declarations.

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

// The marker also works when the marked annotation is applied through a type alias.

/** Alias of the internal API marker. */
public typealias AliasedInternalLibApi = InternalLibApi

@AliasedInternalLibApi
public class InternalThroughAlias

// The marked annotation class itself is ordinary public API and stays watched.

@InternalAnnotationMarker
public annotation class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedMarker<!>

// An annotation without the marker exempts nothing.

/** An ordinary annotation. */
public annotation class OrdinaryAnnotation

@OrdinaryAnnotation
public class <!UNDOCUMENTED_PUBLIC_API!>WatchedDespiteAnnotated<!>

// Unmarked declarations in the same file are still watched.

/** Documented, but open for unrestricted subclassing: still reported. */
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>WatchedOpenClass<!>

/** Documented, but exhaustively matchable: still reported. */
public enum class <!EXHAUSTIVE_PUBLIC_API!>WatchedEnum<!> {
    /** Documented entry. */
    ENTRY,
}

/** Documented, but abbreviates a function type: still reported. */
public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>WatchedCallback<!> = () -> Unit
