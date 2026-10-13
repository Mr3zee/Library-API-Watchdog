package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.declarations.FirCallableDeclaration
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.utils.isInline
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirCallableReferenceAccess
import org.jetbrains.kotlin.fir.expressions.FirComparisonExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirGetClassCall
import org.jetbrains.kotlin.fir.expressions.FirImplicitInvokeCall
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
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
import org.jetbrains.kotlin.fir.declarations.utils.isInfix
import org.jetbrains.kotlin.fir.declarations.utils.isOperator
import org.jetbrains.kotlin.fir.expressions.impl.FirContractCallBlock
import org.jetbrains.kotlin.fir.expressions.impl.FirUnitExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports watched inline functions and inline property accessors whose body or parameter default
 * values are not a thin delegation. Besides an optional contract, a thin body has one statement
 * composed only of value reads or writes, type checks and casts, callable or class references,
 * lambda literals with thin bodies, direct calls to caller-provided function values, and calls to
 * declarations that don't copy a body of this library into the user. These constructs are logic:
 *
 * - A call to or a reference to an inline declaration of this library. The standard library is
 *   stable, so its inline declarations count as regular calls.
 * - An operator or infix member of a built-in type, in any call syntax. `x + 1` and `x.plus(1)`
 *   both compile to an arithmetic instruction.
 * - Every other FIR construct, for example control flow, equality, string templates, local
 *   declarations, and object literals.
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
            is FirNamedFunction -> checkFunction(declaration)
            is FirProperty -> checkProperty(declaration)
            else -> return
        }
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun checkFunction(declaration: FirNamedFunction) {
        if (declaration.isExpectedDeclaration() || !declaration.isInline ||
            !declaration.isWatchedPublicApi() || declaration.isExempt()
        ) {
            return
        }

        val factory = severities[WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC] ?: return
        val body = declaration.body ?: return
        if (body.isThinWrapper() && declaration.valueParameters.all { it.defaultValue?.isPlain() != false }) {
            return
        }

        reporter.reportOn(
            source = declaration.source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, "inlineFunction"),
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
        if (declaration.isExpectedDeclaration() ||
            !declaration.isWatchedPublicApi() || declaration.isExempt()
        ) {
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
            a = WatchdogDiagnosticMessages.parameterValueFor(
                factory.name,
                if (accessor.isGetter) "inlineGetter" else "inlineSetter",
            ),
            b = property.name,
        )
    }

    context(context: CheckerContext)
    private fun FirCallableDeclaration.isExempt(): Boolean =
        hasAnnotationOnActualOrExpect(WatchdogClassIds.IntentionallyInlinedLogic)

    /**
     * An empty body freezes nothing. Otherwise the single statement must be a plain delegation. A
     * contract declared in the old statement syntax stays in the body as a [FirContractCallBlock]
     * and doesn't count.
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

    /** Whether the statement only reads or writes values and delegates. Unlisted constructs are logic. */
    private fun FirStatement.isPlain(): Boolean = when (this) {
        is FirSmartCastExpression -> originalExpression.isPlain()
        is FirWrappedArgumentExpression -> expression.isPlain()
        is FirVarargArgumentsExpression -> arguments.all { it.isPlain() }
        is FirLiteralExpression -> true
        // The implicit result of a bare `return` and of an empty lambda body.
        is FirUnitExpression -> true
        is FirResolvedQualifier -> true
        is FirResolvedReifiedParameterReference -> true
        is FirGetClassCall -> argument.isPlain()
        // Calling a function value executes caller code or code reached through a runtime value.
        // For the wrapper's own functional parameter, direct invocation also preserves Kotlin's
        // in-place lambda behavior: at a suspend call site, the inlined lambda can inherit the
        // caller's suspend context. The call resolves to the function value's non-inline `invoke`
        // operator.
        is FirImplicitInvokeCall -> isPlainCall()
        is FirFunctionCall -> isPlainCall()
        // `a < b` is `a.compareTo(b)` and follows the rules of that call.
        is FirComparisonExpression -> compareToCall.isPlain()
        is FirCallableReferenceAccess ->
            !calleeReference.toResolvedCallableSymbol().copiesLibraryBody(write = false) &&
                    explicitReceiver?.isPlain() != false
        is FirThisReceiverExpression -> true
        is FirPropertyAccessExpression ->
            !calleeReference.toResolvedCallableSymbol().copiesLibraryBody(write = false) &&
                    explicitReceiver?.isPlain() != false
        // Writing a property delegates to its setter just like reading delegates to the getter.
        is FirVariableAssignment ->
            (lValue as? FirPropertyAccessExpression)?.let { target ->
                !target.calleeReference.toResolvedCallableSymbol().copiesLibraryBody(write = true) &&
                        target.explicitReceiver?.isPlain() != false
            } == true && rValue.isPlain()
        is FirTypeOperatorCall -> arguments.all { it.isPlain() }
        is FirAnonymousFunctionExpression -> anonymousFunction.body?.isThinWrapper() == true
        else -> false
    }

    /**
     * A call delegates when the callee is neither a built-in operation nor an inline declaration
     * of this library, and its receiver and arguments are plain. Constructors are never inline.
     */
    private fun FirFunctionCall.isPlainCall(): Boolean {
        val callee = calleeReference.toResolvedCallableSymbol()
        return !callee.isBuiltinOperation() &&
                !callee.copiesLibraryBody(write = false) &&
                explicitReceiver?.isPlain() != false &&
                arguments.all { it.isPlain() }
    }

    /**
     * Whether a use of the declaration copies an inline body of this library into the user. The
     * standard library is stable, so its inline bodies don't count. An inline property copies the
     * accessor that the access uses.
     */
    private fun FirCallableSymbol<*>?.copiesLibraryBody(write: Boolean): Boolean {
        if (this == null || callableId?.packageName?.startsWith(STANDARD_LIBRARY_ROOT) == true) return false
        if (isInline) return true
        val property = this as? FirPropertySymbol ?: return false
        val accessor = if (write) property.setterSymbol else property.getterSymbol
        return accessor?.isInline == true
    }

    /**
     * An operator or infix member of a built-in type compiles to an instruction in the user
     * binary, in operator syntax and in explicit call syntax alike. A user-defined operator is a
     * regular call.
     */
    private fun FirCallableSymbol<*>?.isBuiltinOperation(): Boolean {
        val function = this as? FirNamedFunctionSymbol ?: return false
        return (function.isOperator || function.isInfix) && function.callableId.classId in BUILTIN_OPERAND_TYPES
    }

    private companion object {
        val STANDARD_LIBRARY_ROOT: Name = Name.identifier("kotlin")

        val BUILTIN_OPERAND_TYPES: Set<ClassId> = buildSet {
            add(StandardClassIds.Any)
            add(StandardClassIds.String)
            add(StandardClassIds.Array)
            addAll(StandardClassIds.primitiveTypes)
            addAll(StandardClassIds.unsignedTypes)
            addAll(StandardClassIds.primitiveArrayTypeByElementType.values)
            addAll(StandardClassIds.unsignedArrayTypeByElementType.values)
        }
    }
}
