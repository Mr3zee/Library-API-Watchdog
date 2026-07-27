package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * Reports the tuple types `Pair` and `Triple` in publicly visible signatures, type parameter
 * bounds and type arguments included (`List<Pair<Int, String>>` exposes the tuple all the same;
 * see [ExposedTypeChecker] for the shared sweep). Tuple components carry no domain meaning: at
 * the use site `first`/`second`/`third` and positional destructuring reveal nothing about the
 * values, and the fixed shape cannot evolve - adding a value means switching to a different
 * type, breaking users. The API should expose
 * [a small class with descriptively named properties](https://kotlinlang.org/docs/data-classes.html)
 * instead. Authors acknowledge a deliberate tuple with `@IntentionallyPairOrTriple`.
 *
 * `Pair` and `Triple` are final, so a direct classifier match suffices - no subtype can smuggle
 * the tuple shape in under another name.
 */
internal class PairOrTripleChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : ExposedTypeChecker(WatchdogClassIds.IntentionallyPairOrTriple) {
    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): Name? =
        (this as? ConeClassLikeType)?.lookupTag?.classId
            ?.takeIf { it in tupleTypes }
            ?.shortClassName

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: Name) {
        val factory = severities[WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API] ?: return
        reporter.reportOn(
            source = source,
            factory = factory,
            a = kind,
            b = name,
            c = violation,
        )
    }

    companion object {
        private val tupleTypes: Set<ClassId> = setOf(
            ClassId(FqName("kotlin"), Name.identifier("Pair")),
            ClassId(FqName("kotlin"), Name.identifier("Triple")),
        )
    }
}
