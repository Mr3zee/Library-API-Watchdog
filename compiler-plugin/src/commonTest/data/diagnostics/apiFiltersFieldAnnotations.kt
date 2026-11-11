// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INTERNAL_API_ANNOTATIONS: test.Hidden
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// An exclusion annotation with the `field:` target excludes the property.

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

    /** Excluded with a private backing field. */
    @field:Hidden
    public var excludedPrivateField: MutableList<InternalModel> = mutableListOf()

    /** Excluded with a private setter. */
    @field:Hidden
    public lateinit var excludedPrivateSetter: MutableList<InternalModel>
        private set

    /** Watched. */
    public var watched: <!MUTABLE_COLLECTION_PUBLIC_API, PUBLIC_TYPE_WITH_INTERNAL_API!>MutableList<InternalModel><!> = mutableListOf()
}

@field:Hidden
public const val EXCLUDED_CONSTANT: Int = 1

public const val <!UNDOCUMENTED_PUBLIC_API!>WATCHED_CONSTANT<!>: Int = 2

/** An excluded property doesn't make its class an internal API type. */
public fun loadHolder(): Holder = Holder()
