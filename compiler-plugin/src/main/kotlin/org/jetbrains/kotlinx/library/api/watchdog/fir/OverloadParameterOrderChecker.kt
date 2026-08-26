package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.Name

/**
 * Reports watched overloads whose shared parameter names have different relative orders. Members
 * visible in one class, top-level functions in one package, constructors of one class, and
 * extensions alongside members of their receiver are compared. Dependencies are excluded.
 *
 * Both declarations in a local inconsistent pair report. For inherited-member and extension-member
 * pairs, only the declaration introduced alongside the existing members reports. Overrides remain
 * comparison references but do not report. Exempt declarations are excluded as both reporters and
 * references.
 */
internal class OverloadParameterOrderChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val factory = severities[WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS] ?: return

        if (declaration !is FirConstructor && declaration !is FirNamedFunction) {
            return
        }

        if (!declaration.isWatchedPublicSourceApi() || declaration.isOverride || declaration.symbol.isExempt()) {
            return
        }

        val ownParameters = declaration.valueParameters
        if (ownParameters.size < 2) {
            return
        }

        val callableName = declaration.reportedName() ?: return
        var reported = false
        declaration.forEachOverloadSibling siblings@{ sibling ->
            if (reported) return@siblings
            if (sibling == declaration.symbol) {
                return@siblings
            }

            if (sibling.isExempt() || !sibling.isWatchedPublicSourceApiSibling()) {
                return@siblings
            }

            reported = reportSwappedPair(
                declaration,
                factory,
                callableName,
                other = sibling.valueParameterSymbols,
                current = ownParameters,
            )
        }
    }

    /**
     * The callables this declaration overloads: same-named functions visible in the class -
     * declared and inherited alike, since users see them side by side - or the module's
     * top-level functions of the same package, or the sibling constructors of the class, which
     * are not inherited. An extension is called like a member of the type it extends, so the
     * same-named members of its receiver class join the list, wherever the extension itself is
     * declared; a receiver that is no class - a type parameter without a class bound, say -
     * contributes nothing.
     *
     * Inherited members surface as fake overrides and are unwrapped to the original declaration,
     * whose source, visibility, and exemption the sibling gate inspects; members originating in
     * dependencies fall out there, having no real source.
     */
    context(context: CheckerContext)
    private inline fun FirFunction.forEachOverloadSibling(
        crossinline action: (FirFunctionSymbol<*>) -> Unit,
    ) {
        val containingClass = context.containingClassSymbol
        when {
            this is FirConstructor -> {
                containingClass?.processAllDeclarations(context.session) { member ->
                    if (member is FirConstructorSymbol) {
                        action(member)
                    }
                }
            }

            this !is FirNamedFunction -> Unit

            else -> {
                val name = this.name
                if (containingClass != null) {
                    containingClass.forEachMemberNamed(name, action)
                } else {
                    for (sibling in context.session.symbolProvider.getTopLevelFunctionSymbols(
                        symbol.callableId.packageName,
                        name,
                    )) {
                        action(sibling)
                    }
                }

                val receiverClass = receiverParameter?.typeRef?.coneType?.erasedClassSymbol()
                if (receiverClass != null && receiverClass != containingClass) {
                    receiverClass.forEachMemberNamed(name, action)
                }
            }
        }
    }

    context(context: CheckerContext)
    private inline fun FirClassSymbol<*>.forEachMemberNamed(
        name: Name,
        crossinline action: (FirFunctionSymbol<*>) -> Unit,
    ) {
        unsubstitutedScope(context).processFunctionsByName(name) { member ->
            action(member.unwrapFakeOverrides())
        }
    }

    /**
     * Reports the first pair of names shared by both parameter lists whose relative order
     * differs, in [current]'s order, and returns true; returns false without reporting when
     * the shared names are ordered consistently.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportSwappedPair(
        declaration: FirFunction,
        factory: KtDiagnosticFactory3<Name, Name, Name>,
        callableName: Name,
        other: List<FirValueParameterSymbol>,
        current: List<FirValueParameter>,
    ): Boolean {
        for (i in current.indices) {
            val firstName = current[i].name
            val firstOtherIndex = other.indexOfFirst { it.name == firstName }
            if (firstOtherIndex < 0) continue

            for (j in i + 1 until current.size) {
                val secondName = current[j].name
                val secondOtherIndex = other.indexOfFirst { it.name == secondName }
                if (secondOtherIndex in 0..<firstOtherIndex) {
                    reporter.reportOn(
                        source = declaration.source,
                        factory = factory,
                        a = firstName,
                        b = secondName,
                        c = callableName,
                    )
                    return true
                }
            }
        }
        return false
    }

    context(context: CheckerContext)
    private fun FirBasedSymbol<*>.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyInconsistentParameterOrder, context.session)
}
