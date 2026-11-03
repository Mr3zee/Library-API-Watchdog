package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.nio.file.Path
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.fir.symbols.impl.FirTypeParameterSymbol
import org.jetbrains.kotlin.fir.types.ConeTypeParameterType

internal expect val ConeTypeParameterType.typeParameterSymbol: FirTypeParameterSymbol

internal expect fun SourceElement.klibPathCompat(): Path?
