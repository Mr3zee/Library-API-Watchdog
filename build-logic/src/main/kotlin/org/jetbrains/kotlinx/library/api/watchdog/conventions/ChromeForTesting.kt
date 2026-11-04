package org.jetbrains.kotlinx.library.api.watchdog.conventions

import java.io.File
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.DIRECTORY_TYPE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

/** The group of the Chrome for Testing archives. The settings of the build map it to the Chrome for Testing storage. */
const val CHROME_FOR_TESTING_GROUP = "chrome-for-testing"

/**
 * The unpacked headless shell and chromedriver of Chrome for Testing [version]. Gradle keeps the archives and the
 * unpacked directories in the Gradle user home. The collection is empty on a platform without Chrome for Testing
 * builds: Linux on ARM.
 */
fun Project.chromeForTesting(version: String): FileCollection {
    val archives = configurations.dependencyScope("chromeForTesting")
    val platform = chromeForTestingPlatform()
    if (platform != null) {
        for (artifact in listOf("chrome-headless-shell", "chromedriver")) {
            dependencies.add(archives.name, "$CHROME_FOR_TESTING_GROUP:$artifact:$version:$platform@zip")
        }
    }
    dependencies.registerTransform(UnzipTransform::class.java) {
        from.attribute(ARTIFACT_TYPE_ATTRIBUTE, ZIP_TYPE)
        to.attribute(ARTIFACT_TYPE_ATTRIBUTE, DIRECTORY_TYPE)
    }
    val resolvable = configurations.resolvable("chromeForTestingArchives") { extendsFrom(archives.get()) }
    return files(resolvable.map { configuration ->
        configuration.incoming.artifactView { attributes { attribute(ARTIFACT_TYPE_ATTRIBUTE, DIRECTORY_TYPE) } }.files
    })
}

private fun Project.chromeForTestingPlatform(): String? {
    val os = providers.systemProperty("os.name").get().lowercase()
    val arch = providers.systemProperty("os.arch").get()
    return when {
        os.startsWith("mac") -> if (arch == "aarch64") "mac-arm64" else "mac-x64"
        // Windows on ARM runs the x64 build.
        os.startsWith("windows") -> if (arch == "x86") "win32" else "win64"
        os.startsWith("linux") && (arch == "amd64" || arch == "x86_64") -> "linux64"
        else -> null
    }
}

/** Unpacks a ZIP archive with the file permissions of its entries. */
abstract class UnzipTransform : TransformAction<TransformParameters.None> {
    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val archive: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val file = archive.get().asFile
        val directory = outputs.dir(file.nameWithoutExtension)
        fileSystemOperations.copy {
            from(archiveOperations.zipTree(file))
            into(directory)
        }
    }
}

/**
 * Sets the `chromeForTesting.browser` and `chromeForTesting.driver` system properties of a test JVM to the
 * executables in [directories], the result of [chromeForTesting]. On a platform without Chrome for Testing builds,
 * sets `chromeForTesting.unsupportedPlatform` instead, and the browser tests skip themselves.
 */
class ChromeForTestingArguments(
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val directories: FileCollection,
) : CommandLineArgumentProvider {
    override fun asArguments(): List<String> {
        if (directories.isEmpty) {
            return listOf("-DchromeForTesting.unsupportedPlatform=${System.getProperty("os.name")} on ${System.getProperty("os.arch")}")
        }
        return listOf(
            "-DchromeForTesting.browser=${executable("chrome-headless-shell")}",
            "-DchromeForTesting.driver=${executable("chromedriver")}",
        )
    }

    private fun executable(name: String): File =
        directories.asFileTree.matching { include("*/$name", "*/$name.exe") }.files.singleOrNull()
            ?: throw GradleException("The Chrome for Testing archives have no $name executable in a top-level directory.")
}
