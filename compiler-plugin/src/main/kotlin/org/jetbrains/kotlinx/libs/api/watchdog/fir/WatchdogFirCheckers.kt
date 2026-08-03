package org.jetbrains.kotlinx.libs.api.watchdog.fir

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
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.languageVersionSettings
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.platform.jvm.isJvm

/**
 * The checkers guard a library's public API surface, so they only run in modules compiled with
 * [explicit API mode](https://kotlinlang.org/docs/api-guidelines-simplicity.html#use-explicit-api-mode)
 * (`kotlin { explicitApi() }` / `-Xexplicit-api`), in either `strict` or `warning` variant.
 *
 * A checker whose every diagnostic is configured to [WatchdogSeverity.NONE] is not registered at
 * all, so a disabled check costs nothing per declaration.
 */
class WatchdogFirCheckers internal constructor(
    session: FirSession,
    severities: WatchdogDiagnosticSeverities,
    recorder: WatchdogDiagnosticsRecorder? = null,
    dependencyExposure: DependencyExposureCheckConfiguration? = null,
) : FirAdditionalCheckersExtension(session) {
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        private val enabled =
            session.languageVersionSettings.getFlag(AnalysisFlags.explicitApiMode) != ExplicitApiMode.DISABLED

        private fun <C : Any> C.unlessDisabled(vararg diagnostics: ConfigurableWatchdogDiagnostic<*>): C? =
            takeIf { enabled && diagnostics.any(severities::isEnabled) }

        // The Java-interop checks guard how the compiled API looks to Java sources - a concern
        // with no counterpart on other backends, so they only register for JVM compilations.
        private fun <C : Any> C.onlyOnJvm(): C? = takeIf { session.moduleData.platform.isJvm() }

        /** With a [recorder], every checker also records its diagnostics for external tooling. */
        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.recorded(): Set<FirDeclarationChecker<D>> =
            if (recorder == null) this else mapTo(mutableSetOf()) { RecordingDeclarationChecker(it, recorder) }

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
        ).recorded()

        // These checkers watch every declaration kind, not just classes. MutableCollectionChecker
        // is one of them because it also inspects class-level type parameter bounds.
        override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = setOfNotNull(
            UndocumentedApiChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API),
            ExemptionExplanationChecker() // Not configurable: exemption explanations are enforced whenever the plugin runs.
                .takeIf { enabled },
            MutableCollectionChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API),
            PairOrTripleChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API),
            NullableBooleanChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API),
            InternalApiTypeExposureChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API),
            dependencyExposure?.let(::NonTransitiveDependencyChecker)
                ?.takeIf { enabled },
        ).recorded()

        // Dispatched to named functions and constructors alike: both declare parameter lists.
        override val functionCheckers: Set<FirFunctionChecker> = setOfNotNull(
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
        ).recorded()

        override val fileCheckers: Set<FirFileChecker> = setOfNotNull(
            TopLevelJvmNameChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME),
        ).recorded()

        override val typeAliasCheckers: Set<FirTypeAliasChecker> = setOfNotNull(
            FunctionTypeAliasChecker(severities).unlessDisabled(WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API),
        ).recorded()

        // Dispatched to every callable: functions, properties, accessors, and value parameters.
        override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> = setOfNotNull(
            DslMarkerTypePositionChecker(severities).unlessDisabled(WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION),
            // Watches properties besides functions: inline accessors are inlined the same way.
            InlineFunctionLogicChecker(severities)
                .unlessDisabled(WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC),
            MangledJvmNameChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API),
            CompanionJvmExposureChecker(severities)
                .onlyOnJvm()
                ?.unlessDisabled(
                    WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC,
                    WatchdogDiagnostics.COMPANION_CONSTANT_WITHOUT_JVM_FIELD,
                ),
        ).recorded()
    }
}
