// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

// An explicit but empty @Target leaves the marker with no usable target.

@DslMarker
@Target()
public annotation class <!DSL_MARKER_WITHOUT_EXPLICIT_TARGETS!>EmptyTargetsDsl<!>
