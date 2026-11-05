package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.nio.file.Paths
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/** The name of a recorded line that pairs an `actual` with its `expect`. `WatchdogDiagnosticsRecorder` gives the format. */
internal const val EXPECT_ACTUAL_PAIR = "EXPECT_ACTUAL_PAIR"

/**
 * The name of a recorded line for the copy on an `actual` of a redundant `expect` exemption. The
 * compiler reports `REDUNDANT_EXEMPTION` on the original only. The line has the fields of that
 * diagnostic, so the fixer removes the copy as one.
 */
internal const val REDUNDANT_COPY = "REDUNDANT_COPY"

/** The [REDUNDANT_COPY] lines as `REDUNDANT_EXEMPTION` diagnostics, and the other lines as they are. */
internal fun List<RecordedDiagnostic>.redundantCopiesAsRemovals(): List<RecordedDiagnostic> =
    map { if (it.name == REDUNDANT_COPY) it.copy(name = WatchdogChecks.REDUNDANT_EXEMPTION.name) else it }

/**
 * Copies each diagnostic that the fixer exempts on an `expect` to its `actual` declarations, because
 * the compiler reports `ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT` when an `actual` doesn't have an annotation
 * of its `expect`. The copy goes to the `actual` of the annotated declaration, or to its type parameter
 * with the same index. [readText] returns null for a missing file.
 */
internal class ExpectActualCopies(
    private val parser: KotlinFileParser,
    private val readText: (String) -> String?,
) {
    private val files = mutableMapOf<String, ParsedFile?>()

    private class ParsedFile(val mapping: LineEndingMapping, val ktFile: KtFile)

    private class ExpectSide(val path: String, val start: Int, val end: Int)

    /** The annotated declaration, or its type parameter at index [typeParameter]. */
    private class Place(val declaration: KtDeclaration, val typeParameter: Int?)

    fun copies(
        diagnostics: List<RecordedDiagnostic>,
        pairs: List<RecordedDiagnostic>,
        fixes: (String) -> Boolean,
    ): List<RecordedDiagnostic> {
        val pairsByExpectFile = pairs.mapNotNull { pair -> pair.expectSide()?.let { it to pair } }
            .groupBy({ it.first.path }, { it })
        return diagnostics.flatMap { diagnostic ->
            val fix = diagnostic.exemptionInPlace(fixes) ?: return@flatMap emptyList()
            val filePairs = pairsByExpectFile[diagnostic.filePath] ?: return@flatMap emptyList()
            val expectFile = parsed(diagnostic.filePath) ?: return@flatMap emptyList()
            val place = expectFile.placeOf(diagnostic, fix) ?: return@flatMap emptyList()
            val range = place.declaration.textRange
            filePairs.filter { (expect, _) ->
                expectFile.mapping.toNormalizedOffset(expect.start) == range.startOffset &&
                        expectFile.mapping.toNormalizedOffset(expect.end) == range.endOffset
            }.mapNotNull { (_, actual) -> copy(diagnostic, place, actual) }
        }
    }

    private fun RecordedDiagnostic.expectSide(): ExpectSide? {
        val (path, start, end) = messageParameters.takeIf { it.size == 3 } ?: return null
        return ExpectSide(path, start.toIntOrNull() ?: return null, end.toIntOrNull() ?: return null)
    }

    /** A file annotation or a replaced annotation has no place in the `actual`. */
    private fun RecordedDiagnostic.exemptionInPlace(fixes: (String) -> Boolean): ExemptionFix? {
        if (!fixes(name)) return null
        val fix = (ExemptionRegistry.resolutionFor(name) as? FixResolution.Fixable)?.fix as? ExemptionFix ?: return null
        return fix.takeIf {
            it.targetStrategy != TargetStrategy.CONTAINING_FILE && it.targetStrategy != TargetStrategy.REPORTED_ANNOTATION
        }
    }

    private fun ParsedFile.placeOf(diagnostic: RecordedDiagnostic, fix: ExemptionFix): Place? {
        val element = ktFile.findElementAt(mapping.toNormalizedOffset(diagnostic.startOffset)) ?: return null
        return when (val target = resolveTarget(element, fix, ktFile)) {
            is FixTarget.BeforeDeclaration -> Place(target.declaration, null)
            is FixTarget.ImplicitPrimaryConstructor -> Place(target.constructor, null)
            is FixTarget.InlineBefore -> when (val inline = target.element) {
                is KtTypeParameter -> {
                    val owner = inline.getStrictParentOfType<KtTypeParameterListOwner>() ?: return null
                    Place(owner, owner.typeParameters.indexOf(inline))
                }
                is KtDeclaration -> Place(inline, null)
                else -> null
            }
            else -> null
        }
    }

    private fun copy(diagnostic: RecordedDiagnostic, place: Place, actual: RecordedDiagnostic): RecordedDiagnostic? {
        val actualFile = parsed(actual.filePath) ?: return null
        val declaration = actualFile.declarationAt(actual.startOffset, actual.endOffset) ?: return null
        val offset = if (place.typeParameter != null) {
            (declaration as? KtTypeParameterListOwner)?.typeParameters?.getOrNull(place.typeParameter)?.textRange?.startOffset
                ?: return null
        } else {
            declaration.anchor()
        }
        val rawOffset = actualFile.mapping.toRawOffset(offset)
        return RecordedDiagnostic(diagnostic.name, actual.filePath, rawOffset, rawOffset, diagnostic.messageParameters)
    }

    private fun parsed(path: String): ParsedFile? = files.getOrPut(path) {
        val text = readText(path) ?: return@getOrPut null
        val mapping = LineEndingMapping.of(text)
        ParsedFile(mapping, parser.parse(Paths.get(path).fileName.toString(), mapping.normalizedText))
    }

    /** A recorded range starts at the KDoc or the first modifier of the declaration. */
    private fun ParsedFile.declarationAt(rawStart: Int, rawEnd: Int): KtDeclaration? {
        val start = mapping.toNormalizedOffset(rawStart)
        val end = mapping.toNormalizedOffset(rawEnd)
        val leaf = ktFile.findElementAt(start) ?: return null
        val declarations = generateSequence(leaf) { it.parent }.filterIsInstance<KtDeclaration>()
            .filter { it.textRange.startOffset == start }
            .toList()
        return declarations.firstOrNull { it.textRange.endOffset == end } ?: declarations.lastOrNull()
    }

    /** The `constructor` keyword or the name, where every target strategy finds the declaration itself. */
    private fun KtDeclaration.anchor(): Int {
        val element = when (this) {
            is KtConstructor<*> -> getConstructorKeyword() ?: valueParameterList
            is KtNamedDeclaration -> nameIdentifier
            else -> null
        } ?: this
        return element.textRange.startOffset
    }
}
