package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.renderReadable

internal actual fun ConeKotlinType.renderReadableCompat(): String = renderReadable()
