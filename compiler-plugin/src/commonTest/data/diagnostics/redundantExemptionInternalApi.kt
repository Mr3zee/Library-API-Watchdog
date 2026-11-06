// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN

// MODULE: lib
// FILE: internalTypes.kt
package lib.api

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class InternalLibApi

@InternalLibApi
public open class InternalType

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class SupportedType

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import lib.api.InternalLibApi
import lib.api.InternalType
import lib.api.SupportedType
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility

@IntentionallyExposedInternalApiForBackwardsCompatibility
public fun neededReturn(): InternalType = InternalType()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public fun supportedReturn(): SupportedType = SupportedType()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility(description = "Shipped in 1.0.")<!>
public val supportedProperty: SupportedType = SupportedType()

public fun neededParameter(@IntentionallyExposedInternalApiForBackwardsCompatibility value: InternalType) {}

public fun plainParameter(<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> count: Int) {}

public fun neededNested(): List<@IntentionallyExposedInternalApiForBackwardsCompatibility InternalType> = emptyList()

public fun plainNested(): List<<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> Int> = emptyList()

public fun <@IntentionallyExposedInternalApiForBackwardsCompatibility T : InternalType> neededBound(value: T) {}

public fun <<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> T : Any> plainBound(value: T) {}

public class NeededConstructorProperty(
    @IntentionallyExposedInternalApiForBackwardsCompatibility public val value: InternalType,
    <!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> public val count: Int,
)

@IntentionallyExposedInternalApiForBackwardsCompatibility
public class NeededSubtype : InternalType()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public class PlainSubtype : SupportedType()

@IntentionallyExposedInternalApiForBackwardsCompatibility
public typealias NeededAlias = InternalType

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public typealias PlainAlias = SupportedType

// A class exemption doesn't cover the members, so a member needs its own.

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public class PlainHolder {
    @IntentionallyExposedInternalApiForBackwardsCompatibility
    public fun model(): InternalType = InternalType()

    public class Nested {
        @IntentionallyExposedInternalApiForBackwardsCompatibility
        public fun deep(): InternalType = InternalType()
    }
}

// The check doesn't run on internal API, `@PublishedApi internal`, or non-public declarations.

@InternalLibApi
<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public fun internalApiFunction(): InternalType = InternalType()

@InternalLibApi
public class InternalApiHolder {
    <!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
    public fun member(): InternalType = InternalType()
}

@PublishedApi
<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
internal fun publishedHelper(): InternalType = InternalType()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
internal fun implementationHelper(): InternalType = InternalType()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
private fun privateHelper(): InternalType = InternalType()
