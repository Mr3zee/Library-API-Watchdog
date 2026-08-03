// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -TOP_LEVEL_API_WITHOUT_JVM_NAME -KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC

package foo.bar

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
public annotation class TreeDsl

@DslMarker
@Target(AnnotationTarget.TYPE)
public annotation class LayoutDsl

public open class Tag

// Inert type positions: the value is only ever accessed by name, so the marker restricts nothing.

public fun process(tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag) { }

// Every marker on the same inert type position is reported.
public fun processTwice(
    tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> <!DSL_MARKER_NOOP_TYPE_POSITION!>@LayoutDsl<!> Tag,
) { }

public fun make(): <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag = Tag()

public val current: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag = Tag()

public class Holder(public val tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag)

public fun local(): Unit {
    val tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag = Tag()
    tag.toString()
}

// A function type without a receiver has no implicit value to propagate the marker to.
public fun run(block: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> () -> Unit): Unit = block()

// Use sites are checked regardless of visibility: an inert marker misleads the authors too.
internal fun internalProcess(tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag) { }

// Effective type positions: no warning.

// The marker on a function type propagates to its receiver.
public fun tree(block: @TreeDsl Tag.() -> Unit): Unit = Tag().block()

// All markers are effective when the function type has an implicit receiver.
public fun layoutTree(block: @TreeDsl @LayoutDsl Tag.() -> Unit): Unit = Tag().block()

// A context parameter is an implicit function-type value too.
public fun contextualTree(block: @TreeDsl context(Tag) () -> Unit): Unit { }

// The marker on the receiver type inside a function type marks the lambda receiver.
public fun tree2(block: (@TreeDsl Tag).() -> Unit): Unit = Tag().block()

// The marker on an extension receiver type marks `this` inside the body.
public fun (@TreeDsl Tag).build() { }

// A marked supertype marks every instance of the subclass.
public class Div : @TreeDsl Tag()

// A marked type alias expansion makes the alias carry the marker like a marked class.
public typealias MarkedTag = @TreeDsl Tag

// Not a DSL marker: type positions are not checked.

@Target(AnnotationTarget.TYPE)
public annotation class PlainTypeAnnotation

public fun plain(tag: @PlainTypeAnnotation Tag) { }

// Known limitation: markers nested in type arguments are not analyzed.
public val tags: List<@TreeDsl Tag> = emptyList()
