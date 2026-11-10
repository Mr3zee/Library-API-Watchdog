package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail

/**
 * A differential fuzz test of the API filters. The generator of [RedundantExemptionFuzzTest] puts the
 * exclusion annotation `@Mark` and the inclusion annotation `@Keep` on random named declarations and
 * files. A `@JvmField`, `const`, or `lateinit` property can also get them with the `field:` target,
 * because its backing field has the visibility of the property. The test compiles each program
 * several times with the same text and other filter options. The oracles:
 *
 * - Annotation and name rules: an annotation rule and a name rule that match the same declarations
 *   give the same diagnostics. The test compiles each combination of an exclusion by the internal
 *   API annotation `@Mark` or by names with an inclusion by `@Keep` or by names.
 * - Everything included: an inclusion rule that matches every top-level declaration gives the
 *   diagnostics of no inclusion rule.
 * - Complement: an inclusion of some top-level declarations gives the diagnostics of an exclusion of
 *   the other top-level declarations. The included declarations always hold the preamble. Only the
 *   preamble types are in signatures, and the exposure check reports an excluded type, but not a type
 *   outside the inclusion rules.
 * - Included member: an inclusion of a public member of a top-level class gives the diagnostics of an
 *   inclusion of the class with an exclusion of its other members. A constructor doesn't have a name
 *   of its own, so it is outside the filters in the first case only. The comparison skips the
 *   diagnostics in the constructors. It also skips `REDUNDANT_EXEMPTION` on the annotations of the
 *   class, because a class exemption can cover the constructors.
 * - Blanking: the `REDUNDANT_EXEMPTION` oracle of [RedundantExemptionFuzzTest] with the filters of a
 *   random combination.
 *
 * A comparison uses the names and the ranges of the watchdog diagnostics.
 * `PUBLIC_TYPE_WITH_INTERNAL_API` has another message for an exclusion by name, so the test doesn't
 * compare the messages. The generator avoids these cases, because the oracles aren't exact for them:
 *
 * - A filter annotation on a declaration without a name of its own: a constructor, an accessor, a
 *   parameter, an enum entry, or a local declaration.
 * - `@file:Mark` on the first file. The exposure check doesn't read an internal API annotation on a
 *   file, and the first file declares the types of the signatures.
 * - Two declarations with one simple name, other than companion objects. So a name rule matches one
 *   declaration, and a function name doesn't match other overloads.
 *
 * Gradle forwards the `watchdog.fuzz.programs` and `watchdog.fuzz.seed` project properties as system
 * properties, as for [RedundantExemptionFuzzTest].
 */
class ApiFiltersFuzzTest {
    private val seed: Long = System.getProperty("watchdog.fuzz.seed")?.toLong() ?: 20261110L
    private val programs: Int = System.getProperty("watchdog.fuzz.programs")?.toInt() ?: 6
    private val directory: Path = Files.createTempDirectory("watchdog-filters-fuzz")
    private val oracle = BlankingOracle(FuzzDiagnosticRunner(), directory, seed, javaClass.name)

