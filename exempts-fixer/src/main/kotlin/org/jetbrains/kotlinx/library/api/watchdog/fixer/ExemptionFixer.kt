package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.nio.file.Paths
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtAnnotationsContainer
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtSecondaryConstructor
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameter

/** The outcome of fixing one file: the rewritten text, or null when nothing was applied. */
internal class FileFixResult(
    val newText: String?,
    val applied: List<AppliedFix>,
    val skipped: List<SkippedDiagnostic>,
    /** Raw-source edits, retained so tests can carry diagnostic ranges through the rewrite. */
    internal val edits: List<TextEdit> = emptyList(),
)

/**
 * Turns the watchdog diagnostics recorded for one source file into `@Intentionally*` annotations
 * with `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`, the reason that explains itself:
 * the shape stays as it is because changing it would break existing users. Missing documentation
 * is no shape, so an undocumented declaration gets a placeholder KDoc instead.
 *
 * All edit offsets are computed against the original text and applied from the end of the file
 * backwards, so earlier edits never invalidate later ones.
 */
internal class ExemptionFixer(private val parser: KotlinFileParser) {

    fun fix(
        filePath: String,
        originalText: String,
        diagnostics: List<RecordedDiagnostic>,
        rewriteLocations: Boolean = true,
    ): FileFixResult {
        // PSI only parses `\n`-normalized text, so targets are resolved on the normalized form
        // and every insertion offset is mapped back to the original text before applying: the
        // file's existing line endings survive untouched, whatever mix they are.
        val mapping = LineEndingMapping.of(originalText)
        val text = mapping.normalizedText
        val ktFile = parser.parse(Paths.get(filePath).fileName.toString(), text)

        val skipped = mutableListOf<PlannedSkip>()
        val plannedFixes = mutableMapOf<FixTarget, MutableMap<String, PlannedFix>>()

        for ((name, _, recordedStartOffset) in diagnostics.sortedWith(compareBy({ it.startOffset }, { it.name }))) {
            val sourceOffset = recordedStartOffset.coerceAtLeast(0)
            val startOffset = mapping.toNormalizedOffset(sourceOffset)

            fun skip(reason: String) {
                skipped += PlannedSkip(name, sourceOffset, reason)
            }

            val fix = when (val resolution = ExemptionRegistry.resolutionFor(name)) {
                is FixResolution.Unfixable -> {
                    skip(resolution.reason)
                    continue
                }
                is FixResolution.Fixable -> resolution.fix
            }

            if (startOffset !in text.indices) {
                skip("The recorded source position is outside the file. It was probably edited during the build")
                continue
            }

            val element = ktFile.findElementAt(startOffset) ?: run {
                skip("No source element found at the recorded position")
                continue
            }
            val target = resolveTarget(element, fix.targetStrategy, ktFile) ?: run {
                skip("No declaration accepting ${fix.describe()} encloses the reported position")
                continue
            }
            if (target.alreadyHas(fix)) {
                skip("The target already carries ${fix.describe()}")
                continue
            }

            plannedFixes.getOrPut(target) { mutableMapOf() }
                .getOrPut(fix.id) { PlannedFix(fix) }
                .reports.getOrPut(name) { sourceOffset }
        }
        plannedFixes.values.forEach { it.collapseCombinedExemptions() }

        if (plannedFixes.isEmpty()) {
            val locations = RewrittenLocations(originalText, originalText, emptyList())
            return FileFixResult(
                newText = null,
                applied = emptyList(),
                skipped = skipped.map { it.toDiagnostic(filePath, locations) },
            )
        }

        val imports = ImportResolver(ktFile)
        val edits = mutableListOf<TextEdit>()
        val applied = mutableListOf<PlannedFix>()
        for ((target, fixesById) in plannedFixes) {
            // The KDoc goes on top, then the annotations in name order.
            val fixes = fixesById.values.sortedWith(compareBy({ it.fix !is PlaceholderKdocFix }, { it.fix.id }))
            edits += target.render(fixes, text, imports)
            applied += fixes
        }
        edits += imports.importInsertions(ktFile)

        val rawEdits = edits.map {
            TextEdit(
                mapping.toRawOffset(it.offset),
                mapping.toRawOffset(it.endOffset),
                it.text.replace("\n", mapping.newline),
            )
        }
        val newText = applyEdits(originalText, rawEdits)
        val locations = if (rewriteLocations) {
            RewrittenLocations(originalText, newText, rawEdits)
        } else {
            RewrittenLocations(originalText, originalText, emptyList())
        }
        return FileFixResult(
            newText = newText,
            applied = applied.flatMap { planned ->
                planned.reports.map { (diagnostic, sourceOffset) ->
                    AppliedFix(
                        diagnostic,
                        (planned.fix as? ExemptionFix)?.annotationShortName,
                        filePath,
                        locations.lineAt(sourceOffset),
                    )
                }
            },
            skipped = skipped.map { it.toDiagnostic(filePath, locations) },
            edits = rawEdits,
        )
    }
}

