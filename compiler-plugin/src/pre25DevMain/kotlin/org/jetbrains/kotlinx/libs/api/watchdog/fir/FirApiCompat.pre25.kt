package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType

internal actual fun ConeTypeParameterType.typeParameterSymbolCompat(
    session: FirSession,
): FirTypeParameterSymbol = lookupTag.typeParameterSymbol
