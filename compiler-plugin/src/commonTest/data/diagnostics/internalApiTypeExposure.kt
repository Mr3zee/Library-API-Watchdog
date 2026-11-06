// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

// MODULE: lib
// FILE: internalTypes.kt
package lib.api

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

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

// `@PublishedApi` doesn't make a declaration callable in source.

@PublishedApi
internal fun publishedHelper(value: InternalType): InternalType = value

@PublishedApi
internal class PublishedHolder<T : InternalType>(val value: InternalType)

internal fun implementationHelper(value: InternalType): InternalType = value

@InternalLibApi
public fun internalApiFunction(value: InternalType): InternalType = value

@InternalLibApi
public class InternalApiContainer {
    public fun member(value: InternalType): InternalType = value
}

public fun supported(value: SupportedType): List<SupportedType> = listOf(value)

// FILE: exempted.kt
package consumer.exempted

import lib.api.InternalAlias
import lib.api.InternalBase
import lib.api.InternalLibApi
import lib.api.InternalType
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility

// The exemption covers the declaration, a parameter, a type parameter, or a type usage.

@IntentionallyExposedInternalApiForBackwardsCompatibility
public fun exemptedReturn(): InternalType = InternalType()

@IntentionallyExposedInternalApiForBackwardsCompatibility(description = "Shipped in 1.0.")
public val exemptedProperty: InternalType = InternalType()

@IntentionallyExposedInternalApiForBackwardsCompatibility
public val InternalType.exemptedReceiver: String
    get() = ""

public fun exemptedParameter(
    @IntentionallyExposedInternalApiForBackwardsCompatibility first: InternalType,
    second: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>,
) {}

context(@IntentionallyExposedInternalApiForBackwardsCompatibility value: InternalType)
public fun exemptedContextParameter() {}

public fun exemptedTypeUsage(): List<@IntentionallyExposedInternalApiForBackwardsCompatibility InternalType> = emptyList()

public fun <@IntentionallyExposedInternalApiForBackwardsCompatibility T : InternalType> exemptedBound(value: T) {}

public class ExemptedConstructor(@IntentionallyExposedInternalApiForBackwardsCompatibility value: InternalType)

public class ExemptedConstructorProperty(
    @IntentionallyExposedInternalApiForBackwardsCompatibility public val value: InternalType,
    public val other: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!>,
)

public class ExemptedPropertyTarget(
    @property:IntentionallyExposedInternalApiForBackwardsCompatibility public val value: InternalType,
)

@IntentionallyExposedInternalApiForBackwardsCompatibility
public class ExemptedSupertype : InternalBase()

@IntentionallyExposedInternalApiForBackwardsCompatibility
public class ExemptedClassBound<T : InternalType>

@IntentionallyExposedInternalApiForBackwardsCompatibility
public typealias ExemptedAlias = InternalAlias

// An exemption on a class covers every nested declaration.

@IntentionallyExposedInternalApiForBackwardsCompatibility
public class ExemptedHolder {
    public fun member(value: InternalType): InternalType = value

    public val property: InternalType = InternalType()

    public class Nested : InternalBase() {
        public fun deep(): InternalType = InternalType()
    }
}

public class UnexemptedHolder {
    @IntentionallyExposedInternalApiForBackwardsCompatibility
    public fun member(): InternalType = InternalType()

    public fun other(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()
}
