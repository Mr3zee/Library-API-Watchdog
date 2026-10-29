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
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.symbols.lazyResolveToPhase
import org.jetbrains.kotlin.fir.types.FirTypeRef
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.fir.types.forEachType
import org.jetbrains.kotlin.name.ClassId

/**
 * A checker that reads `@Intentionally*` exemption annotations. [RedundantExemptionProbingChecker]
 * and [EnclosingExemptionRedundancyChecker] report an exemption the checker doesn't need.
 */
internal interface ExemptingChecker {
    /** The exemptions the checker reads on the checked declaration, its parameters, and its signature types. */
    val exemptions: Set<ClassId>

    /** The exemptions of [exemptions] that an enclosing class carries for every member. */
    val enclosingClassExemptions: Set<ClassId> get() = emptySet()

    /**
     * Whether the checker reads the exemption on sibling declarations too. The probe then treats
     * the exemption as absent everywhere, so two siblings that exempt each other both keep theirs.
     */
    val readsExemptionOnSiblings: Boolean get() = false
}

/**
 * Runs [delegate], then runs it again for each exemption annotation of the declaration with that
 * annotation treated as absent. The annotation is redundant when the second run reports nothing new.
 *
 * The probe runs at the visit of the declaration that reads the annotation. A callable reads the
 * annotations of its parameters and signature types. A property reads the annotations of its
 * primary constructor parameter. A file reads a `@file:` annotation. An `expect` or `actual`
 * declaration isn't probed, because the checks split between them and the annotation on the
 * `expect` serves the `actual`. An exemption of [ExemptingChecker.enclosingClassExemptions] on a
 * class is the job of [EnclosingExemptionRedundancyChecker].
 *
 * When a declaration and one of its parameters or types carry the same exemption, only the
 * narrower one is redundant. The probe of the broader annotation treats the narrower ones in its
 * scope as absent too, so a fix that removes the redundant annotations keeps one of them.
 */
internal class RedundantExemptionProbingChecker<D : FirDeclaration>(
    private val delegate: FirDeclarationChecker<D>,
    private val exempting: ExemptingChecker,
) : FirDeclarationChecker<D>(delegate.mppKind) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: D) {
        val baseline = hashSetOf<ReportedDiagnosticKey>()
        val recording = delegatingDiagnosticReporter(reporter) { diagnostic, diagnosticContext ->
            if (diagnostic != null) baseline += diagnostic.key()
            reporter.report(diagnostic, diagnosticContext)
        }
        context(recording) { delegate.check(declaration) }

        if (!declaration.isProbed()) return
        val candidates = declaration.exemptionCandidates(exempting)
        for (candidate in candidates) {
            val ignored = candidates.filter { it === candidate || candidate.covers(it) }.map { it.annotation }
            val ignoredEverywhere = if (exempting.readsExemptionOnSiblings) {
                setOfNotNull(candidate.annotation.toAnnotationClassIdSafe(context.session))
            } else {
                emptySet()
            }
            if (!delegate.probeReportsNew(declaration, ignored, baseline, ignoredEverywhere)) {
                reportRedundantExemption(candidate.annotation, candidate.kind)
            }
        }
    }

    /** A declaration covers everything in its signature. A parameter or type parameter covers the types in it. */
    context(context: CheckerContext)
    private fun ExemptionCandidate.covers(other: ExemptionCandidate): Boolean =
        other.rank > rank && (group == null || group === other.group) &&
                annotation.toAnnotationClassIdSafe(context.session) == other.annotation.toAnnotationClassIdSafe(context.session)
}

/**
 * Reports a class-level exemption of [ExemptingChecker.enclosingClassExemptions] that neither the
 * class nor a member needs. A class checker runs before the member checkers. So this checker runs
 * the checkers of the exemption on the members itself, with the class on the context. The probe
 * treats every same-kind exemption inside the class as absent too, so a member that needs an
 * exemption keeps the class-level one and loses its own.
 */
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

    /** The exemptions of [classId] on the members and in their signatures, nested classes included. */
    @OptIn(DirectDeclarationsAccess::class)
    context(context: CheckerContext)
    private fun FirRegularClass.collectNestedExemptions(classId: ClassId, into: MutableList<FirAnnotation>) {
        for (member in declarations) {
            member.exemptionCandidates(setOf(classId)).mapTo(into) { it.annotation }
            if (member is FirRegularClass) {
                member.annotations.filterTo(into) { it.toAnnotationClassIdSafe(context.session) == classId }
                member.collectNestedExemptions(classId, into)
            }
        }
    }

    /** The class is a target in the current context. A member needs the class on the context. */
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
        val baseline = hashSetOf<ReportedDiagnosticKey>()
        val recording = delegatingDiagnosticReporter(reporter) { diagnostic, _ ->
            if (diagnostic != null) baseline += diagnostic.key()
        }
        context(context, recording) { checker.check(declaration) }
        return checker.probeReportsNew(declaration, ignored, baseline)
    }

    /** Like the diagnostics visitor, `dropDeclaration` goes to the context that got `addDeclaration`. */
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

