// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection

public fun produce(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<String><!> = mutableListOf()

public fun consume(items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableSet<Int><!>) { items.clear() }

public fun cursor(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableIterator<Int><!> = TODO()

public val registry: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableMap<String, Int><!> = mutableMapOf()

public var handles: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableCollection<Int><!> = mutableListOf()

public class Holder(public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

public class Sink(buffer: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>)

// `@PublishedApi` doesn't make a declaration callable in source.
@PublishedApi
internal fun stash(): MutableList<Int> = mutableListOf()

public fun nested(): <!MUTABLE_COLLECTION_PUBLIC_API!>List<MutableList<Int>><!> = emptyList()

public typealias Bag = MutableList<Int>

public fun aliased(): <!MUTABLE_COLLECTION_PUBLIC_API!>Bag<!> = mutableListOf()

public val makeBuffer: <!MUTABLE_COLLECTION_PUBLIC_API!>() -> MutableList<Int><!> = { mutableListOf() }

public fun onBatch(action: <!MUTABLE_COLLECTION_PUBLIC_API!>(MutableList<Int>) -> Unit<!>) { }

public fun provider(factory: <!MUTABLE_COLLECTION_PUBLIC_API!>() -> MutableSet<String><!>) { }

public fun schedule(task: <!MUTABLE_COLLECTION_PUBLIC_API!>suspend () -> MutableList<Int><!>) { }

public fun callbackFactory(): <!MUTABLE_COLLECTION_PUBLIC_API!>(MutableList<Int>) -> Unit<!> = {}

// A function-type receiver is reported, unlike an extension receiver: the lambda gets state that
// the user didn't hold before.

public fun assemble(block: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int>.() -> Unit<!>): List<Int> =
    mutableListOf<Int>().apply(block).toList()

public fun buildInto(@IntentionallyMutableCollection block: MutableList<Int>.() -> Unit): List<Int> =
    mutableListOf<Int>().apply(block).toList()

public fun onEachBatch(action: (@IntentionallyMutableCollection MutableList<Int>) -> Unit) { }

public fun drainWith(action: @IntentionallyMutableCollection (MutableList<Int>) -> Unit) { }

@IntentionallyMutableCollection
public fun batchCallbackFactory(): (MutableList<Int>) -> Unit = {}

@IntentionallyMutableCollection
public val batchSink: (MutableList<Int>) -> Unit = {}

public fun transform(f: (List<Int>) -> Set<String>) { }

public fun grow(): <!MUTABLE_COLLECTION_PUBLIC_API!>ArrayList<String><!> = ArrayList()

public abstract class IntBuffer : MutableList<Int>

public fun buffer(): <!MUTABLE_COLLECTION_PUBLIC_API!>IntBuffer<!> = TODO()

// `BufferMarker` is an unrelated first superinterface.

public interface BufferMarker

public abstract class BranchedBuffer : BufferMarker, MutableCollection<Int>

public abstract class SpecializedBuffer : BranchedBuffer()

public abstract class ReadOnlyBuffer : Collection<Int>

public fun branchedBuffer(): <!MUTABLE_COLLECTION_PUBLIC_API!>BranchedBuffer<!> = TODO()

public fun specializedBuffer(): <!MUTABLE_COLLECTION_PUBLIC_API!>SpecializedBuffer<!> = TODO()

public fun readOnlyBuffer(): ReadOnlyBuffer = TODO()

public typealias ReadOnlyBox<T> = List<T>

public fun boxedBuffer(): <!MUTABLE_COLLECTION_PUBLIC_API!>ReadOnlyBox<MutableList<Int>><!> = emptyList()

public fun table(): <!MUTABLE_COLLECTION_PUBLIC_API!>Array<String><!> = arrayOf()

public fun bytes(): <!MUTABLE_COLLECTION_PUBLIC_API!>ByteArray<!> = ByteArray(0)

// A vararg array is a defensive copy. Its mutable elements aren't.

public fun joined(vararg parts: String): String = parts.joinToString()

public fun batches(vararg groups: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>) { }

public fun <T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>> drain(source: T): List<Int> = source.toList()

public fun <T> constrain(value: T): T where T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableSet<String><!> = value

public class Depot<T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableCollection<Int><!>>

public val <T : <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>> T.lastItem: Int get() = last()

public fun <@IntentionallyMutableCollection C : MutableCollection<Int>> fillAll(destination: C): C {
    destination.add(1)
    return destination
}

public class Silo<@IntentionallyMutableCollection T : MutableSet<String>>

@IntentionallyMutableCollection
public fun <T : MutableList<Int>> drainExempt(source: T): List<Int> = source.toList()

public fun snapshots(): List<@IntentionallyMutableCollection MutableList<Int>> = emptyList()

public fun buffered(): @IntentionallyMutableCollection MutableList<Int> = mutableListOf()

public fun MutableList<Int>.compact(): List<Int> = toList()

// An override isn't reported.

public interface Producer {
    public fun produceAll(): <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>
}

public class DefaultProducer : Producer {
    override fun produceAll(): MutableList<Int> = mutableListOf()
}

@IntentionallyMutableCollection
public fun sharedRegistry(): MutableList<String> = mutableListOf()

public fun fill(@IntentionallyMutableCollection target: MutableList<Int>) { target.add(1) }

public class Pipeline(@IntentionallyMutableCollection public val stages: MutableList<String>)

public fun snapshot(items: List<Int>): Map<String, Set<Int>> = mapOf("all" to items.toSet())

internal fun scratch(): MutableList<Int> = mutableListOf()

private val cache: MutableMap<String, Int> = mutableMapOf()
