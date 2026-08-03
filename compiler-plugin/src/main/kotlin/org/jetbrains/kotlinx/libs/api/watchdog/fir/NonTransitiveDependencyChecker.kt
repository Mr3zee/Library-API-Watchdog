package org.jetbrains.kotlinx.libs.api.watchdog.fir

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.utils.sourceElement
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.library.metadata.KlibDeserializedContainerSource
import org.jetbrains.kotlin.load.kotlin.KotlinJvmBinarySourceElement
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/** The Gradle-derived inputs needed to distinguish `api` from non-transitive dependencies. */
internal data class DependencyExposureCheckConfiguration(
    val compileDependencies: Set<String>,
    val transitiveDependencies: Set<String>,
)

/**
 * Reports dependency types in public signatures unless their artifacts are published to
 * consumers. Dependency scope is a build-model concern, so this checker is registered only when
 * the Gradle plugin supplies [DependencyExposureCheckConfiguration]. It cannot be suppressed or
 * demoted: the Gradle extension's Boolean switch is its only opt-out.
 *
 * The sweep includes callable return, receiver, value and context parameter types; class
 * supertypes and context parameters; type parameter bounds; public type aliases; and every type
 * argument nested inside those types. Overrides still count because their hand-written signature
 * remains part of this library's API even when another declaration fixed its shape.
 */
internal class NonTransitiveDependencyChecker(
    configuration: DependencyExposureCheckConfiguration,
) : PublicSignatureTypeChecker<ClassId>() {
    private val dependencyIndex = DependencyExposureIndex(configuration)

    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): ClassId? {
        val symbol = (this as? ConeClassLikeType)?.toClassSymbol() ?: return null
        return symbol.classId.takeIf { dependencyIndex.isNonTransitive(symbol, context) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: ClassId) {
        reporter.reportOn(
            source = source,
            factory = WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
            a = kind,
            b = name,
            c = violation.asSingleFqName().asString(),
        )
    }
}

/** Classifies a resolved FIR class by the Gradle artifacts that contain it. */
private class DependencyExposureIndex(configuration: DependencyExposureCheckConfiguration) {
    private val compileRoots = configuration.compileDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val transitiveRoots = configuration.transitiveDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val classification = ConcurrentHashMap<ClassId, Boolean>()

    fun isNonTransitive(symbol: FirClassSymbol<*>, context: CheckerContext): Boolean {
        // A class declared by the compilation itself needs no dependency at all.
        if (symbol.moduleData == context.session.moduleData) {
            return false
        }

        val origin = symbol.dependencyPath()
        if (origin != null) {
            if (transitiveRoots.any { it.containsOrigin(origin) }) {
                return false
            }
            if (compileRoots.any { it.containsOrigin(origin) }) {
                return true
            }
        }

        // Project artifacts can be represented as a jar in one configuration and as a classes
        // directory in another. Java dependency classes also lack the Kotlin container source
        // above. Looking up the class in both root sets covers both cases without relying on path
        // identity.
        return classification.getOrPut(symbol.classId) {
            when {
                transitiveRoots.any { it.containsClass(symbol.classId) } -> false
                compileRoots.any { it.containsClass(symbol.classId) } -> true
                else -> false // JDK and compiler built-ins are not Gradle dependencies.
            }
        }
    }

    private fun FirClassSymbol<*>.dependencyPath(): Path? = when (val source = sourceElement) {
        is KotlinJvmBinarySourceElement -> source.binaryClass.containingLibraryPath?.path?.toString()?.let(::normalizedPath)
        is KlibDeserializedContainerSource -> normalizedPath(source.klib.libraryFile.path)
        else -> null
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

    private companion object {
        fun normalizedPath(path: String): Path = File(path).toPath().toAbsolutePath().normalize()
    }
}