/**
 * One annotation or KDoc to add to a target, with the diagnostics it resolves. [reports] maps
 * each diagnostic name to the first recorded position that led to this fix: a diagnostic
 * reported several times on one target (once per constructor, say) is applied once.
 */
private class PlannedFix(val fix: Fix) {
    val reports: MutableMap<String, Int> = linkedMapOf()
}

private fun Fix.describe(): String = when (this) {
    is ExemptionFix -> "@$annotationShortName"
    PlaceholderKdocFix -> "a KDoc"
}

/**
 * Replaces every complete group of individual annotations with its combined annotation, so a
 * class missing `equals`, `hashCode`, and `toString` alike gets the one
 * `@IntentionallyWithoutEqualsHashCodeOrToString` instead of three annotations.
 */
private fun MutableMap<String, PlannedFix>.collapseCombinedExemptions() {
    for ((combined, parts) in ExemptionRegistry.combinedExemptions) {
        if (!keys.containsAll(parts)) continue
        val individual = parts.map { getValue(it) }
        val merged = PlannedFix(
            ExemptionFix(combined, individual.first().fix.targetStrategy, hasReasonParameter = true)
        )
        individual.forEach { planned ->
            planned.reports.forEach { (diagnostic, offset) -> merged.reports.getOrPut(diagnostic) { offset } }
        }
        parts.forEach { remove(it) }
        put(combined, merged)
    }
}

private class PlannedSkip(val diagnostic: String, val sourceOffset: Int, val reason: String) {
    fun toDiagnostic(filePath: String, locations: RewrittenLocations) =
        SkippedDiagnostic(diagnostic, filePath, locations.lineAt(sourceOffset), reason)
}

/** Maps an original source position to its line after all edits have been applied. */
private class RewrittenLocations(
    private val originalText: String,
    rewrittenText: String,
    private val edits: List<TextEdit>,
) {
    private val rewrittenMapping = LineEndingMapping.of(rewrittenText)

    fun lineAt(sourceOffset: Int): Int {
        val boundedOffset = sourceOffset.coerceIn(0, originalText.length)
        val rewrittenOffset = boundedOffset + edits.sumOf { edit ->
            // An edit ending at the diagnostic offset is in front of the original source token
            // too. One that covers the offset replaced the token itself, and its own start, which
            // is where the replacement begins, stands in for it.
            if (edit.endOffset <= boundedOffset) edit.lengthDelta else 0
        }
        val normalizedOffset = rewrittenMapping.toNormalizedOffset(rewrittenOffset.coerceAtLeast(0))
        return rewrittenMapping.normalizedText.take(normalizedOffset).count { it == '\n' } + 1
    }
}

/**
 * One text change: [text] replaces the original `[offset, endOffset)` range. A pure insertion has
 * an empty range, a pure removal has empty [text]. Edits never overlap.
 */
internal class TextEdit(val offset: Int, val endOffset: Int, val text: String) {
    /** An insertion: [text] goes in front of whatever is at [offset]. */
    constructor(offset: Int, text: String) : this(offset, offset, text)

    /** How much the edit moves everything that follows it. */
    val lengthDelta: Int get() = text.length - (endOffset - offset)
}

/**
 * Applies edits back to front so offsets stay valid. Edits starting at the same offset are applied
 * widest first, so an insertion lands in front of a replacement instead of inside it, and equally
 * wide ones in reverse text order, which leaves them in text order in the result.
 */
