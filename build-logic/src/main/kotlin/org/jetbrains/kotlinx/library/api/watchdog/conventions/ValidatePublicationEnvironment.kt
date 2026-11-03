package org.jetbrains.kotlinx.library.api.watchdog.conventions

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class ValidatePublicationEnvironment : DefaultTask() {
    @get:Input
    abstract val requiredVariables: ListProperty<String>

    @get:Inject
    abstract val providerFactory: ProviderFactory

    @TaskAction
    fun validate() {
        val missing = requiredVariables.get().filter {
            providerFactory.environmentVariable(it).orNull.isNullOrBlank()
        }
        check(missing.isEmpty()) {
            "Missing publication environment variables: ${missing.joinToString()}. Set them before publication."
        }
    }
}
