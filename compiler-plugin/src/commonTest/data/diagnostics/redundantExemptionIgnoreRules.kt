// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION: the later phase is named BACKEND before Kotlin 2.5 and CODEGEN after it
// EXPLICIT_API_MODE: WARNING
// IGNORE_WHEN_ANNOTATED: DATA_CLASS_PUBLIC_API:foo.bar.Generated MANGLED_JVM_NAME_PUBLIC_API:foo.bar.Generated
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class Generated

@Generated
@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
public data class GeneratedPoint(val x: Int)

@Generated
<!REDUNDANT_EXEMPTION!>@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)<!>
public class GeneratedPlain

@JvmInline
public value class Id(public val raw: Int)

@Generated
@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class GeneratedIds {
    public fun id(): Id = Id(1)
}

@Generated
<!REDUNDANT_EXEMPTION!>@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)<!>
public class GeneratedNames {
    public fun id(): Int = 1
}