internal fun applyEdits(text: String, edits: List<TextEdit>): String {
    val builder = StringBuilder(text)
    edits
        .sortedWith(
            compareByDescending<TextEdit> { it.offset }
                .thenByDescending { it.endOffset }
                .thenByDescending { it.text }
        )
        .forEach { builder.replace(it.offset, it.endOffset, it.text) }
    return builder.toString()
}

/** Where a group of fixes goes. */
private sealed interface FixTarget {
    fun alreadyAnnotated(annotationShortName: String): Boolean

    /** Whether the target already has a KDoc, so a placeholder would only get in its way. */
    fun alreadyDocumented(): Boolean = false

    fun alreadyHas(fix: Fix): Boolean = when (fix) {
        is ExemptionFix -> alreadyAnnotated(fix.annotationShortName)
        PlaceholderKdocFix -> alreadyDocumented()
    }

    /**
     * The edits inserting [fixes], which come KDoc first. Fixes sharing an insertion point go
     * into one edit, so their order is the list's and not the tie-break of [applyEdits].
     */
    fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit>

    /**
     * A declaration annotated in front of its first token, keeping its KDoc on top. A
     * placeholder KDoc lands in the same spot, above the annotations: a declaration that has a
     * KDoc never receives one.
     */
    data class BeforeDeclaration(val declaration: KtDeclaration) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            declaration.hasAnnotationNamed(annotationShortName)

        override fun alreadyDocumented() = declaration.docComment != null

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit> {
            var anchor: PsiElement = declaration
            var child = declaration.firstChild
            while (child is PsiComment || child is PsiWhiteSpace) {
                child = child.nextSibling
            }
            if (child != null) {
                anchor = child
            }

            val anchorOffset = anchor.textRange.startOffset
            val lineStart = text.lastIndexOf('\n', anchorOffset - 1) + 1
            val indent = text.substring(lineStart, anchorOffset)
            return if (indent.isBlank()) {
                // The declaration starts its line: stack one line above it per fix.
                listOf(TextEdit(lineStart, fixes.joinToString("") { indent + it.insertionText(imports) + "\n" }))
            } else {
                // Mid-line declarations (an explicit primary constructor, a one-line member) get
                // the fixes inline, directly in front.
                listOf(TextEdit(anchorOffset, fixes.joinToString("") { it.insertionText(imports) + " " }))
            }
        }
    }

    /** An element annotated inline: a value parameter or a type parameter. */
    data class InlineBefore(val element: KtElement) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            (element as? KtDeclaration)?.hasAnnotationNamed(annotationShortName) ?: false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit> =
            listOf(TextEdit(element.textRange.startOffset, fixes.joinToString("") { it.insertionText(imports) + " " }))
    }

    /**
     * The annotation the diagnostic was reported on: the annotation itself is what the diagnostic
     * objects to, so the exemption replaces it in place, keeping its position and indent. When the
     * annotated declaration already carries the exemption, the reported annotation simply goes.
     */
    data class ReplacedAnnotation(val entry: KtAnnotationEntry) : FixTarget {
        /** Inside an `@[First Second]` list the entries carry no `@` of their own. */
        private val list: KtAnnotation? get() = entry.parent as? KtAnnotation

        /** The exemption never sits on the reported annotation, so it always replaces it. */
        override fun alreadyAnnotated(annotationShortName: String) = false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit> {
            val owner = entry.annotatedDeclaration()
            val missing = fixes.filterNot { owner?.hasAnnotationNamed(it.exemption.annotationShortName) == true }
            if (missing.isEmpty()) {
                return listOf(removal(text))
            }

            val replacement = missing.joinToString(" ") {
                if (list == null) it.annotationText(imports) else it.annotationCall(imports)
            }
            return listOf(TextEdit(entry.textRange.startOffset, entry.textRange.endOffset, replacement))
        }

        /** Removes the annotation along with the whitespace it would leave behind. */
        private fun removal(text: String): TextEdit {
            // The last entry of a list takes its brackets with it: `@[]` is not valid Kotlin.
            val removed = list?.takeIf { it.entries.size == 1 } ?: entry
            val start = removed.textRange.startOffset
            val end = removed.textRange.endOffset
            val lineStart = text.lastIndexOf('\n', start - 1) + 1
            val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
            return if (text.substring(lineStart, start).isBlank() && text.substring(end, lineEnd).isBlank()) {
                // The annotation had a line of its own, which goes with it.
                TextEdit(lineStart, lineEnd, "")
            } else {
                TextEdit(start, end + text.substring(end).takeWhile { it == ' ' || it == '\t' }.length, "")
            }
        }
    }

    /**
     * A primary constructor with no `constructor` keyword to annotate: the keyword is introduced
     * together with the annotations, in one insertion in front of the parameter list.
     */
    data class ImplicitPrimaryConstructor(val constructor: KtPrimaryConstructor) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            constructor.hasAnnotationNamed(annotationShortName)

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit> {
            val annotations = fixes.joinToString(" ") { it.insertionText(imports) }
            return listOf(TextEdit(constructor.textRange.startOffset, " $annotations constructor"))
        }
    }

    /** The whole file, annotated with a `@file:` annotation above the package directive. */
    data class WholeFile(val file: KtFile) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            file.fileAnnotationList?.hasAnnotationNamed(annotationShortName) ?: false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit> {
            val annotationList = file.fileAnnotationList
            if (annotationList != null) {
                return fixes.map {
                    TextEdit(annotationList.textRange.startOffset, it.annotationText(imports, useSite = "file:") + "\n")
                }
            }
            val anchorOffset = file.packageDirective?.takeIf { it.textLength > 0 }?.textRange?.startOffset
                ?: run {
                    var child = file.firstChild
                    while (child is PsiComment || child is PsiWhiteSpace) child = child.nextSibling
                    child?.textRange?.startOffset ?: 0
                }

            return fixes.map {
                TextEdit(anchorOffset, it.annotationText(imports, useSite = "file:") + "\n\n")
            }
        }
    }
}

