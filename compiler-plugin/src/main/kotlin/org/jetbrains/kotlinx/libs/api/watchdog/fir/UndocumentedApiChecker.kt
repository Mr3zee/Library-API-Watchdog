package org.jetbrains.kotlinx.libs.api.watchdog.fir

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isActual
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.text

/**
 * Reports watched classifiers, type aliases, functions, properties, secondary constructors, and
 * enum entries with no KDoc token. KDoc content is not inspected beyond recognizing class-level
 * `@property` and constructor `@param` tags.
 *
 * Overrides, `actual` declarations, primary constructors, declarations visible only through
 * `@PublishedApi`, and exempt declarations are skipped.
 */
internal class UndocumentedApiChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    private companion object {
        /**
         * The same plugin jar runs both in the CLI compiler and in `kotlin-compiler-embeddable`,
         * which relocates the IntelliJ platform classes to another package. Referencing
         * `KDocTokens.KDOC` directly would hard-code the `com.intellij` field type in the bytecode
         * and fail to link in one of the two worlds, so the value is resolved reflectively. It is
         * only passed through generic signatures, which erase to `java.util.Set` and link everywhere.
         */
        @Suppress("UNCHECKED_CAST")
        private val kdocElementTypes: Set<IElementType> =
            setOf(
                Class.forName("org.jetbrains.kotlin.kdoc.lexer.KDocTokens").getField("KDOC").get(null)
            ) as Set<IElementType>
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirDeclaration) {
        val factory = severities[WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API] ?: return

        if (declaration !is FirMemberDeclaration) {
            return
        }

        val (kind, name) = declaration.watchedKindAndName(context) ?: return
        if (!declaration.isWatchedPublicSourceApi()) {
            return
        }

        if (declaration.source.hasKdoc()) {
            return
        }

        if (declaration is FirProperty && declaration.isCoveredByClassKdocTags(context)) {
            return
        }

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyUndocumented, context.session)) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = kind,
            b = name,
        )
    }

    /**
     * The declaration kind for the message and the name to report, or null when the declaration
     * is not watched: either users can't reference it directly, or its documentation lives on
     * another declaration - overrides and `actual`s inherit it, and the primary constructor is
     * described by the class KDoc. Secondary constructors report the class name because their
     * own name is the internal `<init>`.
     */
    private fun FirMemberDeclaration.watchedKindAndName(context: CheckerContext): Pair<String, Name>? = when {
        isActual -> null
        // Comparisons instead of a `when` over the enum: an exhaustive `when` compiles to an
        // `ordinal()` switch, and AnimalSniffer rejects that call against the compiler API baseline.
        this is FirRegularClass -> when {
            classKind == ClassKind.CLASS -> "class" to name
            classKind == ClassKind.INTERFACE -> "interface" to name
            classKind == ClassKind.OBJECT -> "object" to name
            classKind == ClassKind.ENUM_CLASS -> "enum class" to name
            classKind == ClassKind.ANNOTATION_CLASS -> "annotation class" to name
            else -> null
        }
        this is FirTypeAlias -> "type alias" to name
        this is FirEnumEntry -> "enum entry" to name
        this is FirNamedFunction -> if (isOverride) null else "function" to name
        this is FirProperty -> if (isOverride) null else "property" to name
        this is FirConstructor ->
            if (isPrimary) null
            else "constructor" to (context.containingClassSymbol?.classId?.shortClassName ?: symbol.name)
        else -> null
    }

    // KDoc never reaches FIR, but the source element keeps the underlying parse tree, where
    // the KDoc is a direct child of the declaration node. `getChild` traverses both source
    // representations: the light tree (CLI) and PSI (Analysis API).
    private fun KtSourceElement?.hasKdoc(): Boolean =
        this?.getChild(kdocElementTypes, index = 0, depth = 1, reverse = false) != null

    /**
     * A property with no KDoc of its own may still be documented in the containing class KDoc:
     * `@property name` covers any property of the class, and `@param name` covers a `val`/`var`
     * declared in the primary constructor.
     */
    private fun FirProperty.isCoveredByClassKdocTags(context: CheckerContext): Boolean {
        val classSource = context.containingClassSymbol?.source ?: return false
        val classKdoc = classSource.getChild(kdocElementTypes, index = 0, depth = 1, reverse = false)?.text
            ?: return false
        return classKdoc.documentsSubject("property", name) ||
            (source?.kind == KtFakeSourceElementKind.PropertyFromParameter &&
                classKdoc.documentsSubject("param", name))
    }

    /**
     * Whether this KDoc text contains a `@tag subject` block tag. KDoc stays a raw comment token
     * in the light tree, so the tags are recognized textually instead of through the KDoc parser:
     * a block tag occurs only at the start of a line, after the comment markers.
     */
    private fun CharSequence.documentsSubject(tag: String, subject: Name): Boolean =
        lineSequence().any { line ->
            val content = line.trim().removePrefix("/**").removePrefix("*").trimStart()
            if (!content.startsWith("@$tag ")) return@any false
            val documented = content.removePrefix("@$tag ").trimStart()
                .takeWhile { !it.isWhitespace() }
                .removeSurrounding("`")
            documented == subject.asString()
        }
}
