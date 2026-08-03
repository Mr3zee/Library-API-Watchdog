plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
    // Native loads compiler plugins in an isolated process, so the dev-kit registrar runtime
    // must travel inside the compiler plugin artifact.
    alias(libs.plugins.shadow)
    id("libs-watchdog.compiler-plugin-conventions")
}

publishing {
    publications {
        create<MavenPublication>("compilerPlugin") {
            from(components["java"])
        }
    }
}

pluginDevKit {
    testDataLibraries { common(project(":plugin-annotations")) }
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