/** Whether the checker reports a diagnostic outside [baseline] when the [ignored] annotations are absent. */
context(context: CheckerContext, reporter: DiagnosticReporter)
private fun <D : FirDeclaration> FirDeclarationChecker<D>.probeReportsNew(
    declaration: D,
    ignored: List<FirAnnotation>,
    baseline: Set<ReportedDiagnosticKey>,
    ignoredEverywhere: Set<ClassId> = emptySet(),
): Boolean {
    var reportsNew = false
    val collecting = delegatingDiagnosticReporter(reporter) { diagnostic, _ ->
        if (diagnostic != null && diagnostic.key() !in baseline) reportsNew = true
    }
    ExemptionProbe.ignoring(ignored, ignoredEverywhere) {
        context(context, collecting) { check(declaration) }
    }
    return reportsNew
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

/**
 * Whether the declaration owns the exemptions in its signature. The visitor also visits a value
 * parameter, a type parameter, and an accessor, but their owner reads the exemptions. The property
 * of a constructor parameter has a fake source and owns the exemptions on the parameter.
 */
context(context: CheckerContext)
private fun FirDeclaration.isProbed(): Boolean {
    if (this is FirFile) return true
    if (this !is FirRegularClass && this !is FirNamedFunction && this !is FirConstructor &&
        this !is FirProperty && this !is FirTypeAlias
    ) {
        return false
    }

    val sourceKind = source?.kind
    if (sourceKind != KtRealSourceElementKind && sourceKind != KtFakeSourceElementKind.PropertyFromParameter) {
        return false
    }

    if ((this as? FirMemberDeclaration)?.visibility == Visibilities.Local) return false
    return !isExpectedDeclaration() && !isActualizedDeclaration()
}

/**
 * [rank] orders the scopes: a declaration, then a parameter or type parameter, then a type usage.
 * [group] is the parameter or type parameter whose signature part holds the annotation, or null
 * at the declaration level.
 */
private class ExemptionCandidate(val annotation: FirAnnotation, val kind: String, val rank: Int, val group: Any?)

/**
 * The exemption annotations of [exempting] in the signature of the declaration, each with the kind
 * word of `diagnostics.json` for the annotated element. A primary constructor parameter with a
 * property belongs to the property, like in the checkers. A class-level exemption that covers the
 * members belongs to [EnclosingExemptionRedundancyChecker].
 */
context(context: CheckerContext)
private fun FirDeclaration.exemptionCandidates(exempting: ExemptingChecker): List<ExemptionCandidate> =
    exemptionCandidates(exempting.exemptions, exceptOnClass = exempting.enclosingClassExemptions)

context(context: CheckerContext)
private fun FirDeclaration.exemptionCandidates(
    exemptions: Set<ClassId>,
    exceptOnClass: Set<ClassId> = emptySet(),
): List<ExemptionCandidate> {
    val collector = ExemptionCandidateCollector(context.session, exemptions)
    when (this) {
        is FirFile -> collector.annotations(this, "file")
        is FirRegularClass -> {
            collector.annotations(this, "declaration", except = exceptOnClass)
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
            val propertyParameters = if (isPrimary) propertyParameterSymbols() else emptySet()
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

context(context: CheckerContext)
private fun propertyParameterSymbols(): Set<FirValueParameterSymbol> {
    val result = hashSetOf<FirValueParameterSymbol>()
    context.containingClassSymbol?.processAllDeclarations(context.session) { member ->
        if (member is FirPropertySymbol) {
            member.correspondingValueParameterFromPrimaryConstructor?.let(result::add)
        }
    }
    return result
}

private class ExemptionCandidateCollector(private val session: FirSession, private val exemptions: Set<ClassId>) {
    val candidates = mutableListOf<ExemptionCandidate>()
    private val seenOffsets = hashSetOf<Pair<Int, Int>>()

    fun annotations(container: FirAnnotationContainer, kind: String, except: Set<ClassId> = emptySet()) {
        for (annotation in container.annotations) {
            val classId = annotation.toAnnotationClassIdSafe(session) ?: continue
            if (classId !in exemptions || classId in except) continue
            add(annotation, kind, rank = 0, group = null)
        }
    }

    fun types(typeRef: FirTypeRef?, group: Any? = null) {
        typeRef?.coneType?.forEachType { type ->
            for (annotation in type.customAnnotations) {
                val classId = annotation.toAnnotationClassIdSafe(session) ?: continue
                if (classId in exemptions) add(annotation, "typeUsage", rank = 2, group = group)
            }
        }
    }

    fun parameters(parameters: List<FirValueParameter>) {
        for (parameter in parameters) {
            for (annotation in parameter.annotations) {
                val classId = annotation.toAnnotationClassIdSafe(session) ?: continue
                if (classId in exemptions) add(annotation, "parameter", rank = 1, group = parameter.symbol)
            }
            types(parameter.returnTypeRef, group = parameter.symbol)
        }
    }

    fun parameter(parameter: FirValueParameterSymbol) {
        for (annotation in parameter.resolvedAnnotationsWithClassIds) {
            val classId = annotation.toAnnotationClassIdSafe(session) ?: continue
            if (classId in exemptions) add(annotation, "parameter", rank = 1, group = parameter)
        }
        types(parameter.resolvedReturnTypeRef, group = parameter)
    }

    fun typeParameters(typeParameters: List<FirTypeParameterRef>) {
        for (typeParameterRef in typeParameters) {
            val typeParameter = typeParameterRef as? FirTypeParameter ?: continue
            for (annotation in typeParameter.annotations) {
                val classId = annotation.toAnnotationClassIdSafe(session) ?: continue
                if (classId in exemptions) add(annotation, "typeParameter", rank = 1, group = typeParameter.symbol)
            }
            typeParameter.bounds.forEach { types(it, group = typeParameter.symbol) }
        }
    }

    /** The annotation copy on the property of a constructor parameter has a fake source, so the parameter's wins. */
    private fun add(annotation: FirAnnotation, kind: String, rank: Int, group: Any?) {
        val source = annotation.source ?: return
        if (source.kind != KtRealSourceElementKind) return
        if (seenOffsets.add(source.startOffset to source.endOffset)) {
            candidates += ExemptionCandidate(annotation, kind, rank, group)
        }
    }
}
