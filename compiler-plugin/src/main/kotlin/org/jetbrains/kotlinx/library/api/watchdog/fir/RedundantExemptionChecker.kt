package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
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
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeAliasChecker
import org.jetbrains.kotlin.fir.declarations.DirectDeclarationsAccess
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
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
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.declarations.utils.correspondingValueParameterFromPrimaryConstructor
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.getContainingClassSymbol
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
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

/**
 * [disabled] holds the exempting checkers of the same category that `NONE` turns off. An exemption
 * that only they read is redundant.
 *
 * A cache across visits can hold FIR declarations and their sessions on IDE pooled threads.
 */
internal class RedundantExemptionProbingChecker<D : FirDeclaration>(
    private val delegates: List<FirDeclarationChecker<D>>,
    private val disabled: List<ExemptingChecker> = emptyList(),
) : FirDeclarationChecker<D>(delegates.firstOrNull()?.mppKind ?: MppCheckerKind.Common) {
    init {
        require(delegates.all { it is ExemptingChecker && it.mppKind == mppKind }) {
            "Every delegate is an ExemptingChecker of the same MppCheckerKind"
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: D) {
        delegates.forEach { it.check(declaration) }

        // The actuals of an expect declaration are visible from the platform session only.
        if (declaration.isExpectedDeclaration() || !declaration.hasProbedShape()) return
        val candidates = declaration.exemptionCandidates(allExemptions)
        if (candidates.isEmpty()) return

        val baselines = HashMap<FirDeclarationChecker<D>, Set<ReportedDiagnosticKey>>()
        for (candidate in candidates) {
            val readers = delegates.filter { candidate.isReadBy(it as ExemptingChecker, declaration) }
            if (readers.isEmpty() && disabled.none { candidate.isReadBy(it, declaration) }) continue
            val needed = readers.any { reader ->
                val baseline = baselines.getOrPut(reader) { reader.probedDiagnostics(declaration) }
                reader.needs(declaration, candidate, candidates, baseline)
            }
            if (!needed) reportRedundantExemption(candidate.annotation, candidate.kind)
        }
    }
}

/** The member checkers that read a class-level exemption, by exemption. */
internal class EnclosingExemptionProbes(
    basicCheckers: Collection<FirBasicDeclarationChecker>,
    functionCheckers: Collection<FirFunctionChecker>,
    callableCheckers: Collection<FirCallableDeclarationChecker>,
    disabled: Collection<ExemptingChecker>,
) {
    class Probe(val checker: FirDeclarationChecker<FirDeclaration>, val accepts: (FirDeclaration) -> Boolean)

    class ClassExemption(val annotation: FirAnnotation, val classId: ClassId, val probes: List<Probe>)

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

    private val disabledExemptions: Set<ClassId> = disabled.flatMapTo(hashSetOf()) { it.enclosingClassExemptions }

    val isEmpty: Boolean get() = probesByExemption.isEmpty() && disabledExemptions.isEmpty()

    /** The class-level exemptions of [declaration]. An exemption without probes belongs to a check that is off. */
    context(context: CheckerContext)
    fun classExemptions(declaration: FirRegularClass): List<ClassExemption> = buildList {
        for (annotation in declaration.annotations) {
            if (annotation.source?.kind != KtRealSourceElementKind) continue
            val classId = annotation.toAnnotationClassIdSafe(context.session) ?: continue
            val probes = probesByExemption[classId]
            if (probes != null) {
                add(ClassExemption(annotation, classId, probes))
            } else if (classId in disabledExemptions) {
                add(ClassExemption(annotation, classId, emptyList()))
            }
        }
    }

    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContext)
    fun collectNestedExemptions(declaration: FirRegularClass, classId: ClassId, into: MutableList<FirAnnotation>) {
        for (member in declaration.declarations) {
            // The IDE can visit a class before its members resolve.
            member.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
            member.exemptionCandidates(setOf(classId)).mapTo(into) { it.annotation }
            if (member is FirRegularClass) {
                member.annotations.filterTo(into) { it.toAnnotationClassIdSafe(context.session) == classId }
                collectNestedExemptions(member, classId, into)
            }
        }
    }

    /** [actual] marks a probe of an `actual` class. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    fun isNeeded(declaration: FirRegularClass, ignored: List<FirAnnotation>, probes: List<Probe>, actual: Boolean = false): Boolean {
        if (probes.any { it.reportsNew(declaration, ignored, actual) }) return true
        val provider = context as? CheckerContextForProvider ?: return true
        return provider.withDeclaration(declaration) { inner ->
            context(inner, reporter) { membersNeed(declaration, ignored, probes, actual) }
        }
    }

    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContextForProvider, reporter: DiagnosticReporter)
    private fun membersNeed(declaration: FirRegularClass, ignored: List<FirAnnotation>, probes: List<Probe>, actual: Boolean): Boolean {
        for (member in declaration.declarations) {
            val needed = when (member) {
                is FirRegularClass -> {
                    member.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
                    probes.any { it.reportsNew(member, ignored, actual) } ||
                            context.withDeclaration(member) { inner ->
                                context(inner, reporter) { membersNeed(member, ignored, probes, actual) }
                            }
                }
                is FirConstructor, is FirNamedFunction, is FirProperty -> {
                    member.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
                    probes.any { it.reportsNew(member, ignored, actual) }
                }
                else -> false
            }
            if (needed) return true
        }
        return false
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun Probe.reportsNew(declaration: FirDeclaration, ignored: List<FirAnnotation>, actual: Boolean): Boolean {
        if (!accepts(declaration)) return false
        val baseline = checker.probedDiagnostics(declaration, actual = actual)
        return checker.probedDiagnostics(declaration, ignored, actual = actual).any { it !in baseline }
    }
}

/** Class checkers run before member checkers, so this checker probes members itself. */
internal class EnclosingExemptionRedundancyChecker(
    private val probes: EnclosingExemptionProbes,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (probes.isEmpty || declaration !is FirRegularClass) return
        if (declaration.isExpectedDeclaration() || !declaration.hasProbedShape()) return

        for (exemption in probes.classExemptions(declaration)) {
            if (exemption.probes.isNotEmpty()) {
                val ignored = buildList {
                    add(exemption.annotation)
                    probes.collectNestedExemptions(declaration, exemption.classId, this)
                }
                if (probes.isNeeded(declaration, ignored, exemption.probes)) continue
            }
            reportRedundantExemption(exemption.annotation, "classAndMembers")
        }
    }
}

