package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import org.jetbrains.kotlin.com.intellij.psi.PsiComment
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
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
)

/**
 * Turns the watchdog diagnostics recorded for one source file into `@Intentionally*` annotation
 * insertions with `reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY`, the reason that
 * explains itself: the shape stays as it is because changing it would break existing clients.
 *
 * All insertion offsets are computed against the original text and applied from the end of the
 * file backwards, so earlier insertions never invalidate later ones.
 */
internal class ExemptionFixer(private val parser: KotlinFileParser) {

    fun fix(filePath: String, originalText: String, diagnostics: List<RecordedDiagnostic>): FileFixResult {
        // PSI only parses `\n`-normalized text, so targets are resolved on the normalized form
        // and every insertion offset is mapped back to the original text before applying: the
        // file's existing line endings survive untouched, whatever mix they are.
        val mapping = LineEndingMapping.of(originalText)
        val text = mapping.normalizedText
        val ktFile = parser.parse(filePath.substringAfterLast('/'), text)

        val applied = mutableListOf<AppliedFix>()
        val skipped = mutableListOf<SkippedDiagnostic>()
        val plannedFixes = mutableMapOf<FixTarget, MutableMap<String, PlannedFix>>()

        for (diagnostic in diagnostics.sortedWith(compareBy({ it.startOffset }, { it.name }))) {
            val startOffset = mapping.toNormalizedOffset(diagnostic.startOffset.coerceAtLeast(0))
            val line = text.take(startOffset.coerceIn(0, text.length)).count { it == '\n' } + 1

            fun skip(reason: String) {
                skipped += SkippedDiagnostic(diagnostic.name, filePath, line, reason)
            }

            val fix = when (val resolution = ExemptionRegistry.resolutionFor(diagnostic.name)) {
                is FixResolution.Unfixable -> {
                    skip(resolution.reason)
                    continue
                }
                is FixResolution.Fixable -> resolution.fix
            }

            if (startOffset !in text.indices) {
                skip("the recorded source position is outside the file; it was probably edited during the build")
                continue
            }

            val element = ktFile.findElementAt(startOffset) ?: run {
                skip("no source element found at the recorded position")
                continue
            }
            val target = resolveTarget(element, fix.targetStrategy, ktFile) ?: run {
                skip("no declaration accepting @${fix.annotationShortName} encloses the reported position")
                continue
            }
            if (target.alreadyAnnotated(fix.annotationShortName)) {
                skip("the target already carries @${fix.annotationShortName}")
                continue
            }

            plannedFixes.getOrPut(target) { mutableMapOf() }
                .getOrPut(fix.annotationShortName) { PlannedFix(fix, diagnostic.name, line) }
        }

        if (plannedFixes.isEmpty()) {
            return FileFixResult(newText = null, applied = applied, skipped = skipped)
        }

        val imports = ImportResolver(ktFile)
        val insertions = mutableListOf<Insertion>()
        for ((target, fixesByAnnotation) in plannedFixes) {
            val fixes = fixesByAnnotation.values.sortedBy { it.fix.annotationShortName }
            insertions += target.render(fixes, text, imports)
            fixes.forEach {
                applied += AppliedFix(it.diagnostic, it.fix.annotationShortName, filePath, it.line)
            }
        }
        insertions += imports.importInsertions(ktFile)

        val rawInsertions = insertions.map {
            Insertion(mapping.toRawOffset(it.offset), it.text.replace("\n", mapping.newline))
        }
        return FileFixResult(
            newText = applyInsertions(originalText, rawInsertions),
            applied = applied,
            skipped = skipped,
        )
    }
}

private class PlannedFix(val fix: ExemptionFix, val diagnostic: String, val line: Int)

/** A pure text insertion; [text] goes in front of whatever is at [offset]. */
internal class Insertion(val offset: Int, val text: String)

/**
 * Applies insertions back to front so offsets stay valid. Insertions at the same offset are
 * applied in reverse text order, which leaves them in text order in the result.
 */
