@file:Suppress("RedundantVisibilityModifier")

package org.jetbrains.kotlinx.libs.api.watchdog.fixer

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

    /** Builds a diagnostic whose recorded range covers [snippet] inside [text]. */
    private fun diagnostic(name: String, text: String, snippet: String): RecordedDiagnostic {
        val start = text.indexOf(snippet)
        require(start >= 0) { "Snippet not found: $snippet" }
        return RecordedDiagnostic(name, FILE_PATH, start, start + snippet.length)
    }

    private fun fix(text: String, vararg diagnostics: RecordedDiagnostic): FileFixResult =
        fixer.fix(FILE_PATH, text, diagnostics.toList())

    @Test
    fun annotationGoesBetweenKdocAndDeclaration() {
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

                import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen

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
    fun reportedLinesIncludeEveryEarlierMultilineInsertion() {
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
            result.skipped.single().line,
        )
    }

    @Test
    fun inlineInsertionsDoNotShiftReportedLines() {
        @Language("kotlin")
        val text = """
            package org.jetbrains.kotlinx.libs.api.watchdog

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
    fun nestedDeclarationKeepsItsIndent() {
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
    fun annotationLandsAboveExistingAnnotations() {
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
    fun fileFacadeGetsFileAnnotationAbovePackage() {
        @Language("kotlin")
        val text = """
            package com.example

            /** A top-level function. */
            public fun topLevel(): Int = 0
        """.trimIndent()

        val result = fix(text, diagnostic("TOP_LEVEL_API_WITHOUT_JVM_NAME", text, "public fun topLevel(): Int = 0"))

        // File annotations resolve against the file's imports even though they precede them.
        assertEquals(
            """
                @file:IntentionallyDefaultFacadeName(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)

                package com.example

                import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
                import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDefaultFacadeName

                /** A top-level function. */
                public fun topLevel(): Int = 0
            """.trimIndent(),
            result.newText,
        )
    }

    @Test
    fun fileAnnotationJoinsExistingFileAnnotations() {
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
        assertContains(result.newText, "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDefaultFacadeName")
    }

    @Test
    fun existingImportsAreReusedNotDuplicated() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen

            public open class Documented
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Documented"),
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public open class Documented"),
        )

        val newText = result.newText!!
        assertEquals(1, Regex.escape("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen").toRegex().findAll(newText).count())
        assertContains(
            newText,
            "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Documented",
        )
        assertFalse("IntentionallyUndocumented" in newText)
        assertEquals("UNDOCUMENTED_PUBLIC_API", result.skipped.single().diagnostic)
    }

    @Test
    fun wildcardImportCoversEverything() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.libs.api.watchdog.*

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        val newText = result.newText!!
        assertEquals(1, newText.lines().count { it.startsWith("import ") })
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun importAliasIsRespected() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen as Open

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertContains(result.newText!!, "@Open(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun conflictingShortNameFallsBackToQualifiedName() {
        @Language("kotlin")
        val text = """
            package com.example

            import some.other.pkg.IntentionallyOpen

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertContains(
            result.newText!!,
            "@org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen(" +
                    "reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)",
        )
    }

    @Test
    fun samePackageNeedsNoImports() {
        @Language("kotlin")
        val text = """
            package org.jetbrains.kotlinx.libs.api.watchdog

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        val newText = result.newText!!
        assertTrue(newText.lines().none { it.startsWith("import ") })
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun sameAnnotationIsAppliedOnceForSeveralDiagnostics() {
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
        // Both diagnostics are still reported as fixed by the one annotation.
        assertEquals(1, result.applied.size)
    }

    @Test
    fun implicitPrimaryConstructorGrowsTheConstructorKeyword() {
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
    fun explicitPrimaryConstructorIsAnnotatedInline() {
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
    fun classTypeParameterBoundIsAnnotatedInline() {
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
    fun constructorValParameterIsAnnotatedInline() {
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
    fun undocumentedEnumEntryIsSkipped() {
        @Language("kotlin")
        val text = """
            package com.example

            /** Modes. */
            public enum class Mode { FAST }
        """.trimIndent()

        val result = fix(text, diagnostic("UNDOCUMENTED_PUBLIC_API", text, "FAST"))

        assertNull(result.newText)
        assertEquals(1, result.skipped.size)
        assertContains(result.skipped.single().reason, "KDocs")
    }

    @Test
    fun dslMarkerFixCarriesNoArguments() {
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
        assertContains(result.newText, "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility")
        assertTrue(result.newText.lines().none { "ExemptionReason" in it })
    }

    @Test
    fun unfixableDiagnosticsAreSkippedWithReasons() {
        @Language("kotlin")
        val text = """
            package com.example

            public class Anything
        """.trimIndent()

        val result = fix(
            text,
            diagnostic("SUBCLASS_OPT_IN_WITHOUT_MARKERS", text, "public class Anything"),
            diagnostic("UNDOCUMENTED_PUBLIC_API", text, "public class Anything"),
            diagnostic("EXEMPTION_WITHOUT_EXPLANATION", text, "public class Anything"),
            diagnostic("DSL_MARKER_NOOP_TYPE_POSITION", text, "public class Anything"),
            diagnostic("SOME_FUTURE_DIAGNOSTIC", text, "public class Anything"),
        )

        assertNull(result.newText)
        val reasonsByDiagnostic = result.skipped.associate { it.diagnostic to it.reason }
        assertEquals(5, reasonsByDiagnostic.size)
        assertContains(reasonsByDiagnostic.getValue("SUBCLASS_OPT_IN_WITHOUT_MARKERS"), "@SubclassOptInRequired")
        assertContains(reasonsByDiagnostic.getValue("UNDOCUMENTED_PUBLIC_API"), "KDocs")
        assertContains(reasonsByDiagnostic.getValue("EXEMPTION_WITHOUT_EXPLANATION"), "author")
        assertContains(reasonsByDiagnostic.getValue("DSL_MARKER_NOOP_TYPE_POSITION"), "DSL marker")
        assertContains(reasonsByDiagnostic.getValue("SOME_FUTURE_DIAGNOSTIC"), "Unknown diagnostic")
    }

    @Test
    fun alreadyAnnotatedTargetIsSkipped() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen

            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        assertNull(result.newText)
        assertContains(result.skipped.single().reason, "already carries")
    }

    @Test
    fun secondaryConstructorIsAnnotatedInPlace() {
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
            "    @IntentionallyRequiredParameterAfterOptional(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "    public constructor(retries: Int = 3, host: String) : this()",
        )
        assertFalse("IntentionallyUndocumented" in newText)
        assertEquals("UNDOCUMENTED_PUBLIC_API", result.skipped.single().diagnostic)
    }

    @Test
    fun diagnosticsOnSeveralConstructorsCollapseToOneClassAnnotation() {
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

        // OPEN_API_WITHOUT_SUBCLASS_OPT_IN reports once per accessible constructor when the
        // class has no public primary constructor; both resolve to the one class annotation.
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
    fun doubleWildcardImportsGetAnExplicitImport() {
        @Language("kotlin")
        val text = """
            package com.example

            import org.jetbrains.kotlinx.libs.api.watchdog.*
            import some.other.pkg.*

            public open class Service
        """.trimIndent()

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        // The other wildcard could contribute the same short names, so explicit imports (which
        // win over wildcards) keep the inserted references unambiguous.
        val newText = result.newText!!
        assertContains(newText, "import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason\n")
        assertContains(newText, "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen\n")
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)")
    }

    @Test
    fun bareCarriageReturnsDoNotSkewOffsets() {
        // An even number of bare \r matters: it would expose a miscount of \r\n pairs (there are
        // none here) through the inserted-newline choice, which must stay \n.
        val text = "package com.example\r/** Doc. */\rpublic open class Service"

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        // The bare \r line breaks stay exactly as they were, and the annotation still lands
        // directly in front of the declaration: the offsets are not skewed by the \r characters.
        assertContains(
            result.newText!!,
            "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen" +
                    "\r/** Doc. */\r" +
                    "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\n" +
                    "public open class Service",
        )
    }

    @Test
    fun mixedLineEndingsArePreservedOutsideInsertions() {
        val text = "package com.example\r\n\r\n/** Doc. */\npublic open class Service\r\n"

        val result = fix(text, diagnostic("OPEN_API_WITHOUT_SUBCLASS_OPT_IN", text, "public open class Service"))

        // CRLF dominates, so the inserted lines use it, while the existing bare-LF line keeps
        // its ending: nothing but the insertions changes.
        val newText = result.newText!!
        assertEquals(
            "package com.example\r\n" +
                    "\r\n" +
                    "import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason\r\n" +
                    "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen\r\n" +
                    "\r\n" +
                    "/** Doc. */\n" +
                    "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\r\n" +
                    "public open class Service\r\n",
            newText,
        )
        assertEquals(lineOf(newText, "public open class Service"), result.applied.single().line)
    }

    @Test
    fun crlfLineEndingsSurviveTheFix() {
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
    fun staleOffsetIsSkipped() {
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
    fun propertyWithAccessorLogicIsAnnotatedOnTheProperty() {
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
    fun companionMembersAreAnnotatedInPlace() {
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
            diagnostic("COMPANION_CONSTANT_WITHOUT_JVM_FIELD", text, "public val DEFAULT_LABEL: String = \"coordinator\""),
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
    fun noPackageDirectiveStillGetsImportsAndFileAnnotation() {
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
        assertContains(newText, "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDefaultFacadeName")
        assertContains(newText, "import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen")
        assertContains(newText, "@IntentionallyOpen(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic open class Service")
    }

    @Test
    fun typeAliasAndDataClassAndToStringFixes() {
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
            diagnostic("STATEFUL_CLASS_WITHOUT_TO_STRING", text, "public class Session(public val id: Int)"),
        )

        val newText = result.newText!!
        assertContains(newText, "@IntentionallyFunctionTypeAlias(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic typealias Callback")
        assertContains(newText, "@IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic data class Point")
        assertContains(newText, "@IntentionallyWithoutToString(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)\npublic class Session")
    }

    @Test
    fun insertionsAtTheSameOffsetKeepTextOrder() {
        val result = applyInsertions(
            "cd",
            listOf(Insertion(0, "a"), Insertion(0, "b"), Insertion(2, "e")),
        )
        assertEquals("abcde", result)
    }

    private companion object {
        const val FILE_PATH = "/project/src/main/kotlin/com/example/Sample.kt"

        fun lineOf(text: String, snippet: String): Int {
            val offset = text.indexOf(snippet)
            require(offset >= 0) { "Snippet not found: $snippet" }
            return text.take(offset).count { it == '\n' } + 1
        }
    }
}
