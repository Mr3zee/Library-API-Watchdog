@file:Suppress("RedundantVisibilityModifier")

package org.jetbrains.kotlinx.library.api.watchdog.fixer

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.intellij.lang.annotations.Language

class ExemptionFixerTest {
    private val parser = KotlinFileParser()
    private val fixer = ExemptionFixer(parser)

    @AfterTest
    fun tearDown() {
        parser.close()
    }

    private fun diagnostic(name: String, text: String, snippet: String): RecordedDiagnostic {
        val start = text.indexOf(snippet)
        require(start >= 0) { "Snippet not found: $snippet" }
        return RecordedDiagnostic(name, FILE_PATH, start, start + snippet.length)
    }

    private fun fix(text: String, vararg diagnostics: RecordedDiagnostic): FileFixResult =
        fixer.fix(FILE_PATH, text, diagnostics.toList())

    @Test
    fun `message parameters follow fixes skips and warnings after source edits`() {
        val text = "package test\n\npublic open class Api\n"
        val result = fixer.fix(
            FILE_PATH,
            text,
            listOf(
                diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Api")
                    .copy(messageParameters = listOf("class", "Api", "Use opt-in.")),
                diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public open class Api")
                    .copy(messageParameters = listOf("class", "Api")),
                diagnostic("UNKNOWN_CHECK", text, "public open class Api")
                    .copy(messageParameters = listOf("unknown")),
            ),
            fixes = { it != "UNDOCUMENTED_PUBLIC_API" },
        )
        assertEquals(listOf("class", "Api", "Use opt-in."), result.applied.single().messageParameters)
        assertEquals(listOf("class", "Api"), result.warnings.single().messageParameters)
        assertEquals(listOf("unknown"), result.skipped.single().messageParameters)
        assertTrue(result.warnings.single().line > 3)
    }

    @Test
    fun `reported name ranges follow source edits with each line ending`() {
        for (newline in listOf("\n", "\r\n", "\r")) {
            val text = "package test\n\npublic open class Api\n".replace("\n", newline)
            val diagnostics = listOf("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "UNDOCUMENTED_PUBLIC_API", "UNKNOWN_CHECK").map { name ->
                diagnostic(name, text, "public open class Api").copy(
                    reportedStartOffset = text.indexOf("Api"),
                    reportedEndOffset = text.indexOf("Api") + 3,
                )
            }
            val result = fixer.fix(FILE_PATH, text, diagnostics, fixes = { it != "UNDOCUMENTED_PUBLIC_API" })
            val rewrittenLine = lineOf(result.newText!!.replace("\r\n", "\n").replace('\r', '\n'), "public open class Api")
            val expected = DiagnosticSourceRange(rewrittenLine, 19, rewrittenLine, 22)
            assertEquals(expected, result.applied.single().sourceRange, "applied, newline=$newline")
            assertEquals(expected, result.warnings.single().sourceRange, "warning, newline=$newline")
            assertEquals(expected, result.skipped.single().sourceRange, "skipped, newline=$newline")

            val readOnly = fixer.fix(FILE_PATH, text, diagnostics, rewriteLocations = false, fixes = { it != "UNDOCUMENTED_PUBLIC_API" })
            assertEquals(DiagnosticSourceRange(3, 19, 3, 22), readOnly.applied.single().sourceRange)
            assertEquals(DiagnosticSourceRange(3, 19, 3, 22), readOnly.warnings.single().sourceRange)
        }
    }

    @Test
    fun `reported range can span multiple lines`() {
        val text = "package test\n\npublic fun configure(\n    enabled: Boolean,\n    name: String,\n) = Unit\n"
        val recorded = diagnostic("UNKNOWN_CHECK", text, "public fun configure").copy(
            reportedStartOffset = text.indexOf("enabled"),
            reportedEndOffset = text.indexOf("String") + 6,
        )
        val result = fix(text, recorded)
        assertEquals(DiagnosticSourceRange(4, 5, 5, 17), result.skipped.single().sourceRange)
    }

