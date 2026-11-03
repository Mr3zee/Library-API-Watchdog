// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// FILE: shapes.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInconsistentParameterOrder

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)
public fun draw(x: Int, y: Int) {}

public interface Canvas {
    public fun place(x: Int, y: Int)
}

public class Panel : Canvas {
    <!REDUNDANT_EXEMPTION!>@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)<!>
    override fun place(x: Int, y: Int) {}

    public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(y: Int, x: Int, scale: Double) {}
}

public class Frame : Canvas {
    override fun place(x: Int, y: Int) {}

    @IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)
    public fun place(y: Int, x: Int, scale: Double) {}
}

public class Rect @IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN) constructor(x: Int, y: Int) {
    public constructor(y: Int, x: Int, scale: Double) : this(x, y)
}

public class Square <!REDUNDANT_EXEMPTION!>@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)<!> constructor(side: Int, color: Int)

// FILE: other.kt
package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyInconsistentParameterOrder

@IntentionallyInconsistentParameterOrder(reason = ExemptionReason.API_DESIGN)
public fun draw(y: Int, x: Int, scale: Double) {}
