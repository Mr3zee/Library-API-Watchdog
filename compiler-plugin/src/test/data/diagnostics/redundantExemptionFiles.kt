// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

// A file needs its exemption when a Java-visible top-level function or property gets the default facade name.

// FILE: markers.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
public annotation class MarkedInternal

// FILE: privateOnly.kt
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

private fun hidden(): Int = 1

internal fun alsoHidden(): Int = hidden()

// FILE: propertyOnly.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public val facadeProperty: Int = 1

// FILE: constOnly.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public const val LIMIT: Int = 1

// FILE: publishedOnly.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

@PublishedApi
internal fun published(): Int = 1

// FILE: typeAliasOnly.kt
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public typealias Count = Int

// FILE: markedOnly.kt
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

@MarkedInternal
public fun marked(): Int = 1

// FILE: suspendOnly.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public suspend fun load(): Int = 1

// FILE: multifile.kt
@file:JvmMultifileClass
@file:JvmName("Shared")
<!REDUNDANT_EXEMPTION!>@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)<!>

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public fun part(): Int = 1

// FILE: mixed.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public class OnlyClass

private fun helper(): Int = 1

public fun visible(): Int = helper()

// FILE: extensionOnly.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public val Int.doubled: Int
    get() = this * 2
