package org.jetbrains.kotlinx.library.api.watchdog.fixer

import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective

internal class ImportResolver(private val ktFile: KtFile) {
    private val samePackage = ktFile.packageFqName.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE

    private val wildcardImports = ktFile.importDirectives.filter { it.isAllUnder }

    /** Another wildcard import can bring the same short name, so only a sole wildcard suffices. */
    private val wildcardImportSuffices =
        wildcardImports.any { it.importedFqName?.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE } &&
                wildcardImports.all { it.importedFqName?.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE }

    private val visibleNameByFqName: Map<String, String> = buildMap {
        ktFile.importDirectives.forEach { directive ->
            if (directive.isAllUnder) return@forEach
            val fqName = directive.importedFqName?.asString() ?: return@forEach
            put(fqName, directive.aliasName ?: fqName.substringAfterLast('.'))
        }
    }

    private val takenShortNames: Set<String> = visibleNameByFqName.values.toSet()

    private val importsToAdd = sortedSetOf<String>()

    private val referencedFqNames = hashSetOf<String>()

    fun references(shortName: String): Boolean =
        "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$shortName" in referencedFqNames

    fun reference(shortName: String): String {
        val fqName = "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$shortName"
        referencedFqNames += fqName
        visibleNameByFqName[fqName]?.let { return it }
        if (samePackage || wildcardImportSuffices) return shortName
        if (fqName in importsToAdd) return shortName
        if (shortName in takenShortNames) return fqName
        importsToAdd += fqName
        return shortName
    }

    fun importEdits(unusedImports: List<KtImportDirective>, text: String): List<TextEdit> {
        val edits = mutableListOf<TextEdit>()
        val unused = unusedImports.sortedBy { it.textRange.startOffset }
        val remaining = ktFile.importDirectives.filter { it !in unused }
        if (unused.isNotEmpty() && remaining.isEmpty()) {
            val start = unused.first().textRange.startOffset
            var end = text.lineEndAfter(unused.last().textRange.endOffset)
            if (importsToAdd.isEmpty() && end < text.length && text[end] == '\n') end++
            edits += TextEdit(start, end, "")
        } else {
            unused.mapTo(edits) { TextEdit(it.textRange.startOffset, text.lineEndAfter(it.textRange.endOffset), "") }
        }

        if (importsToAdd.isNotEmpty()) {
            val importLines = importsToAdd.joinToString("\n") { "import $it" }
            edits += when {
                remaining.isNotEmpty() -> TextEdit(remaining.last().textRange.endOffset, "\n$importLines")
                // The insertion at the offset of the removal goes in front of it.
                unused.isNotEmpty() -> TextEdit(unused.first().textRange.startOffset, "$importLines\n")
                else -> {
                    val packageDirective = ktFile.packageDirective?.takeIf { it.textLength > 0 }
                    if (packageDirective != null) {
                        TextEdit(packageDirective.textRange.endOffset, "\n\n$importLines")
                    } else {
                        TextEdit(ktFile.declarations.firstOrNull()?.textRange?.startOffset ?: 0, "$importLines\n\n")
                    }
                }
            }
        }
        return edits
    }

    private fun String.lineEndAfter(offset: Int): Int = indexOf('\n', offset).let { if (it < 0) length else it + 1 }
}
