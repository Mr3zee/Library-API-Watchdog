package org.jetbrains.kotlinx.libs.api.watchdog.fir

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
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
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

        if (declaration !is FirNamedFunction && declaration !is FirConstructor) {
            return
        }

        if (!declaration.isWatchedPublicApi() || declaration.isOverride || declaration.symbol.isExempt()) {
            return
        }

        val ownOrder = declaration.valueParameters.map { it.name }
        if (ownOrder.size < 2) {
            return
        }

        val callableName = declaration.reportedName() ?: return
        for (sibling in declaration.overloadSiblings()) {
            if (sibling == declaration.symbol) {
                continue
            }

            if (sibling.isExempt() || !sibling.isWatchedPublicApiSibling()) {
                continue
            }

            val siblingOrder = sibling.valueParameterSymbols.map { it.name }
            if (reportSwappedPair(declaration, factory, callableName, other = siblingOrder, current = ownOrder)) {
                return
            }
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
    private fun FirFunction.overloadSiblings(): List<FirFunctionSymbol<*>> {
        val containingClass = context.containingClassSymbol
        return when {
            this is FirConstructor -> buildList {
                containingClass?.processAllDeclarations(context.session) { member ->
                    if (member is FirConstructorSymbol) {
                        add(member)
                    }
                }
            }

            this !is FirNamedFunction -> {
                emptyList()
            }

            else -> buildList {
                if (containingClass != null) {
                    addMembersNamed(containingClass, name)
                } else {
                    addAll(
                        context.session.symbolProvider
                            .getTopLevelFunctionSymbols(symbol.callableId.packageName, name)
                    )
                }

                val receiverClass = receiverParameter?.typeRef?.coneType?.erasedClassSymbol()
                if (receiverClass != null && receiverClass != containingClass) {
                    addMembersNamed(receiverClass, name)
                }
            }
        }
    }

    context(context: CheckerContext)
    private fun MutableList<FirFunctionSymbol<*>>.addMembersNamed(
        classSymbol: FirClassSymbol<*>,
        name: Name,
    ) {
        classSymbol.unsubstitutedScope(context).processFunctionsByName(name) { member ->
            add(member.unwrapFakeOverrides())
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
        other: List<Name>,
        current: List<Name>,
    ): Boolean {
        val otherIndex = buildMap {
            other.forEachIndexed { index, name -> put(name, index) }
        }
        val shared = current.filter { it in otherIndex }
        for (i in shared.indices) {
            for (j in i + 1 until shared.size) {
                if (otherIndex.getValue(shared[i]) > otherIndex.getValue(shared[j])) {
                    reporter.reportOn(
                        source = declaration.source,
                        factory = factory,
                        a = shared[i],
                        b = shared[j],
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
