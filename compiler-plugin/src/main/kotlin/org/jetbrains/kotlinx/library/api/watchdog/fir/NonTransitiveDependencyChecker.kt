package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.sourceElement
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * The Gradle-derived inputs needed to distinguish `api` from non-transitive dependencies.
 * [dependencyDeclarations] maps a compile dependency path to the build script declaration that
 * adds it, for the exemption snippet of the diagnostic.
 */
internal data class DependencyExposureCheckConfiguration(
    val compileDependencies: Set<String>,
    val transitiveDependencies: Set<String>,
    val dependencyDeclarations: Map<String, DependencyDeclaration> = emptyMap(),
)

/**
 * A dependency declaration in a build script. [configuration] is the one it is declared on,
 * such as `implementation`. [notation] is written as in the script: `"com.example:models:1.0"`
 * or `project(":model")`.
 */
internal data class DependencyDeclaration(val configuration: String, val notation: String)

/** A dependency type in a public signature and the compile classpath entry that holds it. */
internal data class NonTransitiveType(val classId: ClassId, val origin: Path)

/**
 * Reports dependency types in public signatures unless their artifacts are published to
 * consumers. Dependency scope is a build-model concern, so this checker is registered only when
 * the Gradle plugin supplies [DependencyExposureCheckConfiguration]. It can't be demoted: the
 * Gradle extension's Boolean switch disables it for a whole module, and
 * `@IntentionallyNonTransitiveDependency` exempts a declaration whose dependency the runtime
 * environment supplies on its own, as Gradle does for every plugin.
 *
 * The sweep includes callable return, receiver, value and context parameter types. It also includes class
 * supertypes and context parameters, type parameter bounds, public type aliases, and every type
 * argument nested inside those types. Overrides still count because their hand-written signature
 * remains part of this library's API even when another declaration fixed its shape.
 */
internal class NonTransitiveDependencyChecker(
    session: FirSession,
    configuration: DependencyExposureCheckConfiguration,
) : PublicSignatureTypeChecker<NonTransitiveType>(exemption = WatchdogClassIds.IntentionallyNonTransitiveDependency) {
    private val dependencyIndex = DependencyExposureIndex(session, configuration)
    private val dependencyDeclarations = configuration.dependencyDeclarations
        .mapKeys { (path, _) -> normalizedPath(path) }

    /**
     * The exemption on an enclosing class covers everything declared inside it: a Gradle task or
     * extension class mentions the environment's types in most members, and one acknowledgement
     * on the class states why for all of them.
     */
    context(context: CheckerContext)
    override fun isDeclarationExempt(declaration: FirDeclaration): Boolean =
        super.isDeclarationExempt(declaration) || context.containingDeclarations.any {
            it.hasAnnotation(WatchdogClassIds.IntentionallyNonTransitiveDependency, context.session)
        }

    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): NonTransitiveType? {
        val symbol = (this as? ConeClassLikeType)?.toClassSymbol() ?: return null
        val origin = dependencyIndex.nonTransitiveOrigin(symbol, context) ?: return null
        return NonTransitiveType(symbol.classId, origin)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: NonTransitiveType) {
        val factory = WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY
        reporter.reportOn(
            source = source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, kind),
            b = name,
            c = violation.classId.asSingleFqName().asString(),
            d = exemptionSnippet(violation.origin),
        )
    }

    /**
     * The build script block that exempts the dependency. A direct compiler invocation passes
     * no declarations, so the snippet then names the classpath entry instead.
     */
    private fun exemptionSnippet(origin: Path): String {
        val declaration = dependencyDeclarations[origin]
            ?: DependencyDeclaration("implementation", "\"<the dependency of ${origin.fileName}>\"")
        return WatchdogDiagnosticMessages.parameterValueFor(
            WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY.name,
            "exemptDeclaration",
            declaration.configuration,
            declaration.notation,
        )
    }
}

/** Classifies a resolved FIR class by the Gradle artifacts that contain it. */
private class DependencyExposureIndex(
    session: FirSession,
    configuration: DependencyExposureCheckConfiguration,
) {
    private val compileRoots = configuration.compileDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val transitiveRoots = configuration.transitiveDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val classification = session.firCachesFactory.createCache(::classifyByContents)

    /**
     * The compile classpath root that holds [symbol] when consumers don't get it, or null when
     * they do.
     */
    fun nonTransitiveOrigin(symbol: FirClassSymbol<*>, context: CheckerContext): Path? {
        // A class declared by the compilation itself needs no dependency at all.
        if (symbol.moduleData == context.session.moduleData) {
            return null
        }

        // The Kotlin standard library is implicit for every Kotlin compilation. Older Kotlin
        // Gradle plugins do not consistently expose it through the target's API elements, so
        // classifying kotlin.* types from those configurations would produce false positives.
        val packageName = symbol.classId.packageFqName.asString()
        if (packageName == "kotlin" || packageName.startsWith("kotlin.")) {
            return null
        }

        val origin = symbol.dependencyPath()
        if (origin != null) {
            if (transitiveRoots.any { it.containsOrigin(origin) }) {
                return null
            }
            compileRoots.firstOrNull { it.containsOrigin(origin) }?.let { return it }
        }

        // Project artifacts can be represented as a jar in one configuration and as a classes
        // directory in another. Java dependency classes also lack the Kotlin container source
        // above. Looking up the class in both root sets covers both cases without relying on path
        // identity.
        return classification.getValue(symbol.classId)
    }

    // JDK and compiler built-ins aren't Gradle dependencies, so no root holds them.
    private fun classifyByContents(classId: ClassId): Path? =
        if (transitiveRoots.any { it.containsClass(classId) }) {
            null
        } else {
            compileRoots.firstOrNull { it.containsClass(classId) }
        }

    private fun FirClassSymbol<*>.dependencyPath(): Path? = when (val source = sourceElement) {
        is KotlinJvmBinarySourceElement -> source.binaryClass.containingLibraryPath?.path?.toString()?.let(::normalizedPath)
        else -> source?.klibPathCompat()
    }

    private fun Path.containsOrigin(origin: Path): Boolean =
        this == origin || Files.isDirectory(this) && origin.startsWith(this)

    private fun Path.containsClass(classId: ClassId): Boolean {
        val resource = buildString {
            if (!classId.packageFqName.isRoot) {
                append(classId.packageFqName.asString().replace('.', '/')).append('/')
            }
            append(classId.relativeClassName.asString().replace('.', '$')).append(".class")
        }
        return when {
            Files.isDirectory(this) -> Files.isRegularFile(resolve(resource))
            fileName.toString().endsWith(".jar", ignoreCase = true) ||
                    fileName.toString().endsWith(".zip", ignoreCase = true) ->
                runCatching { JarFile(toFile()).use { it.getJarEntry(resource) != null } }.getOrDefault(false)
            else -> false
        }
    }

}

private fun normalizedPath(path: String): Path = File(path).toPath().toAbsolutePath().normalize()
