package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

abstract class JvmTargetConventionExtension {
    abstract val release: Property<JvmTarget>
}

class JvmTargetConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val extension = extensions.create("jvmTargetConventions", JvmTargetConventionExtension::class.java)
            extension.release.convention(JvmTarget.JVM_1_8)

            tasks.withType(KotlinJvmCompile::class.java).configureEach {
                compilerOptions {
                    jvmTarget.set(extension.release)
                    freeCompilerArgs.add(extension.release.map { "-Xjdk-release=${it.target}" })
                }
            }
            tasks.withType(JavaCompile::class.java).configureEach {
                options.release.set(extension.release.map { it.target.removePrefix("1.").toInt() })
            }
        }
    }
}
