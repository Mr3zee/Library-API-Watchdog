package org.jetbrains.kotlinx.library.api.watchdog.conventions

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.api.publish.PublishingExtension

/**
 * Adds two publishing repositories: `Space`, the EAP repository, and `Build`, the `build/repo` directory of the
 * repository root. The Release workflow publishes to `Build` to build every publication before it creates the tag.
 * The Maven Central upload takes the publications from `Build`.
 */
class SpacePublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("maven-publish") {
                extensions.configure(PublishingExtension::class.java) {
                    repositories.maven {
                        name = "Space"
                        url = uri(SPACE_REPOSITORY_URL)
                        credentials(Action<PasswordCredentials> {
                            username = providers
                                .gradleProperty(SPACE_USERNAME_PROPERTY)
                                .orElse(providers.environmentVariable(SPACE_USERNAME_ENVIRONMENT_VARIABLE))
                                .orNull
                            password = providers
                                .gradleProperty(SPACE_PASSWORD_PROPERTY)
                                .orElse(providers.environmentVariable(SPACE_PASSWORD_ENVIRONMENT_VARIABLE))
                                .orNull
                        })
                    }
                    repositories.maven {
                        name = "Build"
                        url = repositoryRoot.dir("build/repo").asFile.toURI()
                    }
                }
            }
        }
    }

    private companion object {
        const val SPACE_REPOSITORY_URL = "https://packages.jetbrains.team/maven/p/kt-lib/eap"
        const val SPACE_USERNAME_PROPERTY = "kotlin.library.space.username"
        const val SPACE_PASSWORD_PROPERTY = "kotlin.library.space.password"
        const val SPACE_USERNAME_ENVIRONMENT_VARIABLE = "KOTLIN_LIBRARY_SPACE_USERNAME"
        const val SPACE_PASSWORD_ENVIRONMENT_VARIABLE = "KOTLIN_LIBRARY_SPACE_PASSWORD"
    }
}
