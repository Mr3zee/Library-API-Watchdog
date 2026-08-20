package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.abbreviatedType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Reports internal-API types exposed in supported public source signatures. A declaration marked
 * as internal API is already outside the supported surface and is skipped by
 * [isWatchedPublicApi]. Declarations that only `@PublishedApi` promotes are skipped too: they are
 * binary implementation details rather than types users can name as source API.
 *
 * [PublicSignatureTypeChecker] supplies the same sweep as [NonTransitiveDependencyChecker]. Both
 * type aliases and their expanded types are checked.
 */
internal class InternalApiTypeExposureChecker : PublicSignatureTypeChecker<InternalApiExposure>() {
    context(context: CheckerContext)
    override fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicSourceApi()

    /** Preserve an alias long enough to inspect a marker on the alias declaration itself. */
    context(context: CheckerContext)
    override fun ConeKotlinType.classifierType(): ConeKotlinType = this

    /** FIR may retain the source alias separately from the type reached through it. */
    context(context: CheckerContext)
    override fun ConeKotlinType.typeBeforeClassifier(): ConeKotlinType? = abbreviatedType

    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): InternalApiExposure? {
        val symbol =
            (this as? ConeClassLikeType)?.lookupTag?.classId?.let {
                context.session.symbolProvider.getClassLikeSymbolByClassId(it)
            }
        return symbol?.internalApiExposure()
    }

    /** An unmarked alias can still expand to an internal-API type. */
    context(context: CheckerContext)
    override fun ConeKotlinType.typeAfterClassifier(): ConeKotlinType? {
        val symbol =
            (this as? ConeClassLikeType)?.lookupTag?.classId?.let {
                context.session.symbolProvider.getClassLikeSymbolByClassId(it)
            }
        return if (symbol is FirTypeAliasSymbol) fullyExpandedType() else null
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(
        source: KtSourceElement?,
        kind: String,
        name: Name,
        violation: InternalApiExposure,
    ) {
        reporter.reportOn(
            source = source,
            factory = WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API,
            a = kind,
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

/** An internal type found in a public signature and the annotation that marks it. */
internal data class InternalApiExposure(val type: ClassId, val annotation: ClassId)