/** The text of the fix as it stands on its own: an annotation with its `@`, or a KDoc. */
private fun PlannedFix.insertionText(imports: ImportResolver, useSite: String = ""): String = when (fix) {
    is ExemptionFix -> annotationText(imports, useSite)
    PlaceholderKdocFix -> "/** ${PlaceholderKdocFix.TEXT} */"
}

private fun PlannedFix.annotationText(imports: ImportResolver, useSite: String = ""): String =
    "@$useSite${annotationCall(imports)}"

/** The fix as an annotation. Only annotations replace annotations or sit on a file. */
private val PlannedFix.exemption: ExemptionFix
    get() = fix as? ExemptionFix ?: error("${fix.id} is not an exemption annotation")

/** The annotation without its `@`, the form the entries of an `@[First Second]` list take. */
private fun PlannedFix.annotationCall(imports: ImportResolver): String {
    val fix = exemption
    val name = imports.reference(fix.annotationShortName)
    val arguments = if (fix.hasReasonParameter) {
        "(reason = ${imports.reference(ExemptionRegistry.REASON_CLASS)}.${ExemptionRegistry.REASON_ENTRY})"
    } else {
        ""
    }
    return "$name$arguments"
}

private fun KtAnnotationsContainer.hasAnnotationNamed(shortName: String): Boolean =
    annotationEntries.any { it.shortName?.asString() == shortName }

private fun KtAnnotated.hasAnnotationNamed(shortName: String): Boolean =
    annotationEntries.any { it.shortName?.asString() == shortName }

/** The declaration an annotation entry is attached to. */
private fun KtAnnotationEntry.annotatedDeclaration(): KtAnnotated? =
    parent.ancestorsWithSelf().filterIsInstance<KtDeclaration>().firstOrNull()

