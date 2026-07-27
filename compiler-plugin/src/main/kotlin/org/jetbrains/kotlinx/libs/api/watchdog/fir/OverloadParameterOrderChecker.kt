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
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.Name

/**
 * Reports publicly visible overloads whose same-named parameters appear in a different relative
 * order than in another overload:
 * [users transfer their expectations between overloads](https://kotlinlang.org/docs/api-guidelines-consistency.html#preserve-parameter-order-naming-and-usage),
 * so an inconsistent order of same-named parameters invites silently swapped arguments -
 * especially when the parameters share a type and the call still compiles. Same-named parameters
 * with different types stay legal: conversion overloads like `BigDecimal(Int)`/`BigDecimal(String)`
 * are the point of overloading. Authors acknowledge a deliberate order difference with
 * `@IntentionallyInconsistentParameterOrder`.
 *
 * No overload is preferred as the canonical order: every member of an inconsistent pair reports,
 * and reordering either clears both. Overloads are compared as users see them side by side -
 * the members visible in a class, inherited ones included, or the module's top-level functions
 * of the same package; constructors of a class are overloads of each other. For an inheritance
 * pair only the subtype's declaration reports: the supertype can't see its subtypes' overloads,
 * and it is the new overload that strays from the established signature. Dependencies are not
 * compared - only declarations the library author can reorder are held against each other.
 *
 * Overrides never report - their parameter order is fixed by the overridden declaration - but
 * they still serve as ordering references: a new overload next to an inherited signature should
 * follow it. An exempt declaration is skipped entirely, as reporter and as reference, so one
 * acknowledged legacy overload doesn't spread its order to consistent newer ones.
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
     * are not inherited. Inherited members surface as fake overrides and are unwrapped to the
     * original declaration, whose source, visibility, and exemption the sibling gate inspects;
     * members originating in dependencies fall out there, having no real source.
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

            containingClass != null -> buildList {
                containingClass.unsubstitutedScope(context).processFunctionsByName(name) { member ->
                    add(member.unwrapFakeOverrides())
                }
            }

            else -> {
                context.session.symbolProvider
                    .getTopLevelFunctionSymbols(symbol.callableId.packageName, name)
            }
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
