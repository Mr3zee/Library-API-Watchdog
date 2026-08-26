package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.nio.file.Path
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.library.metadata.KlibDeserializedContainerSource

// Briefly deprecated in 2.4.20-dev-6724 (KT-87006)
@Suppress("UnnecessaryOptInAnnotation")
@OptIn(K1Deprecation::class)
internal actual fun SourceElement.klibPathCompat(): Path? =
    (this as? KlibDeserializedContainerSource)?.klib?.libraryFile?.path?.let(Path::of)
