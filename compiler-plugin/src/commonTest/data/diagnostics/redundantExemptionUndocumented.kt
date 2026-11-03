// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// REPORT_UNDOCUMENTED_CLASS_PROPERTIES
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API

// `@IntentionallyUndocumented` on every declaration kind the check watches, with and without KDoc.
// With `REPORT_UNDOCUMENTED_CLASS_PROPERTIES` the check watches member properties too, so they need their exemptions.

// FILE: declarations.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented

@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
public typealias Count = Int

/** A size. */
<!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
public typealias Size = Int

@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
public val limit: Int = 1

/** A bound. */
<!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
public val bound: Int = 1

/** A holder. */
public class Holder <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!> constructor(public val x: Int) {
    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
    public constructor(x: String) : this(x.length)

    /** From a count. */
    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    public constructor(x: Long) : this(x.toInt())

    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
    public val member: Int = 1

    /** Documented. */
    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    public val documentedMember: Int = 1

    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    public companion object

    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
    public object Nested
}

/** A named companion holder. */
public class NamedHolder {
    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
    public companion object Factory
}

/**
 */
<!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
public class EmptyDoc

/** Documented. */
public abstract class Documented {
    /** Runs. */
    public abstract fun run()

    public abstract fun stop()
}

/** Documented. */
public class Runner : Documented() {
    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    override fun run() {}

    <!REDUNDANT_EXEMPTION!>@IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)<!>
    override fun stop() {}
}

/** Documented. */
public enum class Mode {
    FAST,
    SLOW;

    @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
    public fun next(): Mode = this
}
