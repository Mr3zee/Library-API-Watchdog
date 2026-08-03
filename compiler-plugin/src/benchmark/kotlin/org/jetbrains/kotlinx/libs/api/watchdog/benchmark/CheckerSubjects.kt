package org.jetbrains.kotlinx.libs.api.watchdog.benchmark

import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirBasicDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirCallableDeclarationChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFileChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirTypeAliasChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.BooleanParameterChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.CompanionJvmExposureChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.DataClassChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.DependencyExposureCheckConfiguration
import org.jetbrains.kotlinx.libs.api.watchdog.fir.DslMarkerTargetsChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.DslMarkerTypePositionChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.ExemptionExplanationChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.ExhaustiveApiChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.FunctionTypeAliasChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.InlineFunctionLogicChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.JvmOverloadsChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.KotlinOnlyApiChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.MangledJvmNameChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.MutableCollectionChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.NonTransitiveDependencyChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.NullableBooleanChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.OpenApiChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.OverloadParameterOrderChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.PairOrTripleChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.RequiredParameterAfterOptionalChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.StatefulClassWithoutGeneratedMembersChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.TopLevelJvmNameChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.UndocumentedApiChecker
import org.jetbrains.kotlinx.libs.api.watchdog.fir.WatchdogDiagnosticSeverities

/**
 * One watchdog checker as a benchmark subject: how to construct it in isolation for the
 * per-checker benchmark, and which configurable diagnostics enable it in a whole-compilation
 * run.
 *
 * [configurableDiagnostics] is empty for the two checkers that no `diagnosticSeverity` option
 * controls: `ExemptionExplanationChecker` always runs with the plugin, and
 * `NonTransitiveDependencyChecker` is activated by the dependency-path options instead
 * ([needsDependencyPaths]).
 */
internal class CheckerSubject(
    val name: String,
    val configurableDiagnostics: List<String>,
    val needsDependencyPaths: Boolean = false,
    val createCheckers: (corpusClasspath: List<String>) -> DeclarationCheckers,
)

/**
 * Registry of independently benchmarkable watchdog checkers. Checkers whose diagnostics are
 * unavoidable errors are omitted when they cannot run against the triggering corpus without
 * failing compilation.
 */
internal object CheckerSubjects {
    private val severities = WatchdogDiagnosticSeverities.DEFAULT

