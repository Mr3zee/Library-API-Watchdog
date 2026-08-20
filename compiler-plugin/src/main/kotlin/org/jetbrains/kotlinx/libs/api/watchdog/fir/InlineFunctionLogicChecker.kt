package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirFunctionCallOrigin
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirOperation
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirResolvedReifiedParameterReference
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirSmartCastExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirThisReceiverExpression
import org.jetbrains.kotlin.fir.expressions.FirTypeOperatorCall
import org.jetbrains.kotlin.fir.expressions.FirVarargArgumentsExpression
import org.jetbrains.kotlin.fir.expressions.FirVariableAssignment
import org.jetbrains.kotlin.fir.expressions.FirWrappedArgumentExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.fir.expressions.impl.FirContractCallBlock
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.name.Name

/**
 * Reports watched inline functions and inline property accessors whose body is not classified as a
 * thin delegation. Besides an optional contract, a thin body has one statement composed only of
 * value reads or writes, casts, callable or class references, lambda literals with thin bodies,
 * and calls to non-inline declarations. Calls through inline functions or accessors and all other
 * FIR constructs are classified as logic.
 *
 * `@PublishedApi internal` inline declarations are included. Bodiless functions and non-inline
 * accessors are skipped. An exemption on a property covers both accessors.
 */
internal class InlineFunctionLogicChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        when (declaration) {
            is FirFunction -> if (declaration.isNamedFunction()) checkFunction(declaration)
            is FirProperty -> checkProperty(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirFunction) {
        if (!declaration.isInline || !declaration.isWatchedPublicApi() || declaration.isExempt()) {
            return
        }

        val factory = severities[WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC] ?: return
        val body = declaration.body ?: return
        if (body.isThinWrapper()) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = "inline function",
            b = declaration.namedFunctionName,
        )
    }

    /**
     * An accessor is inlined when it carries the `inline` modifier itself or inherits it from the
     * property. Default accessors have no body and are skipped, and non-inline accessors keep
     * their body in the library binary.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkProperty(declaration: FirProperty) {
        if (!declaration.isWatchedPublicApi() || declaration.isExempt()) {
            return
        }

        val factory = severities[WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC] ?: return
        declaration.getter?.let { checkAccessor(it, declaration, factory) }
        declaration.setter?.let { checkAccessor(it, declaration, factory) }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkAccessor(
        accessor: FirPropertyAccessor,
        property: FirProperty,
        factory: KtDiagnosticFactory2<String, Name>,
    ) {
        if (!accessor.isInline && !property.isInline) return

        val body = accessor.body ?: return
        if (body.isThinWrapper()) return

        reporter.reportOn(
            source = property.source,
            factory = factory,
            a = if (accessor.isGetter) "inline getter" else "inline setter",
            b = property.name,
        )
    }

    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyInlinedLogic, context.session)

    /**
     * An empty body freezes nothing. Otherwise the single statement - a contract declared in the
     * old statement syntax stays in the body as a [FirContractCallBlock] and doesn't count - must
     * be a plain delegation.
     */
    private fun FirBlock.isThinWrapper(): Boolean {
        var statement: FirStatement? = null
        for (candidate in statements) {
            if (candidate is FirContractCallBlock) continue
            if (statement != null) return false
            statement = candidate
        }

        val singleStatement = statement ?: return true
        return ((singleStatement as? FirReturnExpression)?.result ?: singleStatement).isPlain()
    }

    /**
     * Whether the statement only reads or writes values and delegates. The whitelist errs on the
     * safe side: any construct not listed - control flow, operators, string templates, local
     * declarations, object literals - counts as logic.
     */
    private fun FirStatement.isPlain(): Boolean = when (this) {
        is FirSmartCastExpression -> originalExpression.isPlain()
        is FirWrappedArgumentExpression -> expression.isPlain()
        is FirVarargArgumentsExpression -> arguments.all { it.isPlain() }
        is FirLiteralExpression -> true
        is FirResolvedQualifier -> true
        is FirResolvedReifiedParameterReference -> true
        is FirGetClassCall -> argument.isPlain()
        // Calling a value - typically the wrapper's own functional parameter - executes no
        // library code, however the call resolves its `invoke` operator.
        is FirImplicitInvokeCall -> isPlainCall()
        is FirFunctionCall -> origin == FirFunctionCallOrigin.Regular && isPlainCall()
        is FirCallableReferenceAccess -> explicitReceiver?.isPlain() != false
        is FirThisReceiverExpression -> true
        is FirPropertyAccessExpression -> !usesInlineAccessor(write = false) && explicitReceiver?.isPlain() != false
        // Writing a property delegates to its setter just like reading delegates to the getter.
        is FirVariableAssignment ->
            (lValue as? FirPropertyAccessExpression)?.let { target ->
                !target.usesInlineAccessor(write = true) && target.explicitReceiver?.isPlain() != false
            } == true && rValue.isPlain()
        is FirTypeOperatorCall ->
            (operation == FirOperation.AS || operation == FirOperation.SAFE_AS) &&
                    arguments.all { it.isPlain() }
        is FirAnonymousFunctionExpression -> anonymousFunction.body?.isThinWrapper() == true
        else -> false
    }

    /**
     * A call delegates cleanly when the callee is not inline (an inline callee's body would be
     * inlined into the user right through the wrapper) and its receiver and arguments only
     * read values. Constructor calls resolve to never-inline constructors and pass the same way.
     */
    private fun FirFunctionCall.isPlainCall(): Boolean =
        calleeReference.toResolvedCallableSymbol()?.isInline != true &&
                explicitReceiver?.isPlain() != false &&
                arguments.all { it.isPlain() }

    /** Accessing a property through an inline accessor inlines that accessor's body into the user. */
    private fun FirPropertyAccessExpression.usesInlineAccessor(write: Boolean): Boolean {
        val property = calleeReference.toResolvedPropertySymbol() ?: return false
        if (property.isInline) {
            return true
        }

        val accessor = if (write) property.setterSymbol else property.getterSymbol
        return accessor?.isInline == true
    }
}
