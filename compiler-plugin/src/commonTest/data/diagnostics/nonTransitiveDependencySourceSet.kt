// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY

// The platform publishes both dependencies. Another target that compiles the common module doesn't
// publish `models`, so the common module gets `shared` only. The verdict on the exemption of an `expect`
// declaration uses the dependencies of its module too. As in a Gradle build, only the platform module gets
// the dependency paths. The redundancy verdicts show the dependency check, because the fixer test replays
// this file without dependency paths.

// MODULE: lib
// FILE: models.kt
package com.example.models

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

/** A model type of the `models` dependency. */
@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public open class Model

// FILE: shared.kt
package com.example.shared

/** A type of the `shared` dependency. */
public class Shared

// MODULE: common(lib)
// FILE: common.kt
package consumer

import com.example.models.Model
import com.example.shared.Shared
import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonTransitiveDependency

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public fun commonModel(): Model = Model()

<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
public fun commonShared(): Shared = Shared()

public expect class Source() {
    @IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
    public fun model(): Model

    <!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
    public fun shared(): Shared
}

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public expect class ModelHolder() {
    public fun model(): Model
}

<!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
public expect class SharedHolder() {
    public fun shared(): Shared
}

// MODULE: jvm(lib)()(common)
// COMPILE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// TRANSITIVE_DEPENDENCY_PATHS: ../messages/nonTransitiveDependency.deps/models ../messages/nonTransitiveDependency.deps/shared
// SOURCE_SET_TRANSITIVE_DEPENDENCY_PATHS: common=../messages/nonTransitiveDependency.deps/shared
// FILE: jvm.kt
package consumer

import com.example.models.Model
import com.example.shared.Shared
import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNonTransitiveDependency

<!REDUNDANT_EXEMPTION!>@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")<!>
public fun jvmModel(): Model = Model()

public actual class Source public actual constructor() {
    @IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
    public actual fun model(): Model = Model()

    @IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
    public actual fun shared(): Shared = Shared()
}

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public actual class ModelHolder public actual constructor() {
    public actual fun model(): Model = Model()
}

@IntentionallyNonTransitiveDependency(reason = ExemptionReason.INTEROP, description = "The host ships the models.")
public actual class SharedHolder public actual constructor() {
    public actual fun shared(): Shared = Shared()
}
