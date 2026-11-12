package org.jetbrains.kotlinx.library.api.watchdog.handlers

import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.test.frontend.fir.FirOutputArtifact
import org.jetbrains.kotlin.test.model.TestFile

/** `mainFirFilesByTestFile`. Its name is `mainFirFiles` before Kotlin 2.3.20. */
expect val FirOutputArtifact.mainFirFilesByTestFileCompat: Map<TestFile, FirFile>
