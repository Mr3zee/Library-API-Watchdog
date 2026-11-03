// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION: the later phase is named BACKEND before Kotlin 2.5 and CODEGEN after it
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION

// Without explicit API mode the plugin is off, so a redundant exemption isn't reported either.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public class FinalWithOpenExemption

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public class NotData(public val x: Int)

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public fun count(n: Int) {}

public fun read(@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN) n: Int) {}

// FILE: facade.kt
@file:JvmName("Facade")
@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

public fun facadeMember(): Int = 1
