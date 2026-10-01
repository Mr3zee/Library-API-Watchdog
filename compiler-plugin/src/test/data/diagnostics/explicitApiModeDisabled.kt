// RUN_PIPELINE_TILL: FRONTEND
// DISABLE_NEXT_PHASE_SUGGESTION: the later phase is named BACKEND before Kotlin 2.5 and CODEGEN after it

package foo.bar

// Without explicit API mode the plugin doesn't run: none of the declarations below are reported
// even though each would trigger a checker otherwise.

open class UnprotectedOpenClass

abstract class UnprotectedAbstractClass

interface UnprotectedInterface

enum class UnmarkedEnum {
    A,
    B,
}

sealed class UnmarkedSealedClass {
    class Only : UnmarkedSealedClass()
}

class UndocumentedClass

object UndocumentedObject

typealias UnacknowledgedCallback = (Int) -> Unit
