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
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.unwrapFakeOverrides
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * Reports watched regular classes with a backing-field property when their resolved member scope
 * still selects the `kotlin.Any` implementation of `equals`, `hashCode`, or `toString`. Each
 * missing member has its own diagnostic and exemption.
 *
 * Data and value classes, enums, interfaces, annotation classes, and objects are skipped.
 */
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

        // A delegated property stores its value in the delegate, not in a backing field.
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

    /** Reports one missing generated member, including the compiler host's IntelliJ IDEA shortcut. */
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
            c = ideaGenerateShortcut(),
        )
    }

    /**
     * Whether the class declares or inherits [member]. The scope resolves the name to the most
     * specific override. Only `kotlin.Any` itself provides the identity/opaque defaults these
     * diagnostics exist to flag.
     */
    context(context: CheckerContext)
    private fun FirRegularClass.provides(member: GeneratedMember): Boolean {
        var provided = false
        unsubstitutedScope().processFunctionsByName(member.functionName) { function ->
            val original = function.unwrapFakeOverrides()
            if (original.valueParameterSymbols.size == member.valueParameterCount &&
                original.contextParameterSymbols.isEmpty() &&
                original.receiverParameterSymbol == null &&
                original.resolvedStatus.isOverride &&
                original.containingClassLookupTag()?.classId != StandardClassIds.Any
            ) {
                provided = true
            }
        }
        return provided
    }

    private enum class GeneratedMember(
        val functionName: Name,
        val valueParameterCount: Int,
    ) {
        EQUALS(OperatorNameConventions.EQUALS, 1),
        HASH_CODE(OperatorNameConventions.HASH_CODE, 0),
        TO_STRING(OperatorNameConventions.TO_STRING, 0),
    }
}

/** The shortcut for Code | Generate in IntelliJ IDEA on the compiler's host OS. */
private fun ideaGenerateShortcut(osName: String = System.getProperty("os.name").orEmpty()): String =
    if (osName.startsWith("Mac", ignoreCase = true)) "⌘N" else "Alt+Insert"
