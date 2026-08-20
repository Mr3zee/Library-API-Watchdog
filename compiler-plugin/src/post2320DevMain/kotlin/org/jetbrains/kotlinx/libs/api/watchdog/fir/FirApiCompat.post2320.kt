package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.fir.declarations.FirProperty

internal actual val FirProperty.isLocalCompat: Boolean
    get() = isLocal
