package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.unsubstitutedScope
import org.jetbrains.kotlin.fir.containingClassLookupTag
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.processAllDeclarations
import org.jetbrains.kotlin.fir.declarations.utils.hasBackingField
import org.jetbrains.kotlin.fir.declarations.utils.isData
import org.jetbrains.kotlin.fir.declarations.utils.isInlineOrValue
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.types.isNullableAny
import org.jetbrains.kotlin.fir.types.upperBoundIfFlexible
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.util.OperatorNameConventions

internal class StatefulClassWithoutGeneratedMembersChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : FirClassChecker(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirClass) {
        if (declaration !is FirRegularClass || declaration.isExpectedDeclaration() ||
            !declaration.isWatchedPublicSourceApi()
        ) {
            return
        }

        if (declaration.classKind != ClassKind.CLASS || declaration.isData || declaration.isInlineOrValue) {
            return
        }

        var stateful = false
        declaration.symbol.processAllDeclarations(context.session) { member ->
            if (member is FirPropertySymbol && member.hasBackingField && !member.hasDelegate) {
                stateful = true
            }
        }

        if (!stateful) {
            return
        }

        if (declaration.hasAnnotationOnActualOrExpect(
                WatchdogClassIds.IntentionallyWithoutEqualsHashCodeOrToString,
            )
        ) {
            return
        }

        declaration.reportIfMissing(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_EQUALS,
            member = GeneratedMember.EQUALS,
            exemption = WatchdogClassIds.IntentionallyWithoutEquals,
        )
        declaration.reportIfMissing(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_HASH_CODE,
            member = GeneratedMember.HASH_CODE,
            exemption = WatchdogClassIds.IntentionallyWithoutHashCode,
        )
        declaration.reportIfMissing(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING,
            member = GeneratedMember.TO_STRING,
            exemption = WatchdogClassIds.IntentionallyWithoutToString,
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    private fun FirRegularClass.reportIfMissing(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory3<Name, String, String>>,
        member: GeneratedMember,
        exemption: ClassId,
    ) {
        val factory = severities[diagnostic] ?: return

        if (hasAnnotationOnActualOrExpect(exemption) || provides(member)) {
            return
        }

        val generationHint = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = diagnostic.name,
            value = "generationHint",
        )
        reporter.reportOn(
            source = source,
            factory = factory,
            a = name,
            b = generationHint,
            c = ideaGenerateShortcut(diagnostic.name),
        )
    }

    /**
     * Whether the class declares or inherits [member] from a class other than `kotlin.Any`.
     * The signature decides, not the `override` modifier, because a compiler plugin such as
     * Poko generates the member in FIR without the modifier.
     */
    context(context: CheckerContext)
    private fun FirRegularClass.provides(member: GeneratedMember): Boolean {
        var provided = false
        unsubstitutedScope().processFunctionsByName(member.functionName) { function ->
            val original = function.unwrapFakeOverrides()
            if (original.containingClassLookupTag()?.classId != StandardClassIds.Any &&
                original.hasSignatureOf(member)
            ) {
                provided = true
            }
        }
        return provided
    }

    private fun FirNamedFunctionSymbol.hasSignatureOf(member: GeneratedMember): Boolean {
        if (contextParameterSymbols.isNotEmpty() || receiverParameterSymbol != null) return false
        val parameters = valueParameterSymbols
        return when (member) {
            GeneratedMember.EQUALS ->
                parameters.size == 1 && parameters.single().resolvedReturnType.upperBoundIfFlexible().isNullableAny
            GeneratedMember.HASH_CODE, GeneratedMember.TO_STRING -> parameters.isEmpty()
        }
    }

    private enum class GeneratedMember(val functionName: Name) {
        EQUALS(OperatorNameConventions.EQUALS),
        HASH_CODE(OperatorNameConventions.HASH_CODE),
        TO_STRING(OperatorNameConventions.TO_STRING),
    }
}

/** The shortcut of Code | Generate in IntelliJ IDEA on the host OS of the compiler. */
internal fun ideaGenerateShortcut(
    diagnostic: String,
    osName: String = System.getProperty("os.name").orEmpty(),
): String = WatchdogDiagnosticMessages.parameterValueFor(
    diagnostic = diagnostic,
    value = if (osName.startsWith("Mac", ignoreCase = true)) "ideaGenerateShortcutMac" else "ideaGenerateShortcut",
)