/**
 * Judges the exemptions of `expect` declarations. A check that reports on the `actual` declaration
 * reads them through the `actual`. So the checker runs in the platform session, where the symbol
 * provider finds the `actual` declarations to probe. Without a visible `actual`, the exemption stays.
 *
 * When the `actual` duplicates an exemption of the `expect`, the duplicate is the redundant one.
 */
internal class ExpectExemptionRedundancyChecker(
    basicCheckers: Collection<FirBasicDeclarationChecker>,
    functionCheckers: Collection<FirFunctionChecker>,
    callableCheckers: Collection<FirCallableDeclarationChecker>,
    classCheckers: Collection<FirClassChecker>,
    typeAliasCheckers: Collection<FirTypeAliasChecker>,
    private val disabled: Collection<ExemptingChecker>,
    private val enclosing: EnclosingExemptionProbes,
) : FirBasicDeclarationChecker(MppCheckerKind.Platform) {
    private val probes: List<EnclosingExemptionProbes.Probe> = buildList {
        fun <D : FirDeclaration> register(checkers: Collection<FirDeclarationChecker<D>>, accepts: (FirDeclaration) -> Boolean) {
            for (checker in checkers) {
                if (checker !is ExemptingChecker) continue
                @Suppress("UNCHECKED_CAST")
                add(EnclosingExemptionProbes.Probe(checker as FirDeclarationChecker<FirDeclaration>, accepts))
            }
        }
        register(basicCheckers) { true }
        register(functionCheckers) { it is FirFunction }
        register(callableCheckers) { it is FirCallableDeclaration }
        register(classCheckers) { it is FirClass }
        register(typeAliasCheckers) { it is FirTypeAlias }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        if (declaration !is FirMemberDeclaration || !declaration.isExpectedDeclaration() || !declaration.hasProbedShape()) {
            return
        }
        val candidates = declaration.exemptionCandidates(allExemptions)
        val classExemptions = (declaration as? FirRegularClass)?.let { enclosing.classExemptions(it) }.orEmpty()
        if (candidates.isEmpty() && classExemptions.isEmpty()) return

        val actuals: List<FirDeclaration>? by lazy { declaration.findActuals() }

        val baselines = HashMap<FirDeclarationChecker<FirDeclaration>, Set<ReportedDiagnosticKey>>()
        for (candidate in candidates) {
            val readers = probes.filter {
                it.accepts(declaration) && candidate.isReadBy(it.checker as ExemptingChecker, declaration)
            }
            if (readers.isEmpty()) {
                if (disabled.any { candidate.isReadBy(it, declaration) }) {
                    reportRedundantExemption(candidate.annotation, candidate.kind)
                }
                continue
            }
            val neededOnExpect = readers.any { reader ->
                val baseline = baselines.getOrPut(reader.checker) { reader.checker.probedDiagnostics(declaration) }
                reader.checker.needs(declaration, candidate, candidates, baseline)
            }
            if (neededOnExpect) continue
            val actualDeclarations = actuals ?: continue
            val neededOnActual = actualDeclarations.any { actual ->
                readers.any { it.accepts(actual) && it.checker.needsOnActual(actual, candidate) }
            }
            if (!neededOnActual) reportRedundantExemption(candidate.annotation, candidate.kind)
        }

        if (declaration !is FirRegularClass) return
        for (exemption in classExemptions) {
            if (exemption.probes.isEmpty()) {
                reportRedundantExemption(exemption.annotation, "classAndMembers")
                continue
            }
            val actualClasses = actuals?.filterIsInstance<FirRegularClass>() ?: continue
            val ignored = buildList {
                add(exemption.annotation)
                enclosing.collectNestedExemptions(declaration, exemption.classId, this)
                for (actualClass in actualClasses) {
                    actualClass.annotations.filterTo(this) {
                        it.toAnnotationClassIdSafe(context.session) == exemption.classId
                    }
                    enclosing.collectNestedExemptions(actualClass, exemption.classId, this)
                }
            }
            if (enclosing.isNeeded(declaration, ignored, exemption.probes)) continue
            if (actualClasses.any { enclosing.isNeeded(it, ignored, exemption.probes, actual = true) }) continue
            reportRedundantExemption(exemption.annotation, "classAndMembers")
        }
    }

    /** Whether [actual] needs [candidate] from its `expect` when its own duplicates of the exemption are gone. */
    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun FirDeclarationChecker<FirDeclaration>.needsOnActual(actual: FirDeclaration, candidate: ExemptionCandidate): Boolean {
        actual.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
        val ignored = buildList {
            add(candidate.annotation)
            actual.exemptionCandidates(setOf(candidate.classId)).mapTo(this) { it.annotation }
        }
        val ignoredEverywhere = if ((this as ExemptingChecker).readsExemptionOnSiblings) setOf(candidate.classId) else emptySet()
        val containingClass = (actual as? FirCallableDeclaration)?.getContainingClassSymbol()?.fir as? FirRegularClass
        val provider = context as? CheckerContextForProvider
        if (containingClass == null || provider == null) {
            return probedDiagnostics(actual, ignored, ignoredEverywhere, actual = true)
                .any { it !in probedDiagnostics(actual, actual = true) }
        }
        return provider.withDeclaration(containingClass) { inner ->
            context(inner, reporter) {
                probedDiagnostics(actual, ignored, ignoredEverywhere, actual = true)
                    .any { it !in probedDiagnostics(actual, actual = true) }
            }
        }
    }

    /** The `actual` declarations that the session's symbol provider matches to this `expect`, or null without one. */
    @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
    context(context: CheckerContext)
    private fun FirMemberDeclaration.findActuals(): List<FirDeclaration>? {
        val provider = context.session.symbolProvider
        val found: List<FirBasedSymbol<*>> = when (this) {
            is FirClassLikeDeclaration -> listOfNotNull(provider.getClassLikeSymbolByClassId(symbol.classId))
            is FirCallableDeclaration -> {
                val containingClass = getContainingClassSymbol()
                if (containingClass == null) {
                    val callableId = symbol.callableId ?: return null
                    provider.getTopLevelCallableSymbols(callableId.packageName, callableId.callableName)
                } else {
                    val actualClass = provider.getClassLikeSymbolByClassId(containingClass.classId) as? FirRegularClassSymbol
                        ?: return null
                    if (!actualClass.isActualOf(containingClass)) return null
                    actualClass.fir.declarations.map { it.symbol }
                }
            }
            else -> return null
        }
        return found.filter { it.isActualOf(symbol) }.map { it.fir }.takeIf { it.isNotEmpty() }
    }

    @OptIn(SymbolInternals::class)
    private fun FirBasedSymbol<*>.isActualOf(expect: FirBasedSymbol<*>): Boolean {
        val isActual = when (this) {
            is FirClassLikeSymbol<*> -> isActual
            is FirCallableSymbol<*> -> isActual
            else -> false
        }
        if (!isActual) return false
        lazyResolveToPhase(FirResolvePhase.EXPECT_ACTUAL_MATCHING)
        val matched = getSingleMatchedExpectForActualOrNull() ?: return false
        return matched == expect || matched.fir === expect.fir
    }
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
    actual: Boolean = false,
): Set<ReportedDiagnosticKey> {
    val reported = hashSetOf<ReportedDiagnosticKey>()
    val collecting = delegatingDiagnosticReporter(reporter) { diagnostic, _ ->
        if (diagnostic != null) reported += diagnostic.key()
    }
    ExemptionProbe.probing(ignored, ignoredEverywhere, actual) {
        context(context, collecting) { check(declaration) }
    }
    return reported
}

