// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

// Internal-API types may be used within implementation code, but supported public source API must
// not expose them. Every public signature position and nested type argument is checked.

// MODULE: lib
// FILE: internalTypes.kt
package lib.api

import org.jetbrains.kotlinx.libs.api.watchdog.InternalAnnotationMarker

/** Marks declarations that are public only for technical reasons. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.FUNCTION)
public annotation class InternalLibApi

/** A second internal-API annotation used to verify one type reference retains every marker. */
@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class OtherInternalApi

@InternalLibApi
public open class InternalBase

@InternalLibApi
public open class InternalType {
    public class Nested
}

@OtherInternalApi
public class OtherInternalType

@InternalLibApi
public typealias InternalAlias = String

@InternalLibApi
public typealias AliasExpandingToInternal = InternalType

/** A supported public type used as the negative control. */
public class SupportedType

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import lib.api.AliasExpandingToInternal
import lib.api.InternalAlias
import lib.api.InternalBase
import lib.api.InternalLibApi
import lib.api.InternalType
import lib.api.OtherInternalType
import lib.api.SupportedType

public val leakedProperty: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()

public val <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>.leakedReceiver: String
    get() = ""

public fun leakedReturn(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()

public fun <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>.leakedFunctionReceiver(): String = ""

public fun leakedParameter(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>) {}

context(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>)
public fun leakedContextParameter() {}

public class LeakedConstructor(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>)

public class LeakedConstructorProperty(
    public val value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>,
)

public class LeakedSupertype : <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalBase<!>()

public class LeakedClassBound<T : <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>>

public fun <T : <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>> leakedFunctionBound(value: T) {}

public typealias LeakedTypeAlias = <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>

public typealias LeakedMarkedAlias = <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalAlias<!>

public typealias LeakedExpandedAlias = <!PUBLIC_TYPE_WITH_INTERNAL_API!>AliasExpandingToInternal<!>

public fun leakedNestedArgument(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>List<InternalType><!> = emptyList()

public fun leakedNestedClass(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType.Nested<!> = InternalType.Nested()

public fun leakedDifferentAnnotations(
    first: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>,
    second: <!PUBLIC_TYPE_WITH_INTERNAL_API!>OtherInternalType<!>,
) {}

// Declarations that only @PublishedApi promotes are binary implementation details rather than
// source API. They and ordinary internal declarations are not checked.

@PublishedApi
internal fun publishedHelper(value: InternalType): InternalType = value

@PublishedApi
internal class PublishedHolder<T : InternalType>(val value: InternalType)

internal fun implementationHelper(value: InternalType): InternalType = value

// An exposing declaration marked as internal API is itself outside the supported API surface.

@InternalLibApi
public fun internalApiFunction(value: InternalType): InternalType = value

@InternalLibApi
public class InternalApiContainer {
    public fun member(value: InternalType): InternalType = value
}

// Supported types in the same positions are accepted.

public fun supported(value: SupportedType): List<SupportedType> = listOf(value)
