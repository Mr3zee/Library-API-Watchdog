// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// REPORT_UNDOCUMENTED_CLASS_PROPERTIES
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -DATA_CLASS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -COMPANION_API_WITHOUT_JVM_STATIC -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyUndocumented

public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedClass<!>

public interface <!UNDOCUMENTED_PUBLIC_API!>UndocumentedInterface<!>

public object <!UNDOCUMENTED_PUBLIC_API!>UndocumentedObject<!>

public enum class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedEnum<!> {
    A,
}

public annotation class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedAnnotation<!>

public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedTopLevelFunction<!>() {}

public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedTopLevelProperty<!>: Int = 0

public typealias <!UNDOCUMENTED_PUBLIC_API!>UndocumentedTypeAlias<!> = String

/** Documented. */
public fun documentedTopLevelFunction() {}

/** Documented. */
public val documentedTopLevelProperty: Int = 0

/** Documented. */
public typealias DocumentedTypeAlias = String

// A line comment isn't KDoc.
public class <!UNDOCUMENTED_PUBLIC_API!>LineCommentedClass<!>

/* A block comment isn't KDoc. */
public class <!UNDOCUMENTED_PUBLIC_API!>BlockCommentedClass<!>

/** Documented. */
public class DocumentedClass

/**
 * Documented with a multi-line KDoc.
 */
public interface DocumentedInterface

/** An annotation applied below. */
public annotation class Marker

/** Documented even though annotations follow the KDoc. */
@Marker
public class DocumentedAnnotatedClass

@IntentionallyUndocumented
public class DeliberatelyUndocumentedClass

@IntentionallyUndocumented
public object DeliberatelyUndocumentedObject

@IntentionallyUndocumented
public typealias DeliberatelyUndocumentedTypeAlias = Int

/** Documented. */
public class WithAcknowledgedMembers {
    @IntentionallyUndocumented
    public constructor()

    @IntentionallyUndocumented
    public fun acknowledgedFunction() {}

    @IntentionallyUndocumented
    public val acknowledgedProperty: Int = 0
}

// The exemption doesn't cover nested declarations.

@IntentionallyUndocumented
public class DeliberatelyUndocumentedOuter {
    public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNestedInAcknowledged<!>
}

/** Documented outer. */
public class DocumentedOuter {
    public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNested<!>

    /** Documented nested. */
    public class DocumentedNested

    private class PrivateNested
}

/** Documented. */
public class WithUndocumentedMembers {
    public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedFunction<!>() {}

    public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedProperty<!>: Int = 0
}

// The `@property` and `@param` tags of the class KDoc don't document the properties. Only the
// primary constructor takes its documentation from the class KDoc.

/**
 * Documented.
 *
 * @constructor Documented via the class KDoc.
 * @param input Documented via the class KDoc.
 * @property fromConstructor Documented via the class KDoc.
 * @property fromBody Documented via the class KDoc.
 */
public class DocumentedThroughClassKDocTags(
    input: Int,
    public val <!UNDOCUMENTED_PUBLIC_API!>fromConstructor<!>: Int,
) {
    public val <!UNDOCUMENTED_PUBLIC_API!>fromBody<!>: Int = input
}

/** Documented. */
public class ConstructorPropertyDocumentedInPlace(
    /** Documented on the parameter itself. */
    public val documentedParameter: Int,
    public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedParameter<!>: Int,
)

// A primary constructor can't carry its own KDoc, so the check watches only secondary constructors.

/** Documented. */
public class WithSecondaryConstructors(input: Int) {
    <!UNDOCUMENTED_PUBLIC_API!>public constructor() : this(0)<!>

    /** Documented secondary constructor. */
    public constructor(a: Int, b: Int) : this(a + b)
}

// A companion with the default name doesn't need KDoc. The check watches a named companion.

/** Documented. */
public class WithDefaultNamedCompanion {
    public companion object {
        /** Documented member. */
        public fun documentedMember(): Int = 0

        public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedMember<!>(): Int = 0

        public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedValue<!>: Int = 0
    }
}

/** Documented. */
public class WithNamedCompanion {
    public companion object <!UNDOCUMENTED_PUBLIC_API!>Factory<!> {
        /** Documented member. */
        public fun create(): WithNamedCompanion = WithNamedCompanion()
    }
}

/** Documented. */
public class WithDocumentedNamedCompanion {
    /** Documented companion. */
    public companion object Factory
}

// An override inherits the documentation.

/** Documented. */
public interface DocumentedContract {
    /** Documented member. */
    public fun documentedMember(): Int

    public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedMember<!>(): Int

    public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedValue<!>: Int
}

/** Documented. */
public class DocumentedImplementation : DocumentedContract {
    override fun documentedMember(): Int = 0

    override fun undocumentedMember(): Int = 0

    override val undocumentedValue: Int = 0
}

// An enum entry isn't checked.

/** Documented enum with entries in every documentation state. */
public enum class DocumentedEnumWithEntries {
    /** Documented entry. */
    DOCUMENTED_ENTRY,

    UNDOCUMENTED_ENTRY,

    @IntentionallyUndocumented
    ACKNOWLEDGED_ENTRY,
}

/** Documented. */
public data class DocumentedDataClass(
    /** Documented. */
    public val value: Int,
)

internal class InternalClass

private class PrivateClass

internal class InternalOuter {
    class NestedInsideInternal

    fun memberOfInternal() {}
}

/** Members that users can't see don't need documentation. */
public abstract class WithNonPublicMembers {
    internal fun internalFunction() {}

    private val privateProperty: Int = 0

    protected abstract fun <!UNDOCUMENTED_PUBLIC_API!>protectedFunction<!>(): Int
}

/** Local declarations are invisible to users: no warning inside. */
public fun documentedFunction() {
    val localProperty: Int = 0

    fun localFunction(): Int = localProperty

    class LocalClass {
        fun memberOfLocalClass(): Int = localFunction()
    }

    LocalClass()
}
