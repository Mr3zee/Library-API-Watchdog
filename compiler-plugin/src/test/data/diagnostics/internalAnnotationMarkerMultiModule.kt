// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING

// The meta-annotation is resolved through the symbols of a dependency.

// MODULE: lib
// FILE: markers.kt
package lib.api

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

/** Flags declarations that are public for technical reasons but aren't supported API. */
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

public class <!UNDOCUMENTED_PUBLIC_API!>WatchedClass<!>

/** Documented, but open for unrestricted subclassing: still reported. */
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>WatchedOpenClass<!>
