package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.name.Name

/**
 * Reports publicly visible classes and interfaces that can be subclassed outside the library
 * without any control: every external subclass constrains how the declaration can evolve. Authors
 * either gate subclassing with [kotlin.SubclassOptInRequired] or explicitly acknowledge the
 * contract with `@IntentionallyOpen`. A `@SubclassOptInRequired` with no marker classes gates
 * nothing, so it is reported as well. Classes whose constructors are all internal or private
 * can't be subclassed outside the library and are not reported.
 */
internal class OpenApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass) {
            return
        }

        if (!declaration.isWatchedPublicApi()) {
            return
        }

        val session = context.session

        // A subclass has to delegate to some superclass constructor, so a class whose
        // constructors are all internal or private can't be subclassed outside the library.
        val accessibleConstructors = if (declaration.classKind == ClassKind.CLASS) {
            declaration.constructors(session).filter {
                it.visibility == Visibilities.Public || it.visibility == Visibilities.Protected
            }
        } else {
            emptyList()
        }

        val openForSubclassing =
            when {
                declaration.classKind == ClassKind.CLASS ->
                    (declaration.modality == Modality.OPEN ||
                            declaration.modality == Modality.ABSTRACT) &&
                            accessibleConstructors.isNotEmpty()
                // Sealed interfaces are reported by ExhaustiveApiChecker instead.
                declaration.classKind == ClassKind.INTERFACE ->
                    declaration.modality != Modality.SEALED

                else -> false
            }
        if (!openForSubclassing) {
            return
        }

        val subclassOptIn = declaration.getAnnotationByClassId(
            classId = WatchdogClassIds.SubclassOptInRequired,
            session = session,
        )

        if (subclassOptIn != null) {
            val markersFactory = severities[WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS]
            if (markersFactory != null && !subclassOptIn.hasMarkerClasses()) {
                reporter.reportOn(
                    source = subclassOptIn.source,
                    factory = markersFactory,
                )
            }
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyOpen, session)) return

        val factory = severities[WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN] ?: return
        val hasPublicPrimaryConstructor = accessibleConstructors.any {
            it.isPrimary && it.visibility == Visibilities.Public
        }

        if (declaration.classKind == ClassKind.CLASS && !hasPublicPrimaryConstructor) {
            for (constructor in accessibleConstructors) {
                reporter.reportOn(
                    source = constructor.source,
                    factory = factory,
                    a = declaration.classKind,
                    b = declaration.name,
                )
            }
        } else {
            reporter.reportOn(
                source = declaration.source,
                factory = factory,
                a = declaration.classKind,
                b = declaration.name,
            )
        }
    }

    private val markerClassParameter = Name.identifier("markerClass")

    /** `markerClass` is a vararg, so `@SubclassOptInRequired` compiles with no markers at all. */
    private fun FirAnnotation.hasMarkerClasses(): Boolean =
        findArgumentByName(markerClassParameter, returnFirstWhenNotFound = false)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            ?.isNotEmpty() == true
}
