// RUN_PIPELINE_TILL: FRONTEND
// REQUIRE_EXPLICIT_API_OFF
// DIAGNOSTICS: -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

// Without explicit API mode a declaration without a modifier is public, so the checks report it.

open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>UnprotectedOpenClass<!>

abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>UnprotectedAbstractClass<!>

interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>UnprotectedInterface<!>

enum class <!EXHAUSTIVE_PUBLIC_API, UNDOCUMENTED_PUBLIC_API!>UnmarkedEnum<!> {
    A,
    B,
}

sealed class <!EXHAUSTIVE_PUBLIC_API, UNDOCUMENTED_PUBLIC_API!>UnmarkedSealedClass<!> {
    class <!UNDOCUMENTED_PUBLIC_API!>Only<!> : UnmarkedSealedClass()
}

class <!UNDOCUMENTED_PUBLIC_API!>UndocumentedClass<!>

object <!UNDOCUMENTED_PUBLIC_API!>UndocumentedObject<!>

typealias <!FUNCTION_TYPE_ALIAS_PUBLIC_API, UNDOCUMENTED_PUBLIC_API!>UnacknowledgedCallback<!> = (Int) -> Unit
