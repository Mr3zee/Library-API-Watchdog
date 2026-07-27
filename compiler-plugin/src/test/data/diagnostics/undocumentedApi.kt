// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXHAUSTIVE_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION -DATA_CLASS_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyUndocumented

// Public API without KDoc: should warn.

public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedClass<!>

public interface <!UNDOCUMENTED_PUBLIC_API!>UndocumentedInterface<!>

public object <!UNDOCUMENTED_PUBLIC_API!>UndocumentedObject<!>

public enum class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedEnum<!> {
    <!UNDOCUMENTED_PUBLIC_API!>A<!>,
}

public annotation class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedAnnotation<!>

// Every public declaration kind is watched, not only classifiers.

public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedTopLevelFunction<!>() {}

public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedTopLevelProperty<!>: Int = 0

public typealias <!UNDOCUMENTED_PUBLIC_API!>UndocumentedTypeAlias<!> = String

/** Documented. */
public fun documentedTopLevelFunction() {}

/** Documented. */
public val documentedTopLevelProperty: Int = 0

/** Documented. */
public typealias DocumentedTypeAlias = String

// Non-KDoc comments are not documentation: should warn.

// A line comment is not KDoc.
public class <!UNDOCUMENTED_PUBLIC_API!>LineCommentedClass<!>

/* A block comment is not KDoc. */
public class <!UNDOCUMENTED_PUBLIC_API!>BlockCommentedClass<!>

// Documented API: no warning.

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

// Deliberately undocumented: no warning.

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

// The acknowledgment covers only the annotated declaration, not its nested ones.

@IntentionallyUndocumented
public class DeliberatelyUndocumentedOuter {
    public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNestedInAcknowledged<!>
}

// Nested public declarations are checked on their own.

/** Documented outer. */
public class DocumentedOuter {
    public class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedNested<!>

    /** Documented nested. */
    public class DocumentedNested

    private class PrivateNested
}

// Public members without KDoc: should warn.

/** Documented. */
public class WithUndocumentedMembers {
    public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedFunction<!>() {}

    public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedProperty<!>: Int = 0
}

// Members documented by tags in the class KDoc stay clean: `@property` covers properties (both
// constructor and body ones), `@constructor` and `@param` cover the primary constructor.

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
    public val fromConstructor: Int,
) {
    public val fromBody: Int = input
}

// A class KDoc covers a property only through a matching `@property` tag; the KDoc's mere
// presence doesn't document the properties.

/**
 * Documented, but the tags cover only one of the two properties.
 *
 * @property covered Documented via the class KDoc.
 */
public class ClassKDocNotCoveringAllProperties(public val covered: Int) {
    public val <!UNDOCUMENTED_PUBLIC_API!>uncovered<!>: Int = 0
}

// A constructor property can also carry its own KDoc on the parameter.

/** Documented. */
public class ConstructorPropertyDocumentedInPlace(
    /** Documented on the parameter itself. */
    public val documentedParameter: Int,
    public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedParameter<!>: Int,
)

// The primary constructor cannot carry its own KDoc - `@constructor` and `@param` tags in the
// class KDoc describe it - so only secondary constructors are watched.

/** Documented. */
public class WithSecondaryConstructors(input: Int) {
    <!UNDOCUMENTED_PUBLIC_API!>public constructor() : this(0)<!>

    /** Documented secondary constructor. */
    public constructor(a: Int, b: Int) : this(a + b)
}

// Overrides inherit the documentation of the declaration they override: no warning on them.

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

// Enum entries are part of the public API surface.

/** Documented enum with entries in every documentation state. */
public enum class DocumentedEnumWithEntries {
    /** Documented entry. */
    DOCUMENTED_ENTRY,

    <!UNDOCUMENTED_PUBLIC_API!>UNDOCUMENTED_ENTRY<!>,

    @IntentionallyUndocumented
    ACKNOWLEDGED_ENTRY,
}

// Compiler-generated members (data class copy/componentN, enum values/valueOf/entries) have no
// source of their own: no warning.

/**
 * Documented.
 *
 * @property value Documented via the class KDoc.
 */
public data class DocumentedDataClass(public val value: Int)

// Not visible outside the library: no warning.

internal class InternalClass

private class PrivateClass

internal class InternalOuter {
    class NestedInsideInternal

    fun memberOfInternal() {}
}

/** Members that users cannot see need no documentation. */
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
