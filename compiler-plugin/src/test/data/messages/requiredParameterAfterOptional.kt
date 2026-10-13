// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS

package foo.bar

// Message parameters: the parameter name, the callable name, and the callable kind.

public fun connect(retries: Int = 3, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String) {}

public class Server(port: Int = 80, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>host<!>: String)
