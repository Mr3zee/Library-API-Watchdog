package org.jetbrains.kotlinx.libs.api.watchdog.fir

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
 * Reports `@DslMarker` annotation classes with no explicit `@Target` or with targets other than
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
        if (declaration !is FirRegularClass || declaration.classKind != ClassKind.ANNOTATION_CLASS) {
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

        val targetAnnotation = declaration.getTargetAnnotation(session)
        if (targetAnnotation == null) {
            val factory = severities[WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS] ?: return
            reporter.reportOn(
                source = declaration.source,
                factory = factory,
                a = declaration.name,
            )
            return
        }

        val noopTargetFactory = severities[WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET] ?: return
        val allowedTargets = targetAnnotation
            .findArgumentByName(StandardClassIds.Annotations.ParameterNames.targetAllowedTargets)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            .orEmpty()

        for (argument in allowedTargets) {
            val target = argument.extractEnumValueArgumentInfo()?.enumEntryName?.asString()
                ?.let(KotlinTarget::valueOrNull) ?: continue
            if (target !in effectiveDslMarkerTargets) {
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
     * The targets on which scope control reacts to a marker; `ANNOTATION_CLASS` counts because
     * it is a classifier declaration.
     */
    private val effectiveDslMarkerTargets = setOf(
        KotlinTarget.CLASS,
        KotlinTarget.ANNOTATION_CLASS,
        KotlinTarget.TYPE,
        KotlinTarget.TYPEALIAS,
    )
}
