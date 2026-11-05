// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// A `val` parameter of the `actual` actualizes a constructor parameter and a body property of the
// `expect`. An exemption on the `val` parameter is a copy of the constructor parameter exemption, so the
// checker reports both or neither. The property also takes the `expect` constructor parameter exemption.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean

@JvmInline
public value class Id(public val raw: Int)

public expect class Needed(@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN) flag: Boolean?) {
    @IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)
    public val flag: Boolean?
}

internal expect class Redundant(<!REDUNDANT_EXEMPTION!>@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)<!> flag: Boolean?) {
    public val flag: Boolean?
}

public expect class Inherited @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) constructor(
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) id: Id,
) {
    public val id: Id
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean

public actual class Needed actual constructor(@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN) public actual val flag: Boolean?)

internal actual class Redundant actual constructor(<!REDUNDANT_EXEMPTION!>@IntentionallyNullableBoolean(reason = ExemptionReason.API_DESIGN)<!> public actual val flag: Boolean?)

public actual class Inherited @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) actual constructor(public actual val id: Id)
