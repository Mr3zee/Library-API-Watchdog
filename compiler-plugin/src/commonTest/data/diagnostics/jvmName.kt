// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -COMPANION_API_WITHOUT_JVM_STATIC

// FILE: facade.kt
package foo.bar

// Each file is reported one time, on its first public top-level callable.
public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>connect<!>(): Int = 0

public fun disconnect(): Int = 0

public val timeout: Int = 0

// FILE: propertyFirst.kt
package foo.bar

public val <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>retries<!>: Int = 3

// FILE: named.kt
@file:JvmName("Ciphers")

package foo.bar

public fun encrypt(payload: String): String = payload

public val strength: Int = 256

// FILE: classifiers.kt
package foo.bar

public class Session

public object Broker {
    public fun send(): Int = 0
}

public typealias Handler = Session

// FILE: hiddenOnly.kt
package foo.bar

@JvmSynthetic
public fun kotlinOnlyHelper(): Int = 0

@get:JvmSynthetic
public val kotlinOnlyValue: Int = 0

// FILE: syntheticFields.kt
package foo.bar

// The backing field is the only Java entry point of a `const` or `@JvmField` property, so
// `@JvmSynthetic` on it hides the property.

@JvmSynthetic
public const val HIDDEN_CONSTANT: Int = 1

@JvmSynthetic
@JvmField
public val HIDDEN_FIELD: Int = 2

// FILE: partiallyHiddenProperty.kt
package foo.bar

// The setter is still visible.
@get:JvmSynthetic
public var <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>partiallyHidden<!>: Int = 0

// FILE: fieldAnnotationOnly.kt
package foo.bar

// On an ordinary property, `@JvmSynthetic` without a use-site target hides only the backing field.
@JvmSynthetic
public val <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>fieldAnnotationOnly<!>: Int = 0

// FILE: acknowledged.kt
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public fun legacyEntryPoint(): Int = 0

// FILE: internalOnly.kt
package foo.bar

internal fun helper(): Int = 0

private val secret: Int = 0

// FILE: publishedOnly.kt
package foo.bar

// `@PublishedApi` makes an internal declaration binary API.
@PublishedApi
internal fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>publishedHelper<!>(): Int = 0
