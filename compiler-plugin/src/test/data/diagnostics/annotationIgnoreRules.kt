// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// IGNORE_WHEN_ANNOTATED: STATEFUL_CLASS_WITHOUT_EQUALS:foo.bar.Generated OPEN_API_WITHOUT_SUBCLASS_OPT_IN:foo.bar.Generated TOP_LEVEL_API_WITHOUT_JVM_NAME:foo.bar.Generated KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC:foo.bar.Generated
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// FILE: annotation.kt
package foo.bar

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FILE)
public annotation class Generated

// FILE: classes.kt
package foo.bar

// The rule applies to the annotated class.
@Generated
public class GeneratedState(public val value: Int)

// A nested class doesn't inherit the rule from the outer class.
@Generated
public class Outer(public val value: Int) {
    public class <!STATEFUL_CLASS_WITHOUT_EQUALS!>Nested<!>(public val value: Int)

    public inner class <!STATEFUL_CLASS_WITHOUT_EQUALS!>Inner<!>(public val value: Int)
}

// The rule covers an annotated nested class and not its outer class.
public class <!STATEFUL_CLASS_WITHOUT_EQUALS!>Plain<!>(public val value: Int) {
    @Generated
    public class Nested(public val value: Int)
}

// The class annotation covers what the class check reports on a constructor.
@Generated
public open class OpenWithSecondaryConstructor private constructor() {
    public constructor(value: Int) : this()
}

public open class OpenPlain private constructor() {
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>public constructor(value: Int) : this()<!>
}

// FILE: enclosingClass.kt
package foo.bar

// The exemption of KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC covers the enclosing class, so the rule does too.
@Generated
public class KotlinOnlyHolder {
    public suspend fun covered(): Int = 0

    public class Nested {
        public suspend fun coveredDeeper(): Int = 0
    }
}

public class PlainHolder {
    public suspend fun <!KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC!>reported<!>(): Int = 0

    @Generated
    public suspend fun covered(): Int = 0
}

// FILE: annotatedFunction.kt
package foo.bar

// An annotation on the reported function doesn't cover a file check.
@Generated
public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME!>annotatedTopLevel<!>(): Int = 0

// FILE: annotatedFile.kt
@file:Generated
package foo.bar

// A file annotation ignores a file check.
public fun fileAnnotated(): Int = 0
