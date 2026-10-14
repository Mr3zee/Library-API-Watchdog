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
 * The KDoc lookup isn't cached: in the IDE the session outlives a KDoc edit, and the source
 * element of the declaration stays the same object.
 */
internal class UndocumentedApiChecker(
    severities: WatchdogDiagnosticSeverities,
    private val reportClassProperties: Boolean,
) : FirBasicDeclarationChecker(MppCheckerKind.Common) {
    private val factory = severities[WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API]

    private companion object {
        /**
         * The jar runs in the CLI and in `kotlin-compiler-embeddable`, which relocates
         * `com.intellij`. A direct reference to `KDocTokens.KDOC` puts the field type in the
         * bytecode and fails to link in one of them. The generic signature erases to `java.util.Set`.
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
     * The kind word of `diagnostics.json`, or null when another declaration can hold the KDoc:
     *
     * - the class for a primary constructor
     * - the members for a default-named companion
     * - the class for a member property, unless [reportClassProperties] is on
     * - the overridden or the `expect` declaration for an override or an actualized declaration
     */
    context(context: CheckerContext)
    private fun FirMemberDeclaration.watchedKind(): String? = when {
        isActualizedDeclaration() -> null
        this is FirRegularClass -> if (isDefaultNamedCompanion) null else kindParameterValue
        this is FirTypeAlias -> "typeAlias"
        this is FirNamedFunction -> if (isOverride) null else "function"
        this is FirProperty -> when {
            isOverride -> null
            !reportClassProperties && context.containingClassSymbol != null -> null
            else -> "property"
        }
        this is FirConstructor -> if (isPrimary) null else "constructor"
        else -> null
    }

    private val FirRegularClass.isDefaultNamedCompanion: Boolean
        get() = isCompanion && name == SpecialNames.DEFAULT_NAME_FOR_COMPANION_OBJECT

    private fun FirMemberDeclaration.watchedName(context: CheckerContext): Name? = when (this) {
        is FirRegularClass -> name
        is FirTypeAlias -> name
        is FirNamedFunction -> name
        is FirProperty -> name
        is FirConstructor -> context.containingClassSymbol?.classId?.shortClassName ?: symbol.name
        else -> null
    }

    // KDoc isn't in FIR. `getChild` reads both the light tree (CLI) and PSI (Analysis API).
    private fun KtSourceElement?.hasKdoc(): Boolean =
        this?.getChild(kdocElementTypes, index = 0, depth = 1, reverse = false) != null
}
