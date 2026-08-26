// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

// Published declarations are watched in every module of a multimodule compilation. The
// dependency module acknowledges everything its published API is reported for, so it compiles
// into a binary cleanly; the consuming module reports its own published declarations while using
// the dependency's published API through its public inline functions. Neither module documents a
// published declaration: the KDoc check leaves them alone.

// MODULE: lib
// FILE: lib.kt
@file:JvmName("LibApi")

package libapi

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

@PublishedApi
internal class LibPublishedClass

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
@PublishedApi
internal open class LibPublishedOpenClass

@PublishedApi
internal fun libPublishedHelper(): Int = 0

/** Documented; the inlined logic exists to exercise the published declarations above. */
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun libInlineApi(block: () -> Int): Int {
    return block() + libPublishedHelper()
}

// MODULE: main(lib)
// FILE: main.kt

package foo.bar

import libapi.libInlineApi
import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic

@PublishedApi
internal class MainPublishedClass

@PublishedApi
internal open class MainPublishedOpenClass

@PublishedApi
internal enum class MainPublishedEnum {
    ENTRY,
}

@PublishedApi
internal fun mainPublishedHelper(): Int = 0

/** Documented; the inlined logic exists to exercise the published declarations above. */
@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public inline fun mainInlineApi(block: () -> Int): Int {
    return libInlineApi(block) + mainPublishedHelper()
}
