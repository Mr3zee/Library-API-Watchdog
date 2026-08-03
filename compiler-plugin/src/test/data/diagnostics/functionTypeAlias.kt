// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyFunctionTypeAlias

// Unacknowledged function type aliases: should warn.

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>Callback<!> = (Int) -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>Action<!> = () -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>SuspendAction<!> = suspend () -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>NullableCallback<!> = ((String) -> Boolean)?

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>BuilderBlock<!> = StringBuilder.() -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>Transformer<!><T> = (T) -> T

// An alias of an alias still expands to a function type.
public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>CallbackAlias<!> = Callback

// Deliberate function type aliases: no warning.

@IntentionallyFunctionTypeAlias
public typealias MarkedCallback = (Int) -> Unit

@IntentionallyFunctionTypeAlias
public typealias MarkedSuspendAction = suspend () -> Unit

// Not function types: no warning.

public typealias StringList = List<String>

// A function type only occurring inside the expansion doesn't count.
public typealias Callbacks = List<(Int) -> Unit>

// Reflection function types have no fun interface counterpart.
public typealias FunctionReference = kotlin.reflect.KFunction1<Int, Unit>

// Not visible outside the library: no warning.

@PublishedApi
internal class PublishedAliases {
    public typealias PublishedCallback = (Int) -> Unit
}

internal typealias InternalCallback = (Int) -> Unit

private typealias PrivateCallback = (Int) -> Unit
