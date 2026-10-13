package org.jetbrains.kotlinx.library.api.watchdog

import com.autonomousapps.kit.GradleBuilder
import com.autonomousapps.kit.GradleProject
import com.autonomousapps.kit.Source
import com.autonomousapps.kit.gradle.BuildScript
import com.autonomousapps.kit.gradle.Dependency
import com.autonomousapps.kit.gradle.Imports
import com.autonomousapps.kit.gradle.Plugin
import com.autonomousapps.kit.gradle.Repositories
import com.autonomousapps.kit.gradle.Repository
import java.io.File
import java.util.Locale
import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.compiler.plugin.devkit.test.AbstractDevKitGradleProject
import org.jetbrains.kotlin.compiler.plugin.devkit.test.KotlinPlugins
import org.jetbrains.kotlin.compiler.plugin.devkit.test.TEST_COMPILER_VERSION
import org.jetbrains.kotlin.compiler.plugin.devkit.test.pluginUnderTestVersion
import org.junit.Assume.assumeTrue

/** A Kotlin/JVM library with the watchdog applied, written to disk. */
fun jvmProject(block: ProjectSpec.() -> Unit = {}): WatchdogBuild =
    ProjectSpec(sourceSet = "main").apply(block).write(ProjectKind.Jvm)

/** A Kotlin Multiplatform library with the watchdog applied. [targets] is the body of the `kotlin { }` block. */
fun kmpProject(targets: String = "jvm()", block: ProjectSpec.() -> Unit = {}): WatchdogBuild =
    ProjectSpec(sourceSet = "commonMain").apply(block).write(ProjectKind.Kmp(targets))

/** An Android library with the watchdog applied. Skips the test without an Android SDK. */
fun androidLibrary(agp: AgpVersion, block: AndroidLibrarySpec.() -> Unit = {}): WatchdogBuild {
    assumeAndroidBuildEnvironment()
    return AndroidLibrarySpec().apply(block).write(ProjectKind.AndroidLibrary(agp))
}

/**
 * A multiplatform library with a `jvm()` target and the Android [target]. Skips the test without
 * an Android SDK. [ProjectSpec.subproject] adds a plain Kotlin/JVM module.
 */
fun kmpAndroidProject(target: KmpAndroidTarget, block: ProjectSpec.() -> Unit = {}): WatchdogBuild {
    assumeAndroidBuildEnvironment()
    return ProjectSpec(sourceSet = "commonMain", subprojectSourceSet = "main")
        .apply(block)
        .write(ProjectKind.KmpAndroid(target))
}

enum class AgpVersion(internal val gradleVersion: GradleVersion) {
    /** Gradle 9.6.0 removed an internal API that AGP 8 uses, so AGP 8 runs on the last Gradle before it. */
    AGP8(GradleVersion.version("9.5.1")),

    /** AGP 9 embeds its own KGP. An `apply false` plugin line pins the Kotlin version under test. */
    AGP9(GradleVersion.current()),
    ;

    internal val version: String
        get() = when (this) {
            AGP8 -> requiredProperty(testAgp8Version)
            AGP9 -> requiredProperty(testAgp9Version)
        }
}

/** The Android target of a multiplatform fixture. */
enum class KmpAndroidTarget(
    val agp: AgpVersion,
    val mainCompileTask: String,
    val hostTestCompileTask: String,
    val hostTestSourceSet: String,
) {
    /** `com.android.kotlin.multiplatform.library` from AGP 8. */
    LIBRARY_AGP8(AgpVersion.AGP8, "compileAndroidMain", "compileAndroidHostTest", "androidHostTest"),

    /** The same plugin from AGP 9. */
    LIBRARY_AGP9(AgpVersion.AGP9, "compileAndroidMain", "compileAndroidHostTest", "androidHostTest"),

    /** `androidTarget()` on `com.android.library`. AGP 9 doesn't accept it. */
    LEGACY(AgpVersion.AGP8, "compileDebugKotlinAndroid", "compileDebugUnitTestKotlinAndroid", "androidUnitTest"),
}

/** A generated Gradle build on disk. */
class WatchdogBuild internal constructor(
    val gradleProject: GradleProject,
    private val gradleVersion: GradleVersion,
) {
    val rootDir: File get() = gradleProject.rootDir

    fun build(vararg arguments: String): BuildResult = GradleBuilder.build(gradleVersion, rootDir, *arguments)

    fun buildAndFail(vararg arguments: String): BuildResult =
        GradleBuilder.buildAndFail(gradleVersion, rootDir, *arguments)

    fun file(relativePath: String): File = rootDir.resolve(relativePath)
}

