package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import java.io.File
import org.jetbrains.kotlin.buildtools.api.CompilationResult
import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
import org.jetbrains.kotlin.buildtools.api.KotlinLogger
import org.jetbrains.kotlin.buildtools.api.KotlinToolchains
import org.jetbrains.kotlin.buildtools.api.jvm.JvmPlatformToolchain

/**
 * Compiles the module once through the Kotlin Build Tools API, in process, with the watchdog
 * compiler plugin recording its diagnostics into [reportFile]. The class files land in a scratch
 * directory: the compilation only exists to run the watchdog checkers, and it is expected to
 * fail with a [CompilationResult.COMPILATION_ERROR] whenever the watchdog diagnostics themselves
 * are errors - the frontend still visits every file and records every diagnostic first.
 */
@OptIn(ExperimentalBuildToolsApi::class)
internal fun compileRecordingDiagnostics(
    request: FixerRequest,
    reportFile: File,
    classesDir: File,
    onCompilerMessage: (String) -> Unit,
): CompilationResult {
    val toolchains = KotlinToolchains.loadImplementation(FixerRequest::class.java.classLoader)
    val jvmToolchain = toolchains.getToolchain(JvmPlatformToolchain::class.java)

    val operationBuilder = jvmToolchain.jvmCompilationOperationBuilder(
        request.sources.map { it.toPath() },
        classesDir.toPath(),
    )
    operationBuilder.compilerArguments.applyArgumentStrings(
        request.compilerArgs + listOf(
            "-P",
            "plugin:${request.pluginId}:diagnosticsOutputFile=${reportFile.absolutePath}",
        )
    )

    val logger = object : KotlinLogger {
        override val isDebugEnabled: Boolean = false
        override fun error(msg: String, throwable: Throwable?) = onCompilerMessage("e: $msg")
        override fun warn(msg: String, throwable: Throwable?) = onCompilerMessage("w: $msg")
        override fun info(msg: String) = Unit
        override fun debug(msg: String) = Unit
        override fun lifecycle(msg: String) = Unit
    }

    return toolchains.createBuildSession().use { session ->
        session.executeOperation(
            operationBuilder.build(),
            toolchains.createInProcessExecutionPolicy(),
            logger,
        )
    }
}
