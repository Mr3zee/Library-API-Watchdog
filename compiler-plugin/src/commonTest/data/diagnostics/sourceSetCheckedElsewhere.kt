// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING
// SOURCE_SETS_CHECKED_ELSEWHERE: common

// Each declaration of the common module has a diagnostic without the option.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

public open class CommonOpen

public fun commonFunction(): Int = 0

@IntentionallyOpen
public open class Unexplained

public expect class Server() {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun configure(name: String)
}

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public expect class Record(value: Int) {
    public val value: Int
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>JvmOpen<!>

public actual class Server public actual constructor() {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public actual fun configure(name: String) {}
}

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public actual class Record public actual constructor(public actual val value: Int)
