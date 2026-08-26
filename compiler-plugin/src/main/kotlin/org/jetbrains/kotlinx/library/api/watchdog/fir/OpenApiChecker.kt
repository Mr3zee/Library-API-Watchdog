package org.jetbrains.kotlinx.library.api.watchdog.fir

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
 * Reports watched open or abstract classes and interfaces without
 * [kotlin.SubclassOptInRequired] or an exemption. Classes with no public or protected constructor
 * are skipped. A separate diagnostic reports `@SubclassOptInRequired` calls with no marker classes.
 */
internal class OpenApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass || declaration.isActualizedDeclaration()) {
            return
        }

        if (!declaration.isWatchedPublicSourceApi()) {
            return
        }

        val session = context.session

        // A subclass has to delegate to some superclass constructor, so a class whose
        // constructors are all internal or private can't be subclassed outside the library.
        val constructors = if (declaration.classKind == ClassKind.CLASS) {
            declaration.constructors(session)
        } else emptyList()

        val openForSubclassing =
            when {
                declaration.classKind == ClassKind.CLASS ->
                    (declaration.modality == Modality.OPEN ||
                            declaration.modality == Modality.ABSTRACT) &&
                            constructors.any {
                                it.visibility == Visibilities.Public || it.visibility == Visibilities.Protected
                            }
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
                    a = declaration.name,
                )
            }
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyOpen, session)) return

        val factory = severities[WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN] ?: return
        val fix = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN.name,
            value = if (declaration.classKind == ClassKind.CLASS) "classFix" else "interfaceFix",
        )
        val hasPublicPrimaryConstructor = constructors.any {
            it.isPrimary && it.visibility == Visibilities.Public
        }

        if (declaration.classKind == ClassKind.CLASS && !hasPublicPrimaryConstructor) {
            for (constructor in constructors) {
                if (constructor.visibility != Visibilities.Public &&
                    constructor.visibility != Visibilities.Protected
                ) {
                    continue
                }
                reporter.reportOn(
                    source = constructor.source,
                    factory = factory,
                    a = declaration.classKind,
                    b = declaration.name,
                    c = fix,
                )
            }
        } else {
            reporter.reportOn(
                source = declaration.source,
                factory = factory,
                a = declaration.classKind,
                b = declaration.name,
                c = fix,
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
