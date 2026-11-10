package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirModuleData

/** The checks that a session runs on its own module. */
internal enum class CheckScope {
    ALL,

    /**
     * The checks that a metadata compilation can't run: the Java interop checks, the dependency check,
     * and the `REDUNDANT_EXEMPTION` verdicts that need the `actual` declarations.
     */
    PLATFORM,

    NONE,
}

/**
 * A platform compilation compiles each parent source set again as a fragment module with the name of the
 * source set. The fragment is common (`isCommon`) and has the platform of the compilation.
 *
 * - [checkedElsewhere]: the source sets that another compilation checks.
 * - [platformChecked]: the subset that gets the [CheckScope.PLATFORM] checks in this compilation.
 * - [transitiveDependencies]: the dependency paths that every target of a parent source set publishes
 *   transitively. The dependency check of the fragment uses them instead of the paths of this compilation.
 *
 * The checkers of [org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind.Platform] come from the
 * session of the leaf module and run on every fragment, so they ask [scopeOf] for each declaration.
 */
internal class SourceSetOwnership(
    private val checkedElsewhere: Set<String>,
    private val platformChecked: Set<String>,
    private val transitiveDependencies: Map<String, Set<String>> = emptyMap(),
) {
    fun scopeOf(module: FirModuleData): CheckScope {
        if (checkedElsewhere.isEmpty() || !module.isCommon) return CheckScope.ALL
        val sourceSet = module.sourceSetName
        return when (sourceSet) {
            !in checkedElsewhere -> CheckScope.ALL
            in platformChecked -> CheckScope.PLATFORM
            else -> CheckScope.NONE
        }
    }

    /** The transitive dependency paths of the fragment [module], or null for the paths of the compilation. */
    fun transitiveDependenciesOf(module: FirModuleData): Set<String>? =
        if (module.isCommon) transitiveDependencies[module.sourceSetName] else null

    private val FirModuleData.sourceSetName: String get() = name.asString().removeSurrounding("<", ">")

    companion object {
        val NONE = SourceSetOwnership(emptySet(), emptySet())
    }
}