    val all: List<CheckerSubject> = listOf(
        // Class checkers.
        CheckerSubject(
            "OpenApiChecker",
            listOf("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "SUBCLASS_OPT_IN_WITHOUT_MARKERS"),
        ) { ofClass(OpenApiChecker(severities)) },
        CheckerSubject("ExhaustiveApiChecker", listOf("EXHAUSTIVE_PUBLIC_API")) {
            ofClass(ExhaustiveApiChecker(severities))
        },
        CheckerSubject("DataClassChecker", listOf("DATA_CLASS_PUBLIC_API")) {
            ofClass(DataClassChecker(severities))
        },
        CheckerSubject(
            "StatefulClassWithoutGeneratedMembersChecker",
            listOf(
                "STATEFUL_CLASS_WITHOUT_EQUALS",
                "STATEFUL_CLASS_WITHOUT_HASH_CODE",
                "STATEFUL_CLASS_WITHOUT_TO_STRING",
            ),
        ) { ofClass(StatefulClassWithoutGeneratedMembersChecker(severities)) },
        CheckerSubject(
            "DslMarkerTargetsChecker",
            listOf("DSL_MARKER_NOOP_TARGET", "DSL_MARKER_WITHOUT_EXPLICIT_TARGETS"),
        ) { ofClass(DslMarkerTargetsChecker(severities)) },

        // Basic declaration checkers.
        CheckerSubject("UndocumentedApiChecker", listOf("UNDOCUMENTED_PUBLIC_API")) {
            ofBasic(UndocumentedApiChecker(severities))
        },
        CheckerSubject("ExemptionExplanationChecker", emptyList()) {
            ofBasic(ExemptionExplanationChecker())
        },
        CheckerSubject("MutableCollectionChecker", listOf("MUTABLE_COLLECTION_PUBLIC_API")) {
            ofBasic(MutableCollectionChecker(severities))
        },
        CheckerSubject("PairOrTripleChecker", listOf("PAIR_OR_TRIPLE_PUBLIC_API")) {
            ofBasic(PairOrTripleChecker(severities))
        },
        CheckerSubject("NullableBooleanChecker", listOf("NULLABLE_BOOLEAN_PUBLIC_API")) {
            ofBasic(NullableBooleanChecker(severities))
        },
        CheckerSubject(
            "NonTransitiveDependencyChecker",
            emptyList(),
            needsDependencyPaths = true,
        ) { corpusClasspath ->
            ofBasic(
                NonTransitiveDependencyChecker(
                    DependencyExposureCheckConfiguration(
                        compileDependencies = corpusClasspath.toSet(),
                        transitiveDependencies = corpusClasspath.filterTo(linkedSetOf()) {
                            it.contains("kotlin-stdlib")
                        },
                    ),
                ),
            )
        },

        // Function checkers.
        CheckerSubject("BooleanParameterChecker", listOf("BOOLEAN_PARAMETER_PUBLIC_API")) {
            ofFunction(BooleanParameterChecker(severities))
        },
        CheckerSubject(
            "RequiredParameterAfterOptionalChecker",
            listOf("REQUIRED_PARAMETER_AFTER_OPTIONAL"),
        ) { ofFunction(RequiredParameterAfterOptionalChecker(severities)) },
        CheckerSubject(
            "OverloadParameterOrderChecker",
            listOf("INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS"),
        ) { ofFunction(OverloadParameterOrderChecker(severities)) },
        CheckerSubject("KotlinOnlyApiChecker", listOf("KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC")) {
            ofFunction(KotlinOnlyApiChecker(severities))
        },
        CheckerSubject("JvmOverloadsChecker", listOf("DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS")) {
            ofFunction(JvmOverloadsChecker(severities))
        },

        // File checkers.
        CheckerSubject("TopLevelJvmNameChecker", listOf("TOP_LEVEL_API_WITHOUT_JVM_NAME")) {
            ofFile(TopLevelJvmNameChecker(severities))
        },

        // Type alias checkers.
        CheckerSubject("FunctionTypeAliasChecker", listOf("FUNCTION_TYPE_ALIAS_PUBLIC_API")) {
            ofTypeAlias(FunctionTypeAliasChecker(severities))
        },

        // Callable declaration checkers.
        CheckerSubject("DslMarkerTypePositionChecker", listOf("DSL_MARKER_NOOP_TYPE_POSITION")) {
            ofCallable(DslMarkerTypePositionChecker(severities))
        },
        CheckerSubject("InlineFunctionLogicChecker", listOf("INLINE_FUNCTION_WITH_LOGIC")) {
            ofCallable(InlineFunctionLogicChecker(severities))
        },
        CheckerSubject("MangledJvmNameChecker", listOf("MANGLED_JVM_NAME_PUBLIC_API")) {
            ofCallable(MangledJvmNameChecker(severities))
        },
        CheckerSubject(
            "CompanionJvmExposureChecker",
            listOf("COMPANION_API_WITHOUT_JVM_STATIC", "COMPANION_CONSTANT_WITHOUT_JVM_FIELD"),
        ) { ofCallable(CompanionJvmExposureChecker(severities)) },
    )

    val byName: Map<String, CheckerSubject> = all.associateBy { it.name }

    /** Traversal-only baseline for the isolated benchmark. */
    val emptyCheckers: DeclarationCheckers = object : DeclarationCheckers() {}

    private fun ofClass(checker: FirClassChecker): DeclarationCheckers =
        object : DeclarationCheckers() {
            override val classCheckers: Set<FirClassChecker> = setOf(checker)
        }

    private fun ofBasic(checker: FirBasicDeclarationChecker): DeclarationCheckers =
        object : DeclarationCheckers() {
            override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = setOf(checker)
        }

    private fun ofFunction(checker: FirFunctionChecker): DeclarationCheckers =
        object : DeclarationCheckers() {
            override val functionCheckers: Set<FirFunctionChecker> = setOf(checker)
        }

    private fun ofFile(checker: FirFileChecker): DeclarationCheckers =
        object : DeclarationCheckers() {
            override val fileCheckers: Set<FirFileChecker> = setOf(checker)
        }

    private fun ofTypeAlias(checker: FirTypeAliasChecker): DeclarationCheckers =
        object : DeclarationCheckers() {
            override val typeAliasCheckers: Set<FirTypeAliasChecker> = setOf(checker)
        }

    private fun ofCallable(checker: FirCallableDeclarationChecker): DeclarationCheckers =
        object : DeclarationCheckers() {
            override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> = setOf(checker)
        }
}