private fun resolveTarget(element: PsiElement, strategy: TargetStrategy, ktFile: KtFile): FixTarget? =
    when (strategy) {
        TargetStrategy.CONTAINING_FILE -> FixTarget.WholeFile(ktFile)

        TargetStrategy.ENCLOSING_CLASS ->
            element.ancestorsWithSelf()
                .filterIsInstance<KtClassOrObject>()
                .firstOrNull { it !is KtEnumEntry }
                ?.let { FixTarget.BeforeDeclaration(it) }

        TargetStrategy.ENCLOSING_ANNOTATION_CLASS ->
            element.ancestorsWithSelf()
                .filterIsInstance<KtClass>()
                .firstOrNull { it.isAnnotation() }
                ?.let { FixTarget.BeforeDeclaration(it) }

        TargetStrategy.REPORTED_DECLARATION -> element.resolveDeclarationTarget()

        TargetStrategy.DOCUMENTED_DECLARATION -> element.resolveDocumentedTarget()

        TargetStrategy.REPORTED_ANNOTATION ->
            element.ancestorsWithSelf()
                .filterIsInstance<KtAnnotationEntry>()
                .firstOrNull()
                ?.let { FixTarget.ReplacedAnnotation(it) }

        TargetStrategy.ENCLOSING_CALLABLE -> element.resolveCallableTarget()

        TargetStrategy.ENCLOSING_FUNCTION_OR_CONSTRUCTOR -> element.resolveFunctionOrConstructorTarget()
    }

/**
 * The declaration the diagnostic was reported on. Enum entries are unfixable: no exemption
 * annotation applies to the `ENUM_ENTRY` target. A property compiled from a `val`/`var` primary
 * constructor parameter is annotated on the parameter, where the annotation lands on the
 * property it creates.
 */
private fun PsiElement.resolveDeclarationTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            is KtEnumEntry -> return null
            is KtParameter -> if (candidate.hasValOrVar()) return FixTarget.InlineBefore(candidate)
            is KtPropertyAccessor -> return FixTarget.BeforeDeclaration(candidate.property)
            is KtPrimaryConstructor -> return candidate.asTarget()
            is KtNamedFunction, is KtSecondaryConstructor, is KtProperty, is KtClassOrObject, is KtTypeAlias ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
        }
    }
    return null
}

/**
 * The declaration whose KDoc the diagnostic misses. Unlike [resolveDeclarationTarget], enum
 * entries are documented like any declaration, and a `val`/`var` primary constructor parameter
 * takes its KDoc the way a declaration does: on a line of its own when the parameter has one.
 * The primary constructor itself is described by the class KDoc and has no KDoc of its own.
 */
private fun PsiElement.resolveDocumentedTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            is KtParameter -> return if (candidate.hasValOrVar()) FixTarget.BeforeDeclaration(candidate) else null
            is KtPropertyAccessor -> return FixTarget.BeforeDeclaration(candidate.property)
            is KtPrimaryConstructor -> return null
            is KtNamedFunction, is KtSecondaryConstructor, is KtProperty, is KtClassOrObject, is KtTypeAlias ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
        }
    }
    return null
}

/**
 * The callable whose signature contains the reported element. Type parameters take their
 * annotation inline: their mutable/tuple/nullable-Boolean bounds may belong to a class, which
 * the signature-level exemption annotations don't target.
 */
private fun PsiElement.resolveCallableTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            is KtTypeParameter -> return FixTarget.InlineBefore(candidate)
            is KtParameter -> if (candidate.hasValOrVar()) return FixTarget.InlineBefore(candidate)
            is KtPropertyAccessor -> return FixTarget.BeforeDeclaration(candidate.property)
            is KtPrimaryConstructor -> return candidate.asTarget()
            is KtNamedFunction, is KtSecondaryConstructor, is KtProperty ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
            is KtClassOrObject -> return null
        }
    }
    return null
}

private fun PsiElement.resolveFunctionOrConstructorTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            is KtPrimaryConstructor -> return candidate.asTarget()
            is KtNamedFunction, is KtSecondaryConstructor ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
            is KtClass -> return candidate.primaryConstructor?.asTarget()
            is KtClassOrObject -> return null
        }
    }
    return null
}

private fun KtPrimaryConstructor.asTarget(): FixTarget =
    if (getConstructorKeyword() != null) {
        FixTarget.BeforeDeclaration(this)
    } else {
        FixTarget.ImplicitPrimaryConstructor(this)
    }

private fun PsiElement.ancestorsWithSelf(): Sequence<PsiElement> = generateSequence(this) { it.parent }
