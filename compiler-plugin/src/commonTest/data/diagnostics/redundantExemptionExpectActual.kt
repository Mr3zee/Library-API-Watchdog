// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -DATA_CLASS_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -BOOLEAN_PARAMETER_PUBLIC_API

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public expect class PlainExpect(value: Int) {
    public val value: Int
}

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public expect class Record(value: Int) {
    public val value: Int
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public expect fun blocking(): Int

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect suspend fun fetch(): Int

public expect suspend fun pull(): Int

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
public expect fun count(n: Int)

@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
public expect fun flag(on: Boolean)

public expect fun flagged(on: Boolean)

public expect class Service() {
    @IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
    public suspend fun run()

    <!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
    public fun stop()
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect class Suspending() {
    public suspend fun go()
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public expect class Blocking() {
    public fun go()
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

public actual class PlainExpect public actual constructor(public actual val value: Int)

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public actual data class Record public actual constructor(public actual val value: Int)

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public actual fun blocking(): Int = 1

public actual suspend fun fetch(): Int = 1

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public actual suspend fun pull(): Int = 1

public actual fun count(n: Int) {}

public actual fun flag(on: Boolean) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
public actual fun flagged(on: Boolean) {}

<!REDUNDANT_EXEMPTION!>@IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)<!>
public fun platformCount(n: Int) {}

public actual class Service public actual constructor() {
    public actual suspend fun run() {}

    public actual fun stop() {}
}

public actual class Suspending public actual constructor() {
    public actual suspend fun go() {}
}

public actual class Blocking public actual constructor() {
    public actual fun go() {}
}
