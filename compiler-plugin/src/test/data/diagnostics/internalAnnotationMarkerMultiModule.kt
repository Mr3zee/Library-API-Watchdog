// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING

// The internal API marker annotation is declared in another module than the declarations it
// exempts: the meta-annotation must be resolved through the dependency's symbols.

// MODULE: lib
// FILE: markers.kt
package lib.api

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

/** Flags declarations that are public for technical reasons but are not supported API. */
@InternalAnnotationMarker
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
)
public annotation class InternalLibApi

// MODULE: main(lib)
// FILE: usage.kt
package consumer

import lib.api.InternalLibApi

// Declarations marked with the dependency's marker are exempt from the public API checks.

@InternalLibApi
public open class InternalOpenClass {
    public fun memberOfInternal() {}

    public class NestedInInternal
}

@InternalLibApi
public enum class InternalEnum { ENTRY }

@InternalLibApi
public fun internalFunction() {}

@InternalLibApi
public val internalProperty: Int = 0

// Unmarked declarations in the consuming module are still watched.

public class <!UNDOCUMENTED_PUBLIC_API!>WatchedClass<!>

/** Documented, but open for unrestricted subclassing: still reported. */
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>WatchedOpenClass<!>
