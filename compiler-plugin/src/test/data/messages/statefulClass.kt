// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

// The IntelliJ IDEA shortcut in the message depends on the host OS. The dump normalizes it.

public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING!>Connection<!>(public val host: String)
