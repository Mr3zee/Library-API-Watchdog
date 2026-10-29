package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithSource
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContextForProvider
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.declarations.FirTypeParameterRef
import org.jetbrains.kotlin.fir.declarations.FirValueParameter
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.visibility
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.forEachType
import org.jetbrains.kotlin.name.ClassId

internal interface ExemptingChecker {
    val exemptions: Set<ClassId>

    /** The subset of [exemptions] that a class applies to all its members. */
    val enclosingClassExemptions: Set<ClassId> get() = emptySet()

    val readsExemptionOnSiblings: Boolean get() = false
}

/** A cache across visits can hold FIR declarations and their sessions on IDE pooled threads. */
internal class RedundantExemptionProbingChecker<D : FirDeclaration>(
    private val delegates: List<FirDeclarationChecker<D>>,
) : FirDeclarationChecker<D>(delegates.first().mppKind) {
    init {
        require(delegates.all { it is ExemptingChecker && it.mppKind == mppKind }) {
            "Every delegate is an ExemptingChecker of the same MppCheckerKind"
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: D) {
        delegates.forEach { it.check(declaration) }

        if (!declaration.isProbed()) return
        val candidates = declaration.exemptionCandidates(allExemptions)
        if (candidates.isEmpty()) return

        for (delegate in delegates) {
            val exempting = delegate as ExemptingChecker
            val own = candidates.filter { it.isReadBy(exempting, declaration) }
            if (own.isEmpty()) continue

            val baseline = delegate.probedDiagnostics(declaration)
            for (candidate in own) {
                val ignored = own.filter { it === candidate || candidate.covers(it) }.map { it.annotation }
                val ignoredEverywhere = if (exempting.readsExemptionOnSiblings) setOf(candidate.classId) else emptySet()
                if (delegate.probedDiagnostics(declaration, ignored, ignoredEverywhere).all { it in baseline }) {
                    reportRedundantExemption(candidate.annotation, candidate.kind)
                }
            }
        }
    }

    private fun ExemptionCandidate.isReadBy(exempting: ExemptingChecker, declaration: FirDeclaration): Boolean =
        classId in exempting.exemptions &&
                !(declaration is FirRegularClass && rank == 0 && classId in exempting.enclosingClassExemptions)

    private fun ExemptionCandidate.covers(other: ExemptionCandidate): Boolean =
        other.rank > rank && (group == null || group === other.group) && classId == other.classId
}

/** Class checkers run before member checkers, so this checker probes members itself. */
internal class EnclosingExemptionRedundancyChecker(
    basicCheckers: Collection<FirBasicDeclarationChecker>,
    functionCheckers: Collection<FirFunctionChecker>,
    callableCheckers: Collection<FirCallableDeclarationChecker>,
) : FirClassChecker(MppCheckerKind.Common) {
    private class Probe(val checker: FirDeclarationChecker<FirDeclaration>, val accepts: (FirDeclaration) -> Boolean)

    private val probesByExemption: Map<ClassId, List<Probe>> = buildMap<ClassId, MutableList<Probe>> {
        fun <D : FirDeclaration> register(checkers: Collection<FirDeclarationChecker<D>>, accepts: (FirDeclaration) -> Boolean) {
            for (checker in checkers) {
                val exemptions = (checker as? ExemptingChecker)?.enclosingClassExemptions ?: continue
                @Suppress("UNCHECKED_CAST")
                val probe = Probe(checker as FirDeclarationChecker<FirDeclaration>, accepts)
                exemptions.forEach { getOrPut(it) { mutableListOf() }.add(probe) }
            }
        }
        register(basicCheckers) { true }
        register(functionCheckers) { it is FirFunction }
        register(callableCheckers) { it is FirCallableDeclaration }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (probesByExemption.isEmpty() || declaration !is FirRegularClass || !declaration.isProbed()) return

        for (annotation in declaration.annotations) {
            if (annotation.source?.kind != KtRealSourceElementKind) continue
            val classId = annotation.toAnnotationClassIdSafe(context.session) ?: continue
            val probes = probesByExemption[classId] ?: continue
            val ignored = buildList {
                add(annotation)
                declaration.collectNestedExemptions(classId, this)
            }
            if (!isNeeded(declaration, ignored, probes)) {
                reportRedundantExemption(annotation, "classAndMembers")
            }
        }
    }

    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContext)
    private fun FirRegularClass.collectNestedExemptions(classId: ClassId, into: MutableList<FirAnnotation>) {
        for (member in declarations) {
            // The IDE can visit a class before its members resolve.
            member.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
            member.exemptionCandidates(setOf(classId)).mapTo(into) { it.annotation }
            if (member is FirRegularClass) {
                member.annotations.filterTo(into) { it.toAnnotationClassIdSafe(context.session) == classId }
                member.collectNestedExemptions(classId, into)
            }
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun isNeeded(declaration: FirRegularClass, ignored: List<FirAnnotation>, probes: List<Probe>): Boolean {
        if (probes.any { it.reportsNew(declaration, ignored) }) return true
        val provider = context as? CheckerContextForProvider ?: return true
        return provider.withDeclaration(declaration) { inner ->
            context(inner, reporter) { membersNeed(declaration, ignored, probes) }
        }
    }

    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContextForProvider, reporter: DiagnosticReporter)
    private fun membersNeed(declaration: FirRegularClass, ignored: List<FirAnnotation>, probes: List<Probe>): Boolean {
        for (member in declaration.declarations) {
            val needed = when (member) {
                is FirRegularClass -> {
                    member.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
                    probes.any { it.reportsNew(member, ignored) } ||
                            context.withDeclaration(member) { inner ->
                                context(inner, reporter) { membersNeed(member, ignored, probes) }
                            }
                }
                is FirConstructor, is FirNamedFunction, is FirProperty -> {
                    member.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
                    probes.any { it.reportsNew(member, ignored) }
                }
                else -> false
            }
            if (needed) return true
        }
        return false
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun Probe.reportsNew(declaration: FirDeclaration, ignored: List<FirAnnotation>): Boolean {
        if (!accepts(declaration)) return false
        val baseline = checker.probedDiagnostics(declaration)
        return checker.probedDiagnostics(declaration, ignored).any { it !in baseline }
    }

    /** The outer context gets `dropDeclaration`, as in the diagnostics visitor. */
    private inline fun <R> CheckerContextForProvider.withDeclaration(
        declaration: FirDeclaration,
        block: (CheckerContextForProvider) -> R,
    ): R {
        val inner = addDeclaration(declaration)
        try {
            return block(inner)
        } finally {
            dropDeclaration()
        }
    }
}

private data class ReportedDiagnosticKey(val name: String, val startOffset: Int, val endOffset: Int)

private fun KtDiagnostic.key(): ReportedDiagnosticKey {
    val element = (this as? KtDiagnosticWithSource)?.element
    return ReportedDiagnosticKey(factoryName, element?.startOffset ?: -1, element?.endOffset ?: -1)
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun <D : FirDeclaration> FirDeclarationChecker<D>.probedDiagnostics(
    declaration: D,
    ignored: List<FirAnnotation> = emptyList(),
    ignoredEverywhere: Set<ClassId> = emptySet(),
): Set<ReportedDiagnosticKey> {
    val reported = hashSetOf<ReportedDiagnosticKey>()
    val collecting = delegatingDiagnosticReporter(reporter) { diagnostic, _ ->
        if (diagnostic != null) reported += diagnostic.key()
    }
    ExemptionProbe.probing(ignored, ignoredEverywhere) {
        context(context, collecting) { check(declaration) }
    }
    return reported
}

context(context: CheckerContext, reporter: DiagnosticReporter)
private fun reportRedundantExemption(annotation: FirAnnotation, kind: String) {
    val classId = annotation.toAnnotationClassIdSafe(context.session) ?: return
    reporter.reportOn(
        source = annotation.source,
        factory = WatchdogDiagnostics.REDUNDANT_EXEMPTION,
        a = classId.shortClassName,
        b = WatchdogDiagnosticMessages.parameterValueFor(WatchdogDiagnostics.REDUNDANT_EXEMPTION.name, kind),
    )
}

context(context: CheckerContext)
private fun FirDeclaration.isProbed(): Boolean {
    if (this is FirFile) return true
    if (this !is FirRegularClass && this !is FirNamedFunction && this !is FirConstructor &&
        this !is FirProperty && this !is FirTypeAlias
    ) {
        return false
    }

    val sourceKind = source?.kind
    // Constructor properties own their parameter's exemptions.
    if (sourceKind != KtRealSourceElementKind && sourceKind != KtFakeSourceElementKind.PropertyFromParameter) {
        return false
    }

    if ((this as? FirMemberDeclaration)?.visibility == Visibilities.Local) return false
    return !isExpectedDeclaration() && !isActualizedDeclaration()
}

/**
 * [rank]: 0 for a declaration, 1 for a parameter or type parameter, 2 for a type usage.
 * [group]: the parameter or type parameter that holds the annotation, or null on the declaration.
 */
private class ExemptionCandidate(
    val annotation: FirAnnotation,
    val classId: ClassId,
    val kind: String,
    val rank: Int,
    val group: Any?,
)

private val allExemptions: Set<ClassId> =
    WatchdogClassIds.exemptionAnnotations + WatchdogClassIds.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility

context(context: CheckerContext)
private fun FirDeclaration.exemptionCandidates(exemptions: Set<ClassId>): List<ExemptionCandidate> {
    val collector = ExemptionCandidateCollector(context.session, exemptions)
    when (this) {
        is FirFile -> collector.annotations(this, "file")
        is FirRegularClass -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            superTypeRefs.forEach { collector.types(it) }
            collector.parameters(contextParameters)
        }
        is FirTypeAlias -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            collector.types(expandedTypeRef)
        }
        is FirNamedFunction -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            collector.types(receiverParameter?.typeRef)
            collector.types(returnTypeRef)
            collector.parameters(contextParameters)
            collector.parameters(valueParameters)
        }
        is FirConstructor -> {
            collector.annotations(this, "declaration")
            collector.parameters(contextParameters)
            val propertyParameters = if (isPrimary) propertyParameterSymbols(this) else emptySet()
            collector.parameters(valueParameters.filter { it.symbol !in propertyParameters })
        }
        is FirProperty -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            collector.types(receiverParameter?.typeRef)
            collector.parameters(contextParameters)
            val constructorParameter = correspondingValueParameterFromPrimaryConstructor
            if (constructorParameter != null) {
                collector.parameter(constructorParameter)
            } else {
                collector.types(returnTypeRef)
            }
        }
        else -> return emptyList()
    }
    return collector.candidates
}

