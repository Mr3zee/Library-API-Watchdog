// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

package foo.bar

// Message parameters: the declaration kind, its name, the mutable type, and the fix for a
// return type, a parameter, or a type parameter bound.

public fun produce(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<String><!> = mutableListOf()

public val registry: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableMap<String, Int><!> = mutableMapOf()

public fun consume(items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableSet<Int><!>) { items.clear() }

public class Holder(public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public fun table(): <!MUTABLE_COLLECTION_PUBLIC_API!>Array<String><!> = arrayOf()

public fun <T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>> drain(source: T): List<Int> = source.toList()

public class Depot<T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableCollection<Int><!>>
