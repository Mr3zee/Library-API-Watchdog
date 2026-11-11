package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirModuleData

internal enum class CheckScope {
    ALL,

    /** Only the checks that a metadata compilation can't run. */
    PLATFORM,

    NONE,
}

/**
 * A platform compilation compiles each parent source set again as a common fragment module named after
 * the source set. A [org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind.Platform] checker of the
 * leaf session checks every fragment, so it calls [scopeOf] for each declaration.
 */
internal class SourceSetOwnership(
    private val checkedElsewhere: Set<String>,
    private val platformChecked: Set<String>,
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

    private val FirModuleData.sourceSetName: String get() = name.asString().removeSurrounding("<", ">")

    companion object {
        val NONE = SourceSetOwnership(emptySet(), emptySet())
    }
}
