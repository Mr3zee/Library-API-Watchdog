// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INTERNAL_API_ANNOTATIONS: test.markers.Excluded
// EXCLUDED_NAMES: test.named.**
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// An internal API annotation on a file doesn't make the classes of the file internal API types,
// because an incremental build reads an unchanged class without the annotations of its file. An
// annotation on the class itself or a name rule does.

// FILE: markers.kt
package test.markers

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

/** Marks internal API. */
@InternalAnnotationMarker
@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
public annotation class Hidden

/** The `exclude.annotatedWith` annotation of the API filters. */
@Target(AnnotationTarget.FILE, AnnotationTarget.CLASS)
public annotation class Excluded

// FILE: hiddenFile.kt
@file:Hidden

package test.hidden

import test.markers.Hidden

public open class InHiddenFile

// FILE: excludedFile.kt
@file:Excluded

package test.excluded

import test.markers.Excluded

public open class InExcludedFile

// FILE: marked.kt
package test.marked

import test.markers.Hidden

@Hidden
public open class MarkedClass

// FILE: named.kt
package test.named

public open class ExcludedByName

// FILE: api.kt
@file:JvmName("Api")

package test.api

import test.excluded.InExcludedFile
import test.hidden.InHiddenFile
import test.marked.MarkedClass
import test.named.ExcludedByName

/** A class of a file with an internal API annotation isn't an internal API type. */
public fun loadFromHiddenFile(): InHiddenFile = InHiddenFile()

/** The same for the exclusion annotation of the API filters. */
public fun loadFromExcludedFile(): InExcludedFile = InExcludedFile()

/** A class with the annotation is an internal API type. */
public fun loadMarked(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>MarkedClass<!> = MarkedClass()

/** A class that a name rule excludes is an internal API type. */
public fun loadNamed(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>ExcludedByName<!> = ExcludedByName()