/** The declarations of one module of a generated build. */
open class ModuleSpec internal constructor(
    /** The default source set of [source]. */
    val sourceSet: String,
) {
    internal val sources = mutableListOf<Source>()
    internal val dependencies = mutableListOf<Pair<String?, Dependency>>()
    internal val scriptParts = mutableListOf<String>()
    internal val imports = linkedSetOf<String>()
    internal var plugins: List<Plugin>? = null

    /**
     * A Kotlin file of [code] with a package statement and imports. [defaultImports] imports the
     * watchdog annotations. The file name is [name] or the first declared type or function.
     */
    fun source(
        @Language("kotlin") code: String,
        name: String? = null,
        packageName: String = "test",
        sourceSet: String = this.sourceSet,
        imports: List<String> = emptyList(),
        defaultImports: Boolean = true,
        fileAnnotation: String? = null,
    ): Source {
        val text = buildString {
            if (fileAnnotation != null) {
                appendLine(fileAnnotation)
                appendLine()
            }
            appendLine("package $packageName")
            if (defaultImports) watchdogAnnotationImports.forEach { appendLine("import $it") }
            imports.forEach { appendLine("import $it") }
            appendLine()
            appendLine(code.trimIndent())
        }
        return add(
            Source.kotlin(text)
                .withSourceSet(sourceSet)
                .withPath(packageName.replace('.', '/'), name ?: inferFileName(code))
                .build(),
        )
    }

    /** A Kotlin file written as is. */
    fun kotlinFile(name: String, sourceSet: String = this.sourceSet, @Language("kotlin") text: String): Source =
        add(Source.kotlin(text).withSourceSet(sourceSet).withPath(packagePath(text), name).build())

    /** A Java file written as is. */
    fun javaFile(name: String, @Language("java") text: String): Source =
        add(Source.java(text).withPath(packagePath(text), name).build())

    fun sources(vararg sources: Source) {
        this.sources += sources
    }

    fun dependencies(vararg dependencies: Dependency) {
        dependencies.forEach { this.dependencies += null to it }
    }

    /** Dependencies declared as `<sourceSet><Configuration>(...)`. */
    fun dependencies(sourceSet: String, vararg dependencies: Dependency) {
        dependencies.forEach { this.dependencies += sourceSet to it }
    }

    /** Script text before the dependencies block. */
    fun buildScript(@Language("kts") script: String) {
        scriptParts += script.trimIndent()
    }

    fun apiWatchdog(block: ApiWatchdogBlock.() -> Unit) {
        imports += "$WATCHDOG_PACKAGE.WatchdogSeverity"
        imports += "$WATCHDOG_PACKAGE.WatchdogChecks"
        scriptParts += ApiWatchdogBlock().apply(block).render()
    }

    fun imports(vararg imports: String) {
        this.imports += imports
    }

    /** Replaces the default plugins and removes the default Kotlin script. */
    fun plugins(vararg plugins: Plugin) {
        this.plugins = plugins.toList()
    }

    private fun add(source: Source): Source = source.also { sources += it }
}

/** The declarations of the root module and the whole build. */
open class ProjectSpec internal constructor(
    sourceSet: String,
    private val subprojectSourceSet: String = sourceSet,
) : ModuleSpec(sourceSet) {
    /** `kotlin { explicitApi() }` in every module. */
    var explicitApi: Boolean = true

    internal val subprojects = linkedMapOf<String, SubprojectSpec>()
    internal var versionCatalog: String? = null
    internal val files = linkedMapOf<String, String>()
    internal var buildSrc: ModuleSpec? = null

    /** A subproject of the kind of the root. Under a multiplatform Android root it is Kotlin/JVM. */
    fun subproject(path: String, block: ModuleSpec.() -> Unit = {}) {
        subprojects[path] = SubprojectSpec(subprojectSourceSet, jvm = false).apply(block)
    }

    fun jvmSubproject(path: String, block: ModuleSpec.() -> Unit = {}) {
        subprojects[path] = SubprojectSpec("main", jvm = true).apply(block)
    }

    fun versionCatalog(@Language("toml") toml: String) {
        versionCatalog = toml.trimIndent()
    }

    fun file(relativePath: String, content: String) {
        files[relativePath] = content
    }

    /** The `buildSrc` build. It declares its plugins itself. */
    fun buildSrc(block: ModuleSpec.() -> Unit) {
        buildSrc = ModuleSpec("main").apply(block)
    }

    internal fun write(kind: ProjectKind): WatchdogBuild =
        WatchdogBuild(WatchdogFixture(kind, this).gradleProject, kind.gradleVersion)
}

