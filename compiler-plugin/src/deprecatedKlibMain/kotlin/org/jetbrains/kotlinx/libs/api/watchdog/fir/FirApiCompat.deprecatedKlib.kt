package org.jetbrains.kotlinx.libs.api.watchdog.fir

import java.nio.file.Path
import org.jetbrains.kotlin.descriptors.SourceElement

internal actual fun SourceElement.klibPathCompat(): Path? {
    if (javaClass.name != "org.jetbrains.kotlin.library.metadata.KlibDeserializedContainerSource") {
        return null
    }
    val klib = javaClass.getMethod("getKlib").invoke(this)
    val libraryFile = klib.javaClass.getMethod("getLibraryFile").invoke(klib)
    val value = libraryFile.javaClass.getMethod("getPath").invoke(libraryFile) as String
    return Path.of(value)
}
