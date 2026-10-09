package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.name.ClassId

internal class ConfiguredInternalApiAnnotations(
    session: FirSession,
    private val annotationNames: Set<String>,
) : FirExtensionSessionComponent(session) {
    fun contains(annotationClassId: ClassId): Boolean =
        annotationClassId.asSingleFqName().asString() in annotationNames
}

internal val FirSession.configuredInternalApiAnnotations: ConfiguredInternalApiAnnotations?
        by FirSession.nullableSessionComponentAccessor()
