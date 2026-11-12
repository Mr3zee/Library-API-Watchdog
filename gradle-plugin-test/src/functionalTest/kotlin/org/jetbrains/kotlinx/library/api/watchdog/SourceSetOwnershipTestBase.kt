package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.testkit.runner.BuildResult
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.OPEN_API_WITHOUT_SUBCLASS_OPT_IN
import org.jetbrains.kotlinx.library.api.watchdog.Diagnostic.TOP_LEVEL_API_WITHOUT_JVM_NAME

abstract class SourceSetOwnershipTestBase {
    protected fun ModuleSpec.api(name: String, sourceSet: String) {
        source(
            """
                /** An open API. */
                public open class $name

                /** A top-level function. */
                public fun ${name.replaceFirstChar(Char::lowercase)}(): Int = 0
            """,
            name,
            sourceSet = sourceSet,
            defaultImports = false,
        )
    }

    protected fun BuildResult.assertOpenApiBy(vararg reports: Pair<String, String>) =
        assertReportedBy(OPEN_API_WITHOUT_SUBCLASS_OPT_IN, *reports)

    protected fun BuildResult.assertJvmNameBy(vararg reports: Pair<String, String>) =
        assertReportedBy(TOP_LEVEL_API_WITHOUT_JVM_NAME, *reports)

    protected companion object {
        const val EXEMPT_TASK = "exemptExistingApi"
        const val REPORT_TASK = "generateApiWatchdogReport"
        const val JVM = ":compileKotlinJvm"
        const val JS = ":compileKotlinJs"
        const val WASM = ":compileKotlinWasmJs"
        const val COMMON_METADATA = ":compileCommonMainKotlinMetadata"
        const val WEB_METADATA = ":compileWebMainKotlinMetadata"
        const val JVM_AND_JS_TARGETS = "jvm()\njs { nodejs() }"
        const val JVM_JS_AND_WASM_TARGETS = "jvm()\njs { nodejs() }\nwasmJs { nodejs() }"
    }
}
