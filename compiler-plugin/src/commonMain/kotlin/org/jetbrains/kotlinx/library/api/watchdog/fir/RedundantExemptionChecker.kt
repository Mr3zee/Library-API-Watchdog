package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnostic
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithSource
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirAnnotationContainer
import org.jetbrains.kotlin.fir.FirModuleData
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
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeAliasSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.FirFunctionTypeRef
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.FirTypeProjectionWithVariance
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.FirUserTypeRef
import org.jetbrains.kotlin.fir.types.coneTypeOrNull
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.forEachType
import org.jetbrains.kotlin.name.ClassId

/** The side of an `expect`/`actual` pair that a checker reports on. */
internal enum class PairSide {
    /** The checker skips `actual` declarations, so only the `expect` reads an exemption of the `expect`. */
    EXPECT,

    /** The checker skips `expect` declarations and reads an exemption of the `expect` through the `actual`. */
    ACTUAL,

    /** The checker reports on both sides and reads an exemption of the `expect` on both. */
    BOTH,
}

internal interface ExemptingChecker {
    val exemptions: Set<ClassId>

    /** The subset of [exemptions] that a class applies to all its members. */
    val enclosingClassExemptions: Set<ClassId> get() = emptySet()

    val readsExemptionOnSiblings: Boolean get() = false

    /** Decides whether the verdict on an exemption of an `expect` needs the `actual` declarations. */
    val pairSide: PairSide
}

/**
 * [disabled] holds the exempting checkers of the same category that `NONE` turns off. An exemption
 * that only they read is redundant.
 *
 * A copy of an `expect` exemption on an `actual` ([expectOrigins]) isn't judged while its original
 * exists. [ExpectExemptionRedundancyChecker] judges the original.
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
        val origins = declaration.expectOrigins()

        val baselines = HashMap<FirDeclarationChecker<D>, Set<ReportedDiagnosticKey>>()
        for (candidate in candidates) {
            if (origins.any { it.originalsOf(candidate).isNotEmpty() }) continue
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

/** [position] maps a position on the `actual` to the same place on [expect], or to null. */
private class ExpectOrigin(
    val expect: FirMemberDeclaration,
    val candidates: List<ExemptionCandidate>,
    val position: (String) -> String?,
) {
    fun originalsOf(copy: ExemptionCandidate): List<ExemptionCandidate> {
        val expected = position(copy.position) ?: return emptyList()
        return candidates.filter { it.classId == copy.classId && it.position == expected }
    }
}

/**
 * An exemption on an `actual` at the position of an exemption of its `expect` is a copy, because the
 * compiler reports `ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT` without it. A `val` parameter of an `actual`
 * constructor copies both the `expect` body property and the `expect` constructor parameter.
 */
@OptIn(SymbolInternals::class)
context(context: CheckerContext)
private fun FirDeclaration.expectOrigins(): List<ExpectOrigin> = buildList {
    val expect = matchedExpect() as? FirMemberDeclaration
    if (expect != null) add(ExpectOrigin(expect, expect.exemptionCandidates(allExemptions)) { it })
    val parameter = (this@expectOrigins as? FirProperty)?.correspondingValueParameterFromPrimaryConstructor ?: return@buildList
    val constructor = parameter.containingDeclarationSymbol.fir as? FirConstructor ?: return@buildList
    val index = constructor.valueParameters.indexOfFirst { it.symbol == parameter }
    val expectConstructor = constructor.matchedExpect() as? FirConstructor ?: return@buildList
    add(ExpectOrigin(expectConstructor, expectConstructor.exemptionCandidates(allExemptions)) { position ->
        when (position) {
            "declaration" -> "parameter:$index"
            "return/type" -> "parameter:$index/type"
            else -> null
        }
    })
}

