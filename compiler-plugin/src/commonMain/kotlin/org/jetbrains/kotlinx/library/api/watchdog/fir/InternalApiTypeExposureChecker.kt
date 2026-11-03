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

internal class InternalApiTypeExposureChecker : PublicSignatureTypeChecker<InternalApiExposure>() {
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
        val factory = WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API
        reporter.reportOn(
            source = source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, kind),
            b = name,
            c = violation.type.asSingleFqName().asString(),
            d = violation.annotation.shortClassName,
        )
    }

    context(context: CheckerContext)
    private fun FirClassLikeSymbol<*>.internalApiExposure(): InternalApiExposure? {
        var current: FirClassLikeSymbol<*>? = this
        while (current != null) {
            val annotation = current.internalApiAnnotation()
            if (annotation != null) {
                return InternalApiExposure(current.classId, annotation)
            }
            current = current.getContainingClassSymbol()
        }
        return null
    }
}

internal data class InternalApiExposure(val type: ClassId, val annotation: ClassId)
