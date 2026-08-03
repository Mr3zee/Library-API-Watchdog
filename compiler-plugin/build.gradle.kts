plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
    // Native loads compiler plugins in an isolated process, so the dev-kit registrar runtime
    // must travel inside the compiler plugin artifact.
    alias(libs.plugins.shadow)
    id("library-api-watchdog.benchmark-conventions")
    id("library-api-watchdog.compiler-plugin-conventions")
    id("library-api-watchdog.space-publishing-conventions")
}

publishing {
    publications {
        create<MavenPublication>("compilerPlugin") {
            from(components["java"])
        }
    }
}

pluginDevKit {
    pluginPackage.set("org.jetbrains.kotlin.library.api.watchdog")
    generateTestsClass.set("org.jetbrains.kotlinx.libs.api.watchdog.GenerateTestsKt")
    testDataLibraries { common(project(":kotlin-library-api-watchdog-plugin-annotations")) }
}

dependencies {
    // The dev-kit runtime's published testFixtures metadata carries no dependency on the runtime's
    // main jar (it is wired as a local file dependency there), so declare it explicitly; the
    // testFixtures runners subclass DevKitCompilerPluginRegistrar from it.
    "testFixturesApi"(
        "org.jetbrains.kotlin.compiler.plugin.devkit:compiler-plugin-runtime:" +
            providers.gradleProperty("compilerPluginDevKitVersion").get(),
    )
}

// The dev kit pins every org.jetbrains.kotlin dependency to the compiler version under test.
// Keep this project's own test-fixtures dependency on the watchdog version now that the
// watchdog itself is published in that namespace.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == project.group.toString() && requested.name == project.name) {
            useVersion(project.version.toString())
        }
    }
}
