package org.jetbrains.kotlinx.library.api.watchdog.benchmark

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
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlinx.library.api.watchdog.fir.BooleanParameterChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.CompanionJvmExposureChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.DataClassChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.DependencyExposureCheckConfiguration
import org.jetbrains.kotlinx.library.api.watchdog.fir.DslMarkerTargetsChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.DslMarkerTypePositionChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.EnclosingExemptionProbes
import org.jetbrains.kotlinx.library.api.watchdog.fir.EnclosingExemptionRedundancyChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.ExemptingChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.ExemptionExplanationChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.ExpectExemptionRedundancyChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.ExhaustiveApiChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.FunctionTypeAliasChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.InlineFunctionLogicChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.JvmOverloadsChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.KotlinOnlyApiChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.MangledJvmNameChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.MutableCollectionChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.NonTransitiveDependencyChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.NullableBooleanChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.OpenApiChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.OverloadParameterOrderChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.PairOrTripleChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.RedundantExemptionProbingChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.RequiredParameterAfterOptionalChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.StatefulClassWithoutGeneratedMembersChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.TopLevelJvmNameChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.UndocumentedApiChecker
import org.jetbrains.kotlinx.library.api.watchdog.fir.WatchdogDiagnosticSeverities

/**
 * [configurableDiagnostics] is empty for `ExemptionExplanationChecker`, which always runs, and
 * for `NonTransitiveDependencyChecker`, which the dependency-path options enable.
 *
 * An [isolatedOnly] subject has no whole-compilation mode, because the plugin options can't select it.
 */
internal class CheckerSubject(
    val name: String,
    val configurableDiagnostics: List<String>,
    val needsDependencyPaths: Boolean = false,
    val isolatedOnly: Boolean = false,
    val createCheckers: (environment: CheckerEnvironment) -> IsolatedCheckers,
)

internal class IsolatedCheckers(
    val declarations: DeclarationCheckers = DeclarationCheckers.EMPTY,
    val types: TypeCheckers = TypeCheckers.EMPTY,
)

internal class CheckerEnvironment(
    val corpusClasspath: List<String>,
    val session: FirSession,
)

/**
 * Every checker except `InternalApiTypeExposureChecker`. Its diagnostic is always an error and the
 * corpus triggers it, so the corpus wouldn't compile.
 */
internal object CheckerSubjects {
    private val severities = WatchdogDiagnosticSeverities.DEFAULT

    val all: List<CheckerSubject> = listOf(
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

        CheckerSubject("UndocumentedApiChecker", listOf("UNDOCUMENTED_PUBLIC_API")) {
            ofBasic(UndocumentedApiChecker(severities, reportClassProperties = true))
        },
        CheckerSubject("ExemptionExplanationChecker", emptyList()) {
            IsolatedCheckers(
                declarations = object : DeclarationCheckers() {
                    override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> =
                        setOf(ExemptionExplanationChecker.declarationChecker)
                },
                types = object : TypeCheckers() {
                    override val resolvedTypeRefCheckers: Set<FirResolvedTypeRefChecker> =
                        setOf(ExemptionExplanationChecker.typeChecker)
                },
            )
        },
        CheckerSubject("MutableCollectionChecker", listOf("MUTABLE_COLLECTION_PUBLIC_API")) { environment ->
            ofBasic(MutableCollectionChecker(environment.session, severities))
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
        ) { environment ->
            val corpusClasspath = environment.corpusClasspath
            ofBasic(
                NonTransitiveDependencyChecker(
                    environment.session,
                    DependencyExposureCheckConfiguration(
                        compileDependencies = corpusClasspath.toSet(),
                        transitiveDependencies = corpusClasspath.filterTo(linkedSetOf()) {
                            it.contains("kotlin-stdlib")
                        },
                    ),
                ),
            )
        },

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

        CheckerSubject("TopLevelJvmNameChecker", listOf("TOP_LEVEL_API_WITHOUT_JVM_NAME")) {
            ofFile(TopLevelJvmNameChecker(severities))
        },

        CheckerSubject("FunctionTypeAliasChecker", listOf("FUNCTION_TYPE_ALIAS_PUBLIC_API")) {
            ofTypeAlias(FunctionTypeAliasChecker(severities))
        },

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
            listOf("COMPANION_API_WITHOUT_JVM_STATIC", "COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS"),
        ) { ofCallable(CompanionJvmExposureChecker(severities)) },