internal fun applyInsertions(text: String, insertions: List<Insertion>): String {
    val builder = StringBuilder(text)
    insertions
        .sortedWith(compareByDescending<Insertion> { it.offset }.thenByDescending { it.text })
        .forEach { builder.insert(it.offset, it.text) }
    return builder.toString()
}

/** Where a group of exemption annotations is inserted. */
private sealed interface FixTarget {
    fun alreadyAnnotated(annotationShortName: String): Boolean

    fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<Insertion>

    /** A declaration annotated in front of its first token, keeping its KDoc on top. */
    data class BeforeDeclaration(val declaration: KtDeclaration) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            declaration.hasAnnotationNamed(annotationShortName)

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<Insertion> {
            var anchor: PsiElement = declaration
            var child = declaration.firstChild
            while (child is PsiComment || child is PsiWhiteSpace) child = child.nextSibling
            if (child != null) anchor = child

            val anchorOffset = anchor.textRange.startOffset
            val lineStart = text.lastIndexOf('\n', anchorOffset - 1) + 1
            val indent = text.substring(lineStart, anchorOffset)
            return if (indent.isBlank()) {
                // The declaration starts its line: stack one annotation line above it per fix.
                fixes.map { Insertion(lineStart, indent + it.annotationText(imports) + "\n") }
            } else {
                // Mid-line declarations (an explicit primary constructor, a one-line member) get
                // the annotations inline, directly in front.
                fixes.map { Insertion(anchorOffset, it.annotationText(imports) + " ") }
            }
        }
    }

    /** An element annotated inline: a value parameter or a type parameter. */
    data class InlineBefore(val element: KtElement) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            (element as? KtDeclaration)?.hasAnnotationNamed(annotationShortName) ?: false

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<Insertion> =
            fixes.map { Insertion(element.textRange.startOffset, it.annotationText(imports) + " ") }
    }

    /**
     * A primary constructor with no `constructor` keyword to annotate: the keyword is introduced
     * together with the annotations, in one insertion in front of the parameter list.
     */
    data class ImplicitPrimaryConstructor(val constructor: KtPrimaryConstructor) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            constructor.hasAnnotationNamed(annotationShortName)

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<Insertion> {
            val annotations = fixes.joinToString(" ") { it.annotationText(imports) }
            return listOf(Insertion(constructor.textRange.startOffset, " $annotations constructor"))
        }
    }

    /** The whole file, annotated with a `@file:` annotation above the package directive. */
    data class WholeFile(val file: KtFile) : FixTarget {
        override fun alreadyAnnotated(annotationShortName: String) =
            file.fileAnnotationList?.annotationEntries.orEmpty()
                .any { it.shortName?.asString() == annotationShortName }

        override fun render(fixes: List<PlannedFix>, text: String, imports: ImportResolver): List<Insertion> {
            val annotationList = file.fileAnnotationList
            if (annotationList != null) {
                return fixes.map {
                    Insertion(annotationList.textRange.startOffset, it.annotationText(imports, useSite = "file:") + "\n")
                }
            }
            val anchorOffset = file.packageDirective?.takeIf { it.textLength > 0 }?.textRange?.startOffset
                ?: run {
                    var child = file.firstChild
                    while (child is PsiComment || child is PsiWhiteSpace) child = child.nextSibling
                    child?.textRange?.startOffset ?: 0
                }
            return fixes.map {
                Insertion(anchorOffset, it.annotationText(imports, useSite = "file:") + "\n\n")
            }
        }
    }
}

private fun PlannedFix.annotationText(imports: ImportResolver, useSite: String = ""): String {
    val name = imports.reference(fix.annotationShortName)
    val arguments = if (fix.hasReasonParameter) {
        "(reason = ${imports.reference(ExemptionRegistry.REASON_CLASS)}.${ExemptionRegistry.REASON_ENTRY})"
    } else {
        ""
    }
    return "@$useSite$name$arguments"
}

private fun KtDeclaration.hasAnnotationNamed(shortName: String): Boolean =
    annotationEntries.any { it.shortName?.asString() == shortName }

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
 * The callable whose signature contains the reported element. Type parameters take their
 * annotation inline: their mutable/tuple/nullable-Boolean bounds may belong to a class, which
 * the signature-level exemption annotations do not target.
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
