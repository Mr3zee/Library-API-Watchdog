package org.jetbrains.kotlinx.library.api.watchdog.conventions

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Jar

class MavenCentralPublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.vanniktech.maven.publish.base")

            val validateEnvironment = tasks.register(
                "validateMavenCentralEnvironment",
                ValidatePublicationEnvironment::class.java,
            ) {
                group = "publishing"
                description = "Checks the credentials for Maven Central publication."
                requiredVariables.set(
                    listOf(
                        "ORG_GRADLE_PROJECT_mavenCentralUsername",
                        "ORG_GRADLE_PROJECT_mavenCentralPassword",
                        "ORG_GRADLE_PROJECT_signingInMemoryKey",
                        "ORG_GRADLE_PROJECT_signingInMemoryKeyPassword",
                    ),
                )
            }

            extensions.configure(MavenPublishBaseExtension::class.java) {
                publishToMavenCentral(automaticRelease = false)
                if (providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").isPresent) {
                    signAllPublications()
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
            tasks.withType(PublishToMavenRepository::class.java).configureEach {
                if (name.endsWith("ToMavenCentralRepository")) dependsOn(validateEnvironment)
            }
        }
    }
}
