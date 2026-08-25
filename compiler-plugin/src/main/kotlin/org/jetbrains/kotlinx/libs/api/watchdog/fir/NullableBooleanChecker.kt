package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.lowerBoundIfFlexible
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports nullable Booleans found by the [ExposedTypeChecker] signature sweep. Flexible types are
 * inspected through their lower bound so source nullability is preserved. Constructors are included.
 * A `vararg` needs no special handling because its nullable element type is visited as a type
 * argument.
 */
internal class NullableBooleanChecker(
    private val severities: WatchdogDiagnosticSeverities,
    validateExemptionExplanations: Boolean = true,
) : ExposedTypeChecker(WatchdogClassIds.IntentionallyNullableBoolean, validateExemptionExplanations) {
    /** Nullability lives on the upper bound, so the lower one is what Kotlin sources declare. */
    override fun ConeKotlinType.declaredBound(): ConeKotlinType = lowerBoundIfFlexible()

    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): Name? =
        StandardClassIds.Boolean.shortClassName
            .takeIf { classId == StandardClassIds.Boolean && isMarkedNullable }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: Name) {
        val factory = severities[WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API] ?: return
        reporter.reportOn(
            source = source,
            factory = factory,
            a = kind,
            b = name,
        )
    }
}