class AndroidLibrarySpec internal constructor() : ProjectSpec(sourceSet = "main") {
    internal val androidLines = mutableListOf<String>()
    internal val kotlinLines = mutableListOf<String>()

    /** Script text inside the `android { }` block. */
    fun android(@Language("kts") script: String) {
        androidLines += script.trimIndent()
    }

    /** Script text inside the `kotlin { }` block. */
    fun kotlin(@Language("kts") script: String) {
        kotlinLines += script.trimIndent()
    }
}

internal class SubprojectSpec(sourceSet: String, val jvm: Boolean) : ModuleSpec(sourceSet)

internal sealed class ProjectKind(val multiplatform: Boolean, val gradleVersion: GradleVersion) {
    object Jvm : ProjectKind(multiplatform = false, GradleVersion.current())
    class Kmp(val targets: String) : ProjectKind(multiplatform = true, GradleVersion.current())
    class AndroidLibrary(val agp: AgpVersion) : ProjectKind(multiplatform = false, agp.gradleVersion)
    class KmpAndroid(val target: KmpAndroidTarget) : ProjectKind(multiplatform = true, target.agp.gradleVersion)

    val android: Boolean get() = this is AndroidLibrary || this is KmpAndroid
}

internal class WatchdogFixture(
    private val kind: ProjectKind,
    private val spec: ProjectSpec,
) : AbstractDevKitGradleProject(multiplatform = kind.multiplatform) {
    override val pluginUnderTest: Plugin = Plugin(WATCHDOG_PLUGIN_ID, pluginUnderTestVersion)

    // The dev kit runtime artifacts come from mavenLocal.
    override fun repositories(defaults: List<Repository>): Repositories =
        Repositories((defaults + Repository.MAVEN_LOCAL).toMutableList())

    // AGP 8 refuses AndroidX dependencies without the opt-in.
    override val extraGradleProperties: List<String>
        get() = if (kind.android) super.extraGradleProperties + "android.useAndroidX=true" else super.extraGradleProperties

    override fun buildGradleProject(): GradleProject {
        val builder = newGradleProjectBuilder(GradleProject.DslKind.KOTLIN)
            .withRootProject {
                sources = spec.sources
                withBuildScript { renderRoot() }
                withDevKitSettings()
                spec.versionCatalog?.let { withVersionCatalog(it) }
                spec.files.forEach { (path, content) -> withFile(path, content) }
                if (kind.android) withFile("local.properties", "sdk.dir=${androidSdkDir()}")
            }
        spec.subprojects.forEach { (path, module) ->
            builder.withSubproject(path) {
                sources.addAll(module.sources)
                withBuildScript { renderSubproject(path, module) }
            }
        }
        spec.buildSrc?.let { module ->
            builder.withBuildSrc {
                sources.addAll(module.sources)
                withBuildScript { renderCustom(module) }
            }
        }
        return builder.write()
    }

    private fun BuildScript.Builder.renderRoot() {
        if (spec.plugins != null) return renderCustom(spec)
        when (kind) {
            ProjectKind.Jvm -> {
                plugins(KotlinPlugins.jvm(kotlinVersion), pluginUnderTest)
                finish(spec, explicitApiBlock())
            }
            is ProjectKind.Kmp -> {
                plugins(KotlinPlugins.multiplatform(kotlinVersion), pluginUnderTest)
                finish(spec, explicitApiBlock() + kotlinBlock(kind.targets), sourceSetPrefix = "commonMain")
            }
            is ProjectKind.AndroidLibrary -> {
                val androidSpec = spec as AndroidLibrarySpec
                plugins(*androidLibraryPlugins(kind.agp, root = true))
                finish(
                    spec,
                    androidLibraryScript(
                        kind.agp,
                        namespace = "test.consumer",
                        androidLines = androidSpec.androidLines,
                        kotlinLines = explicitApiLines() + androidSpec.kotlinLines,
                    ),
                )
            }
            is ProjectKind.KmpAndroid -> {
                plugins(KotlinPlugins.multiplatform(kotlinVersion), androidPlugin(kind.target), pluginUnderTest)
                finish(spec, explicitApiBlock() + kmpAndroidScript(kind.target), sourceSetPrefix = "commonMain")
            }
        }
    }

    /** A subproject names the Kotlin plugin version only when the root applies its own plugins. */
    private fun BuildScript.Builder.renderSubproject(path: String, module: SubprojectSpec) {
        if (module.plugins != null) return renderCustom(module)
        val kotlinVersion = testKotlinVersion().takeIf { spec.plugins != null }
        when {
            module.jvm || kind is ProjectKind.Jvm || kind is ProjectKind.KmpAndroid -> {
                plugins(Plugin("org.jetbrains.kotlin.jvm", kotlinVersion), pluginUnderTest)
                finish(module, explicitApiBlock())
            }
            kind is ProjectKind.Kmp -> {
                plugins(Plugin("org.jetbrains.kotlin.multiplatform", kotlinVersion), pluginUnderTest)
                finish(module, explicitApiBlock() + kotlinBlock(kind.targets), sourceSetPrefix = "commonMain")
            }
            kind is ProjectKind.AndroidLibrary -> {
                plugins(*androidLibraryPlugins(kind.agp, root = false))
                finish(
                    module,
                    androidLibraryScript(
                        kind.agp,
                        namespace = "test.$path",
                        androidLines = emptyList(),
                        kotlinLines = explicitApiLines(),
                    ),
                )
            }
        }
    }

    private fun BuildScript.Builder.renderCustom(module: ModuleSpec) {
        plugins(module.plugins.orEmpty())
        finish(module, head = "")
    }

    private fun BuildScript.Builder.finish(module: ModuleSpec, head: String, sourceSetPrefix: String? = null) {
        if (module.imports.isNotEmpty()) imports = Imports.of(module.imports)
        withKotlin(
            buildString {
                if (head.isNotBlank()) appendLine(head.trimEnd())
                module.scriptParts.forEach { appendLine(it) }
                append(dependenciesBlock(module, sourceSetPrefix))
            }
        )
    }

    private fun explicitApiLines(): List<String> = if (spec.explicitApi) listOf("explicitApi()") else emptyList()

    private fun explicitApiBlock(): String = if (spec.explicitApi) "kotlin { explicitApi() }\n" else ""

    private fun kotlinBlock(body: String): String = buildString {
        appendLine("kotlin {")
        appendLine(body.trimIndent().prependIndent("    "))
        appendLine("}")
    }

    /** A source set configuration such as `commonMainImplementation` has no type-safe accessor. */
    private fun dependenciesBlock(module: ModuleSpec, sourceSetPrefix: String?): String {
        if (module.dependencies.isEmpty()) return ""
        return buildString {
            appendLine("dependencies {")
            for ((sourceSet, dependency) in module.dependencies) {
                val prefix = sourceSet ?: sourceSetPrefix
                val configuration = if (prefix == null) {
                    dependency.configuration
                } else {
                    "\"$prefix${dependency.configuration.replaceFirstChar { it.titlecase(Locale.US) }}\""
                }
                appendLine("    $configuration(${dependency.notation()})")
            }
            appendLine("}")
        }
    }

    private fun Dependency.notation(): String =
        if (identifier.startsWith(":")) "project(\"$identifier\")" else "\"$identifier${version?.let { ":$it" }.orEmpty()}\""

    private fun androidLibraryPlugins(agp: AgpVersion, root: Boolean): Array<Plugin> = buildList {
        add(Plugin("com.android.library", agp.version.takeIf { root }))
        when (agp) {
            AgpVersion.AGP9 -> if (root) add(Plugin("org.jetbrains.kotlin.jvm", testKotlinVersion(), apply = false))
            AgpVersion.AGP8 -> add(Plugin("org.jetbrains.kotlin.android", testKotlinVersion().takeIf { root }))
        }
        add(pluginUnderTest)
    }.toTypedArray()

    private fun androidLibraryScript(
        agp: AgpVersion,
        namespace: String,
        androidLines: List<String>,
        kotlinLines: List<String>,
    ): String = buildString {
        appendLine("android {")
        appendLine("    namespace = \"$namespace\"")
        appendLine("    compileSdk = 36")
        androidLines.forEach { appendLine(it.prependIndent("    ")) }
        when (agp) {
            AgpVersion.AGP9 -> {
                if (kotlinLines.isNotEmpty()) appendLine(kotlinBlock(kotlinLines.joinToString("\n")).prependIndent("    ").trimEnd())
                appendLine("}")
            }
            AgpVersion.AGP8 -> {
                // AGP 8 defaults Java to 1.8 while Kotlin follows the build JDK, and KGP
                // validates that the JVM targets match.
                appendLine("    compileOptions {")
                appendLine("        sourceCompatibility = JavaVersion.VERSION_17")
                appendLine("        targetCompatibility = JavaVersion.VERSION_17")
                appendLine("    }")
                appendLine("}")
                append(kotlinBlock((listOf(JVM_TARGET_17) + kotlinLines).joinToString("\n")))
            }
        }
    }

    private fun androidPlugin(target: KmpAndroidTarget): Plugin = when (target) {
        KmpAndroidTarget.LIBRARY_AGP8, KmpAndroidTarget.LIBRARY_AGP9 ->
            Plugin("com.android.kotlin.multiplatform.library", target.agp.version)
        KmpAndroidTarget.LEGACY -> Plugin("com.android.library", target.agp.version)
    }

    private fun kmpAndroidScript(target: KmpAndroidTarget): String = when (target) {
        KmpAndroidTarget.LIBRARY_AGP8, KmpAndroidTarget.LIBRARY_AGP9 -> kotlinBlock(
            """
                jvm()
                ${if (target == KmpAndroidTarget.LIBRARY_AGP9) "android" else "androidLibrary"} {
                    namespace = "test.lib"
                    compileSdk = 36
                    minSdk = 24
                    withHostTestBuilder {}
                }
            """
        )
        // The JVM targets match for the same reason as in androidLibraryScript.
        KmpAndroidTarget.LEGACY -> kotlinBlock(
            """
                jvm()
                androidTarget {
                    $JVM_TARGET_17
                }
            """
        ) + """
            android {
                namespace = "test.lib"
                compileSdk = 36
                defaultConfig { minSdk = 24 }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
            }
        """.trimIndent() + "\n"
    }

    private fun testKotlinVersion(): String = kotlinVersion ?: "$TEST_COMPILER_VERSION"
}

