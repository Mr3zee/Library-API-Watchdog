// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INCLUDED_NAMES: test.api.** test.other.Included test.other.Shell.member test.other.Shell.property test.other.topLevel test.other.Outer.Inner test.other.Levels.HIGH
// EXCLUDED_NAMES: test.api.internal.** test.other.Included.Excluded
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: api.kt
package test.api

import test.other.NotIncluded

public class <!UNDOCUMENTED_PUBLIC_API!>InIncludedPackage<!> {
    public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>() {}

    public class <!UNDOCUMENTED_PUBLIC_API!>Nested<!>
}

public fun <!UNDOCUMENTED_PUBLIC_API!>inIncludedPackage<!>() {}

/** A type outside the inclusion rules in a watched signature isn't reported. */
public fun usesNotIncluded(value: NotIncluded) {}

// FILE: internal.kt
// An exclusion rule wins over an inclusion rule.
package test.api.internal

public open class ExcludedInIncludedPackage

public fun excludedInIncludedPackage(flag: Boolean) {}

// FILE: other.kt
package test.other

// A rule on a class covers the members and the nested classes.
public class <!UNDOCUMENTED_PUBLIC_API!>Included<!> {
    public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>() {}

    public class <!UNDOCUMENTED_PUBLIC_API!>Nested<!>

    public open class Excluded
}

public open class NotIncluded {
    public fun member(flag: Boolean) {}
}

// A class with an included member is watched, but its other members aren't.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>Shell<!>(flag: Boolean) {
    public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>() {}

    public fun otherMember(flag: Boolean) {}

    /** Included. */
    public val property: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!> = mutableListOf()

    public val otherProperty: MutableList<Int> = mutableListOf()

    public open class Nested
}

public fun <!UNDOCUMENTED_PUBLIC_API!>topLevel<!>() {}

public fun otherTopLevel(flag: Boolean) {}

// An included nested class doesn't include the enclosing class.
public open class Outer {
    public class <!UNDOCUMENTED_PUBLIC_API!>Inner<!> {
        public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>() {}
    }

    public fun member(flag: Boolean) {}
}

// An included enum entry includes the enum class.
public enum class <!EXHAUSTIVE_PUBLIC_API, UNDOCUMENTED_PUBLIC_API!>Levels<!> {
    HIGH,
    LOW,
}

public enum class OtherLevels {
    HIGH,
}
