// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -EXEMPTION_WITHOUT_EXPLANATION -COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS -COMPANION_API_WITHOUT_JVM_STATIC -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

// By default a member property isn't reported. A top-level property doesn't have a class KDoc to hold a
// `@property` tag, so it stays reported.

public val <!UNDOCUMENTED_PUBLIC_API!>undocumentedTopLevelProperty<!>: Int = 0

public val String.<!UNDOCUMENTED_PUBLIC_API!>undocumentedTopLevelExtension<!>: Int get() = length

/**
 * Documented.
 *
 * @property fromConstructor Documented via the class KDoc.
 * @property fromBody Documented via the class KDoc.
 */
public class DocumentedThroughClassKDocTags(
    public val fromConstructor: Int,
) {
    public val fromBody: Int = 0

    public val String.memberExtension: Int get() = length

    public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedFunction<!>() {}

    public companion object {
        public val companionProperty: Int = 0
    }
}

/** Documented. */
public interface DocumentedContract {
    public val abstractProperty: Int

    public fun <!UNDOCUMENTED_PUBLIC_API!>undocumentedMember<!>(): Int
}

/** Documented. */
public object DocumentedObject {
    public val objectProperty: Int = 0
}
