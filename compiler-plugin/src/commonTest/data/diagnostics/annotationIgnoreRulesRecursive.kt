// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// IGNORE_WHEN_ANNOTATED: STATEFUL_CLASS_WITHOUT_EQUALS:foo.bar.Generated:recursive BOOLEAN_PARAMETER_PUBLIC_API:foo.bar.Generated:recursive STATEFUL_CLASS_WITHOUT_HASH_CODE:foo.bar.Generated
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: annotation.kt
package foo.bar

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
public annotation class Generated

// FILE: classes.kt
package foo.bar

// A recursive rule covers every declaration nested in the annotated class. The non-recursive rule
// of STATEFUL_CLASS_WITHOUT_HASH_CODE doesn't.
@Generated
public class Outer(public val value: Int) {
    public class <!STATEFUL_CLASS_WITHOUT_HASH_CODE!>Nested<!>(public val value: Int) {
        public class <!STATEFUL_CLASS_WITHOUT_HASH_CODE!>Deeper<!>(public val value: Int)
    }

    public inner class <!STATEFUL_CLASS_WITHOUT_HASH_CODE!>Inner<!>(public val value: Int)

    public fun member(flag: Boolean) {}
}

// A recursive rule doesn't cover the outer class of an annotated nested class.
public class <!STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE!>Plain<!>(public val value: Int) {
    @Generated
    public class Nested(public val value: Int)

    public fun member(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean) {}
}

// FILE: annotatedFile.kt
@file:Generated
package foo.bar

// A recursive rule on a file covers every declaration in the file.
public class <!STATEFUL_CLASS_WITHOUT_HASH_CODE!>FileState<!>(public val value: Int) {
    public class <!STATEFUL_CLASS_WITHOUT_HASH_CODE!>Nested<!>(public val value: Int)
}

public fun inAnnotatedFile(flag: Boolean) {}
