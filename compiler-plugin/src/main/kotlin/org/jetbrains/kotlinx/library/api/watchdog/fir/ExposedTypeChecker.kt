package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * Base for style checks that hunt a type down in publicly visible callable signatures and type
 * parameter bounds. The full declaration and type traversal comes from
 * [PublicSignatureTypeChecker]. This specialization applies their common policy:
 *
 * - Overrides are skipped because their signature is fixed by the overridden declaration.
 * - Extension receivers are skipped because they provide functionality for values users already
 *   hold instead of exposing new ones.
 * - Class supertypes, class context parameters, and type aliases are outside these checks' scope.
 * - The exemption annotation is honored on a whole declaration, on a single (type) parameter, or
 *   on a type usage, where it covers the annotated type and everything nested in it.
 */
internal abstract class ExposedTypeChecker(
    exemption: ClassId,
) : PublicSignatureTypeChecker<Name>(
    checkExtensionReceivers = false,
    checkClassSupertypes = false,
    checkClassContextParameters = false,
    checkTypeAliases = false,
    skipOverrides = true,
    exemption = exemption,
) {
    context(context: CheckerContext)
    override fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicSourceApi()
}
