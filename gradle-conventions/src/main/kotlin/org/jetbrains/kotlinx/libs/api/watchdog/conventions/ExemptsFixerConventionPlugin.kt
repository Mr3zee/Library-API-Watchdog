package org.jetbrains.kotlinx.libs.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources

class ExemptsFixerConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.named("processTestResources", ProcessResources::class.java).configure {
                from(
                    target.isolated.rootProject.projectDirectory.dir("compiler-plugin/src/test/data"),
                )
            }
            tasks.named("test", Test::class.java).configure {
                useJUnitPlatform()
                systemProperty("junit.jupiter.execution.parallel.enabled", "false")
            }
            extensions.configure(PublishingExtension::class.java) {
                publications.create("maven", MavenPublication::class.java) {
                    from(components.getByName("java"))
                }
            }
        }
    }
}
