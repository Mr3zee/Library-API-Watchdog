package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
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

/**
 * Reports publicly visible
 * [inline functions](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html#considerations-for-using-the-publishedapi-annotation)
 * - and inline property accessors, which inline the same way - whose body does more than delegate
 * to a non-inline function. The compiler copies an inline body into every user binary, so logic
 * placed there - and its bugs - stays frozen in users compiled against an old library version
 * until they recompile. A public inline function should be a thin wrapper: it resolves what only
 * the call site knows - a reified type argument, an inlined lambda - and hands the actual work to
 * a non-inline function (`@PublishedApi internal` when it should stay out of the public API),
 * where the library can still fix it. Authors acknowledge deliberately inlined logic with
 * `@IntentionallyInlinedLogic` - on the function, or on the property for its accessors.
 *
 * A body counts as a thin wrapper when its single statement - besides an optional contract - is a
 * delegation whose parts only read or write values: parameters, properties, `this`, literals,
 * object references, `T::class`, callable references, nested non-inline calls, an assignment to a
 * property with a non-inline setter (the thin shape of a setter or a `set`-prefixed wrapper), and
 * an `as`/`as?` cast on a read (reified wrappers narrow their delegate's result that way). A
 * lambda literal passed along counts too when its own body is such a thin statement - the
 * `impl { block() }` shape a `crossinline` wrapper needs. Everything else is logic frozen into
 * users: control flow of any kind, operator and infix calls (arithmetic compiles inline into
 * the user), string templates, local variables, multiple statements, object literals - and
 * calls to inline functions or inline property accessors, whose bodies the inliner drags into the
 * user binary transitively.
 *
 * `@PublishedApi internal` inline functions are checked like public ones: a public inline wrapper
 * may call them, which inlines their body into users just as transitively. Bodiless functions
 * (`expect`, `external`) and non-inline accessors freeze nothing and are skipped.
 */
internal class InlineFunctionLogicChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirCallableDeclarationChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirCallableDeclaration) {
        when (declaration) {
            is FirNamedFunction -> checkFunction(declaration)
            is FirProperty -> checkProperty(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
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
            b = declaration.name,
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
        for (accessor in listOfNotNull(declaration.getter, declaration.setter)) {
            if (!accessor.isInline && !declaration.isInline) {
                continue
            }

            val body = accessor.body ?: continue
            if (body.isThinWrapper()) {
                continue
            }

            reporter.reportOn(
                source = declaration.source,
                factory = factory,
                a = if (accessor.isGetter) "inline getter" else "inline setter",
                b = declaration.name,
            )
        }
    }

    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean =
        hasAnnotation(WatchdogClassIds.IntentionallyInlinedLogic, context.session)

    /**
     * An empty body freezes nothing. Otherwise the single statement - a contract declared in the
     * old statement syntax stays in the body as a [FirContractCallBlock] and does not count - must
     * be a plain delegation.
     */
    private fun FirBlock.isThinWrapper(): Boolean {
        val statements = statements.filterNot { it is FirContractCallBlock }
        if (statements.isEmpty()) {
            return true
        }

        val statement = statements.singleOrNull() ?: return false
        return ((statement as? FirReturnExpression)?.result ?: statement).isPlain()
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
