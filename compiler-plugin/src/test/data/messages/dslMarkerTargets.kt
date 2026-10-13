// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

// Message parameters: the marker name and, for a no-op target, each target name a DSL marker has
// no effect on.

// The EXPRESSION target needs SOURCE retention.
@DslMarker
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPEALIAS,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.TYPE_PARAMETER<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.PROPERTY<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FIELD<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.LOCAL_VARIABLE<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.VALUE_PARAMETER<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.CONSTRUCTOR<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FUNCTION<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.PROPERTY_GETTER<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.PROPERTY_SETTER<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FILE<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.EXPRESSION<!>,
)
public annotation class TreeDsl

@DslMarker
public annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>DefaultTargetsDsl<!>
