package org.jetbrains.kotlinx.libs.api.watchdog.fir

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.caches.createCache
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirEnumEntry
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
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
    session: FirSession,
    severities: WatchdogDiagnosticSeverities,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    private val factory = severities[WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API]

    private val kdocBySource = session.firCachesFactory.createCache<KtSourceElement, KtSourceElement?> { source ->
        source.getChild(kdocElementTypes, index = 0, depth = 1, reverse = false)
    }

    private val classKdocTagsByKdoc = session.firCachesFactory.createCache<KtSourceElement, ClassKdocTags> { kdoc ->
        kdoc.text?.parseClassKdocTags() ?: ClassKdocTags(emptySet(), emptySet())
    }

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
        val factory = factory ?: return

        if (declaration !is FirMemberDeclaration) {
            return
        }

        val kind = declaration.watchedKind() ?: return
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
            b = declaration.watchedName(context) ?: return,
        )
    }

    /**
     * The declaration kind for the message, or null when the declaration is not watched: either
     * users can't reference it directly, or its documentation lives on another declaration -
     * overrides and `actual`s inherit it, and the primary constructor is described by class KDoc.
     */
    private fun FirMemberDeclaration.watchedKind(): String? = when {
        isActual -> null
        // Comparisons instead of a `when` over the enum: an exhaustive `when` compiles to an
        // `ordinal()` switch, and AnimalSniffer rejects that call against the compiler API baseline.
        this is FirRegularClass -> when {
            classKind == ClassKind.CLASS -> "class"
            classKind == ClassKind.INTERFACE -> "interface"
            classKind == ClassKind.OBJECT -> "object"
            classKind == ClassKind.ENUM_CLASS -> "enum class"
            classKind == ClassKind.ANNOTATION_CLASS -> "annotation class"
            else -> null
        }
        this is FirTypeAlias -> "type alias"
        this is FirEnumEntry -> "enum entry"
        this is FirFunction && isNamedFunction() -> if (isOverride) null else "function"
        this is FirProperty -> if (isOverride) null else "property"
        this is FirConstructor -> if (isPrimary) null else "constructor"
        else -> null
    }

    /** Secondary constructors report the class name instead of their internal `<init>` name. */
    private fun FirMemberDeclaration.watchedName(context: CheckerContext): Name? = when (this) {
        is FirRegularClass -> name
        is FirTypeAlias -> name
        is FirEnumEntry -> name
        is FirFunction if isNamedFunction() -> namedFunctionName
        is FirProperty -> name
        is FirConstructor -> context.containingClassSymbol?.classId?.shortClassName ?: symbol.name
        else -> null
    }

    // KDoc never reaches FIR, but the source element keeps the underlying parse tree, where
    // the KDoc is a direct child of the declaration node. `getChild` traverses both source
    // representations: the light tree (CLI) and PSI (Analysis API).
    private fun KtSourceElement?.kdoc(): KtSourceElement? = this?.let(kdocBySource::getValue)

    private fun KtSourceElement?.hasKdoc(): Boolean = kdoc() != null

    /**
     * A property with no KDoc of its own may still be documented in the containing class KDoc:
     * `@property name` covers any property of the class, and `@param name` covers a `val`/`var`
     * declared in the primary constructor.
     */
    private fun FirProperty.isCoveredByClassKdocTags(context: CheckerContext): Boolean {
        val classSource = context.containingClassSymbol?.source ?: return false
        val classKdoc = classSource.kdoc() ?: return false
        val tags = classKdocTagsByKdoc.getValue(classKdoc)
        val propertyName = name.asString()
        return propertyName in tags.properties ||
            (source?.kind == KtFakeSourceElementKind.PropertyFromParameter &&
                propertyName in tags.parameters)
    }

    /**
     * KDoc stays a raw comment token in the light tree, so class-level property and constructor
     * parameter tags are recognized textually. A block tag occurs only at the start of a line,
     * after the comment markers.
     */
    private fun CharSequence.parseClassKdocTags(): ClassKdocTags {
        val properties = mutableSetOf<String>()
        val parameters = mutableSetOf<String>()
        lineSequence().forEach { line ->
            val content = line.trim().removePrefix("/**").removePrefix("*").trimStart()
            when {
                content.startsWith("@property ") -> properties += content.subjectAfter("@property ")
                content.startsWith("@param ") -> parameters += content.subjectAfter("@param ")
            }
        }
        return ClassKdocTags(properties, parameters)
    }

    private fun String.subjectAfter(tagPrefix: String): String =
        removePrefix(tagPrefix).trimStart()
            .takeWhile { !it.isWhitespace() }
            .removeSurrounding("`")

    private data class ClassKdocTags(
        val properties: Set<String>,
        val parameters: Set<String>,
    )
}
