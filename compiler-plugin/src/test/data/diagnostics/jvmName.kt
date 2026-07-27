// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -COMPANION_API_WITHOUT_JVM_STATIC

// FILE: facade.kt
package foo.bar

// The facade class FacadeKt leaks the file name to Java callers: reported once per file, on the
// first public top-level function or property.
public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>connect<!>(): Int = 0

public fun disconnect(): Int = 0

public val timeout: Int = 0

// FILE: propertyFirst.kt
package foo.bar

// A public top-level property creates the facade too.
public val <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>retries<!>: Int = 3

// FILE: named.kt
@file:JvmName("Ciphers")

package foo.bar

// The facade name is pinned deliberately: no warning.
public fun encrypt(payload: String): String = payload

public val strength: Int = 256

// FILE: classifiers.kt
package foo.bar

// Classifiers don't compile into a file facade: no warning.

public class Session

public object Broker {
    public fun send(): Int = 0
}

public typealias Handler = Session

// FILE: hiddenOnly.kt
package foo.bar

// Every top-level callable is hidden from Java, so the facade has no Java-visible members.

@JvmSynthetic
public fun kotlinOnlyHelper(): Int = 0

@get:JvmSynthetic
public val kotlinOnlyValue: Int = 0

// FILE: acknowledged.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDefaultFacadeName

// The derived facade name is acknowledged: no warning.
public fun legacyEntryPoint(): Int = 0

// FILE: internalOnly.kt
package foo.bar

// Not visible outside the library: no warning.

internal fun helper(): Int = 0

private val secret: Int = 0
