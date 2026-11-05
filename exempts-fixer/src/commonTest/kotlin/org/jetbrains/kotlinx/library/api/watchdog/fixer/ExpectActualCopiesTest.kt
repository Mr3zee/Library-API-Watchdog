package org.jetbrains.kotlinx.library.api.watchdog.fixer

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.intellij.lang.annotations.Language

class ExpectActualCopiesTest {
    private val parser = KotlinFileParser()
    private val fixer = ExemptionFixer(parser)

    @AfterTest
    fun tearDown() {
        parser.close()
    }

    @Language("kotlin")
    private val common = """
        package com.example

        public expect fun configure(name: String, enabled: Boolean)

        /** A plugin. */
        public expect interface Plugin {
            public fun run(fast: Boolean)
        }

        public expect fun top()
    """.trimIndent()

    @Language("kotlin")
    private val jvm = """
        package com.example

        public actual fun configure(name: String, enabled: Boolean) {}

        public actual interface Plugin {
            public actual fun run(fast: Boolean)
        }

        public actual fun top() {}
    """.trimIndent()

    private val texts = mapOf(COMMON to common, JVM to jvm)

    private fun copies(vararg diagnostics: RecordedDiagnostic, fixes: (String) -> Boolean = { true }): List<RecordedDiagnostic> =
        ExpectActualCopies(parser, texts::get).copies(diagnostics.toList(), pairs(), fixes)

    /** The pairs that the compiler records for [jvm]: each `actual` with its `expect`. */
    private fun pairs(): List<RecordedDiagnostic> = listOf(
        pair("public actual fun configure", "public expect fun configure", "{}", ")"),
        pair("public actual interface Plugin", "/** A plugin. */", "}", "}"),
        pair("public actual fun run", "public fun run", ")", ")"),
        pair("public actual fun top", "public expect fun top", "{}", ")"),
    )

    /** [actualEnd] and [expectEnd] are the last text of each declaration, searched from its start. */
    private fun pair(actualStart: String, expectStart: String, actualEnd: String, expectEnd: String): RecordedDiagnostic {
        val actual = jvm.indexOf(actualStart)
        val actualEndOffset = jvm.indexOf(actualEnd, actual) + actualEnd.length
        val expect = common.indexOf(expectStart)
        val expectEndOffset = common.indexOf(expectEnd, expect) + expectEnd.length
        return RecordedDiagnostic(
            EXPECT_ACTUAL_PAIR, JVM, actual, actualEndOffset,
            listOf(COMMON, expect.toString(), expectEndOffset.toString()),
        )
    }

    private fun onCommon(name: String, snippet: String, from: String = ""): RecordedDiagnostic {
        val start = common.indexOf(snippet, common.indexOf(from))
        return RecordedDiagnostic(name, COMMON, start, start + snippet.length)
    }

    private fun fixedJvm(copies: List<RecordedDiagnostic>): String? = fixer.fix(JVM, jvm, copies).newText

    @Test
    fun `a parameter exemption goes to the actual function`() {
        val copies = copies(onCommon("BOOLEAN_PARAMETER_PUBLIC_API", "enabled"))

        assertEquals(
            """
                package com.example

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter

                @IntentionallyBooleanParameter(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                public actual fun configure(name: String, enabled: Boolean) {}

                public actual interface Plugin {
                    public actual fun run(fast: Boolean)
                }

                public actual fun top() {}
            """.trimIndent(),
            fixedJvm(copies),
        )
    }

    @Test
    fun `a member exemption goes to the actual member and a class exemption to the actual class`() {
        val copies = copies(
            onCommon("BOOLEAN_PARAMETER_PUBLIC_API", "fast"),
            onCommon("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "Plugin"),
        )

        assertEquals(
            """
                package com.example

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

                public actual fun configure(name: String, enabled: Boolean) {}

                @IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                public actual interface Plugin {
                    @IntentionallyBooleanParameter(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                    public actual fun run(fast: Boolean)
                }

                public actual fun top() {}
            """.trimIndent(),
            fixedJvm(copies),
        )
    }

    @Test
    fun `a constructor exemption goes to the actual constructor and a property exemption to the val parameter`() {
        @Language("kotlin")
        val common = """
            package com.example

            public expect class Point(flag: Boolean?) {
                public val flag: Boolean?
            }
        """.trimIndent()

        @Language("kotlin")
        val jvm = """
            package com.example

            public actual class Point actual constructor(public actual val flag: Boolean?)
        """.trimIndent()

        fun pair(actual: String, expect: String) = RecordedDiagnostic(
            EXPECT_ACTUAL_PAIR, JVM, jvm.indexOf(actual), jvm.indexOf(actual) + actual.length,
            listOf(COMMON, common.indexOf(expect).toString(), (common.indexOf(expect) + expect.length).toString()),
        )
        val pairs = listOf(
            pair("actual constructor(public actual val flag: Boolean?)", "(flag: Boolean?)"),
            pair("public actual val flag: Boolean?", "public val flag: Boolean?"),
        )
        val onParameter = common.indexOf("Boolean?")
        val onProperty = common.lastIndexOf("Boolean?")
        val diagnostics = listOf(
            RecordedDiagnostic("NULLABLE_BOOLEAN_PUBLIC_API", COMMON, onParameter, onParameter + 8),
            RecordedDiagnostic("NULLABLE_BOOLEAN_PUBLIC_API", COMMON, onProperty, onProperty + 8),
        )

        val copies = ExpectActualCopies(parser, mapOf(COMMON to common, JVM to jvm)::get).copies(diagnostics, pairs) { true }

        assertEquals(
            """
                package com.example

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyNullableBoolean

                public actual class Point @IntentionallyNullableBoolean(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) actual constructor(@IntentionallyNullableBoolean(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) public actual val flag: Boolean?)
            """.trimIndent(),
            fixer.fix(JVM, jvm, copies).newText,
        )
    }

    @Test
    fun `a file exemption, a removal, and a warning aren't copied`() {
        val copies = copies(
            onCommon("TOP_LEVEL_API_WITHOUT_JVM_NAME", "top"),
            onCommon("REDUNDANT_EXEMPTION", "configure"),
            onCommon("BOOLEAN_PARAMETER_PUBLIC_API", "enabled"),
            fixes = { it != "BOOLEAN_PARAMETER_PUBLIC_API" },
        )

        assertEquals(emptyList(), copies)
    }

    @Test
    fun `a diagnostic outside every expect isn't copied`() {
        assertEquals(emptyList(), copies(onCommon("BOOLEAN_PARAMETER_PUBLIC_API", "package")))
    }

    private companion object {
        const val COMMON = "/project/src/commonMain/kotlin/com/example/Api.kt"
        const val JVM = "/project/src/jvmMain/kotlin/com/example/Api.jvm.kt"
    }
}