    @Test
    fun `empty invalid and replaced reported ranges have no source highlight`() {
        val text = "package test\n\n@SubclassOptInRequired\npublic open class Api\n"
        val annotation = text.indexOf("@SubclassOptInRequired")
        val recorded = diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "@SubclassOptInRequired").copy(
            reportedStartOffset = annotation,
            reportedEndOffset = annotation + "@SubclassOptInRequired".length,
        )
        assertNull(fix(text, recorded).applied.single().sourceRange)
        for ((start, end) in listOf(annotation to annotation, -1 to annotation, annotation to text.length + 1)) {
            val invalid = recorded.copy(name = "UNKNOWN_CHECK", reportedStartOffset = start, reportedEndOffset = end)
            assertNull(fix(text, invalid).skipped.single().sourceRange)
        }
    }

    @Test
    fun `a redundant exemption and its unused import go away`() {
        val text = """
            package test

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            /** Settled. */
            @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
            public class Settled

            /** Open. */
            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN) public open class Base

            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            public open class Other
        """.trimIndent()
        val result = fix(
            text,
            diagnostic("REDUNDANT_EXEMPTION", text, "@IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)")
                .copy(messageParameters = listOf("IntentionallyDataClass", "declaration")),
            diagnostic("REDUNDANT_EXEMPTION", text, "@IntentionallyOpen(reason = ExemptionReason.API_DESIGN) public open class Base")
                .let { it.copy(endOffset = it.startOffset + "@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)".length) }
                .copy(messageParameters = listOf("IntentionallyOpen", "declaration")),
        )

        assertEquals(
            """
                package test

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

                /** Settled. */
                public class Settled

                /** Open. */
                public open class Base

                @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
                public open class Other
            """.trimIndent(),
            result.newText,
        )
        assertEquals(listOf("IntentionallyDataClass", "IntentionallyOpen"), result.applied.map { it.annotation })
        assertEquals(listOf(7, 10), result.applied.map { it.line })
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `a removed annotation keeps the import that a class literal uses`() {
        val text = """
            package test

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            /** Settled. */
            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            public class Settled

            /** The marker. */
            public val marker: Any = IntentionallyOpen::class
        """.trimIndent()
        val result = fix(
            text,
            diagnostic("REDUNDANT_EXEMPTION", text, "@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)")
                .copy(messageParameters = listOf("IntentionallyOpen", "declaration")),
        )

        assertEquals(
            """
                package test

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

                /** Settled. */
                public class Settled

                /** The marker. */
                public val marker: Any = IntentionallyOpen::class
            """.trimIndent(),
            result.newText,
        )
    }

    @Test
    fun `a removal and a new exemption on one declaration combine`() {
        val text = """
            package test

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            /** A point. */
            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            public data class Point(val x: Int)
        """.trimIndent()
        val result = fix(
            text,
            diagnostic("REDUNDANT_EXEMPTION", text, "@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)")
                .copy(messageParameters = listOf("IntentionallyOpen", "declaration")),
            diagnostic("DATA_CLASS_PUBLIC_API", text, "Point"),
        )

        assertEquals(
            """
                package test

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass

                /** A point. */
                @IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                public data class Point(val x: Int)
            """.trimIndent(),
            result.newText,
        )
        assertEquals(setOf("IntentionallyOpen", "IntentionallyDataClass"), result.applied.map { it.annotation }.toSet())
    }

    @Test
    fun `an annotation list goes when the run removes every entry`() {
        val text = """
            package test

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            /** Listed. */
            @[IntentionallyOpen(reason = ExemptionReason.API_DESIGN) IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)]
            public class Listed
        """.trimIndent()
        val open = "IntentionallyOpen(reason = ExemptionReason.API_DESIGN)"
        val data = "IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)"
        val openStart = text.indexOf(open)
        val dataStart = text.indexOf(data)
        val result = fix(
            text,
            RecordedDiagnostic("REDUNDANT_EXEMPTION", FILE_PATH, openStart, openStart + open.length, listOf("IntentionallyOpen", "declaration")),
            RecordedDiagnostic("REDUNDANT_EXEMPTION", FILE_PATH, dataStart, dataStart + data.length, listOf("IntentionallyDataClass", "declaration")),
        )

        assertEquals(
            """
                package test

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason

                /** Listed. */
                public class Listed
            """.trimIndent(),
            result.newText,
        )
    }

    @Test
    fun `a removed entry of an annotation list leaves the other entries and its import goes once`() {
        val text = """
            package test

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            /** Settled. */
            @[IntentionallyOpen(reason = ExemptionReason.API_DESIGN) Suppress("unused")]
            public class Settled

            /** Alone. */
            @[IntentionallyOpen(reason = ExemptionReason.API_DESIGN)]
            public class Alone
        """.trimIndent()
        val entry = "IntentionallyOpen(reason = ExemptionReason.API_DESIGN)"
        val first = text.indexOf(entry)
        val second = text.indexOf(entry, first + 1)
        val parameters = listOf("IntentionallyOpen", "declaration")
        val result = fix(
            text,
            RecordedDiagnostic("REDUNDANT_EXEMPTION", FILE_PATH, first, first + entry.length, parameters),
            RecordedDiagnostic("REDUNDANT_EXEMPTION", FILE_PATH, second, second + entry.length, parameters),
        )

        assertEquals(
            """
                package test

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason

                /** Settled. */
                @[Suppress("unused")]
                public class Settled

                /** Alone. */
                public class Alone
            """.trimIndent(),
            result.newText,
        )
        assertEquals(listOf(6, 10), result.applied.map { it.line })
    }

    @Test
    fun `annotation goes between KDoc and declaration`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** An open service. */
            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertEquals(
            """
                package com.example

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

                /** An open service. */
                @IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                public open class Service
            """.trimIndent(),
            result.newText,
        )
        assertEquals(
            listOf(AppliedFix("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", "IntentionallyOpen", FILE_PATH, 8)),
            result.applied,
        )
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `warnings stay and get the rewritten line`() {
        @Language("kotlin")
        val text = """
            package com.example

            public open class Service

            public data class Point(val x: Int)
        """.trimIndent()

        val result = fixer.fix(
            FILE_PATH,
            text,
            listOf(
                diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public open class Service"),
                diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"),
                diagnostic("DATA_CLASS_PUBLIC_API", text, "public data class Point"),
            ),
            fixes = { it != "UNDOCUMENTED_PUBLIC_API" && it != "DATA_CLASS_PUBLIC_API" },
        )

        val newText = result.newText!!
        assertFalse("TODO: Document" in newText)
        assertFalse("IntentionallyDataClass" in newText)
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Service")
        assertEquals(
            listOf(
                WarningDiagnostic("UNDOCUMENTED_PUBLIC_API", FILE_PATH, lineOf(newText, "public open class Service")),
                WarningDiagnostic("DATA_CLASS_PUBLIC_API", FILE_PATH, lineOf(newText, "public data class Point")),
            ),
            result.warnings,
        )
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `a file with warnings only isn't rewritten`() {
        @Language("kotlin")
        val text = "package com.example\n\npublic open class Service\n"

        val result = fixer.fix(
            FILE_PATH,
            text,
            listOf(diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service")),
            fixes = { false },
        )

        assertNull(result.newText)
        assertEquals(listOf(WarningDiagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", FILE_PATH, 3)), result.warnings)
    }

    @Test
    fun `reported lines include every earlier multiline insertion`() {
        @Language("kotlin")
        val text = """
            package com.example

            public open class Service

            public data class Point(val x: Int)

            public class NeedsDocumentation
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"),
            diagnostic("DATA_CLASS_PUBLIC_API", text, "public data class Point"),
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public class NeedsDocumentation"),
        )

        val newText = result.newText!!
        val appliedByDiagnostic = result.applied.associateBy { it.diagnostic }
        assertEquals(
            lineOf(newText, "public open class Service"),
            appliedByDiagnostic.getValue("OPEN_API_WITHOUT_SUBCLASS_OPT_IN").line,
        )
        assertEquals(
            lineOf(newText, "public data class Point"),
            appliedByDiagnostic.getValue("DATA_CLASS_PUBLIC_API").line,
        )
        assertEquals(
            lineOf(newText, "public class NeedsDocumentation"),
            appliedByDiagnostic.getValue("UNDOCUMENTED_PUBLIC_API").line,
        )
        assertContains(newText, "$PLACEHOLDER_KDOC\npublic class NeedsDocumentation")
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `inline insertions don't shift reported lines`() {
        @Language("kotlin")
        val text = """
            package org.jetbrains.kotlinx.library.api.watchdog

            public class Retry(retries: Int = 3, host: String)

            public data class Point(val x: Int)
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("REQUIRED_PARAMETER_AFTER_OPTIONAL", text, "host: String"),
            diagnostic("DATA_CLASS_PUBLIC_API", text, "public data class Point"),
        )

        val appliedByDiagnostic = result.applied.associateBy { it.diagnostic }
        assertEquals(3, appliedByDiagnostic.getValue("REQUIRED_PARAMETER_AFTER_OPTIONAL").line)
        assertEquals(6, appliedByDiagnostic.getValue("DATA_CLASS_PUBLIC_API").line)
    }

    @Test
    fun `nested declaration keeps its indent`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A holder. */
            public class Holder {
                /** Leaks state. */
                public fun leak(): MutableList<String> = mutableListOf()
            }
        """.trimIndent()

        val result = fix(text, diagnostic("MUTABLE_COLLECTION_PUBLIC_API", text, "MutableList<String>"))

        assertContains(
            result.newText!!,
            "    /** Leaks state. */\n" +
                    "    @IntentionallyMutableCollection(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "    public fun leak(): MutableList<String> = mutableListOf()",
        )
    }

    @Test
    fun `annotation goes above existing annotations`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** Squares. */
            @Suppress("NOTHING_TO_INLINE")
            public inline fun squared(value: Int): Int = value * value
        """.trimIndent()

        val result = fix(text, diagnostic("INLINE_FUNCTION_WITH_LOGIC", text, "@Suppress(\"NOTHING_TO_INLINE\")"))

        assertContains(
            result.newText!!,
            "/** Squares. */\n" +
                    "@IntentionallyInlinedLogic(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "@Suppress(\"NOTHING_TO_INLINE\")",
        )
    }

    @Test
    fun `file facade gets file annotation above package`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A top-level function. */
            public fun topLevel(): Int = 0
        """.trimIndent()

        val result = fix(text, diagnostic("TOP_LEVEL_API_WITHOUT_JVM_NAME", text, "public fun topLevel(): Int = 0"))

        assertEquals(
            """
                @file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

                package com.example

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName

                /** A top-level function. */
                public fun topLevel(): Int = 0
            """.trimIndent(),
            result.newText,
        )
    }

    @Test
    fun `file annotation joins existing file annotations`() {
        val text = """
            @file:JvmName("Explicit")

            package com.example

            /** A top-level function. */
            public fun topLevel(): Int = 0
        """.trimIndent()

        val result = fix(text, diagnostic("TOP_LEVEL_API_WITHOUT_JVM_NAME", text, "public fun topLevel(): Int = 0"))

        assertTrue(
            result.newText!!.startsWith(
                "@file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                        "@file:JvmName(\"Explicit\")"
            ),
        )
        assertContains(result.newText, "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName")
    }

    @Test
    fun `existing imports are reused, not duplicated`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            public open class Documented
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Documented"),
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public open class Documented"),
        )

        val newText = result.newText!!
        assertEquals(1, Regex.escape("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen").toRegex().findAll(newText).count())
        assertContains(
            newText,
            "$PLACEHOLDER_KDOC\n" +
                    "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public open class Documented",
        )
        assertFalse("IntentionallyUndocumented" in newText)
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `wildcard import covers everything`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.*

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        val newText = result.newText!!
        assertEquals(1, newText.lines().count { it.startsWith("import ") })
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun `import alias is respected`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen as Open

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertContains(result.newText!!, "@Open(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun `conflicting short name falls back to qualified name`() {
        @Language("kotlin")
        val text = """
            package com.example

            import some.other.pkg.IntentionallyOpen

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertContains(
            result.newText!!,
            "@org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen(" +
                    "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
        )
    }

    @Test
    fun `same package needs no imports`() {
        @Language("kotlin")
        val text = """
            package org.jetbrains.kotlinx.library.api.watchdog

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        val newText = result.newText!!
        assertTrue(newText.lines().none { it.startsWith("import ") })
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun `same annotation is applied once for several diagnostics`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** Merges two buffers. */
            public fun merge(left: MutableList<String>, right: MutableSet<String>) {}
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("MUTABLE_COLLECTION_PUBLIC_API", text, "MutableList<String>"),
            diagnostic("MUTABLE_COLLECTION_PUBLIC_API", text, "MutableSet<String>"),
        )

        val newText = result.newText!!
        assertEquals(1, "@IntentionallyMutableCollection".toRegex(RegexOption.LITERAL).findAll(newText).count())
        assertEquals(1, result.applied.size)
    }

    @Test
    fun `implicit primary constructor gets the constructor keyword`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A legacy signature. */
            public class Retry(retries: Int = 3, host: String)
        """.trimIndent()

        val result = fix(text, diagnostic("REQUIRED_PARAMETER_AFTER_OPTIONAL", text, "host: String"))

        assertContains(
            result.newText!!,
            "public class Retry @IntentionallyRequiredParameterAfterOptional(" +
                    "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) constructor(retries: Int = 3, host: String)",
        )
    }

    @Test
    fun `primary constructor diagnostic reported on class name finds constructor`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A legacy rectangle. */
            public class Rect(width: Int, height: Int)
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS", text, "Rect"),
        )

        assertContains(
            result.newText!!,
            "public class Rect @IntentionallyInconsistentParameterOrder(" +
                    "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) constructor(width: Int, height: Int)",
        )
    }

    @Test
    fun `explicit primary constructor is annotated inline`() {
        @Suppress("RedundantConstructorKeyword")
        @Language("kotlin")
        val text = """
            package com.example

            /** A legacy signature. */
            public class Retry constructor(retries: Int = 3, host: String)
        """.trimIndent()

        val result = fix(text, diagnostic("REQUIRED_PARAMETER_AFTER_OPTIONAL", text, "host: String"))

        assertContains(
            result.newText!!,
            "public class Retry @IntentionallyRequiredParameterAfterOptional(" +
                    "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) constructor(retries: Int = 3, host: String)",
        )
    }

    @Test
    fun `class type parameter bound is annotated inline`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A box constrained to mutable lists. */
            public class Box<T : MutableList<String>>
        """.trimIndent()

        val result = fix(text, diagnostic("MUTABLE_COLLECTION_PUBLIC_API", text, "MutableList<String>"))

        assertContains(
            result.newText!!,
            "public class Box<@IntentionallyMutableCollection(" +
                    "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) T : MutableList<String>>",
        )
    }

    @Test
    fun `constructor val parameter is annotated inline`() {
        @Language("kotlin")
        val text = """
            package com.example

            /**
             * A user handle.
             *
             * @param raw the raw value.
             */
            @JvmInline
            public value class UserHandle(public val raw: String)

            /** A wrapper storing the handle. */
            public class Wrapper(public val handle: UserHandle)
        """.trimIndent()

        val result = fix(text, diagnostic("MANGLED_JVM_NAME_PUBLIC_API", text, "public val handle: UserHandle"))

        assertContains(
            result.newText!!,
            "public class Wrapper(@IntentionallyMangledJvmName(" +
                    "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) public val handle: UserHandle)",
        )
    }

    @Test
    fun `undocumented declaration gets a placeholder KDoc`() {
        @Language("kotlin")
        val text = """
            package com.example

            public class NeedsDocumentation {
                public fun undocumented() {}
            }
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "NeedsDocumentation"),
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "undocumented()"),
        )

        assertEquals(
            """
                package com.example

                $PLACEHOLDER_KDOC
                public class NeedsDocumentation {
                    $PLACEHOLDER_KDOC
                    public fun undocumented() {}
                }
            """.trimIndent(),
            result.newText,
        )
        assertEquals(
            listOf(
                AppliedFix("UNDOCUMENTED_PUBLIC_API", null, FILE_PATH, 4),
                AppliedFix("UNDOCUMENTED_PUBLIC_API", null, FILE_PATH, 6),
            ),
            result.applied.sortedBy { it.line },
        )
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `placeholder KDoc follows comments bound to the declaration`() {
        @Language("kotlin")
        val text = """
            package com.example

            // A line comment isn't KDoc.
            /* Neither is a block comment. */
            public class Commented
        """.trimIndent()

        val result = fix(text, diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public class Commented"))

        assertContains(
            result.newText!!,
            "// A line comment isn't KDoc.\n/* Neither is a block comment. */\n$PLACEHOLDER_KDOC\npublic class Commented",
        )
    }

    @Test
    fun `already documented declaration is skipped`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** Documented after the report was written. */
            public class Documented
        """.trimIndent()

        val result = fix(text, diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public class Documented"))

        assertNull(result.newText)
        assertContains(result.skipped.single().reason, "already has a KDoc")
    }

    @Test
    fun `undocumented constructor property gets a placeholder KDoc`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A session. */
            public class Session(public val id: Int)

            /** A user. */
            public class User(
                /** The name. */
                public val name: String,
                public val handle: UserHandle,
            )
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "id"),
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "handle"),
            diagnostic("MANGLED_JVM_NAME_PUBLIC_API", text, "UserHandle"),
        )

        val newText = result.newText!!
        assertContains(newText, "public class Session($PLACEHOLDER_KDOC public val id: Int)")
        assertContains(
            newText,
            "    public val name: String,\n" +
                    "    $PLACEHOLDER_KDOC\n" +
                    "    @IntentionallyMangledJvmName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) " +
                    "public val handle: UserHandle,\n",
        )
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `DslMarker fix carries no arguments`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A DSL marker without explicit targets. */
            @DslMarker
            public annotation class TargetlessDsl
        """.trimIndent()

        val result = fix(text, diagnostic("DSL_MARKER_WITHOUT_EXPLICIT_TARGETS", text, "TargetlessDsl"))

        assertContains(
            result.newText!!,
            "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility\n@DslMarker\npublic annotation class TargetlessDsl",
        )
        assertContains(result.newText, "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility")
        assertTrue(result.newText.lines().none { "ExemptionReason" in it })
    }

    @Test
    fun `markerless subclass OptIn is replaced by the exemption`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A connector nobody may extend. Except everybody can. */
            @SubclassOptInRequired
            public abstract class Connector
        """.trimIndent()

        val result = fix(text, diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "@SubclassOptInRequired"))

        assertEquals(
            """
                package com.example

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

                /** A connector nobody may extend. Except everybody can. */
                @IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
                public abstract class Connector
            """.trimIndent(),
            result.newText,
        )
        assertEquals(
            listOf(AppliedFix("SUBCLASS_OPT_IN_WITHOUT_MARKERS", "IntentionallyOpen", FILE_PATH, 7)),
            result.applied,
        )
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `markerless subclass OptIn with empty arguments is replaced inline`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A plugin contract. */
            @SubclassOptInRequired() public interface Plugin
        """.trimIndent()

        val result = fix(text, diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "@SubclassOptInRequired()"))

        assertContains(
            result.newText!!,
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) public interface Plugin",
        )
    }

    @Test
    fun `markerless subclass OptIn inside an annotation list keeps the list syntax`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A connector. */
            @[SubclassOptInRequired Suppress("unused")]
            public abstract class Connector
        """.trimIndent()

        val result = fix(text, diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "SubclassOptInRequired"))

        assertContains(
            result.newText!!,
            "@[IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY) Suppress(\"unused\")]",
        )
    }

    @Test
    fun `markerless subclass OptIn is removed when the class is already exempt`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            /** A connector. */
            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            @SubclassOptInRequired
            public abstract class Connector
        """.trimIndent()

        val result = fix(text, diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "@SubclassOptInRequired"))

        assertEquals(
            """
                package com.example

                import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

                /** A connector. */
                @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
                public abstract class Connector
            """.trimIndent(),
            result.newText,
        )
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `markerless subclass OptIn is removed inline when the class is already exempt`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            /** A plugin contract. */
            @SubclassOptInRequired @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            public interface Plugin
        """.trimIndent()

        val result = fix(text, diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "@SubclassOptInRequired"))

        assertContains(
            result.newText!!,
            "/** A plugin contract. */\n" +
                    "@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)\n" +
                    "public interface Plugin",
        )
    }

    @Test
    fun `the replaced annotation still leaves space for other exemptions`() {
        @Language("kotlin")
        val text = """
            package com.example

            /**
             * A session.
             *
             * @param id the session identifier.
             */
            @SubclassOptInRequired
            public abstract class Session(public val id: Int)
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "@SubclassOptInRequired"),
            diagnostic("STATEFUL_CLASS_WITHOUT_EQUALS", text, "public abstract class Session(public val id: Int)"),
            diagnostic("STATEFUL_CLASS_WITHOUT_HASH_CODE", text, "public abstract class Session(public val id: Int)"),
            diagnostic("STATEFUL_CLASS_WITHOUT_TO_STRING", text, "public abstract class Session(public val id: Int)"),
        )

        assertContains(
            result.newText!!,
            "@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public abstract class Session(public val id: Int)",
        )
        assertEquals(4, result.applied.size)
    }

    @Test
    fun `all three stateful diagnostics collapse to the combined annotation`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A session. */
            public class Session(public val id: Int)
        """.trimIndent()
        val declaration = "public class Session(public val id: Int)"

        val result = fix(
            text,
            diagnostic("STATEFUL_CLASS_WITHOUT_TO_STRING", text, declaration),
            diagnostic("STATEFUL_CLASS_WITHOUT_EQUALS", text, declaration),
            diagnostic("STATEFUL_CLASS_WITHOUT_HASH_CODE", text, declaration),
        )

        val newText = result.newText!!
        assertContains(
            newText,
            "/** A session. */\n" +
                    "@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    declaration,
        )
        assertFalse(newText.contains("@IntentionallyWithoutEquals("))
        assertFalse(newText.contains("@IntentionallyWithoutHashCode("))
        assertFalse(newText.contains("@IntentionallyWithoutToString("))
        assertContains(newText, "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString")
        assertFalse(newText.contains("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEquals\n"))

        assertEquals(
            setOf("STATEFUL_CLASS_WITHOUT_EQUALS", "STATEFUL_CLASS_WITHOUT_HASH_CODE", "STATEFUL_CLASS_WITHOUT_TO_STRING"),
            result.applied.map { it.diagnostic }.toSet(),
        )
        assertEquals(
            setOf("IntentionallyWithoutEqualsHashCodeOrToString"),
            result.applied.map { it.annotation }.toSet(),
        )
        // Two imports and the annotation line move the declaration from line 4 to line 8.
        assertEquals(setOf(8), result.applied.map { it.line }.toSet())
    }

    @Test
    fun `two of three stateful diagnostics keep their individual annotations`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A session that renders itself. */
            public class Session(public val id: Int) {
                override fun toString(): String = "Session(id=${'$'}id)"
            }
        """.trimIndent()
        val declaration = "public class Session(public val id: Int) {"

        val result = fix(
            text,
            diagnostic("STATEFUL_CLASS_WITHOUT_EQUALS", text, declaration),
            diagnostic("STATEFUL_CLASS_WITHOUT_HASH_CODE", text, declaration),
        )

        val newText = result.newText!!
        assertContains(
            newText,
            "@IntentionallyWithoutEquals(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "@IntentionallyWithoutHashCode(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    declaration,
        )
        assertFalse(newText.contains("IntentionallyWithoutEqualsHashCodeOrToString"))
        assertEquals(2, result.applied.size)
    }

    @Test
    fun `an existing individual exemption keeps the others individual`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEquals

            /** A session acknowledged as identity-compared. */
            @IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)
            public class Session(public val id: Int)
        """.trimIndent()
        val declaration = "public class Session(public val id: Int)"

        val result = fix(
            text,
            diagnostic("STATEFUL_CLASS_WITHOUT_HASH_CODE", text, declaration),
            diagnostic("STATEFUL_CLASS_WITHOUT_TO_STRING", text, declaration),
        )

        val newText = result.newText!!
        assertContains(
            newText,
            "@IntentionallyWithoutHashCode(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "@IntentionallyWithoutToString(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "@IntentionallyWithoutEquals(reason = ExemptionReason.API_DESIGN)\n" +
                    declaration,
        )
        assertFalse(newText.contains("IntentionallyWithoutEqualsHashCodeOrToString"))
    }

    @Test
    fun `replaced annotations survive CRLF line endings`() {
        val text = "package com.example\r\n\r\n/** Doc. */\r\n@SubclassOptInRequired\r\npublic interface Plugin\r\n"

        val result = fix(text, diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "@SubclassOptInRequired"))

        val newText = result.newText!!
        assertEquals(newText.count { it == '\n' }, newText.windowed(2).count { it == "\r\n" })
        assertContains(
            newText,
            "/** Doc. */\r\n@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\r\npublic interface Plugin",
        )
    }

    @Test
    fun `unfixable diagnostics are skipped with reasons`() {
        @Language("kotlin")
        val text = """
            package com.example

            public class Anything
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("EXEMPTION_WITHOUT_EXPLANATION", text, "public class Anything"),
            diagnostic("DSL_MARKER_NOOP_TYPE_POSITION", text, "public class Anything"),
            diagnostic("SOME_FUTURE_DIAGNOSTIC", text, "public class Anything"),
        )

        assertNull(result.newText)
        val reasonsByDiagnostic = result.skipped.associate { it.diagnostic to it.reason }
        assertEquals(3, reasonsByDiagnostic.size)
        assertContains(reasonsByDiagnostic.getValue("EXEMPTION_WITHOUT_EXPLANATION"), "author")
        assertContains(reasonsByDiagnostic.getValue("DSL_MARKER_NOOP_TYPE_POSITION"), "DSL marker")
        assertContains(reasonsByDiagnostic.getValue("SOME_FUTURE_DIAGNOSTIC"), "Unknown diagnostic")
    }

    @Test
    fun `already annotated target is skipped`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertNull(result.newText)
        assertContains(result.skipped.single().reason, "already has")
    }

    @Test
    fun `secondary constructor is annotated in place`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A holder. */
            public class Holder {
                /** The empty holder. */
                public constructor()

                public constructor(retries: Int = 3, host: String) : this()
            }
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public constructor(retries: Int = 3, host: String) : this()"),
            diagnostic("REQUIRED_PARAMETER_AFTER_OPTIONAL", text, "host: String"),
        )

        val newText = result.newText!!
        assertContains(
            newText,
            "    $PLACEHOLDER_KDOC\n" +
                    "    @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "    public constructor(retries: Int = 3, host: String) : this()",
        )
        assertFalse("IntentionallyUndocumented" in newText)
        assertEquals(
            setOf("REQUIRED_PARAMETER_AFTER_OPTIONAL", "UNDOCUMENTED_PUBLIC_API"),
            result.applied.map { it.diagnostic }.toSet(),
        )
        assertEquals(emptyList(), result.skipped)
    }

    @Test
    fun `diagnostics on several constructors collapse to one class annotation`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** An open base with two ways in. */
            public open class Base {
                /** From a number. */
                public constructor(x: Int)

                /** From a string. */
                public constructor(x: String)
            }
        """.trimIndent()

        // The checker reports once per accessible constructor.
        val result = fix(
            text,
            diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public constructor(x: Int)"),
            diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public constructor(x: String)"),
        )

        val newText = result.newText!!
        assertEquals(1, "@IntentionallyOpen".toRegex(RegexOption.LITERAL).findAll(newText).count())
        assertContains(
            newText,
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Base",
        )
        assertEquals(1, result.applied.size)
    }

    @Test
    fun `double wildcard imports get an explicit import`() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.library.api.watchdog.*
            import some.other.pkg.*

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        val newText = result.newText!!
        assertContains(newText, "import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason\n")
        assertContains(newText, "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen\n")
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun `bare carriage returns don't shift offsets`() {
        // With an even number of bare `\r`, a miscount of `\r\n` pairs would change the inserted newline.
        val text = "package com.example\r/** Doc. */\rpublic open class Service"

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertContains(
            result.newText!!,
            "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen" +
                    "\r/** Doc. */\r" +
                    "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public open class Service",
        )
    }

    @Test
    fun `mixed line endings are preserved outside insertions`() {
        val text = "package com.example\r\n\r\n/** Doc. */\npublic open class Service\r\n"

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        val newText = result.newText!!
        assertEquals(
            "package com.example\r\n" +
                    "\r\n" +
                    "import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason\r\n" +
                    "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen\r\n" +
                    "\r\n" +
                    "/** Doc. */\n" +
                    "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\r\n" +
                    "public open class Service\r\n",
            newText,
        )
        assertEquals(lineOf(newText, "public open class Service"), result.applied.single().line)
    }

    @Test
    fun `CRLF line endings survive the fix`() {
        val text = "package com.example\r\n\r\n/** Doc. */\r\npublic open class Service\r\n"

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        val newText = result.newText!!
        assertEquals(newText.count { it == '\n' }, newText.windowed(2).count { it == "\r\n" })
        assertContains(
            newText,
            "/** Doc. */\r\n@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\r\npublic open class Service",
        )
    }

    @Test
    fun `stale offset is skipped`() {
        @Language("kotlin")
        val text = """
            package com.example

            public class Small
        """.trimIndent()

        val result = fix(text, RecordedDiagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", FILE_PATH, 100_000, 100_010))

        assertNull(result.newText)
        assertContains(result.skipped.single().reason, "outside the file")
    }

    @Test
    fun `property with accessor logic is annotated on the property`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A computed size. */
            public inline val size: Int
                get() = 1 + 2
        """.trimIndent()

        val result = fix(text, diagnostic("INLINE_FUNCTION_WITH_LOGIC", text, "public inline val size"))

        assertContains(
            result.newText!!,
            "/** A computed size. */\n" +
                    "@IntentionallyInlinedLogic(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public inline val size: Int",
        )
    }

    @Test
    fun `companion members are annotated in place`() {
        @Suppress("MayBeConstant")
        @Language("kotlin")
        val text = """
            package com.example

            /** A coordinator. */
            public class Coordinator {
                /** The companion. */
                public companion object {
                    /** A factory. */
                    public fun instance(): Coordinator = Coordinator()

                    /** A label. */
                    public val DEFAULT_LABEL: String = "coordinator"
                }
            }
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("COMPANION_API_WITHOUT_JVM_STATIC", text, "public fun instance(): Coordinator = Coordinator()"),
            diagnostic("COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS", text, "public val DEFAULT_LABEL: String = \"coordinator\""),
        )

        val newText = result.newText!!
        assertContains(
            newText,
            "        /** A factory. */\n" +
                    "        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "        public fun instance(): Coordinator = Coordinator()",
        )
        assertContains(
            newText,
            "        /** A label. */\n" +
                    "        @IntentionallyNonStaticCompanionApi(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "        public val DEFAULT_LABEL: String = \"coordinator\"",
        )
    }

    @Test
    fun `no package directive still gets imports and file annotation`() {
        @Language("kotlin")
        val text = """
            /** A top-level function. */
            public fun topLevel(): Int = 0

            public open class Service
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("TOP_LEVEL_API_WITHOUT_JVM_NAME", text, "public fun topLevel(): Int = 0"),
            diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"),
        )

        val newText = result.newText!!
        assertTrue(newText.startsWith("@file:IntentionallyDefaultFacadeName("))
        assertContains(newText, "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDefaultFacadeName")
        assertContains(newText, "import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen")
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Service")
    }

    @Test
    fun `typealias, data class, and toString fixes`() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A callback alias. */
            public typealias Callback = (Int) -> Unit

            /**
             * A point.
             *
             * @param x the coordinate.
             */
            public data class Point(val x: Int)

            /**
             * A session.
             *
             * @param id the identifier.
             */
            public class Session(public val id: Int)
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("FUNCTION_TYPE_ALIAS_PUBLIC_API", text, "public typealias Callback = (Int) -> Unit"),
            diagnostic("DATA_CLASS_PUBLIC_API", text, "public data class Point(val x: Int)"),
            diagnostic("STATEFUL_CLASS_WITHOUT_EQUALS", text, "public class Session(public val id: Int)"),
            diagnostic("STATEFUL_CLASS_WITHOUT_HASH_CODE", text, "public class Session(public val id: Int)"),
            diagnostic("STATEFUL_CLASS_WITHOUT_TO_STRING", text, "public class Session(public val id: Int)"),
        )

        val newText = result.newText!!
        assertContains(newText, "@IntentionallyFunctionTypeAlias(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic typealias Callback")
        assertContains(newText, "@IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic data class Point")
        assertContains(
            newText,
            "@IntentionallyWithoutEqualsHashCodeOrToString(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public class Session",
        )
    }

    @Test
    fun `insertions at the same offset keep text order`() {
        val result = applyEdits(
            "cd",
            listOf(TextEdit(0, "a"), TextEdit(0, "b"), TextEdit(2, "e")),
        )
        assertEquals("abcde", result)
    }

    @Test
    fun `insertions at a replacement offset go before it`() {
        val result = applyEdits(
            "xd",
            listOf(TextEdit(0, "a"), TextEdit(0, 1, "bc"), TextEdit(2, "e")),
        )
        assertEquals("abcde", result)
    }

    private companion object {
        const val FILE_PATH = "/project/src/main/kotlin/com/example/Sample.kt"
        const val PLACEHOLDER_KDOC = "/** TODO: Document this declaration */"

        fun lineOf(text: String, snippet: String): Int {
            val offset = text.indexOf(snippet)
            require(offset >= 0) { "Snippet not found: $snippet" }
            return text.take(offset).count { it == '\n' } + 1
        }
    }
}
