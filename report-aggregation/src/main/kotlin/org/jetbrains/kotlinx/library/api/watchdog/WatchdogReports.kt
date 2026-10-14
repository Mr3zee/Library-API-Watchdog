package org.jetbrains.kotlinx.library.api.watchdog

import javax.inject.Inject
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile

/**
 * The formats of the [API Watchdog report](https://mr3zee.github.io/Library-API-Watchdog/report). Each format has a switch and a file:
 *
 * ```kotlin
 * apiWatchdog {
 *     reports {
 *         html = false
 *         sarifFile = layout.buildDirectory.file("reports/watchdog.sarif")
 *     }
 * }
 * ```
 */
public abstract class WatchdogReports @Inject internal constructor(private val objectFactory: ObjectFactory) {
    /** Whether the task writes the HTML report. Default is `true`. */
    @get:Input
    public abstract val html: Property<Boolean>

    /** The HTML report file. Default is `build/reports/api-watchdog/report.html`. */
    @get:Internal
    public abstract val htmlFile: RegularFileProperty

    /**
     * Whether the task writes the
     * [SARIF 2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html) report for
     * a code scanning service. Default is `true`.
     */
    @get:Input
    public abstract val sarif: Property<Boolean>

    /** The SARIF report file. Default is `build/reports/api-watchdog/report.sarif`. */
    @get:Internal
    public abstract val sarifFile: RegularFileProperty

    @get:OutputFile
    @get:Optional
    internal val htmlOutput: Provider<RegularFile>
        get() = html.flatMap { if (it) htmlFile else objectFactory.fileProperty() }

    @get:OutputFile
    @get:Optional
    internal val sarifOutput: Provider<RegularFile>
        get() = sarif.flatMap { if (it) sarifFile else objectFactory.fileProperty() }
}
