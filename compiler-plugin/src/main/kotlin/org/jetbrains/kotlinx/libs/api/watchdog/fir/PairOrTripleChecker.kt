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
 * Reports direct `Pair` and `Triple` classifier matches found by the [ExposedTypeChecker]
 * signature sweep. Subtype checks are unnecessary because both tuple classes are final.
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
