// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC -FUNCTION_TYPE_ALIAS_PUBLIC_API

package foo.bar

import java.util.function.Consumer
import kotlin.reflect.KClass
import kotlin.reflect.KFunction0

// A shared parameter that is a trailing lambda in both overloads has the same position.

public fun register(name: String, configure: () -> Unit) {}

public fun register(name: String, type: KClass<*>, configure: () -> Unit) {}

public fun registerSuspend(name: String, configure: suspend () -> Unit) {}

public fun registerSuspend(name: String, type: KClass<*>, configure: suspend () -> Unit) {}

public fun registerNullable(name: String, configure: (() -> Unit)?) {}

public fun registerNullable(name: String, type: KClass<*>, configure: (() -> Unit)?) {}

public class Task

public fun registerWithReceiver(name: String, configure: Task.() -> Unit) {}

public fun registerWithReceiver(name: String, type: KClass<*>, configure: Task.() -> Unit) {}

public typealias Configure = (Task) -> Unit

public fun registerAlias(name: String, configure: Configure) {}

public fun registerAlias(name: String, type: KClass<*>, configure: Configure) {}

public fun interface TaskAction {
    public fun execute(task: Task)
}

public fun registerFunInterface(name: String, configure: TaskAction) {}

public fun registerFunInterface(name: String, type: KClass<*>, configure: TaskAction) {}

public fun registerJavaSam(name: String, configure: Consumer<Task>) {}

public fun registerJavaSam(name: String, type: KClass<*>, configure: Consumer<Task>) {}

public class Tasks {
    public fun register(name: String, configure: () -> Unit) {}

    public fun register(name: String, type: KClass<*>, configure: () -> Unit) {}
}

public class Container(name: String, configure: () -> Unit) {
    public constructor(name: String, type: KClass<*>, configure: () -> Unit) : this(name, configure)
}

// An extra parameter in front of the shared parameters still moves them.

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>registerTyped<!>(type: KClass<*>, name: String, configure: () -> Unit) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>registerTyped<!>(name: String, configure: () -> Unit) {}

// A lambda parameter that isn't last in one overload is compared by its index.

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>subscribe<!>(topic: String, handler: () -> Unit) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>subscribe<!>(topic: String, priority: Int, handler: () -> Unit, retries: Int) {}

// Only a parameter that accepts a lambda literal is a trailing lambda.

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(x: Int, y: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>place<!>(x: Int, scale: Double, y: Int) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>bind<!>(name: String, handler: KFunction0<Unit>) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>bind<!>(name: String, priority: Int, handler: KFunction0<Unit>) {}

public interface Listener {
    public fun onEvent(task: Task)
}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>listen<!>(name: String, listener: Listener) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>listen<!>(name: String, priority: Int, listener: Listener) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>notifyAll<!>(name: String, vararg handlers: () -> Unit) {}

public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>notifyAll<!>(name: String, priority: Int, vararg handlers: () -> Unit) {}
