// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXEMPTING_EXISTING_API
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// The collection mode of `exemptExistingApi` reports the exposure as a warning and judges the exemptions.

// MODULE: lib
// FILE: internalTypes.kt
package lib.api

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class InternalLibApi

@InternalLibApi
public class InternalType

public class SupportedType

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import lib.api.InternalType
import lib.api.SupportedType
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility

public fun collected(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()

@IntentionallyExposedInternalApiForBackwardsCompatibility
public fun exempted(): InternalType = InternalType()

<!REDUNDANT_EXEMPTION!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public fun supported(): SupportedType = SupportedType()
