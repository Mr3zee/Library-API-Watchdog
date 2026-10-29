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
        private fun <C : Any> C.onlyOnJvm(): C? = takeIf { session.moduleData.platform.isJvm() }

        private fun <C : Any> partition(vararg entries: Toggled<C>?): Partition<C> {
            val active = mutableSetOf<C>()
            val disabled = mutableListOf<ExemptingChecker>()
            if (enabled) {
                for (entry in entries.filterNotNull()) {
                    when {
                        entry.diagnostics.any(severities::isEnabled) -> active += entry.checker
                        entry.checker is ExemptingChecker -> disabled += entry.checker
                    }
                }
            }
            return Partition(active, disabled)
        }

        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.recorded(): Set<FirDeclarationChecker<D>> =
            if (recorder == null) this else mapTo(mutableSetOf()) { RecordingDeclarationChecker(it, recorder) }

        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.excludingConfiguredAnnotations():
                Set<FirDeclarationChecker<D>> =
            if (annotationBasedExclusions.isEmpty) this else mapTo(mutableSetOf()) {
                AnnotationExcludingDeclarationChecker(it, annotationBasedExclusions)
            }

        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.probingRedundantExemptions(
            disabled: List<ExemptingChecker>,
        ): Set<FirDeclarationChecker<D>> {
            val (exempting, plain) = partition { it is ExemptingChecker }
            return buildSet {
                addAll(plain)
                if (exempting.isNotEmpty() || disabled.isNotEmpty()) {
                    add(RedundantExemptionProbingChecker(exempting, disabled))
                }
            }
        }

        private fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.wrapped(
            disabled: List<ExemptingChecker>,
        ): Set<FirDeclarationChecker<D>> =
            probingRedundantExemptions(disabled).excludingConfiguredAnnotations().recorded()

        private val basic = partition<FirBasicDeclarationChecker>(
            UndocumentedApiChecker(severities, reportUndocumentedClassProperties)
                .toggledBy(WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API),
            MutableCollectionChecker(session, severities)
                .toggledBy(WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API),
            PairOrTripleChecker(severities)
                .toggledBy(WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API),
            NullableBooleanChecker(severities)
                .toggledBy(WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API),
        )

        private val plainBasicDeclarationCheckers: Set<FirBasicDeclarationChecker> = basic.active + setOfNotNull(
            ExemptionExplanationChecker.declarationChecker
                .takeIf { enabled && !exemptingExistingApi },
            InternalApiTypeExposureChecker()
                .takeIf { enabled && publicTypeWithInternalApiEnabled && !exemptingExistingApi },
            dependencyExposure?.let { NonTransitiveDependencyChecker(session, it) }
                ?.takeIf { enabled && !exemptingExistingApi },
        )

        private val functions = partition<FirFunctionChecker>(
            BooleanParameterChecker(severities)
                .toggledBy(WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API),
            RequiredParameterAfterOptionalChecker(severities)
                .toggledBy(WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL),
            OverloadParameterOrderChecker(severities)
                .toggledBy(WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS),
            KotlinOnlyApiChecker(severities)
                .onlyOnJvm()
                ?.toggledBy(WatchdogDiagnostics.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC),
            JvmOverloadsChecker(severities)
                .onlyOnJvm()
                ?.toggledBy(WatchdogDiagnostics.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS),
        )

        private val callables = partition<FirCallableDeclarationChecker>(
            DslMarkerTypePositionChecker(severities)
                .toggledBy(WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION),
            InlineFunctionLogicChecker(severities)
                .toggledBy(WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC),
            MangledJvmNameChecker(severities)
                .onlyOnJvm()
                ?.toggledBy(WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API),
            CompanionJvmExposureChecker(severities)
                .onlyOnJvm()
                ?.toggledBy(
                    WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC,
                    WatchdogDiagnostics.COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS,
                ),
        )

        private val classes = partition<FirClassChecker>(
            OpenApiChecker(severities)
                .toggledBy(WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN, WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS),
            ExhaustiveApiChecker(severities)
                .toggledBy(WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API),
            DataClassChecker(severities)
                .toggledBy(WatchdogDiagnostics.DATA_CLASS_PUBLIC_API),
            StatefulClassWithoutGeneratedMembersChecker(severities)
                .toggledBy(
                    WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_EQUALS,
                    WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_HASH_CODE,
                    WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING,
                ),
            DslMarkerTargetsChecker(severities)
                .toggledBy(WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET, WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS),
        )

        private val files = partition<FirFileChecker>(
            TopLevelJvmNameChecker(severities)
                .onlyOnJvm()
                ?.toggledBy(WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME),
        )

        private val typeAliases = partition<FirTypeAliasChecker>(
            FunctionTypeAliasChecker(severities)
                .toggledBy(WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API),
        )

        private val enclosingExemptionProbes = EnclosingExemptionProbes(
            plainBasicDeclarationCheckers,
            functions.active,
            callables.active,
            basic.disabled + functions.disabled + callables.disabled,
        )

        override val classCheckers: Set<FirClassChecker> = (classes.active + setOfNotNull(
            EnclosingExemptionRedundancyChecker(enclosingExemptionProbes).takeIf { enabled },
        )).wrapped(classes.disabled)

        override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = (plainBasicDeclarationCheckers + setOfNotNull(
            ExpectExemptionRedundancyChecker(
                basicCheckers = plainBasicDeclarationCheckers,
                functionCheckers = functions.active,
                callableCheckers = callables.active,
                classCheckers = classes.active,
                typeAliasCheckers = typeAliases.active,
                disabled = basic.disabled + functions.disabled + callables.disabled + classes.disabled + typeAliases.disabled,
                enclosing = enclosingExemptionProbes,
            ).takeIf { enabled },
        )).wrapped(basic.disabled)

        override val functionCheckers: Set<FirFunctionChecker> = functions.active.wrapped(functions.disabled)

        override val fileCheckers: Set<FirFileChecker> = files.active.wrapped(files.disabled)

        override val typeAliasCheckers: Set<FirTypeAliasChecker> = typeAliases.active.wrapped(typeAliases.disabled)

        override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> =
            callables.active.wrapped(callables.disabled)
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

private class Toggled<out C : Any>(val checker: C, val diagnostics: List<ConfigurableWatchdogDiagnostic<*>>)

private class Partition<C : Any>(val active: Set<C>, val disabled: List<ExemptingChecker>)

private fun <C : Any> C.toggledBy(vararg diagnostics: ConfigurableWatchdogDiagnostic<*>): Toggled<C> =
    Toggled(this, diagnostics.toList())
