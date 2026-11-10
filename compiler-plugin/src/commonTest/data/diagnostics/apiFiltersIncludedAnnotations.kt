// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INCLUDED_ANNOTATIONS: test.markers.PublicApi test.markers.*Stable test.markers.Markers.Nested
// INCLUDED_NAMES: test.api.ByName test.markers.**
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: markers.kt
package test.markers

/** Includes a declaration. */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FILE,
)
public annotation class PublicApi

/** Matches `test.markers.*Stable`. */
public annotation class VeryStable

/** Doesn't match a rule. */
public annotation class Unrelated

/** Holds a nested annotation. */
public object Markers {
    /** Matches `test.markers.Markers.Nested`. */
    public annotation class Nested
}

/** An alias of an inclusion annotation. */
public typealias Api = PublicApi

// FILE: api.kt
package test.api

import test.markers.Api
import test.markers.Markers
import test.markers.PublicApi
import test.markers.Unrelated
import test.markers.VeryStable

// An annotation on a class covers the members and the nested classes.
@PublicApi
public class <!UNDOCUMENTED_PUBLIC_API!>Annotated<!> {
    public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>() {}

    public class <!UNDOCUMENTED_PUBLIC_API!>Nested<!>
}

public open class NotAnnotated {
    public fun member(flag: Boolean) {}
}

@Unrelated
public open class WithUnrelatedAnnotation

@VeryStable
public class <!UNDOCUMENTED_PUBLIC_API!>WithWildcardAnnotation<!>

@Markers.Nested
public class <!UNDOCUMENTED_PUBLIC_API!>WithNestedAnnotation<!>

@Api
public class <!UNDOCUMENTED_PUBLIC_API!>WithAliasedAnnotation<!>

// A name rule and an annotation rule add up.
public class <!UNDOCUMENTED_PUBLIC_API!>ByName<!>

// A class with an annotated member is watched, but its other members aren't.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>Shell<!> {
    @PublicApi
    public fun <!UNDOCUMENTED_PUBLIC_API!>member<!>() {}

    public fun otherMember(flag: Boolean) {}

    public open class Nested
}

// An annotated constructor is a member.
public class <!UNDOCUMENTED_PUBLIC_API!>WithAnnotatedConstructor<!> @PublicApi constructor(items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!>) {
    public constructor(items: MutableList<Int>, count: Int) : this(items)
}

// An annotated accessor includes the property.
public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>WithAnnotatedGetter<!> {
    /** Included. */
    @get:PublicApi
    public val items: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!> = mutableListOf()

    public val otherItems: MutableList<Int> = mutableListOf()
}

@PublicApi
public fun <!UNDOCUMENTED_PUBLIC_API!>annotatedFunction<!>() {}

public fun otherFunction(flag: Boolean) {}

/** Included. */
@PublicApi
public val annotatedProperty: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!> = mutableListOf()

public val otherProperty: MutableList<Int> = mutableListOf()

// An overload outside the inclusion rules isn't a sibling.
/** Included. */
@PublicApi
public fun mixed(x: Int, y: Int) {}

public fun mixed(y: Int, x: Int, scale: Double) {}

/** Included. */
@PublicApi
public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>both<!>(x: Int, y: Int) {}

/** Included. */
@PublicApi
public fun <!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>both<!>(y: Int, x: Int, scale: Double) {}

// FILE: file.kt
// A file annotation covers every declaration of the file.
@file:PublicApi

package test.api

import test.markers.PublicApi

public class <!UNDOCUMENTED_PUBLIC_API!>InAnnotatedFile<!>

public fun <!UNDOCUMENTED_PUBLIC_API!>inAnnotatedFile<!>() {}
