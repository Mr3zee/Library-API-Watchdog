plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
    implementation(
        "org.jetbrains.kotlin.compiler.plugin.devkit:plugins:${providers.systemProperty("devkitVersion").get()}",
    )
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        register("benchmarkConventions") {
            id = "library-api-watchdog.benchmark-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.BenchmarkConventionPlugin"
        }
        register("compilerPluginConventions") {
            id = "library-api-watchdog.compiler-plugin-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.CompilerPluginConventionPlugin"
        }
        register("devKitVersionsConventions") {
            id = "library-api-watchdog.devkit-versions-conventions"
            implementationClass =
                "org.jetbrains.kotlinx.library.api.watchdog.conventions.DevKitVersionsConventionPlugin"
        }
        register("dokkaConventions") {
            id = "library-api-watchdog.dokka-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.DokkaConventionPlugin"
        }
        register("exemptsFixerConventions") {
            id = "library-api-watchdog.exempts-fixer-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.ExemptsFixerConventionPlugin"
        }
        register("gradlePluginConventions") {
            id = "library-api-watchdog.gradle-plugin-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.GradlePluginConventionPlugin"
        }
        register("rootConventions") {
            id = "library-api-watchdog.root-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.RootConventionPlugin"
        }
        register("spacePublishingConventions") {
            id = "library-api-watchdog.space-publishing-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.SpacePublishingConventionPlugin"
        }
    }
}
