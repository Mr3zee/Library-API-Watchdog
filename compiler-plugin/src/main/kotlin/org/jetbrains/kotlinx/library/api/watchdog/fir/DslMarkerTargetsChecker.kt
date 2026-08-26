package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.annotations.KotlinTarget
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.getTargetAnnotation
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.extractEnumValueArgumentInfo
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports `@DslMarker` annotation classes with no declared targets or with targets other than
 * `CLASS`, `ANNOTATION_CLASS`, `TYPE`, and `TYPEALIAS`. All marker visibilities are checked.
 *
 * The backwards-compatibility exemption applies to the annotation class and covers both
 * diagnostics.
 */
internal class DslMarkerTargetsChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass || declaration.isActualizedDeclaration() ||
            declaration.classKind != ClassKind.ANNOTATION_CLASS
        ) {
            return
        }

        if (declaration.source?.kind != KtRealSourceElementKind) {
            return
        }

        val session = context.session
        if (!declaration.hasAnnotation(StandardClassIds.Annotations.DslMarker, session)) {
            return
        }

        if (declaration.hasAnnotation(
                WatchdogClassIds.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility,
                session,
            )
        ) {
            return
        }

        val allowedTargets = declaration.getTargetAnnotation(session)
            ?.findArgumentByName(StandardClassIds.Annotations.ParameterNames.targetAllowedTargets)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            .orEmpty()

        if (allowedTargets.isEmpty()) {
            val factory = severities[WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS] ?: return
            reporter.reportOn(
                source = declaration.source,
                factory = factory,
                a = declaration.name,
            )
            return
        }

        val noopTargetFactory = severities[WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET] ?: return
        for (argument in allowedTargets) {
            val target = argument.extractEnumValueArgumentInfo()?.enumEntryName?.asString()
                ?.let(KotlinTarget::valueOrNull) ?: continue
            if (!target.isEffectiveDslMarkerTarget()) {
                reporter.reportOn(
                    source = argument.source,
                    factory = noopTargetFactory,
                    a = declaration.name,
                    b = target.name,
                )
            }
        }
    }

    /**
     * The targets on which scope control reacts to a marker. `ANNOTATION_CLASS` counts because
     * it is a classifier declaration.
     */
    private fun KotlinTarget.isEffectiveDslMarkerTarget(): Boolean =
        this == KotlinTarget.CLASS ||
                this == KotlinTarget.ANNOTATION_CLASS ||
                this == KotlinTarget.TYPE ||
                this == KotlinTarget.TYPEALIAS
}
