// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -OPEN_API_WITHOUT_SUBCLASS_OPT_IN

package foo.bar

import java.util.Enumeration
import java.util.concurrent.Callable
import java.util.function.Consumer

// A Java SAM interface in the last position accepts a trailing lambda.

public fun runLater(delay: Long = 0L, action: Runnable) {}

public fun compute(delay: Long = 0L, task: Callable<String>) {}

public fun consume(bufferSize: Int = 16, consumer: Consumer<String>?) {}

// A Java interface with two abstract methods isn't a SAM interface.

public fun drain(limit: Int = 0, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>source<!>: Enumeration<String>) {}

// A Kotlin interface without `fun` isn't a SAM interface.

public interface Watcher {
    public fun onChange(value: Int)
}

public fun watch(bufferSize: Int = 16, <!REQUIRED_PARAMETER_AFTER_OPTIONAL!>watcher<!>: Watcher) {}
