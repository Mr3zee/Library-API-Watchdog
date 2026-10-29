// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/shared
// DEPENDENCY_DECLARATIONS: ../messages/nonTransitiveDependency.deps/models implementation "com.example:models:1.0"

// The dependency exemption applies to a declaration, a parameter, a type usage, and a class with
// its members. The roots under `messages/nonTransitiveDependency.deps` hold an empty `.class`
// file for each type, which is all the checker reads.

// MODULE: lib
// FILE: models.kt
package com.example.models

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

/** A model type of the declared `models` dependency. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Model

// FILE: shared.kt
package com.example.shared

/** A type of the transitive `shared` dependency. */
public class Shared

// MODULE: main(lib)
// FILE: exposure.kt
package consumer

import com.example.models.Model
import com.example.shared.Shared
import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonTransitiveDependency

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public fun neededReturn(): Model = Model()

// The `shared` dependency is transitive.
<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
public fun transitiveReturn(): Shared = Shared()

public fun neededParameter(@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.") model: Model) {}

public fun plainParameter(<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!> count: Int) {}

public fun neededNested(): List<@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.") Model> = emptyList()

public fun plainNested(): List<<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!> Int> = emptyList()

// The class-level exemption covers the supertype and the members.

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public class NeededSubtype : Model()

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public class NeededHolder {
    public fun model(): Model = Model()
}

<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
public class PlainHolder {
    public fun count(): Int = 1
}

// The class-level exemption covers the member, so the member-level one is redundant.

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public class CoveredHolder {
    <!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
    public fun model(): Model = Model()
}