/** Null for an `actual typealias`, because the compiler compares the `expect` annotations with the expanded class. */
@OptIn(SymbolInternals::class)
private fun FirDeclaration.matchedExpect(): FirDeclaration? {
    if ((this as? FirMemberDeclaration)?.isActual != true) return null
    val expect = symbol.getSingleMatchedExpectForActualOrNull() ?: return null
    expect.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
    val fir = expect.fir
    val sameKind = when (this) {
        is FirRegularClass -> fir is FirRegularClass
        is FirNamedFunction -> fir is FirNamedFunction
        is FirConstructor -> fir is FirConstructor
        is FirProperty -> fir is FirProperty
        else -> false
    }
    return fir.takeIf { sameKind }
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

        // A copy of an `expect` class exemption isn't judged while its original exists.
        val expectExemptions = (declaration.matchedExpect() as? FirRegularClass)?.let { probes.classExemptions(it) }.orEmpty()
        for (exemption in probes.classExemptions(declaration)) {
            if (expectExemptions.any { it.classId == exemption.classId }) continue
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
 * Judges the exemptions of `expect` declarations. A checker with [PairSide.EXPECT] decides on the
 * `expect` alone. A checker with [PairSide.ACTUAL] or [PairSide.BOTH] reads the exemption through the
 * `actual` declarations. So this checker runs in the platform session, where the symbol provider finds
 * them. Without a visible `actual`, such an exemption stays. The common compilation and the IDE report
 * only what the `expect` decides, because the IDE runs every checker in the module's own session.
 *
 * A copy of the exemption on an `actual` ([expectOrigins]) is never reported while its original exists.
 * [recorder] gets the copies of a redundant original as [WatchdogDiagnosticsRecorder.REDUNDANT_COPY]
 * lines, so the fixer removes them with the original.
 */
internal class ExpectExemptionRedundancyChecker(
    basicCheckers: Collection<FirBasicDeclarationChecker>,
    functionCheckers: Collection<FirFunctionChecker>,
    callableCheckers: Collection<FirCallableDeclarationChecker>,
    classCheckers: Collection<FirClassChecker>,
    typeAliasCheckers: Collection<FirTypeAliasChecker>,
    private val disabled: Collection<ExemptingChecker>,
    private val enclosing: EnclosingExemptionProbes,
    private val recorder: WatchdogDiagnosticsRecorder? = null,
    private val scopeOf: (FirModuleData) -> CheckScope = { CheckScope.ALL },
    private val platformOnly: (ExemptingChecker) -> Boolean = { false },
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
        if (declaration !is FirMemberDeclaration || !declaration.hasProbedShape() || !declaration.isExpectedDeclaration()) return
        val scope = scopeOf(declaration.moduleData)
        // The `actual` declarations differ for each platform, so each compilation records its own verdicts.
        if (scope == CheckScope.NONE && recorder == null) return
        val candidates = declaration.exemptionCandidates(allExemptions)
        val classExemptions = (declaration as? FirRegularClass)?.let { enclosing.classExemptions(it) }.orEmpty()
        if (candidates.isEmpty() && classExemptions.isEmpty()) return

        val output = if (scope == CheckScope.NONE) recordingOnly(reporter, recorder!!) else reporter
        val actuals: List<FirDeclaration>? by lazy { declaration.findActuals() }
        fun reportsHere(verdict: Verdict): Boolean = scope == CheckScope.ALL || verdict == Verdict.REDUNDANT_HERE
        for (candidate in candidates) {
            val verdict = declaration.verdict(candidate, candidates) { actuals }
            if (verdict == null || verdict == Verdict.NEEDED) continue
            if (reportsHere(verdict)) {
                context(context, output) { reportRedundantExemption(candidate.annotation, candidate.kind) }
            }
            recordRedundantCopies(candidate, candidate.kind) { actuals }
        }
        if (declaration !is FirRegularClass) return
        for (exemption in classExemptions) {
            val verdict = declaration.classVerdict(exemption) { actuals }
            if (verdict == null || verdict == Verdict.NEEDED) continue
            if (reportsHere(verdict)) {
                context(context, output) { reportRedundantExemption(exemption.annotation, "classAndMembers") }
            }
            recordRedundantClassCopies(exemption) { actuals }
        }
    }

    private enum class Verdict { NEEDED, REDUNDANT_FOR_OWNER, REDUNDANT_HERE }

    private fun redundantVerdictOf(checkers: Collection<ExemptingChecker>): Verdict =
        if (checkers.all(platformOnly)) Verdict.REDUNDANT_HERE else Verdict.REDUNDANT_FOR_OWNER

    private fun recordingOnly(reporter: DiagnosticReporter, recorder: WatchdogDiagnosticsRecorder): DiagnosticReporter =
        delegatingDiagnosticReporter(reporter) { diagnostic, diagnosticContext ->
            if (diagnostic != null && !diagnosticContext.isDiagnosticSuppressed(diagnostic)) {
                recorder.record(diagnostic, diagnosticContext)
            }
        }

    /** Whether the `expect` needs [candidate]. Null means unknown. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun FirMemberDeclaration.verdict(
        candidate: ExemptionCandidate,
        candidates: List<ExemptionCandidate>,
        actuals: () -> List<FirDeclaration>?,
    ): Verdict? {
        val readers = probes.filter { it.accepts(this) && candidate.isReadBy(it.exempting, this) }
        if (readers.isEmpty()) {
            val disabledReaders = disabled.filter { candidate.isReadBy(it, this) }
            return if (disabledReaders.isEmpty()) null else redundantVerdictOf(disabledReaders)
        }
        if (readers.any { it.checker.needs(this, candidate, candidates, it.checker.probedDiagnostics(this)) }) return Verdict.NEEDED
        if (readers.all { it.exempting.pairSide == PairSide.EXPECT }) return redundantVerdictOf(readers.map { it.exempting })
        // Other checkers read an `actual typealias` and the property of a `val` constructor parameter.
        val needed = actuals()?.any { actual ->
            (listOf(actual) + actual.valParameterProperty(candidate)).any { probed ->
                probes.any {
                    it.accepts(probed) && candidate.isReadBy(it.exempting, probed) && it.checker.needsOnActual(probed, candidate)
                }
            }
        } ?: return null
        return if (needed) Verdict.NEEDED else Verdict.REDUNDANT_HERE
    }

    /** Records the copies of [candidate] on the visible `actual` declarations. */
    context(context: CheckerContext)
    private fun recordRedundantCopies(candidate: ExemptionCandidate, kind: String, actuals: () -> List<FirDeclaration>?) {
        val recorder = recorder ?: return
        for (actual in actuals().orEmpty()) {
            for (probed in listOf(actual) + actual.valParameterProperty(candidate)) {
                val origins = probed.expectOrigins()
                for (copy in probed.exemptionCandidates(setOf(candidate.classId))) {
                    val isCopy = origins.any { origin ->
                        origin.originalsOf(copy).any { it.annotation.sameSourceAs(candidate.annotation) }
                    }
                    if (isCopy) recorder.recordCopy(probed, copy.annotation, candidate.classId, kind)
                }
            }
        }
    }

    context(context: CheckerContext)
    private fun recordRedundantClassCopies(exemption: EnclosingExemptionProbes.ClassExemption, actuals: () -> List<FirDeclaration>?) {
        val recorder = recorder ?: return
        for (actualClass in actuals().orEmpty().filterIsInstance<FirRegularClass>()) {
            for (copy in actualClass.annotations) {
                if (copy.toAnnotationClassIdSafe(context.session) == exemption.classId) {
                    recorder.recordCopy(actualClass, copy, exemption.classId, "classAndMembers")
                }
            }
        }
    }

    private fun WatchdogDiagnosticsRecorder.recordCopy(actual: FirDeclaration, copy: FirAnnotation, classId: ClassId, kind: String) {
        val path = actual.symbol.sourceFilePath() ?: return
        val source = copy.source ?: return
        recordRedundantCopy(
            path,
            source,
            listOf(
                classId.shortClassName.asString(),
                WatchdogDiagnosticMessages.parameterValueFor(WatchdogDiagnostics.REDUNDANT_EXEMPTION.name, kind),
            ),
        )
    }

    /** The property of the `val` parameter at the position of [candidate]. */
    @OptIn(DirectDeclarationsAccess::class, SymbolInternals::class)
    private fun FirDeclaration.valParameterProperty(candidate: ExemptionCandidate): List<FirProperty> {
        if (this !is FirConstructor || !candidate.position.startsWith("parameter:")) return emptyList()
        val index = candidate.position.substringAfter(':').substringBefore('/').toIntOrNull() ?: return emptyList()
        val parameter = valueParameters.getOrNull(index)?.symbol ?: return emptyList()
        val constructedClass = (getContainingClassSymbol() as? FirRegularClassSymbol)?.fir ?: return emptyList()
        return constructedClass.declarations.filterIsInstance<FirProperty>()
            .filter { it.correspondingValueParameterFromPrimaryConstructor == parameter }
    }

    /** [verdict] for a class-level exemption that covers the members. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun FirRegularClass.classVerdict(
        exemption: EnclosingExemptionProbes.ClassExemption,
        actuals: () -> List<FirDeclaration>?,
    ): Verdict? {
        if (exemption.probes.isEmpty()) return redundantVerdictOf(disabled.filter { exemption.classId in it.enclosingClassExemptions })
        val ignored = buildList {
            add(exemption.annotation)
            enclosing.collectNestedExemptions(this@classVerdict, exemption.classId, this)
        }
        if (enclosing.isNeeded(this, ignored, exemption.probes)) return Verdict.NEEDED
        if (exemption.probes.all { it.exempting.pairSide == PairSide.EXPECT }) return redundantVerdictOf(exemption.probes.map { it.exempting })
        val actualClasses = actuals()?.filterIsInstance<FirRegularClass>() ?: return null
        val ignoredWithCopies = buildList {
            addAll(ignored)
            for (actualClass in actualClasses) {
                actualClass.annotations.filterTo(this) { it.toAnnotationClassIdSafe(context.session) == exemption.classId }
                enclosing.collectNestedExemptions(actualClass, exemption.classId, this)
            }
        }
        val needed = actualClasses.any { enclosing.isNeeded(it, ignoredWithCopies, exemption.probes, actual = true) }
        return if (needed) Verdict.NEEDED else Verdict.REDUNDANT_HERE
    }

    /** Whether [actual] needs [candidate] from its `expect` when its own copies of the exemption are gone. */
    @OptIn(SymbolInternals::class)
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun FirDeclarationChecker<FirDeclaration>.needsOnActual(actual: FirDeclaration, candidate: ExemptionCandidate): Boolean {
        actual.symbol.lazyResolveToPhase(FirResolvePhase.BODY_RESOLVE)
        val ignored = buildList {
            add(candidate.annotation)
            actual.exemptionCandidates(setOf(candidate.classId)).mapTo(this) { it.annotation }
            // A `val` parameter of the `actual` constructor holds the copy of a parameter exemption.
            val index = candidate.position.takeIf { it.startsWith("parameter:") }?.substringAfter(':')?.substringBefore('/')?.toIntOrNull()
            val parameter = (actual as? FirConstructor)?.valueParameters?.getOrNull(index ?: -1)
            if (parameter != null) {
                val collector = ExemptionCandidateCollector(context.session, setOf(candidate.classId), fromLibrary = false)
                collector.parameters(listOf(parameter), "parameter")
                collector.candidates.mapTo(this) { it.annotation }
            }
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
        val outerClassId = when (this) {
            is FirClassLikeDeclaration -> symbol.classId.outerClassId
            is FirCallableDeclaration -> getContainingClassSymbol()?.classId
            else -> null
        }
        // The members of the class that an `actual typealias` expands to don't inherit the exemptions.
        if (generateSequence(outerClassId) { it.outerClassId }.any { outer ->
                (provider.getClassLikeSymbolByClassId(outer) as? FirTypeAliasSymbol)?.isActual == true
            }
        ) {
            return emptyList()
        }
        val found: List<FirBasedSymbol<*>> = when (this) {
            is FirClassLikeDeclaration -> listOfNotNull(provider.getClassLikeSymbolByClassId(symbol.classId))
            is FirCallableDeclaration -> {
                val containingClass = getContainingClassSymbol()
                if (containingClass == null) {
                    val callableId = symbol.callableId ?: return null
                    provider.getTopLevelCallableSymbols(callableId.packageName, callableId.callableName)
                } else {
                    val actualClassLike = provider.getClassLikeSymbolByClassId(containingClass.classId) ?: return null
                    if (!actualClassLike.isActualOf(containingClass)) return null
                    val actualClass = actualClassLike as? FirRegularClassSymbol ?: return null
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

private val EnclosingExemptionProbes.Probe.exempting: ExemptingChecker get() = checker as ExemptingChecker

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
 * [position]: the place of the annotation in its declaration, the same in an `expect` and its `actual`.
 */
private class ExemptionCandidate(
    val annotation: FirAnnotation,
    val classId: ClassId,
    val kind: String,
    val rank: Int,
    val group: Any?,
    val position: String,
)

/** A class-level exemption that covers the members belongs to [EnclosingExemptionProbes]. */
private fun ExemptionCandidate.isReadBy(exempting: ExemptingChecker, declaration: FirDeclaration): Boolean =
    classId in exempting.exemptions &&
            !(declaration is FirRegularClass && rank == 0 && classId in exempting.enclosingClassExemptions)

private fun ExemptionCandidate.covers(other: ExemptionCandidate): Boolean =
    other.rank > rank && (group == null || group === other.group) && classId == other.classId

private val allExemptions: Set<ClassId> =
    WatchdogClassIds.exemptionAnnotations +
            WatchdogClassIds.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility +
            WatchdogClassIds.IntentionallyExposedInternalApiForBackwardsCompatibility

context(context: CheckerContext)
private fun FirDeclaration.exemptionCandidates(exemptions: Set<ClassId>): List<ExemptionCandidate> {
    val collector = ExemptionCandidateCollector(context.session, exemptions, fromLibrary = source == null)
    when (this) {
        is FirFile -> collector.annotations(this, "file")
        is FirRegularClass -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            superTypeRefs.forEachIndexed { index, it -> collector.types(it, position = "supertype:$index") }
            collector.parameters(contextParameters, "context")
        }
        is FirTypeAlias -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            collector.types(expandedTypeRef, position = "expanded")
        }
        is FirNamedFunction -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            collector.types(receiverParameter?.typeRef, position = "receiver")
            collector.types(returnTypeRef, position = "return")
            collector.parameters(contextParameters, "context")
            collector.parameters(valueParameters, "parameter")
        }
        is FirConstructor -> {
            collector.annotations(this, "declaration")
            collector.parameters(contextParameters, "context")
            val propertyParameters = if (isPrimary) propertyParameterSymbols(this) else emptySet()
            collector.parameters(valueParameters, "parameter") { it.symbol !in propertyParameters }
        }
        is FirProperty -> {
            collector.annotations(this, "declaration")
            collector.typeParameters(typeParameters)
            collector.types(receiverParameter?.typeRef, position = "receiver")
            collector.parameters(contextParameters, "context")
            // The positions match the body property of an `expect` class.
            val constructorParameter = correspondingValueParameterFromPrimaryConstructor
            if (constructorParameter != null) {
                collector.parameter(constructorParameter)
            } else {
                collector.types(returnTypeRef, position = "return")
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

private class ExemptionCandidateCollector(
    private val session: FirSession,
    private val exemptions: Set<ClassId>,
    private val fromLibrary: Boolean,
) {
    val candidates = mutableListOf<ExemptionCandidate>()
    private val seenOffsets = hashSetOf<Pair<Int, Int>>()

    fun annotations(container: FirAnnotationContainer, kind: String) {
        for (annotation in container.annotations) {
            add(annotation, kind, rank = 0, group = null, position = kind)
        }
    }

    /**
     * `vararg x: Boolean` resolves to `BooleanArray` without a type argument, so the cone type has no
     * annotations of the element type. The written type reference still has them.
     *
     * All annotations in [typeRef] get one position, because one type rarely has the same exemption twice.
     */
    fun types(typeRef: FirTypeRef?, group: Any? = null, position: String) {
        typeRef?.coneTypeOrNull?.forEachType { type ->
            for (annotation in type.customAnnotations) {
                add(annotation, "typeUsage", rank = 2, group = group, position = "$position/type")
            }
        }
        writtenTypes(typeRef, group, "$position/type")
    }

    private fun writtenTypes(typeRef: FirTypeRef?, group: Any?, position: String) {
        val written = (typeRef as? FirResolvedTypeRef)?.delegatedTypeRef ?: typeRef ?: return
        for (annotation in written.annotations) {
            add(annotation, "typeUsage", rank = 2, group = group, position = position)
        }
        when (written) {
            is FirUserTypeRef -> for (part in written.qualifier) {
                for (argument in part.typeArgumentList.typeArguments) {
                    writtenTypes((argument as? FirTypeProjectionWithVariance)?.typeRef, group, position)
                }
            }
            is FirFunctionTypeRef -> {
                writtenTypes(written.receiverTypeRef, group, position)
                written.contextParameterTypeRefs.forEach { writtenTypes(it, group, position) }
                written.parameters.forEach { writtenTypes(it.returnTypeRef, group, position) }
                writtenTypes(written.returnTypeRef, group, position)
            }
            else -> {}
        }
    }

    /** [included] filters without a change to the indices, so the positions match an `expect`. */
    fun parameters(parameters: List<FirValueParameter>, prefix: String, included: (FirValueParameter) -> Boolean = { true }) {
        parameters.forEachIndexed { index, parameter ->
            if (!included(parameter)) return@forEachIndexed
            for (annotation in parameter.annotations) {
                add(annotation, "parameter", rank = 1, group = parameter.symbol, position = "$prefix:$index")
            }
            types(parameter.returnTypeRef, group = parameter.symbol, position = "$prefix:$index")
        }
    }

    fun parameter(parameter: FirValueParameterSymbol) {
        for (annotation in parameter.resolvedAnnotationsWithClassIds) {
            add(annotation, "parameter", rank = 1, group = parameter, position = "declaration")
        }
        types(parameter.resolvedReturnTypeRef, group = parameter, position = "return")
    }

    fun typeParameters(typeParameters: List<FirTypeParameterRef>) {
        typeParameters.forEachIndexed { index, typeParameterRef ->
            val typeParameter = typeParameterRef as? FirTypeParameter ?: return@forEachIndexed
            for (annotation in typeParameter.annotations) {
                add(annotation, "typeParameter", rank = 1, group = typeParameter.symbol, position = "typeParameter:$index")
            }
            typeParameter.bounds.forEach { types(it, group = typeParameter.symbol, position = "typeParameter:$index") }
        }
    }

    /** FIR copies constructor parameter annotations to their properties, so source offsets identify duplicates. */
    private fun add(annotation: FirAnnotation, kind: String, rank: Int, group: Any?, position: String) {
        val classId = annotation.toAnnotationClassIdSafe(session) ?: return
        if (classId !in exemptions) return
        val source = annotation.source
        if (source == null) {
            if (fromLibrary) candidates += ExemptionCandidate(annotation, classId, kind, rank, group, position)
            return
        }
        if (source.kind != KtRealSourceElementKind) return
        if (seenOffsets.add(source.startOffset to source.endOffset)) {
            candidates += ExemptionCandidate(annotation, classId, kind, rank, group, position)
        }
    }
}