    @AfterTest
    fun deleteGeneratedPrograms() {
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `the filters agree with their equivalent rules`() {
        val generator = FuzzProgramGenerator(seed, filters = true)
        val mismatches = mutableListOf<String>()
        for (index in 0 until programs) {
            mismatches += check(generator.generate(index))
        }
        if (mismatches.isNotEmpty()) {
            fail("${mismatches.size} mismatch(es) with seed $seed:\n\n${mismatches.joinToString("\n\n")}")
        }
    }

    private fun check(program: FuzzProgram): List<String> {
        val filters = program.filters ?: error("The generator didn't write filter annotations")
        val random = Random(seed * 1_000_003L + program.index + 1)
        val mismatches = mutableListOf<String>()
        val compiled = mutableMapOf<String, Set<Key>>()
        fun keys(directives: String): Set<Key>? {
            compiled[directives]?.let { return it }
            val diagnostics = oracle.compile("p${program.index}v${compiled.size}", program.render(directives = directives))
            program.compileErrors(diagnostics, directives)?.let {
                mismatches += it
                return null
            }
            val bases = program.bases(seed)
            return diagnostics.filter { it.name in watchdogDiagnosticNames }
                .mapTo(hashSetOf()) { Key(it.file, it.start - bases.getValue(it.file), it.end - bases.getValue(it.file), it.name) }
                .also { compiled[directives] = it }
        }
        fun compare(oracleName: String, expectedDirectives: String, actualDirectives: String, skipped: (Key) -> Boolean = { false }): Boolean {
            val expected = keys(expectedDirectives)?.filterNot(skipped)?.toSet() ?: return false
            val actual = keys(actualDirectives)?.filterNot(skipped)?.toSet() ?: return false
            if (expected == actual) return true
            mismatches += buildString {
                appendLine("Program ${program.index}, $oracleName: the diagnostics differ.")
                appendLine("  expected with:\n${expectedDirectives.prependIndent("    ")}")
                appendLine("  actual with:\n${actualDirectives.prependIndent("    ")}")
                appendLine("  missing:    ${(expected - actual).sortedBy { it.toString() }}")
                appendLine("  unexpected: ${(actual - expected).sortedBy { it.toString() }}")
                append(program.render(directives = actualDirectives).numbered())
            }
            return false
        }

        val exclusionByAnnotation = directive("INTERNAL_API_ANNOTATIONS", listOf("${filters.packageName}.Mark"))
        val exclusionByName = directive("EXCLUDED_NAMES", filters.excluded)
        val inclusionByAnnotation = if (filters.included.isEmpty()) "" else directive("INCLUDED_ANNOTATIONS", listOf("${filters.packageName}.Keep"))
        val inclusionByName = directive("INCLUDED_NAMES", filters.included)
        val combinations = listOf(
            exclusionByAnnotation + inclusionByAnnotation,
            exclusionByName + inclusionByName,
            exclusionByAnnotation + inclusionByName,
            exclusionByName + inclusionByAnnotation,
        ).distinct()
        for (combination in combinations.drop(1)) {
            if (!compare("annotation and name rules", combinations.first(), combination)) break
        }

        val everything = listOf("${filters.packageName}.*", "fuzz.**", "**")[random.nextInt(3)]
        compare("everything included", exclusionByName, exclusionByName + directive("INCLUDED_NAMES", listOf(everything)))

        val others = filters.topLevel - filters.preamble.toSet()
        val includedTopLevel = filters.preamble + others.filter { random.nextBoolean() }
        compare(
            "complement",
            directive("EXCLUDED_NAMES", filters.excluded + (others - includedTopLevel.toSet())),
            exclusionByName + directive("INCLUDED_NAMES", includedTopLevel),
        )

        val owners = filters.classes.filter { owner -> owner.publicApi && owner.members.any { it.publicApi } }
        if (owners.isNotEmpty()) {
            val owner = owners[random.nextInt(owners.size)]
            val members = owner.members.filter { it.publicApi }
            val member = members[random.nextInt(members.size)]
            compare(
                "included member ${owner.name}.${member.simpleName}",
                directive("INCLUDED_NAMES", listOf(owner.name)) +
                        directive("EXCLUDED_NAMES", (owner.members - member).map { "${owner.name}.${it.simpleName}" }),
                directive("INCLUDED_NAMES", listOf("${owner.name}.${member.simpleName}")),
            ) { key ->
                key.file == owner.file && (owner.constructors.any { key.within(it) } ||
                        key.name == WatchdogDiagnostics.REDUNDANT_EXEMPTION.name && key.within(owner.annotations))
            }
        }

        if (mismatches.isEmpty()) mismatches += oracle.mismatches(program, combinations[random.nextInt(combinations.size)])
        return mismatches
    }

    /** A diagnostic with offsets in the generated text of [file]. */
    private data class Key(val file: String, val start: Int, val end: Int, val name: String) {
        fun within(range: IntRange): Boolean = start >= range.first && end <= range.last + 1

        override fun toString(): String = "$file:$start..$end $name"
    }

    /** A directive line, or nothing without [values]. */
    private fun directive(name: String, values: List<String>): String =
        if (values.isEmpty()) "" else "// $name: ${values.distinct().joinToString(" ")}\n"

    private fun String.numbered(): String = lines().mapIndexed { index, line -> "${(index + 1).toString().padStart(3)}  $line" }.joinToString("\n")
}
