package org.jetbrains.kotlinx.libs.api.watchdog.fir

import java.nio.file.Path
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.library.metadata.KlibDeserializedContainerSource

internal actual fun SourceElement.klibPathCompat(): Path? =
    (this as? KlibDeserializedContainerSource)?.klib?.libraryFile?.path?.let(Path::of)
