// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility

// A no-op target in an explicit @Target: should warn on that target.
// This is the shape that broke Ktor's @KtorDsl (KTOR-8901).

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS, AnnotationTarget.TYPE, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FUNCTION<!>)
public annotation class KtorDsl

// Every no-op target is reported individually.

@DslMarker
@Target(
    AnnotationTarget.CLASS,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.PROPERTY<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.VALUE_PARAMETER<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.CONSTRUCTOR<!>,
    <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.TYPE_PARAMETER<!>,
)
public annotation class ManyNoopsDsl

// The named array form of @Target is seen through.

@DslMarker
@Target(allowedTargets = [AnnotationTarget.TYPE, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.LOCAL_VARIABLE<!>])
public annotation class ArrayFormDsl

// No explicit @Target: the default target set allows many no-op targets and forbids
// the effective TYPE and TYPEALIAS ones.

@DslMarker
public annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>DefaultTargetsDsl<!>

// Only effective targets: no warning.

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE, AnnotationTarget.TYPEALIAS)
public annotation class TidyDsl

@DslMarker
@Target(AnnotationTarget.CLASS)
public annotation class ClassOnlyDsl

// ANNOTATION_CLASS is a classifier declaration, so the marker takes effect there.

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.ANNOTATION_CLASS)
public annotation class MetaDsl

// ANNOTATION_CLASS is not merely accepted by the watchdog: Kotlin's own DSL scope control sees
// a marker placed on an annotation class and hides an outer receiver of that annotation type.

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

// A legacy marker keeps its wrong target set for backwards compatibility: no warning. The
// exemption's reason is baked into its name, so a bare usage needs no description either.

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class LegacyNoopTargetDsl

@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility(description = "Published without targets in 1.0.")
@DslMarker
public annotation class LegacyDefaultTargetsDsl

// Not a DSL marker: targets are not checked.

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
public annotation class NotADslMarker

// Marker visibility is irrelevant: even non-public markers are applied across the library's
// possibly public DSL classes, so their targets are checked too.

@DslMarker
@Target(AnnotationTarget.CLASS, <!DSL_MARKER_NOOP_TARGET!>AnnotationTarget.FUNCTION<!>)
internal annotation class InternalDsl

@DslMarker
private annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>PrivateDsl<!>
