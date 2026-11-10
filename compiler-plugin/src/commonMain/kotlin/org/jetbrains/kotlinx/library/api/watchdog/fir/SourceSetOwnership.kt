package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.platform.jvm.isJvm

/**
 * The checks that a session runs on its own module. `PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY` runs in
 * every scope.
 */
internal enum class CheckScope { ALL, JVM_ONLY, NONE }

/**
 * A platform compilation compiles each parent source set again as a fragment module with the name of the
 * source set. The fragment is common (`isCommon`) and has the platform of the compilation.
 *
 * - [checkedElsewhere]: the source sets that another compilation checks.
 * - [jvmChecked]: the subset that gets the JVM-only checks in this compilation, because the other
 *   compilation isn't a JVM compilation.
 *
 * The checkers of [org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind.Platform] come from the
 * session of the leaf module, so they still run on every fragment.
 */
internal class SourceSetOwnership(
    private val checkedElsewhere: Set<String>,
    private val jvmChecked: Set<String>,
) {
    fun scopeOf(session: FirSession): CheckScope {
        if (checkedElsewhere.isEmpty()) return CheckScope.ALL
        val module = session.moduleData
        if (!module.isCommon) return CheckScope.ALL
        val sourceSet = module.name.asString().removeSurrounding("<", ">")
        return when {
            sourceSet !in checkedElsewhere -> CheckScope.ALL
            sourceSet in jvmChecked && module.platform.isJvm() -> CheckScope.JVM_ONLY
            else -> CheckScope.NONE
        }
    }

    companion object {
        val NONE = SourceSetOwnership(emptySet(), emptySet())
    }
}
