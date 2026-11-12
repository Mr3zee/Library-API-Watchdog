package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.isLocal

internal actual val FirProperty.isLocalCompat: Boolean
    get() = isLocal
