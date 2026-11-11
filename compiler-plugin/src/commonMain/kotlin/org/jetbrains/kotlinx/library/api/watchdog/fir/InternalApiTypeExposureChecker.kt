package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/** [collecting] reports the diagnostic as a warning, so the collection mode of `exemptExistingApi` compiles. */
internal class InternalApiTypeExposureChecker(
    private val collecting: Boolean = false,
) : PublicSignatureTypeChecker<InternalApiExposure>(exemption = WatchdogClassIds.IntentionallyExposedInternalApiForBackwardsCompatibility) {
    context(context: CheckerContext)
    override fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicSourceApi()

    /** The alias itself can carry the marker, so it isn't expanded here. */
    context(context: CheckerContext)
    override fun ConeKotlinType.classifierType(): ConeKotlinType = this

    /** FIR can keep the source alias apart from the type reached through it. */
    context(context: CheckerContext)
    override fun ConeKotlinType.typeBeforeClassifier(): ConeKotlinType? = abbreviatedType

    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): InternalApiExposure? {
        val symbol = (this as? ConeClassLikeType)?.lookupTag?.toSymbol(context.session)
        return symbol?.internalApiExposure()
    }

    /** An unmarked alias can expand to an internal API type. */
    context(context: CheckerContext)
    override fun ConeKotlinType.typeAfterClassifier(): ConeKotlinType? {
        val symbol = (this as? ConeClassLikeType)?.lookupTag?.toSymbol(context.session)
        return if (symbol is FirTypeAliasSymbol) fullyExpandedType() else null
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(
        source: KtSourceElement?,
        kind: String,
        name: Name,
        violation: InternalApiExposure,
    ) {
        val factory = if (collecting) {
            WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API_COLLECTED
        } else {
            WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API
        }
        reporter.reportOn(
            source = source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, kind),
            b = name,
            c = violation.type.asSingleFqName().asString(),
            d = when (violation) {
                is InternalApiExposure.Marked -> WatchdogDiagnosticMessages.parameterValueFor(
                    factory.name,
                    "markedInternal",
                    violation.annotation.shortClassName.asString(),
                )
                is InternalApiExposure.ExcludedByName -> WatchdogDiagnosticMessages.parameterValueFor(
                    factory.name,
                    "excludedByName",
                    violation.pattern,
                )
            },
        )
    }

    /** A name rule describes the API of this module, so it doesn't count for a class of a dependency. */
    context(context: CheckerContext)
    private fun FirClassLikeSymbol<*>.internalApiExposure(): InternalApiExposure? {
        val filters = context.session.configuredApiFilters
        var current: FirClassLikeSymbol<*>? = this
        while (current != null) {
            val annotation = current.internalApiAnnotation()
            if (annotation != null) {
                return InternalApiExposure.Marked(current.classId, annotation)
            }
            val pattern = filters?.takeIf { current.isFromModuleOf(context.session) }?.excludingPattern(current)
            if (pattern != null) {
                return InternalApiExposure.ExcludedByName(current.classId, pattern)
            }
            current = current.getContainingClassSymbol()
        }
        return null
    }
}

/** [type] is the class that carries the marker or matches the rule: the exposed type or a class that encloses it. */
internal sealed class InternalApiExposure {
    abstract val type: ClassId

    data class Marked(override val type: ClassId, val annotation: ClassId) : InternalApiExposure()

    data class ExcludedByName(override val type: ClassId, val pattern: String) : InternalApiExposure()
}
