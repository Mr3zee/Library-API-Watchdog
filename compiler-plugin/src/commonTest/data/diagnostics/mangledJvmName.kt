// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -FINAL_UPPER_BOUND -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -DATA_CLASS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName

@JvmInline
public value class UserId(public val raw: String)

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>take<!>(id: UserId) {}

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>find<!>(id: UserId?) {}

public fun UserId.<!MANGLED_JVM_NAME_PUBLIC_API!>describe<!>(): String = "user"

context(id: UserId)
public fun <!MANGLED_JVM_NAME_PUBLIC_API!>describeContext<!>(): String = id.raw

context(id: UserId)
public val <!MANGLED_JVM_NAME_PUBLIC_API!>contextOwner<!>: String get() = id.raw

// A type parameter erases to its first upper bound.
public fun <T : UserId> <!MANGLED_JVM_NAME_PUBLIC_API!>locate<!>(t: T) {}

public fun <T : U, U : UserId> <!MANGLED_JVM_NAME_PUBLIC_API!>locateChained<!>(t: T) {}

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>handle<!>(result: Result<String>) {}

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>pad<!>(width: UInt) {}

// The JVM boxes a value class in a type argument.
public fun ids(list: List<UserId>): List<UserId> = list

// A return type mangles the name of a member only.

public fun issue(): UserId = UserId("x")

public val topId: UserId = UserId("x")

// The setter of a `var` takes the value as a parameter.
public var <!MANGLED_JVM_NAME_PUBLIC_API!>topVar<!>: UserId = UserId("x")

@set:JvmName("setTopNamed")
public var topNamed: UserId = UserId("x")

public class Ledger {
    public fun <!MANGLED_JVM_NAME_PUBLIC_API!>current<!>(): UserId = UserId("x")

    public val <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId get() = UserId("x")

    @JvmName("currentNamed")
    public fun renamed(): UserId = UserId("x")

    @get:JvmName("getOwnerNamed")
    public val ownerNamed: UserId get() = UserId("x")

    // The setter is still mangled.
    @get:JvmName("getEditorNamed")
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>editor<!>: UserId = UserId("x")
}

public class SyntheticLedger {
    @get:JvmSynthetic
    public val ownerHidden: UserId get() = UserId("x")

    public val ownerExplicit: UserId
        @JvmSynthetic get() = UserId("x")

    @get:JvmSynthetic
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>editorHalf<!>: UserId = UserId("x")

    @get:JvmSynthetic
    @set:JvmSynthetic
    public var editorHidden: UserId = UserId("x")
}

public object Registry {
    public fun <!MANGLED_JVM_NAME_PUBLIC_API!>current<!>(): UserId = UserId("x")
}

public val UserId.<!MANGLED_JVM_NAME_PUBLIC_API!>pretty<!>: String get() = "user"

// A synthetic constructor hides a constructor with a value class parameter.

public class Wallet<!MANGLED_JVM_NAME_PUBLIC_API!>(public val <!MANGLED_JVM_NAME_PUBLIC_API!>id<!>: UserId)<!>

public class Vault {
    <!MANGLED_JVM_NAME_PUBLIC_API!>public constructor(id: UserId)<!>
}

public class ContextWallet {
    <!MANGLED_JVM_NAME_PUBLIC_API!><!UNSUPPORTED!>context(id: UserId)<!>
    public constructor()<!>
}

public class Purse<!MANGLED_JVM_NAME_PUBLIC_API!>(@get:JvmName("getIdNamed") public val id: UserId)<!>

public class Pouch<!MANGLED_JVM_NAME_PUBLIC_API!>(@get:JvmSynthetic public val id: UserId)<!>

// `@JvmExposeBoxed` generates boxed variants for constructors and final members only.

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public fun exposedTake(id: UserId) {}

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public class ExposedWallet(public val id: UserId) {
    public fun current(): UserId = id
}

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public open class ExposedService {
    public open fun <!MANGLED_JVM_NAME_PUBLIC_API!>handle<!>(id: UserId) {}

    public open val <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId get() = UserId("x")

    public open var <!MANGLED_JVM_NAME_PUBLIC_API!>editor<!>: UserId = UserId("x")

    @get:JvmSynthetic
    public open var <!MANGLED_JVM_NAME_PUBLIC_API!>setterOnly<!>: UserId = UserId("x")
}

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public abstract class ExposedStore {
    public abstract fun <!MANGLED_JVM_NAME_PUBLIC_API!>store<!>(id: UserId)
}

// The check skips the members of a value class. `@JvmName` isn't applicable inside.

@JvmInline
public value class Wrapped(public val id: UserId) {
    public fun pretty(): String = "user"
}

// A suspend function is reported by KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC instead.
public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>fetch<!>(id: UserId) {}

// An override isn't reported.

public interface Resolver {
    public fun <!MANGLED_JVM_NAME_PUBLIC_API!>resolve<!>(id: UserId)
}

public class MapResolver : Resolver {
    override fun resolve(id: UserId) {}
}

@JvmSynthetic
public fun hidden(id: UserId) {}

// `@JvmSynthetic` without a use-site target goes to the backing field, so the accessors stay
// visible.
@JvmSynthetic
public var <!MANGLED_JVM_NAME_PUBLIC_API!>fieldOnlySynthetic<!>: UserId = UserId("x")

// `@PublishedApi` declarations aren't supported Java API.
@PublishedApi
internal fun published(id: UserId) {}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public fun acknowledged(id: UserId) {}

// Each constructor needs its own exemption.
public class Account @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) constructor(
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) public val id: UserId,
) {
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
    public constructor(id: UserId, label: String) : this(id)
}

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class KotlinOnlyLedger(public val id: UserId) {
    public fun current(): UserId = id
}

internal fun internalTake(id: UserId) {}

private fun privateTake(id: UserId) {}
