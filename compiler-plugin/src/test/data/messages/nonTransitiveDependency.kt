// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN
// COMPILE_DEPENDENCY_PATHS: nonTransitiveDependency.deps/models nonTransitiveDependency.deps/tools nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: nonTransitiveDependency.deps/shared
// DEPENDENCY_DECLARATIONS: nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// The `lib` module stands in for three dependencies. The roots under `nonTransitiveDependency.deps`
// hold an empty `.class` file per type, which is all the checker looks up. The `DIAGNOSTICS`
// directive doesn't cover a dependency module, so its declarations are documented and exempt.
//
// Message parameters: every signature position kind, the declaration name, the exposed type, and
// the exemption snippet, with the declared `models` dependency or the placeholder of the
// undeclared `tools` dependency.

// MODULE: lib
// FILE: models.kt
package com.example.models

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

/** A model type of the declared `models` dependency. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Model

// FILE: tools.kt
package com.example.tools

/** A type of the undeclared `tools` dependency. */
public class Tool

// FILE: shared.kt
package com.example.shared

/** A type of the transitive `shared` dependency. */
public class Shared

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import com.example.models.Model
import com.example.shared.Shared
import com.example.tools.Tool

public val leakedProperty: <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!> = Model()

public val <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>.leakedReceiver: String
    get() = ""

public fun leakedReturn(): <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!> = Model()

public fun <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>.leakedFunctionReceiver(): String = ""

public fun leakedParameter(value: <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>) {}

public class LeakedSupertype : <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>()

public class LeakedClassBound<T : <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>>

public typealias LeakedTypeAlias = <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Model<!>

public fun leakedUndeclared(tool: <!PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY!>Tool<!>) {}

public fun transitive(shared: Shared) {}
