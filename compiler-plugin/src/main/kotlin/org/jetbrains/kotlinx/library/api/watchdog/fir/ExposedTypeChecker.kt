package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

/**
 * The base of the style checks. An extension receiver isn't checked, because the user already
 * holds the value.
 */
internal abstract class ExposedTypeChecker(
    exemption: ClassId,
) : PublicSignatureTypeChecker<Name>(
    checkExtensionReceivers = false,
    checkClassSupertypes = false,
    checkTypeAliases = false,
    skipOverrides = true,
    exemption = exemption,
) {
    context(context: CheckerContext)
    override fun isCheckedDeclaration(declaration: FirMemberDeclaration): Boolean =
        declaration.isWatchedPublicSourceApi()
}
