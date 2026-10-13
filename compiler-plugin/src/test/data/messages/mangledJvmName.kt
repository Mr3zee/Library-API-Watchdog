// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

package foo.bar

@JvmInline
public value class UserId(public val raw: String)

public fun <!MANGLED_JVM_NAME_PUBLIC_API!>take<!>(id: UserId) {}

public class Wallet<!MANGLED_JVM_NAME_PUBLIC_API!>(public val <!MANGLED_JVM_NAME_PUBLIC_API!>id<!>: UserId)<!>

public class Ledger {
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>editor<!>: UserId = UserId("x")

    @get:JvmName("getOwnerNamed")
    public var <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId = UserId("x")
}

public open class Service {
    public open fun <!MANGLED_JVM_NAME_PUBLIC_API!>handle<!>(id: UserId) {}

    public open val <!MANGLED_JVM_NAME_PUBLIC_API!>owner<!>: UserId get() = UserId("x")

    public open var <!MANGLED_JVM_NAME_PUBLIC_API!>editor<!>: UserId = UserId("x")

    @get:JvmSynthetic
    public open var <!MANGLED_JVM_NAME_PUBLIC_API!>setterOnly<!>: UserId = UserId("x")
}
