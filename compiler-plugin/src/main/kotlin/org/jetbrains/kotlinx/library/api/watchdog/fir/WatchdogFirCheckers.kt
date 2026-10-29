package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.ExplicitApiMode
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeAliasChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.FirResolvedTypeRefChecker
import org.jetbrains.kotlin.fir.analysis.checkers.type.TypeCheckers
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.platform.jvm.isJvm

/**
 * Registers the checkers in a module compiled with explicit API mode, `strict` or `warning`.
 * A contract-shape check reports on the `expect` declaration. An implementation or JVM check
 * reports on the `actual` declaration and accepts annotations of the matched `expect`.
 * [RedundantExemptionProbingChecker] wraps every checker that honors an exemption, so a check that
 * is off reports no redundant exemption either.
 */
class WatchdogFirCheckers internal constructor(
    session: FirSession,
    severities: WatchdogDiagnosticSeverities,
    annotationBasedExclusions: AnnotationBasedCheckExclusions = AnnotationBasedCheckExclusions.NONE,
    recorder: WatchdogDiagnosticsRecorder? = null,
    dependencyExposure: DependencyExposureCheckConfiguration? = null,
    publicTypeWithInternalApiEnabled: Boolean = true,
    exemptingExistingApi: Boolean = false,
    reportUndocumentedClassProperties: Boolean = false,
) : FirAdditionalCheckersExtension(session) {
    private val enabled =
        session.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        private fun <C : Any> C.unlessDisabled(vararg diagnostics: ConfigurableWatchdogDiagnostic<*>): C? =
            takeIf { enabled && diagnostics.any(severities::isEnabled) }

        private fun <C : Any> C.onlyOnJvm(): C? = takeIf { session.moduleData.platform.isJvm() }

        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.recorded(): Set<FirDeclarationChecker<D>> =
            if (recorder == null) this else mapTo(mutableSetOf()) { RecordingDeclarationChecker(it, recorder) }

        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.excludingConfiguredAnnotations():
                Set<FirDeclarationChecker<D>> =
            if (annotationBasedExclusions.isEmpty) this else mapTo(mutableSetOf()) {
                AnnotationExcludingDeclarationChecker(it, annotationBasedExclusions)
            }

        /** The probe sees the diagnostics before an ignore rule drops them, so an ignored declaration keeps its exemptions. */
        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.probingRedundantExemptions():
                Set<FirDeclarationChecker<D>> =
            mapTo(mutableSetOf()) { checker ->
                if (checker is ExemptingChecker) RedundantExemptionProbingChecker(checker, checker) else checker
            }

        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.wrapped(): Set<FirDeclarationChecker<D>> =
            probingRedundantExemptions().excludingConfiguredAnnotations().recorded()

        private val plainBasicDeclarationCheckers: Set<FirBasicDeclarationChecker> = setOfNotNull(
            UndocumentedApiChecker(severities, reportUndocumentedClassProperties)
                .unlessDisabled(WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API),
            ExemptionExplanationChecker.declarationChecker
                .takeIf { enabled && !exemptingExistingApi },
            MutableCollectionChecker(session, severities)
                .unlessDisabled(WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API),
            PairOrTripleChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API),
            NullableBooleanChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API),
            InternalApiTypeExposureChecker()
                .takeIf { enabled && publicTypeWithInternalApiEnabled && !exemptingExistingApi },
            dependencyExposure?.let { NonTransitiveDependencyChecker(session, it) }
                ?.takeIf { enabled && !exemptingExistingApi },
        )

        private val plainFunctionCheckers: Set<FirFunctionChecker> = setOfNotNull(
            BooleanParameterChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API),
            RequiredParameterAfterOptionalChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL),
            OverloadParameterOrderChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS),
            KotlinOnlyApiChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(WatchdogDiagnostics.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC),
            JvmOverloadsChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(WatchdogDiagnostics.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS),
        )

        private val plainCallableDeclarationCheckers: Set<FirCallableDeclarationChecker> = setOfNotNull(
            DslMarkerTypePositionChecker(severities).unlessDisabled(WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION),
            InlineFunctionLogicChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC),
            MangledJvmNameChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API),
            CompanionJvmExposureChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(
                    WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC,
                    WatchdogDiagnostics.COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS,
                ),
        )

        override val classCheckers: Set<FirClassChecker> = setOfNotNull(
            OpenApiChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN, WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS),
            ExhaustiveApiChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API),
            DataClassChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.DATA_CLASS_PUBLIC_API),
            StatefulClassWithoutGeneratedMembersChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_EQUALS, WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_HASH_CODE, WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING,),
            DslMarkerTargetsChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET, WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS),
            EnclosingExemptionRedundancyChecker(
                plainBasicDeclarationCheckers,
                plainFunctionCheckers,
                plainCallableDeclarationCheckers,
            ).takeIf { enabled },
        ).wrapped()

        override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = plainBasicDeclarationCheckers.wrapped()

        override val functionCheckers: Set<FirFunctionChecker> = plainFunctionCheckers.wrapped()

        override val fileCheckers: Set<FirFileChecker> = setOfNotNull(
            TopLevelJvmNameChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME),
        ).wrapped()

        override val typeAliasCheckers: Set<FirTypeAliasChecker> = setOfNotNull(
            FunctionTypeAliasChecker(severities).unlessDisabled(WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API),
        ).wrapped()

        override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> = plainCallableDeclarationCheckers.wrapped()
    }

    override val typeCheckers: TypeCheckers = object : TypeCheckers() {
        override val resolvedTypeRefCheckers: Set<FirResolvedTypeRefChecker> = setOfNotNull(
            ExemptionExplanationChecker.typeChecker
                .takeIf { enabled && !exemptingExistingApi },
        ).let { checkers ->
            if (recorder == null) checkers else checkers.mapTo(mutableSetOf()) {
                RecordingTypeChecker(it, recorder)
            }
        }
    }
}
