package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.name.ClassId

internal class ConfiguredInternalApiAnnotations(
    session: FirSession,
    annotationNames: Set<String>,
) : FirExtensionSessionComponent(session) {
    private val patterns = NamePatterns(annotationNames)

    fun contains(annotationClassId: ClassId): Boolean =
        patterns.matches(annotationClassId.asSingleFqName().asString())
}

internal val FirSession.configuredInternalApiAnnotations: ConfiguredInternalApiAnnotations?
        by FirSession.nullableSessionComponentAccessor()
