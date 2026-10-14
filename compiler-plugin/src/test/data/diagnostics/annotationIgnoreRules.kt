// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// IGNORE_WHEN_ANNOTATED: STATEFUL_CLASS_WITHOUT_EQUALS:foo.bar.GeneratedEquals OPEN_API_WITHOUT_SUBCLASS_OPT_IN:foo.bar.GeneratedEquals TOP_LEVEL_API_WITHOUT_JVM_NAME:foo.bar.GeneratedEquals
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// FILE: annotation.kt
package foo.bar

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
public annotation class GeneratedEquals

// FILE: classes.kt
package foo.bar

// The rule applies to the annotated class.
@GeneratedEquals
public class Generated(public val value: Int)

// A nested class doesn't inherit the rule from the outer class.
@GeneratedEquals
public class Outer(public val value: Int) {
    public class <!STATEFUL_CLASS_WITHOUT_EQUALS!>Nested<!>(public val value: Int)

    public inner class <!STATEFUL_CLASS_WITHOUT_EQUALS!>Inner<!>(public val value: Int)
}

// The rule covers an annotated nested class and not its outer class.
public class <!STATEFUL_CLASS_WITHOUT_EQUALS!>Plain<!>(public val value: Int) {
    @GeneratedEquals
    public class Nested(public val value: Int)
}

// The class annotation covers what the class check reports on a constructor.
@GeneratedEquals
public open class OpenWithSecondaryConstructor private constructor() {
    public constructor(value: Int) : this()
}

public open class OpenPlain private constructor() {
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>public constructor(value: Int) : this()<!>
}

// FILE: annotatedFunction.kt
package foo.bar

// An annotation on the reported function doesn't cover a file check.
@GeneratedEquals
public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>annotatedTopLevel<!>(): Int = 0

// FILE: annotatedFile.kt
@file:GeneratedEquals
package foo.bar

// A file annotation ignores a file check.
public fun fileAnnotated(): Int = 0
