// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INCLUDED_ANNOTATIONS: test.PublicApi
// INCLUDED_NAMES: test.Point.items
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// The positions of an inclusion annotation and a name rule on a property.

package test

/** Includes a declaration. */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FIELD,
)
public annotation class PublicApi

// An annotated setter includes the property.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>WithAnnotatedSetter<!> {
    /** Included. */
    @set:PublicApi
    public var items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!> = mutableListOf()

    public var otherItems: MutableList<Int> = mutableListOf()
}

// A name rule on a property of the primary constructor includes the class through the property.
// The constructor stays outside the filters, so the check of its other parameters doesn't run.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>Point<!>(public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>, flag: Boolean, others: MutableList<Int>)

// The same with an annotation.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>Line<!>(@PublicApi public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>, flag: Boolean, others: MutableList<Int>)

// A `field:` annotation counts for a property with a public backing field: a `@JvmField`, a `const`,
// or a public `lateinit` property.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>WithJvmField<!> {
    /** Included. */
    @field:PublicApi
    @JvmField
    public var items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!> = mutableListOf()

    @JvmField
    public var otherItems: MutableList<Int> = mutableListOf()
}

public object <!UNDOCUMENTED_PUBLIC_API!>WithConst<!> {
    @field:PublicApi
    public const val VERSION: Int = 1
}

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>WithLateinit<!> {
    /** Included. */
    @field:PublicApi
    public lateinit var items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>
}

// A `field:` annotation doesn't count for a property with a private backing field.
public open class WithPrivateField {
    @field:PublicApi
    public var items: MutableList<Int> = mutableListOf()
}

public open class WithPrivateSetterLateinit {
    @field:PublicApi
    public lateinit var items: MutableList<Int>
        private set
}
