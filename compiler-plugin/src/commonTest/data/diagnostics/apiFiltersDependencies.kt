// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: **.internal.**
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// MODULE: lib
// FILE: libInternal.kt
package lib.internal

public open class LibInternal

// MODULE: main(lib)
// FILE: ownInternal.kt
package app.internal

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

public open class OwnInternal

@PublishedApi
internal fun publishedInternal(flag: Boolean) {}

@InternalAnnotationMarker
@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
public annotation class HiddenApi

// FILE: hidden.kt
// An internal API annotation on a file covers the top-level functions for the file facade check too.
@file:HiddenApi

package app.hidden

import app.internal.HiddenApi

public fun hiddenTopLevel(flag: Boolean) {}

// FILE: api.kt
@file:JvmName("Api")

package app.api

import app.internal.HiddenApi
import app.internal.OwnInternal
import lib.internal.LibInternal

/** A name rule describes the API of this module, so a type of a dependency isn't reported. */
public fun exposesDependencyType(): LibInternal = LibInternal()

/** A type of this module is reported. */
public fun exposesOwnType(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>OwnInternal<!> = OwnInternal()

/** A watched alias of an excluded class. */
public typealias WatchedAlias = <!PUBLIC_TYPE_WITH_INTERNAL_API!>OwnInternal<!>

/** The alias doesn't hide the excluded class. */
public fun exposesThroughAlias(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>WatchedAlias<!> = OwnInternal()

/** A member of a class with an internal API annotation isn't an overload sibling of the extension. */
@HiddenApi
public class Canvas {
    public fun paint(x: Int, y: Int) {}
}

/** Its sibling is internal API, so the overload check doesn't compare them. */
public fun <!PUBLIC_TYPE_WITH_INTERNAL_API!>Canvas<!>.paint(y: Int, x: Int, scale: Double) {}
