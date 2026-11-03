// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
public annotation class TreeDsl

public class Tag

public fun process(tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag) { }

public fun make(): <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag = Tag()

public val current: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag = Tag()

public fun local(): Unit {
    val tag: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag = Tag()
    tag.toString()
}
