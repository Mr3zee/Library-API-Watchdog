package org.jetbrains.kotlinx.libs.api.watchdog.fir

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

/**
 * Reports internal-API types exposed in supported public source signatures. A declaration marked
 * as internal API is already outside the supported surface and is skipped by
 * [isWatchedPublicApi]. Declarations that only `@PublishedApi` promotes are skipped too: they are
 * binary implementation details rather than types users can name as source API.
 *
 * [PublicSignatureTypeChecker] supplies the same sweep as [NonTransitiveDependencyChecker]. Both
 * type aliases and their expanded types are checked.
 */
internal class InternalApiTypeExposureChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : PublicSignatureTypeChecker<ClassId>() {
    context(context: CheckerContext)
    override fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicApi() && !declaration.isPublishedApiOnly()

    /** Preserve an alias long enough to inspect a marker on the alias declaration itself. */
    context(context: CheckerContext)
    override fun ConeKotlinType.classifierType(): ConeKotlinType = this

    /** FIR may retain the source alias separately from the type reached through it. */
    context(context: CheckerContext)
    override fun ConeKotlinType.typeBeforeClassifier(): ConeKotlinType? = abbreviatedType

    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): ClassId? {
        val symbol = (this as? ConeClassLikeType)?.lookupTag?.toSymbol(context.session)
        return symbol?.internalApiOwner()?.classId
    }

    /** An unmarked alias can still expand to an internal-API type. */
    context(context: CheckerContext)
    override fun ConeKotlinType.typeAfterClassifier(): ConeKotlinType? {
        val symbol = (this as? ConeClassLikeType)?.lookupTag?.toSymbol(context.session)
        return if (symbol is FirTypeAliasSymbol) fullyExpandedType() else null
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: ClassId) {
        val factory = severities[WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API] ?: return
        reporter.reportOn(
            source = source,
            factory = factory,
            a = kind,
            b = name,
            c = violation.asSingleFqName().asString(),
        )
    }

    context(context: CheckerContext)
    private fun FirClassLikeSymbol<*>.internalApiOwner(): FirClassLikeSymbol<*>? {
        var current: FirClassLikeSymbol<*>? = this
        while (current != null) {
            if (current.hasInternalApiMarker()) return current
            current = current.getContainingClassSymbol()
        }
        return null
    }
}
