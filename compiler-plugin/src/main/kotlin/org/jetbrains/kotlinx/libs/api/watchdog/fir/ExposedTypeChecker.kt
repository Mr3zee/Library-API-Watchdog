package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassIdSafe
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.customAnnotations
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Base for style checks that hunt a type down in publicly visible callable signatures and type
 * parameter bounds. The full declaration and type traversal comes from
 * [PublicSignatureTypeChecker]; this specialization applies their common policy:
 *
 * - Overrides are skipped because their signature is fixed by the overridden declaration.
 * - Extension receivers are skipped because they provide functionality for values users already
 *   hold instead of exposing new ones.
 * - Class supertypes, class context parameters, and type aliases are outside these checks' scope.
 * - The exemption annotation is honored on a whole declaration, on a single (type) parameter, or
 *   on a type usage, where it covers the annotated type and everything nested in it.
 *
 * Type-use exemptions never reach [ExemptionExplanationChecker], so this class normally enforces
 * their explanation requirement while honoring them. The adoption task disables that validation
 * through [validateExemptionExplanations].
 */
internal abstract class ExposedTypeChecker(
    private val exemption: ClassId,
    private val validateExemptionExplanations: Boolean = true,
) : PublicSignatureTypeChecker<Name>(
    checkExtensionReceivers = false,
    checkClassSupertypes = false,
    checkClassContextParameters = false,
    checkTypeAliases = false,
    skipOverrides = true,
) {
    context(context: CheckerContext)
    override fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicSourceApi()

    context(context: CheckerContext)
    override fun isDeclarationExempt(declaration: FirDeclaration): Boolean =
        declaration.hasAnnotation(exemption, context.session)

    context(context: CheckerContext)
    override fun isParameterExempt(parameter: FirValueParameterSymbol): Boolean =
        parameter.hasAnnotation(exemption, context.session)

    /**
     * A type-use [exemption] exempts the annotated type and everything nested in it. The
     * explanation requirement is normally enforced here because declaration checkers never see it.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun ConeKotlinType.isTypeExempt(): Boolean {
        val typeUseExemption = customAnnotations.firstOrNull {
            it.toAnnotationClassIdSafe(context.session) == exemption
        } ?: return false

        if (!validateExemptionExplanations) {
            return true
        }

        typeUseExemption.unexplainedExemptionReason()?.let { reason ->
            reporter.reportOn(
                source = typeUseExemption.source,
                factory = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
                a = exemption.shortClassName,
                b = reason,
            )
        }
        return true
    }
}
