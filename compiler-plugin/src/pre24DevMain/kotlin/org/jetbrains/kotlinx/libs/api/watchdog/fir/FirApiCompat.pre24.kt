package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.name.Name

internal actual fun FirAnnotation.getStringArgumentCompat(
    name: Name,
    session: FirSession,
): String? = getStringArgument(name, session)
