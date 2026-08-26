// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -FINAL_UPPER_BOUND -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -DATA_CLASS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName

@JvmInline
public value class UserId(public val raw: String)

// A value class parameter mangles the JVM name: should warn.

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>take<!>(id: UserId) {}

// Nullability doesn't matter: the boxed parameter still mangles the name.
public fun <!MANGLED_JVM_NAME_PUBLIC_API!>find<!>(id: UserId?) {}

// An extension receiver is a parameter on the JVM and mangles the name too.
public fun UserId.<!MANGLED_JVM_NAME_PUBLIC_API!>describe<!>(): String = "user"

// Context parameters become JVM parameters for functions and property accessors.
context(id: UserId)
public fun <!MANGLED_JVM_NAME_PUBLIC_API!>describeContext<!>(): String = id.raw

context(id: UserId)
public val <!MANGLED_JVM_NAME_PUBLIC_API!>contextOwner<!>: String get() = id.raw

// A type parameter erases to its first upper bound, so it mangles like a direct mention.
public fun <T : UserId> <!MANGLED_JVM_NAME_PUBLIC_API!>locate<!>(t: T) {}

// Erasure follows chains of type-parameter bounds.
public fun <T : U, U : UserId> <!MANGLED_JVM_NAME_PUBLIC_API!>locateChained<!>(t: T) {}

// Stdlib value classes count: kotlin.Result and the unsigned types mangle like any other.

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>handle<!>(result: Result<String>) {}

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>pad<!>(width: UInt) {}

// A value class inside a type argument is boxed and leaves the JVM name intact: no warning.
public fun ids(list: List<UserId>): List<UserId> = list

// Top-level callables merely returning a value class keep their JVM name: no warning.

public fun issue(): UserId = UserId("x")

public val topId: UserId = UserId("x")

// A top-level var still mangles its setter, which takes the value as a parameter.
public var <!MANGLED_JVM_NAME_PUBLIC_API!>topVar<!>: UserId = UserId("x")

@set:JvmName("setTopNamed")
public var topNamed: UserId = UserId("x")

// Members returning a value class are mangled, unlike top-level callables.

public class Ledger {
    public fun <!MANGLED_JVM_NAME_PUBLIC_API!>current<!>(): UserId = UserId("x")

    public val <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId get() = UserId("x")

    // @JvmName settles the Java-facing shape: no warning.

    @JvmName("currentNamed")
    public fun renamed(): UserId = UserId("x")

    @get:JvmName("getOwnerNamed")
    public val ownerNamed: UserId get() = UserId("x")

    // The getter is renamed, but the var's setter is still mangled: should warn.
    @get:JvmName("getEditorNamed")
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>editor<!>: UserId = UserId("x")
}

// @JvmSynthetic on an accessor hides it from Java on purpose and settles the Java-facing
// shape the same way @JvmName does.

public class SyntheticLedger {
    @get:JvmSynthetic
    public val ownerHidden: UserId get() = UserId("x")

    // The annotation may sit on the explicit accessor directly.
    public val ownerExplicit: UserId
        @JvmSynthetic get() = UserId("x")

    // The getter is hidden, but the var's setter is still mangled: should warn.
    @get:JvmSynthetic
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>editorHalf<!>: UserId = UserId("x")

    @get:JvmSynthetic
    @set:JvmSynthetic
    public var editorHidden: UserId = UserId("x")
}

// Object members have a dispatch receiver and mangle the same way as class members.
public object Registry {
    public fun <!MANGLED_JVM_NAME_PUBLIC_API!>current<!>(): UserId = UserId("x")
}

// An extension property's accessors take the receiver as a parameter: should warn.
public val UserId.<!MANGLED_JVM_NAME_PUBLIC_API!>pretty<!>: String get() = "user"

// A constructor with a value class parameter is hidden behind a synthetic one, and the property
// created from the val parameter has a mangled getter: both are reported.

public class Wallet<!MANGLED_JVM_NAME_PUBLIC_API!>(public val <!MANGLED_JVM_NAME_PUBLIC_API!>id<!>: UserId)<!>

public class Vault {
    <!MANGLED_JVM_NAME_PUBLIC_API!>public constructor(id: UserId)<!>
}

// The @get:JvmName on the parameter fixes the property getter; the constructor stays hidden.
public class Purse<!MANGLED_JVM_NAME_PUBLIC_API!>(@get:JvmName("getIdNamed") public val id: UserId)<!>

// The @get:JvmSynthetic on the parameter hides the property getter; the constructor stays hidden.
public class Pouch<!MANGLED_JVM_NAME_PUBLIC_API!>(@get:JvmSynthetic public val id: UserId)<!>

// @JvmExposeBoxed generates Java-callable boxed variants next to the mangled entry points:
// no warning for constructors and final members.

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public fun exposedTake(id: UserId) {}

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public class ExposedWallet(public val id: UserId) {
    public fun current(): UserId = id
}

// Open and abstract members get no boxed variant even inside an annotated class: should warn.

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public open class ExposedService {
    public open fun <!MANGLED_JVM_NAME_PUBLIC_API!>handle<!>(id: UserId) {}

    public open val <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId get() = UserId("x")
}

@OptIn(ExperimentalStdlibApi::class)
@JvmExposeBoxed
public abstract class ExposedStore {
    public abstract fun <!MANGLED_JVM_NAME_PUBLIC_API!>store<!>(id: UserId)
}

// Everything declared inside the value class itself is exempt: declaring the public value class
// is the deliberate choice, and @JvmName is not even applicable inside.

@JvmInline
public value class Wrapped(public val id: UserId) {
    public fun pretty(): String = "user"
}

// suspend functions are not Java-friendly regardless of the name: no warning.
public suspend fun fetch(id: UserId) {}

// Overrides repeat the fixed signature reported on the base declaration.

public interface Resolver {
    public fun <!MANGLED_JVM_NAME_PUBLIC_API!>resolve<!>(id: UserId)
}

public class MapResolver : Resolver {
    override fun resolve(id: UserId) {}
}

// @JvmSynthetic hides the declaration from Java on purpose: no warning.
@JvmSynthetic
public fun hidden(id: UserId) {}

// @JvmSynthetic without a use-site target lands on the backing field - it has no property
// target - so the accessors stay visible to Java and still mangled: should warn.
@JvmSynthetic
public var <!MANGLED_JVM_NAME_PUBLIC_API!>fieldOnlySynthetic<!>: UserId = UserId("x")

// @PublishedApi declarations are binary implementation details, not supported Java API.
@PublishedApi
internal fun published(id: UserId) {}

// Acknowledged declarations: no warning.

@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public fun acknowledged(id: UserId) {}

// The parameter exemption covers the property made from it; each constructor is acknowledged
// separately.
public class Account @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) constructor(
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN) public val id: UserId,
) {
    @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
    public constructor(id: UserId, label: String) : this(id)
}

// A class-level exemption covers every declaration inside.
@IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
public class KotlinOnlyLedger(public val id: UserId) {
    public fun current(): UserId = id
}

// Not visible outside the library: no warning.

internal fun internalTake(id: UserId) {}

private fun privateTake(id: UserId) {}
