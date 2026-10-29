package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory4
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.getChild
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.isOverride
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirConstructorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.ClassId

/**
 * Context parameters aren't compared, because the call site doesn't pass them.
 */
internal class OverloadParameterOrderChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirFunctionChecker(MppCheckerKind.Common), ExemptingChecker {
    override val exemptions: Set<ClassId> = setOf(WatchdogClassIds.IntentionallyInconsistentParameterOrder)

    override val readsExemptionOnSiblings: Boolean get() = true

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirFunction) {
        val factory = severities[WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS] ?: return

        if (declaration !is FirConstructor && declaration !is FirNamedFunction) {
            return
        }

        if (declaration.isActualizedDeclaration() || !declaration.isWatchedPublicSourceApi() ||
            declaration.isOverride || declaration.symbol.isExempt()
        ) {
            return
        }

        val ownParameters = declaration.valueParameters
        if (ownParameters.size < 2) {
            return
        }

        val callableName = declaration.reportedName() ?: return
        val siblings = mutableListOf<FirFunctionSymbol<*>>()
        declaration.forEachOverloadSibling siblings@{ sibling ->
            if (sibling == declaration.symbol) {
                return@siblings
            }

            // An override keeps the order of its supertype, so the check skips it. Its exemption
            // doesn't cover the siblings, or a redundant exemption on it would seem needed.
            if ((sibling.isExempt() && !sibling.isOverride) || !sibling.isWatchedPublicSourceApiSibling()) {
                return@siblings
            }

            if (sibling !in siblings) siblings += sibling
        }
        reportDifferentPositions(declaration, factory, callableName, siblings)
    }

    /**
     * The same-named members of the receiver class of an extension are siblings too, because a
     * call to the extension looks like a member call.
     */
    context(context: CheckerContext)
    private inline fun FirFunction.forEachOverloadSibling(
        crossinline action: (FirFunctionSymbol<*>) -> Unit,
    ) {
        val containingClass = context.containingClassSymbol
        when {
            this is FirConstructor -> {
                containingClass?.processAllDeclarations(context.session) { member ->
                    if (member is FirConstructorSymbol) {
                        action(member)
                    }
                }
            }

            this !is FirNamedFunction -> Unit

            else -> {
                val name = this.name
                if (containingClass != null) {
                    containingClass.forEachMemberNamed(name, action)
                } else {
                    for (sibling in context.session.symbolProvider.getTopLevelFunctionSymbols(
                        symbol.callableId.packageName,
                        name,
                    )) {
                        action(sibling)
                    }
                }

                val receiverClass = receiverParameter?.typeRef?.coneType?.erasedClassSymbol()
                if (receiverClass != null && receiverClass != containingClass) {
                    receiverClass.forEachMemberNamed(name, action)
                }
            }
        }
    }

    context(context: CheckerContext)
    private inline fun FirClassSymbol<*>.forEachMemberNamed(
        name: Name,
        crossinline action: (FirFunctionSymbol<*>) -> Unit,
    ) {
        unsubstitutedScope(context).processFunctionsByName(name) { member ->
            action(member.unwrapFakeOverrides())
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun reportDifferentPositions(
        declaration: FirFunction,
        factory: KtDiagnosticFactory4<String, String, String, String>,
        callableName: Name,
        siblings: List<FirFunctionSymbol<*>>,
    ) {
        val current = declaration.symbol.valueParameterSymbols
        val others = siblings.mapNotNull { sibling ->
            val positions = sibling.valueParameterSymbols.withIndex().associate { it.value.name to it.index }
            if (current.count { it.name in positions } < 2) return@mapNotNull null
            sibling to positions
        }
        if (others.isEmpty()) return

        val changes = current.mapIndexedNotNull { index, parameter ->
            if (others.none { (_, positions) ->
                    val otherIndex = positions[parameter.name]
                    otherIndex != null && otherIndex != index
                }
            ) return@mapIndexedNotNull null
            listOf(
                parameter.name.asString(),
                (index + 1).ordinalPosition(),
            ) + others.map { (_, positions) ->
                positions[parameter.name]?.let { (it + 1).ordinalPosition() }
                    ?: WatchdogDiagnosticMessages.parameterValueFor(factory.name, "absentParameter")
            }
        }
        if (changes.isEmpty()) return

        val header = WatchdogDiagnosticMessages.parameterValueFor(factory.name, "positionTableHeader")
            .split(" | ")
        val labels = others.indices.map { index ->
            if (others.size == 1) header[2] else WatchdogDiagnosticMessages.parameterValueFor(
                factory.name, "numberedOverload", (index + 1).toString(),
            )
        }
        reporter.reportOn(
            source = declaration.nameSource(),
            factory = factory,
            a = current.signature(callableName),
            b = others.zip(labels).joinToString("\n") { (other, label) ->
                WatchdogDiagnosticMessages.parameterValueFor(
                    factory.name, "overloadSignature", "$label: ".padEnd(16),
                    other.first.valueParameterSymbols.signature(callableName),
                )
            },
            c = (listOf(header.take(2) + labels) + changes).alignedTable(),
            d = WatchdogDiagnosticMessages.parameterValueFor(
                factory.name,
                if (declaration is FirConstructor) "constructor" else "function",
            ),
        )
    }

    /**
     * The callable name, the `constructor` keyword, or the class name for a primary constructor
     * without the keyword. A positioning strategy can't select these tokens. Its `mark` override
     * has `com.intellij` types in its signature, so it never binds in `kotlin-compiler-embeddable`.
     */
    context(context: CheckerContext)
    private fun FirFunction.nameSource(): KtSourceElement? {
        val source = source ?: return null
        if (this !is FirConstructor) {
            return source.getChild(IDENTIFIER_TOKENS, depth = 1) ?: source
        }
        return source.getChild(CONSTRUCTOR_KEYWORD_TOKENS, depth = 1)
            ?: context.containingClassSymbol?.source?.getChild(IDENTIFIER_TOKENS, depth = 1)
            ?: source
    }

    private fun Int.ordinalPosition(): String {
        val suffix = if (this % 100 in 11..13) {
            "th"
        } else {
            when (this % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
        }
        return "$this$suffix"
    }

    private fun List<List<String>>.alignedTable(): String {
        val widths = first().indices.map { column -> maxOf { row -> row[column].length } }
        return joinToString("\n") { row ->
            "    " + row.mapIndexed { column, cell -> cell.padEnd(widths[column]) }
                .joinToString(" | ").trimEnd()
        }
    }

    private fun List<FirValueParameterSymbol>.signature(callableName: Name): String =
        joinToString(prefix = "$callableName(", postfix = ")") {
            "${it.name}: ${it.resolvedReturnType.renderReadable()}"
        }

    context(context: CheckerContext)
    private fun FirBasedSymbol<*>.isExempt(): Boolean =
        hasExemption(WatchdogClassIds.IntentionallyInconsistentParameterOrder)

    private companion object {
        private val IDENTIFIER_TOKENS: Set<KtToken> = setOf(KtTokens.IDENTIFIER)
        private val CONSTRUCTOR_KEYWORD_TOKENS: Set<KtToken> = setOf(KtTokens.CONSTRUCTOR_KEYWORD)
    }
}
