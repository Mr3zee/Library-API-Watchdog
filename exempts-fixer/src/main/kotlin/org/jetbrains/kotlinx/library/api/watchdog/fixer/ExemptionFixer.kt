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

internal class FileFixResult(
    /** Null when no fix applies. */
    val newText: String?,
    val applied: List<AppliedFix>,
    val skipped: List<SkippedDiagnostic>,
    val warnings: List<WarningDiagnostic> = emptyList(),
    internal val edits: List<TextEdit> = emptyList(),
)

/** Adds the fix of [ExemptionRegistry] for each diagnostic recorded in one file. */
internal class ExemptionFixer(private val parser: KotlinFileParser) {

    /**
     * @param fixes whether the fixer fixes the named diagnostic. A diagnostic it doesn't fix goes
     *   into [FileFixResult.warnings].
     */
    fun fix(
        filePath: String,
        originalText: String,
        diagnostics: List<RecordedDiagnostic>,
        rewriteLocations: Boolean = true,
        fixes: (String) -> Boolean = { true },
    ): FileFixResult {
        val mapping = LineEndingMapping.of(originalText)
        val text = mapping.normalizedText
        val ktFile = parser.parse(Paths.get(filePath).fileName.toString(), text)

        val skipped = mutableListOf<PlannedSkip>()
        val warnings = mutableListOf<PlannedWarning>()
        val plannedFixes = mutableMapOf<FixTarget, MutableMap<String, PlannedFix>>()

        for ((name, _, recordedStartOffset) in diagnostics.sortedWith(compareBy({ it.startOffset }, { it.name }))) {
            val sourceOffset = recordedStartOffset.coerceAtLeast(0)
            val startOffset = mapping.toNormalizedOffset(sourceOffset)

            fun skip(reason: String) {
                skipped += PlannedSkip(name, sourceOffset, reason)
            }

            if (!fixes(name)) {
                warnings += PlannedWarning(name, sourceOffset)
                continue
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
                warnings = warnings.map { it.toDiagnostic(filePath, locations) },
            )
        }

        val imports = ImportResolver(ktFile)
        val edits = mutableListOf<TextEdit>()
        val applied = mutableListOf<PlannedFix>()
        for ((target, fixesById) in plannedFixes) {
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
            warnings = warnings.map { it.toDiagnostic(filePath, locations) },
            edits = rawEdits,
        )
    }
}

private class PlannedFix(val fix: Fix) {
    /** The first recorded offset of each diagnostic name. */
    val reports: MutableMap<String, Int> = linkedMapOf()
}

private fun Fix.describe(): String = when (this) {
    is ExemptionFix -> "@$annotationShortName"
    PlaceholderKdocFix -> "a KDoc"
}

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

private class PlannedWarning(val diagnostic: String, val sourceOffset: Int) {
    fun toDiagnostic(filePath: String, locations: RewrittenLocations) =
        WarningDiagnostic(diagnostic, filePath, locations.lineAt(sourceOffset))
}

private class RewrittenLocations(
    private val originalText: String,
    rewrittenText: String,
    private val edits: List<TextEdit>,
) {
    private val rewrittenMapping = LineEndingMapping.of(rewrittenText)

    fun lineAt(sourceOffset: Int): Int {
        val boundedOffset = sourceOffset.coerceIn(0, originalText.length)
        val rewrittenOffset = boundedOffset + edits.sumOf { edit ->
            // An edit that ends at the offset is in front of the token. An edit that covers the
            // offset replaced the token, so the start of the edit is the new offset.
            if (edit.endOffset <= boundedOffset) edit.lengthDelta else 0
        }
        val normalizedOffset = rewrittenMapping.toNormalizedOffset(rewrittenOffset.coerceAtLeast(0))
        return rewrittenMapping.normalizedText.take(normalizedOffset).count { it == '\n' } + 1
    }
}

/** [text] replaces the range `[offset, endOffset)`. Edits never overlap. */
internal class TextEdit(val offset: Int, val endOffset: Int, val text: String) {
    constructor(offset: Int, text: String) : this(offset, offset, text)

