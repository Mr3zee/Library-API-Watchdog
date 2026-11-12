package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.analysis.checkers.getContainingClassSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol

internal actual fun FirBasedSymbol<*>.containingClassSymbolCompat(): FirClassLikeSymbol<*>? = getContainingClassSymbol()
