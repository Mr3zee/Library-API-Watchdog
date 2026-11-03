// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// MODULE: lib
// FILE: internalTypes.kt
package lib.api

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class InternalLibApi

@InternalLibApi
public open class InternalType

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import lib.api.InternalType

public val leakedProperty: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()

public val <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>.leakedReceiver: String
    get() = ""

public fun leakedReturn(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()

public fun <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>.leakedFunctionReceiver(): String = ""

public fun leakedParameter(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>) {}

public class LeakedSupertype : <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>()

public class LeakedClassBound<T : <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>>

public typealias LeakedTypeAlias = <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>
