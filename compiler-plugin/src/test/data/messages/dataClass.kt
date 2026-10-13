// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

public data class <!DATA_CLASS_PUBLIC_API!>Coordinates<!>(val x: Int, val y: Int)
