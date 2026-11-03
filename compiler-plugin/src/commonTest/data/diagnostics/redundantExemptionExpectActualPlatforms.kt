// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -DATA_CLASS_PUBLIC_API -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// Two platform modules judge the exemptions of the same `expect` declarations. A platform reports an
// exemption that its own `actual` doesn't need, even when the other platform needs it. The test
// framework skips the second platform module when the common module has a top-level `expect` callable,
// so this file has classes only. `redundantExemptionExpectActualProperties.kt` has the callables.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public expect class Service() {
    public fun run()
}

@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)
public expect class Worker() {
    public suspend fun run()
}

<!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public expect class Record(value: Int) {
    public val value: Int
}

// MODULE: jvmA()()(common)
// FILE: jvmA.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyKotlinOnlyApi

public actual class Service public actual constructor() {
    public actual fun run() {}

    public suspend fun extra() {}
}

<!REDUNDANT_EXEMPTION!>@IntentionallyKotlinOnlyApi(reason = ExemptionReason.API_DESIGN)<!>
public actual class Worker public actual constructor() {
    public actual suspend fun run() {}
}

public actual typealias Record = RecordImpl

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public data class RecordImpl(public val value: Int)

// MODULE: jvmB()()(common)
// FILE: jvmB.kt

package foo.bar

public actual class Service public actual constructor() {
    public actual fun run() {}
}

public actual class Worker public actual constructor() {
    public actual suspend fun run() {}
}

public actual class Record public actual constructor(public actual val value: Int)
