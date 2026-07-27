package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.declarations.utils.isData
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * Reports publicly visible
 * [stateful classes](https://kotlinlang.org/docs/api-guidelines-debuggability.html#provide-a-tostring-method-for-stateful-types)
 * - classes with at least one property that stores its value in a backing field - that neither
 * declare nor inherit a `toString` implementation: their instances render as the opaque
 * class-name-with-hash-code default, which reveals nothing in logs and debugger output. Authors
 * acknowledge the opaque rendering with `@IntentionallyWithoutToString`.
 *
 * A `toString` inherited from any supertype other than `kotlin.Any` counts as provided: the
 * rendering is no longer the opaque default, and whether it must be refined to include the
 * subclass state is a judgement call left to the author.
 *
 * Only regular classes are checked. Data and value classes receive a compiler-generated
 * `toString` (data classes are reported by [DataClassChecker] anyway), enum entries render their
 * name, interfaces and annotation classes can't hold backing fields, and objects - companion
 * objects in particular - typically hold constants rather than per-instance state.
 */
internal class StatefulClassWithoutToStringChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        val factory = severities[WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING] ?: return

        if (declaration !is FirRegularClass || !declaration.isWatchedPublicApi()) {
            return
        }

        if (declaration.classKind != ClassKind.CLASS || declaration.isData || declaration.isInlineOrValue) {
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyWithoutToString, context.session)) {
            return
        }

        // A delegated property stores its value in the delegate, not in a backing field.
        var stateful = false
        declaration.symbol.processAllDeclarations(context.session) { member ->
            if (member is FirPropertySymbol && member.hasBackingField && !member.hasDelegate) {
                stateful = true
            }
        }

        if (!stateful || declaration.providesToString()) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = declaration.name,
        )
    }

    /**
     * Whether the class declares or inherits a `toString` implementation. The scope resolves
     * `toString` to the most specific override; only `kotlin.Any` itself provides the opaque
     * default this checker exists to flag.
     */
    context(context: CheckerContext)
    private fun FirRegularClass.providesToString(): Boolean {
        var provided = false
        unsubstitutedScope().processFunctionsByName(OperatorNameConventions.TO_STRING) { function ->
            val original = function.unwrapFakeOverrides()
            if (original.valueParameterSymbols.isEmpty() &&
                original.contextParameterSymbols.isEmpty() &&
                original.receiverParameterSymbol == null &&
                original.containingClassLookupTag()?.classId != StandardClassIds.Any
            ) {
                provided = true
            }
        }
        return provided
    }
}
