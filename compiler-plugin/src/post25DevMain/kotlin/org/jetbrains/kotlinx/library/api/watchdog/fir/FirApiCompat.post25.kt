package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.fir.resolve.typeParameterSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType

internal actual val ConeTypeParameterType.typeParameterSymbol: FirTypeParameterSymbol
    get() = lookupTag.typeParameterSymbol

internal actual val KtSourceElement.textCompat: CharSequence?
    get() = text