/** [EnclosingExemptionRedundancyChecker] can have the constructor's class already in the context. */
context(context: CheckerContext)
private fun propertyParameterSymbols(constructor: FirConstructor): Set<FirValueParameterSymbol> {
    val result = hashSetOf<FirValueParameterSymbol>()
    (constructor.getContainingClassSymbol() as? FirClassSymbol<*>)?.processAllDeclarations(context.session) { member ->
        if (member is FirPropertySymbol) {
            member.correspondingValueParameterFromPrimaryConstructor?.let(result::add)
        }
    }
    return result
}

private class ExemptionCandidateCollector(private val session: FirSession, private val exemptions: Set<ClassId>) {
    val candidates = mutableListOf<ExemptionCandidate>()
    private val seenOffsets = hashSetOf<Pair<Int, Int>>()

    fun annotations(container: FirAnnotationContainer, kind: String) {
        for (annotation in container.annotations) {
            add(annotation, kind, rank = 0, group = null)
        }
    }

    fun types(typeRef: FirTypeRef?, group: Any? = null) {
        typeRef?.coneTypeOrNull?.forEachType { type ->
            for (annotation in type.customAnnotations) {
                add(annotation, "typeUsage", rank = 2, group = group)
            }
        }
    }

    fun parameters(parameters: List<FirValueParameter>) {
        for (parameter in parameters) {
            for (annotation in parameter.annotations) {
                add(annotation, "parameter", rank = 1, group = parameter.symbol)
            }
            types(parameter.returnTypeRef, group = parameter.symbol)
        }
    }

    fun parameter(parameter: FirValueParameterSymbol) {
        for (annotation in parameter.resolvedAnnotationsWithClassIds) {
            add(annotation, "parameter", rank = 1, group = parameter)
        }
        types(parameter.resolvedReturnTypeRef, group = parameter)
    }

    fun typeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameterRef in typeParameters) {
            val typeParameter = typeParameterRef as? FirTypeParameter ?: continue
            for (annotation in typeParameter.annotations) {
                add(annotation, "typeParameter", rank = 1, group = typeParameter.symbol)
            }
            typeParameter.bounds.forEach { types(it, group = typeParameter.symbol) }
        }
    }

    /** FIR copies constructor parameter annotations to their properties, so source offsets identify duplicates. */
    private fun add(annotation: FirAnnotation, kind: String, rank: Int, group: Any?) {
        val classId = annotation.toAnnotationClassIdSafe(session) ?: return
        if (classId !in exemptions) return
        val source = annotation.source ?: return
        if (source.kind != KtRealSourceElementKind) return
        if (seenOffsets.add(source.startOffset to source.endOffset)) {
            candidates += ExemptionCandidate(annotation, classId, kind, rank, group)
        }
    }
}
