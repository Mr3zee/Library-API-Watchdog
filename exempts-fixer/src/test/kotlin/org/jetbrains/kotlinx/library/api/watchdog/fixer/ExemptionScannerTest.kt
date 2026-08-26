package org.jetbrains.kotlinx.library.api.watchdog.fixer

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ExemptionScannerTest {
    private val parser = KotlinFileParser()
    private val scanner = ExemptionScanner(parser)

    @AfterTest
    fun tearDown() {
        parser.close()
    }

    @Test
    fun findsEveryAppliedAnnotationRegardlessOfReason() {
        val text = """
            package example

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility

            @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
            public open class Designed

            @IntentionallyOpen(description = "FOR_BACKWARDS_COMPATIBILITY")
            public open class MisleadingDescription

            @IntentionallyDataClass(reason = ExemptionReason.FOR_BACKWARDS_COMPATIBILITY)
            public data class Existing(public val value: Int)

            @IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility
            public annotation class LegacyDsl
        """.trimIndent()

        assertEquals(
            listOf(
                AppliedExemption("IntentionallyDataClass", FILE_PATH, 14),
                AppliedExemption("IntentionallyOpen", FILE_PATH, 8),
                AppliedExemption("IntentionallyOpen", FILE_PATH, 11),
                AppliedExemption(
                    "IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility",
                    FILE_PATH,
                    17,
                ),
            ),
            scanner.scan(FILE_PATH, text),
        )
    }

    @Test
    fun findsAnnotationsNotUsedByTheAutomaticFixer() {
        val text = """
            package example

            import org.jetbrains.kotlinx.library.api.watchdog.*

            @IntentionallyUndocumented(reason = ExemptionReason.API_DESIGN)
            public fun undocumented(): Unit = Unit

            @org.jetbrains.kotlinx.library.api.watchdog.IntentionallyWithoutEqualsHashCodeOrToString(
                description = "Opaque identity object",
            )
            public class Opaque
        """.trimIndent()

        assertEquals(
            listOf(
                AppliedExemption("IntentionallyUndocumented", FILE_PATH, 5),
                AppliedExemption("IntentionallyWithoutEqualsHashCodeOrToString", FILE_PATH, 8),
            ),
            scanner.scan(FILE_PATH, text),
        )
    }

    @Test
    fun findsEveryCurrentIntentionallyAnnotationType() {
        val annotationNames = listOf(
            "IntentionallyBooleanParameter",
            "IntentionallyDataClass",
            "IntentionallyDefaultFacadeName",
            "IntentionallyExhaustive",
            "IntentionallyFunctionTypeAlias",
            "IntentionallyInconsistentParameterOrder",
            "IntentionallyInlinedLogic",
            "IntentionallyKotlinOnlyApi",
            "IntentionallyMangledJvmName",
            "IntentionallyMutableCollection",
            "IntentionallyNonStaticCompanionApi",
            "IntentionallyNullableBoolean",
            "IntentionallyOpen",
            "IntentionallyPairOrTriple",
            "IntentionallyRequiredParameterAfterOptional",
            "IntentionallyUndocumented",
            "IntentionallyWithoutEquals",
            "IntentionallyWithoutEqualsHashCodeOrToString",
            "IntentionallyWithoutHashCode",
            "IntentionallyWithoutJvmOverloads",
            "IntentionallyWithoutToString",
            "IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility",
        )
        val text = buildString {
            appendLine("package example")
            appendLine("import org.jetbrains.kotlinx.library.api.watchdog.*")
            annotationNames.forEachIndexed { index, annotation ->
                appendLine("@$annotation")
                appendLine("public class Example$index")
            }
        }

        assertEquals(
            annotationNames.toSet(),
            scanner.scan(FILE_PATH, text).mapTo(mutableSetOf(), AppliedExemption::annotation),
        )
    }

    @Test
    fun resolvesAliasedImports() {
        val text = """
            package example

            import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason.FOR_BACKWARDS_COMPATIBILITY as BC
            import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen as ExistingOpen

            @ExistingOpen(reason = BC)
            public open class Existing
        """.trimIndent()

        assertEquals(
            listOf(AppliedExemption("IntentionallyOpen", FILE_PATH, 6)),
            scanner.scan(FILE_PATH, text),
        )
    }

    @Test
    fun ignoresSameNamedAnnotationsFromOtherPackages() {
        val text = """
            package example

            annotation class IntentionallyOpen(val reason: LocalReason)
            enum class LocalReason { FOR_BACKWARDS_COMPATIBILITY }

            @IntentionallyOpen(reason = LocalReason.FOR_BACKWARDS_COMPATIBILITY)
            public open class NotAWatchdogExemption
        """.trimIndent()

        assertEquals(emptyList(), scanner.scan(FILE_PATH, text))
    }

    private companion object {
        const val FILE_PATH = "/project/Existing.kt"
    }
}
