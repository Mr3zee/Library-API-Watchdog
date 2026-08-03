plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${libs.versions.dokka.get()}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
}

gradlePlugin {
    plugins {
        register("compilerPluginConventions") {
            id = "libs-watchdog.compiler-plugin-conventions"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.conventions.CompilerPluginConventionPlugin"
        }
        register("dokkaConventions") {
            id = "libs-watchdog.dokka-conventions"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.conventions.DokkaConventionPlugin"
        }
        register("exemptsFixerConventions") {
            id = "libs-watchdog.exempts-fixer-conventions"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.conventions.ExemptsFixerConventionPlugin"
        }
        register("gradlePluginConventions") {
            id = "libs-watchdog.gradle-plugin-conventions"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.conventions.GradlePluginConventionPlugin"
        }
        register("rootConventions") {
            id = "libs-watchdog.root-conventions"
            implementationClass = "org.jetbrains.kotlinx.libs.api.watchdog.conventions.RootConventionPlugin"
        }
    }
}
