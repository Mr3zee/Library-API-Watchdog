// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// PUBLIC_TYPE_WITH_INTERNAL_API_DISABLED
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// With the check off, no exposure is reported and every exemption of the check is redundant.

// MODULE: lib
// FILE: internalTypes.kt
package lib.api

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class InternalLibApi

@InternalLibApi
public open class InternalType

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import lib.api.InternalType
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility

public fun unreported(): InternalType = InternalType()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public fun exempted(): InternalType = InternalType()

public fun exemptedParameter(<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> value: InternalType) {}

public fun exemptedNested(): List<<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!> InternalType> = emptyList()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public class ExemptedSubtype : InternalType()
