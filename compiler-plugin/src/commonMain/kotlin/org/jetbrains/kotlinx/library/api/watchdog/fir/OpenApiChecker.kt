package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeAliasChecker
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.constructors
import org.jetbrains.kotlin.fir.declarations.findArgumentByName
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.declarations.utils.isFun
import org.jetbrains.kotlin.fir.declarations.utils.modality
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.unwrapAndFlattenArgument
import org.jetbrains.kotlin.fir.resolve.fullyExpandedClass
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.ClassId

internal class OpenApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyOpen)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass || !declaration.isWatchedPublicSourceApi()) {
            return
        }

        val session = context.session
        val constructors = if (declaration.classKind == ClassKind.CLASS) {
            declaration.constructors(session)
        } else emptyList()
        if (!declaration.symbol.isOpenForSubclassing(constructors)) {
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

        if (declaration.hasAnnotationOnActualOrExpect(WatchdogClassIds.IntentionallyOpen)) return

        // An `actual` can add `open` and constructors. When the `expect` gets the report, the `actual`
        // reports only the constructors that it adds.
        val expect = declaration.symbol.takeIf { it.isActual }?.getSingleMatchedExpectForActualOrNull() as? FirRegularClassSymbol
        val expectReports = expect != null && expect.reportsOpenApi(session)

        val factory = severities[WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN] ?: return
        val diagnostic = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN.name
        val kind = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = diagnostic,
            value = declaration.kindParameterValue ?: return,
        )
        val fix = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = diagnostic,
            value = when {
                declaration.classKind == ClassKind.CLASS -> "classFix"
                // The compiler rejects `@SubclassOptInRequired` on a `fun interface`.
                declaration.isFun -> "funInterfaceFix"
                else -> "interfaceFix"
            },
        )
        val hasPublicPrimaryConstructor = constructors.any {
            it.isPrimary && it.visibility == Visibilities.Public
        }

        if (declaration.classKind == ClassKind.CLASS && !hasPublicPrimaryConstructor) {
            for (constructor in constructors) {
                if (constructor.visibility != Visibilities.Public &&
                    constructor.visibility != Visibilities.Protected ||
                    expectReports && constructor.isActual
                ) {
                    continue
                }
                reporter.reportOn(
                    source = constructor.source,
                    factory = factory,
                    a = kind,
                    b = declaration.name,
                    c = fix,
                )
            }
        } else if (!expectReports) {
            reporter.reportOn(
                source = declaration.source,
                factory = factory,
                a = kind,
                b = declaration.name,
                c = fix,
            )
        }
    }

    private val markerClassParameter = Name.identifier("markerClass")

    /** `markerClass` is a vararg, so `@SubclassOptInRequired()` compiles. */
    private fun FirAnnotation.hasMarkerClasses(): Boolean =
        findArgumentByName(markerClassParameter, returnFirstWhenNotFound = false)
            ?.unwrapAndFlattenArgument(flattenArrays = true)
            ?.isNotEmpty() == true
}

/**
 * Reports an `actual typealias` to an open class or interface of another module or to a Java class of
 * this module. [OpenApiChecker] reports a Kotlin class of this module.
 */
internal class OpenApiTypeAliasChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirTypeAliasChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyOpen)

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirTypeAlias) {
        val factory = severities[WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN] ?: return
        if (!declaration.isActual || !declaration.isWatchedPublicSourceApi()) return

        val session = context.session
        val expanded = (declaration.fullyExpandedClass(session) as? FirRegularClass)?.symbol ?: return
        val ownClass = expanded.isFromModuleOf(session)
        if (ownClass && expanded.origin !is FirDeclarationOrigin.Java) return
        if (!expanded.reportsOpenApi(session)) return
        if (declaration.hasAnnotationOnActualOrExpect(WatchdogClassIds.IntentionallyOpen)) return

        val expect = declaration.symbol.getSingleMatchedExpectForActualOrNull() as? FirRegularClassSymbol
        if (expect != null && expect.reportsOpenApi(session)) return

        val diagnostic = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN.name
        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(diagnostic, "typeAlias"),
            b = declaration.name,
            c = WatchdogDiagnosticMessages.parameterValueFor(diagnostic, if (ownClass) "typeAliasToJavaFix" else "typeAliasFix"),
        )
    }
}

/**
 * Whether this class belongs to the module of [session] or to one of its `dependsOn` modules. Such a class can
 * come from a binary: an incremental compilation reads the unchanged classes from the previous output, and a
 * metadata compilation reads the `dependsOn` source sets from klibs.
 */
private fun FirRegularClassSymbol.isFromModuleOf(session: FirSession): Boolean =
    moduleData == session.moduleData || moduleData in session.moduleData.allDependsOnDependencies

/** `isPublicAPI` includes the `protected` constructor of a Java class. */
private fun FirRegularClassSymbol.isOpenForSubclassing(constructors: List<FirConstructorSymbol>): Boolean =
    when (classKind) {
        ClassKind.CLASS -> (modality == Modality.OPEN || modality == Modality.ABSTRACT) &&
                constructors.any { it.visibility.isPublicAPI }
        // ExhaustiveApiChecker reports a sealed interface.
        ClassKind.INTERFACE -> modality != Modality.SEALED
        else -> false
    }

/** Doesn't read `@IntentionallyOpen`. The caller reads it on the `actual` and the `expect`. */
private fun FirRegularClassSymbol.reportsOpenApi(session: FirSession): Boolean =
    isOpenForSubclassing(if (classKind == ClassKind.CLASS) constructors(session) else emptyList()) &&
            getAnnotationByClassId(WatchdogClassIds.SubclassOptInRequired, session) == null
