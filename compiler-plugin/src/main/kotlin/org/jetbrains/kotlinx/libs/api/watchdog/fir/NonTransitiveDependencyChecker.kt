package org.jetbrains.kotlinx.libs.api.watchdog.fir

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.isLegacyContextReceiver
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.sourceElement
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
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
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    private val dependencyIndex = DependencyExposureIndex(configuration)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        when (declaration) {
            // Parameters are swept from their callable so its visibility is evaluated once.
            is FirValueParameter -> return
            is FirProperty -> checkProperty(declaration)
            is FirNamedFunction -> checkFunction(declaration)
            is FirConstructor -> checkConstructor(declaration)
            is FirRegularClass -> checkClass(declaration)
            is FirTypeAlias -> checkTypeAlias(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty) {
        if (!declaration.isWatchedPublicApi()) return

        checkTypeParameters(declaration.typeParameters)
        declaration.receiverParameter?.typeRef?.let {
            checkType(it, "property receiver", declaration.name, declaration.source)
        }
        declaration.contextParameters.forEach { checkParameter(it) }
        checkType(declaration.returnTypeRef, "property", declaration.name, declaration.source)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (!declaration.isWatchedPublicApi()) return

        checkTypeParameters(declaration.typeParameters)
        declaration.receiverParameter?.typeRef?.let {
            checkType(it, "function receiver", declaration.name, declaration.source)
        }
        checkType(declaration.returnTypeRef, "function", declaration.name, declaration.source)
        (declaration.contextParameters + declaration.valueParameters).forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkConstructor(declaration: FirConstructor) {
        if (!declaration.isWatchedPublicApi()) return

        // A val/var parameter is also a property over the same source text. Let that property own
        // the report so one dependency type produces one diagnostic.
        val propertyParameters = mutableSetOf<FirValueParameterSymbol>()
        if (declaration.isPrimary) {
            context.containingClassSymbol?.processAllDeclarations(context.session) { member ->
                if (member is FirPropertySymbol) {
                    member.correspondingValueParameterFromPrimaryConstructor?.let(propertyParameters::add)
                }
            }
        }

        for (parameter in declaration.contextParameters + declaration.valueParameters) {
            if (parameter.symbol !in propertyParameters) checkParameter(parameter)
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkClass(declaration: FirRegularClass) {
        if (!declaration.isWatchedPublicApi()) return

        checkTypeParameters(declaration.typeParameters)
        declaration.superTypeRefs.forEach {
            checkType(it, "supertype of", declaration.name, declaration.source)
        }
        declaration.contextParameters.forEach { checkParameter(it) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeAlias(declaration: FirTypeAlias) {
        if (!declaration.isWatchedPublicApi()) return

        checkTypeParameters(declaration.typeParameters)
        checkType(declaration.expandedTypeRef, "type alias", declaration.name, declaration.source)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkParameter(parameter: FirValueParameter) {
        if (parameter.isLegacyContextReceiver()) return
        checkType(parameter.returnTypeRef, "parameter", parameter.name, parameter.source)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkTypeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameter in typeParameters.filterIsInstance<FirTypeParameter>()) {
            for (bound in typeParameter.bounds) {
                checkType(bound, "type parameter", typeParameter.name, typeParameter.source)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkType(typeRef: FirTypeRef, kind: String, name: Name, fallbackSource: KtSourceElement?) {
        val violation = typeRef.coneType.findNonTransitiveDependency() ?: return
        reporter.reportOn(
            source = typeRef.source ?: fallbackSource,
            factory = WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
            a = kind,
            b = name,
            c = violation.asSingleFqName().asString(),
        )
    }

    context(context: CheckerContext)
    private fun ConeKotlinType.findNonTransitiveDependency(): ClassId? {
        val type = upperBoundIfFlexible().let {
            if (it is ConeClassLikeType) it.fullyExpandedType() else it
        }

        if (type is ConeClassLikeType) {
            val symbol = type.toClassSymbol()
            if (symbol != null && dependencyIndex.isNonTransitive(symbol, context)) {
                return symbol.classId
            }
        }
        return type.typeArguments.firstNotNullOfOrNull { it.type?.findNonTransitiveDependency() }
    }
}

/** Classifies a resolved FIR class by the Gradle artifacts that contain it. */
private class DependencyExposureIndex(configuration: DependencyExposureCheckConfiguration) {
    private val compileRoots = configuration.compileDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val transitiveRoots = configuration.transitiveDependencies.mapTo(linkedSetOf(), ::normalizedPath)
    private val classification = ConcurrentHashMap<ClassId, Boolean>()

    fun isNonTransitive(symbol: FirClassSymbol<*>, context: CheckerContext): Boolean {
        // A class declared by the compilation itself needs no dependency at all.
        if (symbol.moduleData == context.session.moduleData) return false

        val origin = symbol.dependencyPath()
        if (origin != null) {
            if (transitiveRoots.any { it.containsOrigin(origin) }) return false
            if (compileRoots.any { it.containsOrigin(origin) }) return true
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

    @OptIn(SymbolInternals::class)
    private fun FirClassSymbol<*>.dependencyPath(): Path? = when (val source = fir.sourceElement) {
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
