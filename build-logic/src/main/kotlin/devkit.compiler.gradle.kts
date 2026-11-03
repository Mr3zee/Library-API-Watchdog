import org.jetbrains.kotlinx.library.api.watchdog.conventions.catalogVersion
import org.jetbrains.kotlinx.library.api.watchdog.conventions.config

plugins {
    kotlin("compiler.plugin.devkit.compiler-plugin")
}

pluginDevKit {
    config()
    ideaVersions(catalogVersion("kotlinIdeMin"), includeRc = true, includeEap = true)
    useLatestDev()
}
