plugins {
    `kotlin-dsl`
}

dependencies {
    implementation("com.gradle:develocity-gradle-plugin:4.6.0")
}

gradlePlugin {
    plugins {
        register("develocityConventions") {
            id = "library-api-watchdog.develocity-conventions"
            implementationClass = "org.jetbrains.kotlinx.library.api.watchdog.conventions.DevelocitySettingsConventionPlugin"
        }
    }
}
