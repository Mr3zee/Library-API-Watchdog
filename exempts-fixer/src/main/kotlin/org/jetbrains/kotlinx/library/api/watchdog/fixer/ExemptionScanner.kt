package org.jetbrains.kotlinx.library.api.watchdog.fixer

import java.nio.file.Paths
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** Finds every Watchdog `@Intentionally*` annotation already applied in Kotlin sources. */
internal class ExemptionScanner(private val parser: KotlinFileParser) {
    fun scan(filePath: String, text: String): List<AppliedExemption> {
        val file = parser.parse(Paths.get(filePath).fileName.toString(), text)
        return file.collectDescendantsOfType<KtAnnotationEntry>()
            .mapNotNull { annotation ->
                val name = annotation.watchdogAnnotationName(file) ?: return@mapNotNull null
                AppliedExemption(
                    annotation = name,
                    filePath = filePath,
                    line = text.take(annotation.textOffset.coerceAtMost(text.length)).count { it == '\n' } + 1,
                )
            }
            .distinct()
            .sortedWith(compareBy({ it.annotation }, { it.line }))
    }

    private fun KtAnnotationEntry.watchdogAnnotationName(file: KtFile): String? {
        val referencedName = shortName?.asString() ?: return null
        val aliasedImport = file.importDirectives.firstOrNull { it.aliasName == referencedName }
        if (aliasedImport != null) {
            val importedName = aliasedImport.importedFqName?.asString()
                ?.removePrefix("${ExemptionRegistry.ANNOTATIONS_PACKAGE}.")
            return importedName?.takeIf { it.isIntentionallyAnnotationName() && '.' !in it }
        }
        if (!referencedName.isIntentionallyAnnotationName()) return null

        val typeText = typeReference?.text.orEmpty()
        if (typeText == "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$referencedName") return referencedName
        if (file.packageFqName.asString() == ExemptionRegistry.ANNOTATIONS_PACKAGE) return referencedName
        val imported = file.importDirectives.any { directive ->
            val importedName = directive.importedFqName?.asString()
            importedName == "${ExemptionRegistry.ANNOTATIONS_PACKAGE}.$referencedName" ||
                    directive.isAllUnder && importedName == ExemptionRegistry.ANNOTATIONS_PACKAGE
        }
        return referencedName.takeIf { imported }
    }

    private fun String.isIntentionallyAnnotationName(): Boolean = startsWith("Intentionally")
}
