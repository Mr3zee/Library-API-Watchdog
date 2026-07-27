// RUN_PIPELINE_TILL: BACKEND

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
