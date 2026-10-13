// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyFunctionTypeAlias

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>Callback<!> = (Int) -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>Action<!> = () -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>SuspendAction<!> = suspend () -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>NullableCallback<!> = ((String) -> Boolean)?

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>BuilderBlock<!> = StringBuilder.() -> Unit

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>Transformer<!><T> = (T) -> T

public typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API!>CallbackAlias<!> = Callback

@IntentionallyFunctionTypeAlias
public typealias MarkedCallback = (Int) -> Unit

@IntentionallyFunctionTypeAlias
public typealias MarkedSuspendAction = suspend () -> Unit

public typealias StringList = List<String>

public typealias Callbacks = List<(Int) -> Unit>

// A reflection function type isn't a function type.
public typealias FunctionReference = kotlin.reflect.KFunction1<Int, Unit>

@PublishedApi
internal class PublishedAliases {
    public typealias PublishedCallback = (Int) -> Unit
}

internal typealias InternalCallback = (Int) -> Unit

private typealias PrivateCallback = (Int) -> Unit
