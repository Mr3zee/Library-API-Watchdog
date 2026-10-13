package org.jetbrains.kotlinx.library.api.watchdog.fir

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirTypeAlias
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isCompanion
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames

/**
 * Reports watched classifiers, type aliases, functions, properties, and secondary constructors
 * with no KDoc token. KDoc content is not inspected.
 *
 * Overrides, `actual` declarations, primary constructors, enum entries, companion objects with
 * the default name, declarations visible only through `@PublishedApi`, and exempt declarations
 * are skipped.
 *
 * The KDoc lookup isn't cached. In the IDE the session outlives a KDoc edit, and the source
 * element of the declaration stays the same object. A session cache keyed by it keeps the
 * result from before the edit.
 */
internal class UndocumentedApiChecker(
    severities: WatchdogDiagnosticSeverities,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    private val factory = severities[WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API]

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

        if (declaration.hasAnnotation(WatchdogClassIds.IntentionallyUndocumented, context.session)) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, kind),
            b = declaration.watchedName(context) ?: return,
        )
    }

    /**
     * The `sharedParameterValues` name of the declaration kind for the message, or null when the
     * declaration is not watched: either
     * users can't reference it directly, or its documentation lives on another declaration -
     * overrides and `actual`s inherit it, and the primary constructor is described by class KDoc.
     * A `companion object` with the default name is a namespace for its members, which are
     * documented on their own, so it is skipped too. A named companion is a deliberate type
     * and stays watched. Enum entries are described by the KDoc of their enum class: a name
     * is usually all the documentation a constant needs.
     */
    context(context: CheckerContext)
    private fun FirMemberDeclaration.watchedKind(): String? = when {
        isActualizedDeclaration() -> null
        this is FirRegularClass -> if (isDefaultNamedCompanion) null else kindParameterValue
        this is FirTypeAlias -> "typeAlias"
        this is FirNamedFunction -> if (isOverride) null else "function"
        this is FirProperty -> if (isOverride) null else "property"
        this is FirConstructor -> if (isPrimary) null else "constructor"
        else -> null
    }

    private val FirRegularClass.isDefaultNamedCompanion: Boolean
        get() = isCompanion && name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT

    /** Secondary constructors report the class name instead of their internal `<init>` name. */
    private fun FirMemberDeclaration.watchedName(context: CheckerContext): Name? = when (this) {
        is FirRegularClass -> name
        is FirTypeAlias -> name
        is FirNamedFunction -> name
        is FirProperty -> name
        is FirConstructor -> context.containingClassSymbol?.classId?.shortClassName ?: symbol.name
        else -> null
    }

    // KDoc never reaches FIR, but the source element keeps the underlying parse tree, where
    // the KDoc is a direct child of the declaration node. `getChild` traverses both source
    // representations: the light tree (CLI) and PSI (Analysis API).
    private fun KtSourceElement?.hasKdoc(): Boolean =
        this?.getChild(kdocElementTypes, index = 0, depth = 1, reverse = false) != null
}
