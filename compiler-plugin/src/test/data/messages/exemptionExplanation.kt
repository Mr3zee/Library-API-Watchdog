// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

// Message parameters: the exemption annotation and each generic reason.

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyOpen<!>
public open class BareExemption

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive(reason = ExemptionReason.INTEROP)<!>
public enum class InteropReason {
    ENTRY,
}

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive(reason = ExemptionReason.EXTERNAL_CONTRACT)<!>
public enum class ExternalContractReason {
    ENTRY,
}

<!EXEMPTION_WITHOUT_EXPLANATION!>@IntentionallyExhaustive(reason = ExemptionReason.IGNORE_JAVA_INTEROP)<!>
public enum class IgnoreJavaInteropReason {
    ENTRY,
}
