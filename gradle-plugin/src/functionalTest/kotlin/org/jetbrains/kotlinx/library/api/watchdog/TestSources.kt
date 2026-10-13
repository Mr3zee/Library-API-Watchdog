@file:Suppress("RedundantVisibilityModifier")

package org.jetbrains.kotlinx.library.api.watchdog

import org.intellij.lang.annotations.Language

// Source snippets and modules several test classes share.

/** A main-source declaration no checker has anything to say about. */
@Language("kotlin")
val cleanMainFile = """
    /** A documented, final, closed declaration. */
    public class Settled {
        /** Returns the only supported mode. */
        public fun mode(): Int = 0
    }
""".trimIndent()

/** The types a `:model` module supplies to the tests of the dependency checks. */
@Language("kotlin")
val dependencyTypeFile = """
    /** A model supplied by another module. */
    public class ExternalModel

    /** A contract supplied by another module. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public interface ExternalContract
""".trimIndent()

/** Public signatures that mention the types of [dependencyTypeFile] in every supported position. */
@Language("kotlin")
val exposesDependencyTypeFile = """
    import test.model.ExternalContract
    import test.model.ExternalModel

    /** A public alias whose nested type argument comes from a dependency. */
    public typealias ExternalModels = List<ExternalModel>

    /** A documented and otherwise settled API owner. */
    @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
    public open class Consumer : ExternalContract {
        /** Returns a model whose class must be available to consumers. */
        public fun model(): ExternalModel = ExternalModel()

        /** Returns a dependency type from protected API. */
        protected fun protectedModel(): ExternalModel = ExternalModel()

        /** Uses a dependency type as both a receiver and a nested parameter type. */
        public fun ExternalModel.acceptModels(models: ExternalModels): ExternalModel = this
    }
""".trimIndent()

/** A `:model` subproject holding [dependencyTypeFile], of the root's kind or a plain Kotlin/JVM one. */
fun ProjectSpec.modelSubproject(jvm: Boolean = false) {
    val block: ModuleSpec.() -> Unit = { source(dependencyTypeFile, "ExternalModel", "test.model") }
    if (jvm) jvmSubproject("model", block) else subproject("model", block)
}

/** [exposesDependencyTypeFile] as the `Consumer` file of the `test.consumer` package. */
fun ModuleSpec.consumerSource(sourceSet: String = this.sourceSet) {
    source(exposesDependencyTypeFile, "Consumer", "test.consumer", sourceSet)
}