    val lengthDelta: Int get() = text.length - (endOffset - offset)
}

/**
 * Applies the edits back to front. At one offset the widest edit goes first, so an insertion
 * goes in front of a replacement and not inside it.
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

private sealed interface FixTarget {
    fun alreadyAnnotated(annotationShortName: String): Boolean

    fun alreadyDocumented(): Boolean = false

    fun alreadyHas(fix: Fix): Boolean = when (fix) {
        is ExemptionFix -> alreadyAnnotated(fix.annotationShortName)
        PlaceholderKdocFix -> alreadyDocumented()
    }

    /** Fixes that share an insertion point go into one edit, so [applyEdits] can't reorder them. */
    fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit>

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
                listOf(TextEdit(lineStart, fixes.joinToString("") { indent + it.insertionText(imports) + "\n" }))
            } else {
                listOf(TextEdit(anchorOffset, fixes.joinToString("") { it.insertionText(imports) + " " }))
            }
        }
    }

    data class InlineBefore(val element: KtElement) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            (element as? KtDeclaration)?.hasAnnotationNamed(annotationShortName) ?: false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit> =
            listOf(TextEdit(element.textRange.startOffset, fixes.joinToString("") { it.insertionText(imports) + " " }))
    }

    /** The exemption replaces the reported annotation. */
    data class ReplacedAnnotation(val entry: KtAnnotationEntry) : FixTarget {
        /** The entries of an `@[First Second]` list have no `@` of their own. */
        private val list: KtAnnotation? get() = entry.parent as? KtAnnotation

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

        private fun removal(text: String): TextEdit {
            // `@[]` isn't valid Kotlin, so the last entry of a list takes the brackets with it.
            val removed = list?.takeIf { it.entries.size == 1 } ?: entry
            val start = removed.textRange.startOffset
            val end = removed.textRange.endOffset
            val lineStart = text.lastIndexOf('\n', start - 1) + 1
            val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it + 1 }
            return if (text.substring(lineStart, start).isBlank() && text.substring(end, lineEnd).isBlank()) {
                TextEdit(lineStart, lineEnd, "")
            } else {
                TextEdit(start, end + text.substring(end).takeWhile { it == ' ' || it == '\t' }.length, "")
            }
        }
    }

    data class ImplicitPrimaryConstructor(val constructor: KtPrimaryConstructor) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            constructor.hasAnnotationNamed(annotationShortName)

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<TextEdit> {
            val annotations = fixes.joinToString(" ") { it.insertionText(imports) }
            return listOf(TextEdit(constructor.textRange.startOffset, " $annotations constructor"))
        }
    }

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

private fun PlannedFix.insertionText(imports: ImportResolver, useSite: String = ""): String = when (fix) {
    is ExemptionFix -> annotationText(imports, useSite)
    PlaceholderKdocFix -> "/** ${PlaceholderKdocFix.TEXT} */"
}

private fun PlannedFix.annotationText(imports: ImportResolver, useSite: String = ""): String =
    "@$useSite${annotationCall(imports)}"

private val PlannedFix.exemption: ExemptionFix
    get() = fix as? ExemptionFix ?: error("${fix.id} is not an exemption annotation")

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

private fun PsiElement.resolveDeclarationTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            // No exemption annotation has the `ENUM_ENTRY` target.
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

private fun PsiElement.resolveDocumentedTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            is KtParameter -> return if (candidate.hasValOrVar()) FixTarget.BeforeDeclaration(candidate) else null
            is KtPropertyAccessor -> return FixTarget.BeforeDeclaration(candidate.property)
            // The class KDoc documents the primary constructor.
            is KtPrimaryConstructor -> return null
            is KtNamedFunction, is KtSecondaryConstructor, is KtProperty, is KtClassOrObject, is KtTypeAlias ->
                return FixTarget.BeforeDeclaration(candidate as KtDeclaration)
        }
    }
    return null
}

private fun PsiElement.resolveCallableTarget(): FixTarget? {
    for (candidate in ancestorsWithSelf()) {
        when (candidate) {
            // A type parameter of a class has no enclosing callable, so the annotation goes inline.
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
