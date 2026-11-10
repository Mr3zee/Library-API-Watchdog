// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: lib.internal.** lib.api.Hidden
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// FILE: internal.kt
package lib.internal

public open class InternalType

// FILE: exposure.kt
package lib.api

import lib.internal.InternalType

public open class Hidden {
    public class Nested
}

public fun leakedReturn(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalType<!> = InternalType()

public fun leakedParameter(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>Hidden.Nested<!>) {}
