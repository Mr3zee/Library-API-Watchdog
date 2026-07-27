package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import org.jetbrains.kotlin.psi.KtFile

/**
 * Chooses how inserted annotations refer to the watchdog annotation classes: through an existing
 * import (or import alias), through a newly added import, or - when the short name is taken by an
 * import from another package - through the fully qualified name inline.
 */
internal class ImportResolver(private val ktFile: KtFile) {
    private val samePackage = ktFile.packageFqName.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE

    private val wildcardImports = ktFile.importDirectives.filter { it.isAllUnder }

    /**
     * A wildcard import of the annotations package makes the short names available, but only
     * when it is the sole wildcard import: another package's wildcard could contribute the same
     * short name and turn a bare reference ambiguous, while an explicit import always wins over
     * wildcards, so in that case one is added instead.
     */
    private val wildcardImportSuffices =
        wildcardImports.any { it.importedFqName?.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE } &&
                wildcardImports.all { it.importedFqName?.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE }

    /** Imported fully qualified name to the name the file refers to it by (alias-aware). */
    private val visibleNameByFqName: Map<String, String> = buildMap {
        ktFile.importDirectives.forEach { directive ->
            if (directive.isAllUnder) return@forEach
            val fqName = directive.importedFqName?.asString() ?: return@forEach
            put(fqName, directive.aliasName ?: fqName.substringAfterLast('.'))
        }
    }

    private val takenShortNames: Set<String> = visibleNameByFqName.values.toSet()

    private val importsToAdd = sortedSetOf<String>()

    /**
     * The code reference to use for the watchdog annotation class [shortName], registering a new
     * import when one is needed.
     */
    fun reference(shortName: String): String {
        val fqName = "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$shortName"
        visibleNameByFqName[fqName]?.let { return it }
        if (samePackage || wildcardImportSuffices) return shortName
        if (fqName in importsToAdd) return shortName
        if (shortName in takenShortNames) return fqName
        importsToAdd += fqName
        return shortName
    }

    /** The insertions that add the registered imports, empty when none are needed. */
    fun importInsertions(ktFile: KtFile): List<Insertion> {
        if (importsToAdd.isEmpty()) {
            return emptyList()
        }

        val importLines = importsToAdd.joinToString("\n") { "import $it" }

        val lastImport = ktFile.importList?.imports?.lastOrNull()
        if (lastImport != null) {
            return listOf(Insertion(lastImport.textRange.endOffset, "\n$importLines"))
        }

        val packageDirective = ktFile.packageDirective?.takeIf { it.textLength > 0 }
        if (packageDirective != null) {
            return listOf(Insertion(packageDirective.textRange.endOffset, "\n\n$importLines"))
        }

        // No package and no imports: the imports open the file, before the first declaration.
        val firstDeclarationOffset = ktFile.declarations.firstOrNull()?.textRange?.startOffset ?: 0
        return listOf(Insertion(firstDeclarationOffset, "$importLines\n\n"))
    }
}