        CheckerSubject("ExemptingCheckers", emptyList(), isolatedOnly = true) { environment ->
            exemptingCheckers(environment)
        },
        CheckerSubject("RedundantExemptionProbingChecker", emptyList(), isolatedOnly = true) { environment ->
            exemptingCheckers(environment).probingRedundantExemptions()
        },
    )

    private fun exemptingCheckers(environment: CheckerEnvironment): IsolatedCheckers {
        val created = all.filter { !it.isolatedOnly }.map { it.createCheckers(environment).declarations }
        fun <C : Any> exempting(select: (DeclarationCheckers) -> Set<C>): Set<C> =
            created.flatMapTo(linkedSetOf()) { checkers -> select(checkers).filter { it is ExemptingChecker } }
        return IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val classCheckers: Set<FirClassChecker> = exempting { it.classCheckers }
                override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> =
                    exempting { it.basicDeclarationCheckers }
                override val functionCheckers: Set<FirFunctionChecker> = exempting { it.functionCheckers }
                override val fileCheckers: Set<FirFileChecker> = exempting { it.fileCheckers }
                override val typeAliasCheckers: Set<FirTypeAliasChecker> = exempting { it.typeAliasCheckers }
                override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> =
                    exempting { it.callableDeclarationCheckers }
            },
        )
    }

    /** Mirrors the wrapping in `WatchdogFirCheckers` with every configurable diagnostic on. */
    private fun IsolatedCheckers.probingRedundantExemptions(): IsolatedCheckers {
        val source = declarations
        val enclosingProbes = EnclosingExemptionProbes(
            source.basicDeclarationCheckers,
            source.functionCheckers,
            source.callableDeclarationCheckers,
            disabled = emptyList(),
        )
        val expectChecker = ExpectExemptionRedundancyChecker(
            basicCheckers = source.basicDeclarationCheckers,
            functionCheckers = source.functionCheckers,
            callableCheckers = source.callableDeclarationCheckers,
            classCheckers = source.classCheckers,
            typeAliasCheckers = source.typeAliasCheckers,
            disabled = emptyList(),
            enclosing = enclosingProbes,
        )
        fun <D : FirDeclaration> Set<FirDeclarationChecker<D>>.probed(): Set<FirDeclarationChecker<D>> =
            if (isEmpty()) emptySet() else setOf(RedundantExemptionProbingChecker(toList()))
        return IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val classCheckers: Set<FirClassChecker> =
                    source.classCheckers.probed() + EnclosingExemptionRedundancyChecker(enclosingProbes)
                override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> =
                    source.basicDeclarationCheckers.probed() + expectChecker
                override val functionCheckers: Set<FirFunctionChecker> = source.functionCheckers.probed()
                override val fileCheckers: Set<FirFileChecker> = source.fileCheckers.probed()
                override val typeAliasCheckers: Set<FirTypeAliasChecker> = source.typeAliasCheckers.probed()
                override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> =
                    source.callableDeclarationCheckers.probed()
            },
        )
    }

    val byName: Map<String, CheckerSubject> = all.associateBy { it.name }

    val emptyCheckers: IsolatedCheckers = IsolatedCheckers()

    private fun ofClass(checker: FirClassChecker): IsolatedCheckers =
        IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val classCheckers: Set<FirClassChecker> = setOf(checker)
            },
        )

    private fun ofBasic(checker: FirBasicDeclarationChecker): IsolatedCheckers =
        IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val basicDeclarationCheckers: Set<FirBasicDeclarationChecker> = setOf(checker)
            },
        )

    private fun ofFunction(checker: FirFunctionChecker): IsolatedCheckers =
        IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val functionCheckers: Set<FirFunctionChecker> = setOf(checker)
            },
        )

    private fun ofFile(checker: FirFileChecker): IsolatedCheckers =
        IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val fileCheckers: Set<FirFileChecker> = setOf(checker)
            },
        )

    private fun ofTypeAlias(checker: FirTypeAliasChecker): IsolatedCheckers =
        IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val typeAliasCheckers: Set<FirTypeAliasChecker> = setOf(checker)
            },
        )

    private fun ofCallable(checker: FirCallableDeclarationChecker): IsolatedCheckers =
        IsolatedCheckers(
            declarations = object : DeclarationCheckers() {
                override val callableDeclarationCheckers: Set<FirCallableDeclarationChecker> = setOf(checker)
            },
        )
}
