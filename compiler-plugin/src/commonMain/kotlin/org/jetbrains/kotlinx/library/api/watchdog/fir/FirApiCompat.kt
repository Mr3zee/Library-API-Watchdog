package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.nio.file.Path
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeLookupTag
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType
import org.jetbrains.kotlin.name.Name

/** The class of a named function is `FirNamedFunction` since Kotlin 2.3.20 and `FirSimpleFunction` before it. */
internal fun FirDeclaration.isNamedFunction(): Boolean = this is FirFunction && symbol is FirNamedFunctionSymbol

internal expect val FirProperty.isLocalCompat: Boolean

internal expect fun FirBasedSymbol<*>.containingClassSymbolCompat(): FirClassLikeSymbol<*>?

internal fun FirDeclaration.containingClassSymbolCompat(): FirClassLikeSymbol<*>? = symbol.containingClassSymbolCompat()

/** A call of `toSymbol` with a session is ambiguous on Kotlin 2.3.0 when a `CheckerContext` is in scope. */
internal fun ConeClassLikeLookupTag.toSymbolCompat(session: FirSession): FirClassLikeSymbol<*>? = toSymbol(session)

/** `toClassSymbol` without a session exists since Kotlin 2.3.0. A call with a session has the ambiguity of [toSymbolCompat]. */
internal fun ConeClassLikeType.toClassSymbolCompat(session: FirSession): FirClassSymbol<*>? = toClassSymbol(session)

/** Kotlin 2.4.0 hides `getStringArgument` with a session and adds the overload without one. */
internal expect fun FirAnnotation.getStringArgumentCompat(name: Name, session: FirSession): String?

/** `renderReadable` shows a function type as `Function1<String, Boolean>` before Kotlin 2.3.20. */
internal expect fun ConeKotlinType.renderReadableCompat(): String

internal expect val ConeTypeParameterType.typeParameterSymbol: FirTypeParameterSymbol

internal expect fun SourceElement.klibPathCompat(): Path?
