// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING
// DIAGNOSTIC_SEVERITIES: MANGLED_JVM_NAME_PUBLIC_API:none
// SOURCE_SETS_CHECKED_ELSEWHERE: common
// SOURCE_SETS_WITH_PLATFORM_CHECKS: common

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

public open class CommonOpen

public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>commonFunction<!>(): Int = 0

public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>commonSuspend<!>(): Int = 0

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public fun commonPlain(): Int = 0

@IntentionallyOpen
public open class Unexplained

public expect class Server() {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public fun configure(name: String)

    <!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
    public fun find()
}

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public expect class Finder() {
    public fun find()
}

<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public expect class Record(value: Int) {
    public val value: Int
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName

public actual class Server public actual constructor() {
    @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
    public actual fun configure(name: String) {}

    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
    public actual fun find() {}
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public actual class Finder public actual constructor() {
    public actual fun find() {}
}

@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public actual class Record public actual constructor(public actual val value: Int)
