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
import org.jetbrains.kotlin.fir.declarations.utils.sourceElement
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/** [dependencyDeclarations] maps a compile dependency path to the build script declaration that adds it. */
internal data class DependencyExposureCheckConfiguration(
    val compileDependencies: Set<String>,
    val transitiveDependencies: Set<String>,
    val dependencyDeclarations: Map<String, DependencyDeclaration> = emptyMap(),
)

/** [notation] is as written in the script: `"com.example:models:1.0"` or `project(":model")`. */
internal data class DependencyDeclaration(val configuration: String, val notation: String)

internal data class NonTransitiveType(val classId: ClassId, val origin: Path)

internal class NonTransitiveDependencyChecker(
    session: FirSession,
    configuration: DependencyExposureCheckConfiguration,
) : PublicSignatureTypeChecker<NonTransitiveType>(exemption = WatchdogClassIds.IntentionallyNonTransitiveDependency) {
    override val enclosingClassExemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyNonTransitiveDependency)

    private val dependencyIndex = DependencyExposureIndex(session, configuration)
    private val dependencyDeclarations = configuration.dependencyDeclarations
        .mapKeys { (path, _) -> normalizedPath(path) }

    /** An exemption on an enclosing class covers every member, because a Gradle task class mentions environment types in most members. */
    context(context: CheckerContext)
    override fun isDeclarationExempt(declaration: FirDeclaration): Boolean =
        super.isDeclarationExempt(declaration) ||
                enclosingClassHasAnnotation(WatchdogClassIds.IntentionallyNonTransitiveDependency)

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

    /** A direct compiler invocation doesn't pass any declarations, so the snippet then names the classpath entry. */
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

private class DependencyExposureIndex(
    session: FirSession,
    configuration: DependencyExposureCheckConfiguration,
) {
    private val compileRoots = configuration.compileDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val transitiveRoots = configuration.transitiveDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val classification = session.firCachesFactory.createCache(::classifyByContents)

    /** The compile classpath root that holds [symbol], or null when consumers get the class. */
    fun nonTransitiveOrigin(symbol: FirClassSymbol<*>, context: CheckerContext): Path? {
        if (symbol.moduleData == context.session.moduleData) {
            return null
        }

        // Older Kotlin Gradle plugins don't always expose the standard library through the API
        // elements of the target.
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

        // A project artifact is a jar in one configuration and a classes directory in another,
        // and a Java class doesn't have a Kotlin container source.
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