private const val WATCHDOG_PACKAGE = "org.jetbrains.kotlinx.library.api.watchdog"
private const val WATCHDOG_PLUGIN_ID = "org.jetbrains.kotlin.library.api-watchdog"
private const val JVM_TARGET_17 = "compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 }"

private val watchdogAnnotationImports: List<String> = listOf(
    "ExemptionReason",
    "IntentionallyOpen",
    "IntentionallyExhaustive",
    "IntentionallyUndocumented",
    "IntentionallyFunctionTypeAlias",
    "IntentionallyDataClass",
    "IntentionallyWithoutEquals",
    "IntentionallyWithoutHashCode",
    "IntentionallyWithoutToString",
    "IntentionallyMutableCollection",
    "IntentionallyPairOrTriple",
    "IntentionallyBooleanParameter",
    "IntentionallyNullableBoolean",
    "IntentionallyRequiredParameterAfterOptional",
    "IntentionallyInconsistentParameterOrder",
    "IntentionallyInlinedLogic",
    "IntentionallyMangledJvmName",
    "IntentionallyKotlinOnlyApi",
    "IntentionallyNonStaticCompanionApi",
    "IntentionallyDefaultFacadeName",
    "IntentionallyWithoutJvmOverloads",
    "IntentionallyNonTransitiveDependency",
    "IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility",
    "InternalAnnotationMarker",
).map { "$WATCHDOG_PACKAGE.$it" }

