// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>fetch<!>(key: String): String = key

public inline fun <reified T> <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>instantiate<!>(): T? = null

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>race<!>(work: suspend () -> Int) {}

public class Config

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>configure<!>(block: Config.() -> Int) {}

public fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>onEach<!>(action: (Int) -> Unit) {}
