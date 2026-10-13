// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FUNCTION<!>)
public annotation class KtorDsl

@DslMarker
@Target(
    AnnotationTarget.CLASS,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.PROPERTY<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.VALUE_PARAMETER<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.CONSTRUCTOR<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.TYPE_PARAMETER<!>,
)
public annotation class ManyNoopsDsl

@DslMarker
@Target(allowedTargets = [AnnotationTarget.TYPE, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.LOCAL_VARIABLE<!>])
public annotation class ArrayFormDsl

// The default target set has many no-op targets and lacks TYPE and TYPEALIAS.

@DslMarker
public annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>DefaultTargetsDsl<!>

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
public annotation class TidyDsl

@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class ClassOnlyDsl

// ANNOTATION_CLASS is an effective target: DSL scope control hides an outer receiver of a
// marked annotation type.

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
public annotation class MetaDsl

@DslMarker
@Target(AnnotationTarget.ANNOTATION_CLASS)
public annotation class AnnotationClassDsl

@AnnotationClassDsl
private annotation class OuterAnnotationReceiver(val outerOnly: String)

@AnnotationClassDsl
private annotation class InnerAnnotationReceiver(val innerOnly: String)

private fun annotationReceiverDslScope(
    outer: OuterAnnotationReceiver,
    inner: InnerAnnotationReceiver,
): String = with(outer) {
    with(inner) {
        <!DSL_SCOPE_VIOLATION!>outerOnly<!>
    }
}

// The name of the exemption is its reason, so a bare usage needs no description.

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class LegacyNoopTargetDsl

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(description = "Published without targets in 1.0.")
@DslMarker
public annotation class LegacyDefaultTargetsDsl

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class NotADslMarker

// A non-public marker applies to public DSL classes, so its targets are checked.

@DslMarker
@Target(AnnotationTarget.CLASS, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FUNCTION<!>)
internal annotation class InternalDsl

@DslMarker
private annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>PrivateDsl<!>
