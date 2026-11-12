package org.jetbrains.kotlinx.library.api.watchdog.conventions

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension

/**
 * Gives every publication what Maven Central requires: a sources artifact, a Javadoc artifact, the POM metadata, and
 * a signature. The build doesn't upload to Maven Central. The upload takes the publications from the `Build`
 * repository of [SpacePublishingConventionPlugin].
 */
class MavenCentralPublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.vanniktech.maven.publish.base")

            // The `signing` plugin is applied only with a key. With that plugin applied,
            // `com.gradle.plugin-publish` signs in every build, and a build that has no key fails.
            providers.gradleProperty(SIGNING_KEY_PROPERTY).orNull?.let { key ->
                extensions.configure(MavenPublishBaseExtension::class.java) {
                    signAllPublications()
                }
                extensions.configure(SigningExtension::class.java) {
                    useInMemoryPgpKeys(
                        providers.gradleProperty(SIGNING_KEY_ID_PROPERTY).orNull,
                        key,
                        providers.gradleProperty(SIGNING_PASSPHRASE_PROPERTY).orNull,
                    )
                }
            }

            pluginManager.withPlugin("java") {
                extensions.configure(JavaPluginExtension::class.java) {
                    withSourcesJar()
                    withJavadocJar()
                }
            }
            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                // Central needs a Javadoc artifact: https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
                val javadocJar = tasks.register("mavenCentralJavadocJar", Jar::class.java) {
                    archiveClassifier.set("javadoc")
                }
                extensions.configure(PublishingExtension::class.java) {
                    publications.withType(MavenPublication::class.java).configureEach {
                        artifact(javadocJar)
                    }
                }
            }

            extensions.configure(PublishingExtension::class.java) {
                publications.withType(MavenPublication::class.java).configureEach {
                    pom {
                        name.set(target.name)
                        description.set("Public API checks, exemptions, and reports for Kotlin libraries.")
                        url.set("https://mr3zee.github.io/Library-API-Watchdog/")
                        licenses {
                            license {
                                name.set("Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                                distribution.set("repo")
                            }
                        }
                        developers {
                            developer {
                                id.set("JetBrains")
                                name.set("JetBrains Team")
                                organization.set("JetBrains s.r.o.")
                                organizationUrl.set("https://www.jetbrains.com/")
                            }
                        }
                        scm {
                            url.set("https://github.com/Mr3zee/Library-API-Watchdog")
                            connection.set("scm:git:https://github.com/Mr3zee/Library-API-Watchdog.git")
                            developerConnection.set("scm:git:ssh://git@github.com/Mr3zee/Library-API-Watchdog.git")
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val SIGNING_KEY_ID_PROPERTY = "libs.sign.key.id"
        const val SIGNING_KEY_PROPERTY = "libs.sign.key.private"
        const val SIGNING_PASSPHRASE_PROPERTY = "libs.sign.passphrase"
    }
}