private val CLASS_NAME = Regex("(?:class|object|interface) (?<name>[A-Za-z0-9_]+)")
private val FUNCTION_NAME = Regex("fun(?: <[A-Za-z0-9_]+>)? (?<name>[A-Za-z0-9_]+)")
private val PACKAGE = Regex("^package ([A-Za-z0-9_.]+)", RegexOption.MULTILINE)

private fun inferFileName(code: String): String =
    CLASS_NAME.find(code)?.groups?.get("name")?.value
        ?: FUNCTION_NAME.find(code)?.groups?.get("name")?.value?.replaceFirstChar { it.titlecase(Locale.US) }
        ?: "source"

private fun packagePath(text: String): String =
    PACKAGE.find(text)?.groupValues?.get(1)?.replace('.', '/') ?: "test"

private val testAgp8Version: String? = System.getProperty("watchdog.test.agp8Version")
private val testAgp9Version: String? = System.getProperty("watchdog.test.agp9Version")
private val testAndroidSdkPath: String? = System.getProperty("watchdog.test.androidHome")

fun assumeAndroidBuildEnvironment() {
    assumeTrue(
        "No AGP version is configured, see build.gradle.kts",
        testAgp8Version != null && testAgp9Version != null,
    )
    assumeTrue("No Android SDK was found, see build.gradle.kts", testAndroidSdkPath != null)
}

private fun requiredProperty(value: String?): String =
    requireNotNull(value) { "Call assumeAndroidBuildEnvironment() before an Android fixture" }

private fun androidSdkDir(): String = File(requiredProperty(testAndroidSdkPath)).invariantSeparatorsPath
