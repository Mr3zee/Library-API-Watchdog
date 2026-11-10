// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INTERNAL_API_ANNOTATIONS: test.Hidden
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// An exclusion annotation with the `field:` target counts for a property with a public backing
// field: a `@JvmField`, a `const`, or a public `lateinit` property. It doesn't count for a property
// with a private backing field.

package test

/** Excludes a declaration. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FIELD)
public annotation class Hidden

/** An internal API type. */
@Hidden
public class InternalModel

/** Holds the properties. */
public class Holder {
    /** Excluded, so it can expose an internal API type. */
    @field:Hidden
    @JvmField
    public var excludedJvmField: MutableList<InternalModel> = mutableListOf()

    /** Excluded. */
    @field:Hidden
    public lateinit var excludedLateinit: MutableList<InternalModel>

    /** The backing field is private, so the annotation doesn't count. */
    @field:Hidden
    public var watched: <!MUTABLE_COLLECTION_PUBLIC_API, PUBLIC_TYPE_WITH_INTERNAL_API!>MutableList<InternalModel><!> = mutableListOf()

    /** The setter is private, so the backing field is private too. */
    @field:Hidden
    public lateinit var watchedLateinit: <!MUTABLE_COLLECTION_PUBLIC_API, PUBLIC_TYPE_WITH_INTERNAL_API!>MutableList<InternalModel><!>
        private set
}

@field:Hidden
public const val EXCLUDED_CONSTANT: Int = 1

public const val <!UNDOCUMENTED_PUBLIC_API!>WATCHED_CONSTANT<!>: Int = 2

/** An excluded property doesn't make its class an internal API type. */
public fun loadHolder(): Holder = Holder()
