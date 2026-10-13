package org.jetbrains.kotlinx.library.api.watchdog.fixer

import org.jetbrains.kotlin.psi.KtFile

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

    fun reference(shortName: String): String {
        val fqName = "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$shortName"
        visibleNameByFqName[fqName]?.let { return it }
        if (samePackage || wildcardImportSuffices) return shortName
        if (fqName in importsToAdd) return shortName
        if (shortName in takenShortNames) return fqName
        importsToAdd += fqName
        return shortName
    }

    fun importInsertions(ktFile: KtFile): List<TextEdit> {
        if (importsToAdd.isEmpty()) {
            return emptyList()
        }

        val importLines = importsToAdd.joinToString("\n") { "import $it" }

        val lastImport = ktFile.importList?.imports?.lastOrNull()
        if (lastImport != null) {
            return listOf(TextEdit(lastImport.textRange.endOffset, "\n$importLines"))
        }

        val packageDirective = ktFile.packageDirective?.takeIf { it.textLength > 0 }
        if (packageDirective != null) {
            return listOf(TextEdit(packageDirective.textRange.endOffset, "\n\n$importLines"))
        }

        val firstDeclarationOffset = ktFile.declarations.firstOrNull()?.textRange?.startOffset ?: 0
        return listOf(TextEdit(firstDeclarationOffset, "$importLines\n\n"))
    }
}
