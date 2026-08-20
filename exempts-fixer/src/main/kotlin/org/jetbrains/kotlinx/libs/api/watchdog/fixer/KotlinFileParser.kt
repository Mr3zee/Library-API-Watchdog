package org.jetbrains.kotlinx.libs.api.watchdog.fixer

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.setupIdeaStandaloneExecution
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * A minimal standalone Kotlin PSI parsing environment: the same application and project
 * environments the K2 CLI pipeline builds on, with just the Kotlin file type and parser
 * definition registered on top. Parsing is all the fixer needs - the semantic analysis already
 * happened in the compilation that recorded the diagnostics, so the recorded offsets locate the
 * target declarations syntactically.
 */
internal class KotlinFileParser : AutoCloseable {
    private val disposable = Disposer.newDisposable("watchdog-exempts-fixer")
    private val psiFactory: KtPsiFactory

    init {
        setupIdeaStandaloneExecution()
        val applicationEnvironment = KotlinCoreApplicationEnvironment.create(
            parentDisposable = disposable,
            environmentMode = KotlinCoreApplicationEnvironmentMode.Production,
        )
        applicationEnvironment.registerFileType(KotlinFileType.INSTANCE, KotlinFileType.EXTENSION)
        applicationEnvironment.registerParserDefinition(KotlinParserDefinition())
        val projectEnvironment = KotlinCoreProjectEnvironment(disposable, applicationEnvironment)
        psiFactory = KtPsiFactory(projectEnvironment.project, markGenerated = false)
    }

    /** Parses [text], which must use `\n` line endings. PSI rejects anything else. */
    fun parse(fileName: String, text: String): KtFile = psiFactory.createFile(fileName, text)

    override fun close() {
        Disposer.dispose(disposable)
    }
}
