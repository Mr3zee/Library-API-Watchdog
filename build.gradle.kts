import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import java.time.Year

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.dokka)
    kotlin("compiler.plugin.devkit.functional-test-publishing")
}

// Aggregated API reference. The output is published to GitHub Pages under /api
// next to the documentation website (see .github/workflows/docs.yml).
dokka {
    moduleVersion = version.toString()

    pluginsConfiguration.html {
        customAssets.from("docs/static/img/logo-icon.svg")
        footerMessage = "© ${Year.now()} JetBrains s.r.o and contributors."
        homepageLink = "https://mr3zee.github.io/libs-api-watchdog/"
    }
}

dependencies {
    // Only user-facing modules are documented; :compiler-plugin is an implementation detail.
    dokka(project(":plugin-annotations"))
    dokka(project(":gradle-plugin"))
}

subprojects {
    plugins.withId("org.jetbrains.dokka") {
        extensions.configure<DokkaExtension> {
            moduleName = "libs-api-watchdog-${project.name}"

            dokkaSourceSets.configureEach {
                documentedVisibilities = setOf(
                    VisibilityModifier.Public,
                    VisibilityModifier.Protected,
                )

                sourceLink {
                    localDirectory = rootDir
                    remoteUrl("https://github.com/Mr3zee/libs-api-watchdog/blob/main")
                    remoteLineSuffix = "#L"
                }
            }

            dokkaPublications.configureEach {
                suppressObviousFunctions = true
                failOnWarning = true
            }
        }
    }
}
