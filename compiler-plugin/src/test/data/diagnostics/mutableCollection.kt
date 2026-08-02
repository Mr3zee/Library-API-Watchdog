// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyMutableCollection

// Mutable collection types in public signatures: should warn.

public fun produce(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<String><!> = mutableListOf()

public fun consume(items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableSet<Int><!>) { items.clear() }

public fun cursor(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableIterator<Int><!> = TODO()

public val registry: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableMap<String, Int><!> = mutableMapOf()

public var handles: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableCollection<Int><!> = mutableListOf()

public class Holder(public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public class Sink(buffer: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

// @PublishedApi declarations belong to the published binary API.
@PublishedApi
internal fun stash(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!> = mutableListOf()

// The mutable type may hide in a type argument, behind a type alias, or in a function type.

public fun nested(): <!MUTABLE_COLLECTION_PUBLIC_API!>List<MutableList<Int>><!> = emptyList()

public typealias Bag = MutableList<Int>

public fun aliased(): <!MUTABLE_COLLECTION_PUBLIC_API!>Bag<!> = mutableListOf()

public val makeBuffer: <!MUTABLE_COLLECTION_PUBLIC_API!>() -> MutableList<Int><!> = { mutableListOf() }

// Function types are FunctionN classifiers, so mutable state hidden in a lambda's parameter,
// return, or receiver position is found the same way - whether the lambda is accepted...

public fun onBatch(action: <!MUTABLE_COLLECTION_PUBLIC_API!>(MutableList<Int>) -> Unit<!>) { }

public fun provider(factory: <!MUTABLE_COLLECTION_PUBLIC_API!>() -> MutableSet<String><!>) { }

public fun schedule(task: <!MUTABLE_COLLECTION_PUBLIC_API!>suspend () -> MutableList<Int><!>) { }

// ...returned...

public fun callbackFactory(): <!MUTABLE_COLLECTION_PUBLIC_API!>(MutableList<Int>) -> Unit<!> = {}

// ...or given a mutable receiver: unlike an extension declared on a mutable collection, a
// builder lambda receives mutable state the user did not hold before.

public fun assemble(block: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int>.() -> Unit<!>): List<Int> =
    mutableListOf<Int>().apply(block).toList()

// Deliberately mutable lambdas: acknowledged on the parameter, on the mutable type inside the
// function type, on the whole function type, or on the declaration.

public fun buildInto(@IntentionallyMutableCollection block: MutableList<Int>.() -> Unit): List<Int> =
    mutableListOf<Int>().apply(block).toList()

public fun onEachBatch(action: (@IntentionallyMutableCollection MutableList<Int>) -> Unit) { }

public fun drainWith(action: @IntentionallyMutableCollection (MutableList<Int>) -> Unit) { }

@IntentionallyMutableCollection
public fun batchCallbackFactory(): (MutableList<Int>) -> Unit = {}

@IntentionallyMutableCollection
public val batchSink: (MutableList<Int>) -> Unit = {}

// Lambdas over read-only types stay clean.

public fun transform(f: (List<Int>) -> Set<String>) { }

// Concrete implementations expose the same mutators as the interfaces they implement.

public fun grow(): <!MUTABLE_COLLECTION_PUBLIC_API!>ArrayList<String><!> = ArrayList()

public abstract class IntBuffer : MutableList<Int>

public fun buffer(): <!MUTABLE_COLLECTION_PUBLIC_API!>IntBuffer<!> = TODO()

// Arrays are mutable collections too.

public fun table(): <!MUTABLE_COLLECTION_PUBLIC_API!>Array<String><!> = arrayOf()

public fun bytes(): <!MUTABLE_COLLECTION_PUBLIC_API!>ByteArray<!> = ByteArray(0)

// A vararg parameter receives a defensive copy of the array: no warning...

public fun joined(vararg parts: String): String = parts.joinToString()

// ...but a mutable element type is still shared.

public fun batches(vararg groups: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>) { }

// A mutable bound constrains every instantiation of the type parameter to mutable state,
// exposing the same mutability as a direct mention of the bound.

public fun <T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>> drain(source: T): List<Int> = source.toList()

public fun <T> constrain(value: T): T where T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableSet<String><!> = value

public class Depot<T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableCollection<Int><!>>

public val <T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>> T.lastItem: Int get() = last()

// ...unless the type parameter (or its owner) acknowledges the mutable bound.

public fun <@IntentionallyMutableCollection C : MutableCollection<Int>> fillAll(destination: C): C {
    destination.add(1)
    return destination
}

public class Silo<@IntentionallyMutableCollection T : MutableSet<String>>

@IntentionallyMutableCollection
public fun <T : MutableList<Int>> drainExempt(source: T): List<Int> = source.toList()

// A type-use exemption covers the annotated type and everything nested in it.

public fun snapshots(): List<@IntentionallyMutableCollection MutableList<Int>> = emptyList()

public fun buffered(): @IntentionallyMutableCollection MutableList<Int> = mutableListOf()

// Extensions on mutable collections provide functionality for values the user already
// holds instead of sharing new mutable state: no warning on the receiver.

public fun MutableList<Int>.compact(): List<Int> = toList()

// Overrides repeat the signature fixed by the overridden declaration, which is reported instead.

public interface Producer {
    public fun produceAll(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>
}

public class DefaultProducer : Producer {
    override fun produceAll(): MutableList<Int> = mutableListOf()
}

// Deliberately shared mutable state: no warning.

@IntentionallyMutableCollection
public fun sharedRegistry(): MutableList<String> = mutableListOf()

public fun fill(@IntentionallyMutableCollection target: MutableList<Int>) { target.add(1) }

public class Pipeline(@IntentionallyMutableCollection public val stages: MutableList<String>)

// Read-only collection types: no warning.

public fun snapshot(items: List<Int>): Map<String, Set<Int>> = mapOf("all" to items.toSet())

// Not visible outside the library: no warning.

internal fun scratch(): MutableList<Int> = mutableListOf()

private val cache: MutableMap<String, Int> = mutableMapOf()