/** Whether this checker reports less on [declaration] without [candidate] and the candidates it covers. */
context(context: CheckerContext, reporter: DiagnosticReporter)
private fun <D : FirDeclaration> FirDeclarationChecker<D>.needs(
    declaration: D,
    candidate: ExemptionCandidate,
    candidates: List<ExemptionCandidate>,
    baseline: Set<ReportedDiagnosticKey>,
): Boolean {
    val ignored = candidates.filter { it === candidate || candidate.covers(it) }.map { it.annotation }
    val ignoredEverywhere = if ((this as ExemptingChecker).readsExemptionOnSiblings) setOf(candidate.classId) else emptySet()
    return probedDiagnostics(declaration, ignored, ignoredEverywhere).any { it !in baseline }
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

private fun FirDeclaration.hasProbedShape(): Boolean {
    if (this is FirFile) return true
    if (this !is FirRegularClass && this !is FirNamedFunction && this !is FirConstructor &&
        this !is FirProperty && this !is FirTypeAlias
    ) {
        return false
    }
    val sourceKind = source?.kind
    // Constructor properties own their parameter's exemptions.
    return sourceKind == KtRealSourceElementKind || sourceKind == KtFakeSourceElementKind.PropertyFromParameter
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

/** A class-level exemption that covers the members belongs to [EnclosingExemptionProbes]. */
private fun ExemptionCandidate.isReadBy(exempting: ExemptingChecker, declaration: FirDeclaration): Boolean =
    classId in exempting.exemptions &&
            !(declaration is FirRegularClass && rank == 0 && classId in exempting.enclosingClassExemptions)

private fun ExemptionCandidate.covers(other: ExemptionCandidate): Boolean =
    other.rank > rank && (group == null || group === other.group) && classId == other.classId

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
