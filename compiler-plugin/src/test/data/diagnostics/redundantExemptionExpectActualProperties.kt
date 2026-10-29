// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -MUTABLE_COLLECTION_PUBLIC_API -INLINE_FUNCTION_WITH_LOGIC -MANGLED_JVM_NAME_PUBLIC_API

// Exemptions on `expect` properties. A signature check reads them on the `expect`, a body or JVM
// check on the `actual`.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection

@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
public expect val items: MutableList<Int>

<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
public expect val views: List<Int>

@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)
public expect val logic: Int

<!REDUNDANT_EXEMPTION!>@IntentionallyInlinedLogic(reason = ExemptionReason.API_DESIGN)<!>
public expect val thin: Int

public expect value class Id(public val raw: Int)

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public expect var mangled: Id

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public expect val plain: Int

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInlinedLogic
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection

<!REDUNDANT_EXEMPTION!>@IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)<!>
public actual val items: MutableList<Int> = mutableListOf()

public actual val views: List<Int> = emptyList()

public actual inline val logic: Int
    get() {
        val base = 1
        return base + 1
    }

public actual inline val thin: Int
    get() = 1

@JvmInline
public actual value class Id(public actual val raw: Int)

<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public actual var mangled: Id = Id(1)

public actual val plain: Int = 1
