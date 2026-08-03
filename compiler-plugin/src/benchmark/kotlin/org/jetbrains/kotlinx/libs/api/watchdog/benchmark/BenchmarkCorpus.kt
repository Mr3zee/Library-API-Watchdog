package org.jetbrains.kotlinx.libs.api.watchdog.benchmark

import java.nio.file.Files
import java.nio.file.Path

/**
 * Deterministic generator of a synthetic library-style source corpus for the watchdog
 * benchmarks. The same file count always produces byte-identical sources, so results are
 * comparable across runs and modes.
 *
 * The corpus is written in the explicit API style and rotates through ten file templates, each
 * exercising a different group of checkers. Most declarations are clean; every template carries
 * a small fixed set of intended diagnostics so that report construction is also measured. The
 * templates reference the watchdog exemption annotations, so the annotations library must be on
 * the compilation classpath.
 */
internal object BenchmarkCorpus {

    /** Writes the support file plus [fileCount] template files under [root]. */
    fun generate(root: Path, fileCount: Int): List<Path> {
        require(fileCount >= 1) { "fileCount must be positive, got $fileCount" }
        val files = ArrayList<Path>(fileCount + 1)
        files.add(write(root, "support/BenchSupport.kt", supportFile()))
        for (i in 0 until fileCount) {
            val template = TEMPLATES[i % TEMPLATES.size]
            files.add(write(root, "p$i/File$i.kt", template(i)))
        }
        return files
    }

    private fun write(root: Path, relative: String, text: String): Path {
        val path = root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.writeString(path, text)
        return path
    }

    private val TEMPLATES: List<(Int) -> String> = listOf(
        ::modelsFile,
        ::servicesFile,
        ::dslFile,
        ::functionsFile,
        ::collectionsFile,
        ::companionFile,
        ::sealedFile,
        ::inlineValueFile,
        ::typeAliasFile,
        ::interopFile,
    )

    private fun header(i: Int, jvmName: String?): String = buildString {
        if (jvmName != null) {
            appendLine("@file:JvmName(\"$jvmName$i\")")
            appendLine()
        }
        appendLine("package watchdog.bench.p$i")
        appendLine()
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.ExemptionReason")
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyBooleanParameter")
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyDataClass")
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyExhaustive")
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyFunctionTypeAlias")
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyMangledJvmName")
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyMutableCollection")
        appendLine("import org.jetbrains.kotlinx.libs.api.watchdog.IntentionallyOpen")
        appendLine("import watchdog.bench.support.BenchDsl")
        appendLine("import watchdog.bench.support.BenchExperimental")
        appendLine()
    }

    /**
     * Shared declarations referenced by the templates: an opt-in marker for
     * `@SubclassOptInRequired` and a DSL marker with explicit targets.
     */
    private fun supportFile(): String = """
        package watchdog.bench.support

        /** Guards experimental bench API extension points. */
        @RequiresOptIn(message = "Experimental bench API.", level = RequiresOptIn.Level.WARNING)
        @Retention(AnnotationRetention.BINARY)
        public annotation class BenchExperimental

        /** Marks bench builder DSL scopes. */
        @DslMarker
        @Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
        @Retention(AnnotationRetention.BINARY)
        public annotation class BenchDsl

    """.trimIndent()

    /**
     * Model classes. Intended diagnostics: DATA_CLASS_PUBLIC_API, STATEFUL_CLASS_WITHOUT_EQUALS,
     * STATEFUL_CLASS_WITHOUT_HASH_CODE, STATEFUL_CLASS_WITHOUT_TO_STRING,
     * UNDOCUMENTED_PUBLIC_API.
     */
    private fun modelsFile(i: Int): String = header(i, null) + """
        /** Identifier of bench entities. */
        public class ModelId$i(public val raw: Long) {
            public fun shifted(delta: Long): ModelId$i = ModelId$i(raw + delta)

            override fun equals(other: Any?): Boolean = other is ModelId$i && other.raw == raw
            override fun hashCode(): Int = raw.hashCode()
            override fun toString(): String = "ModelId$i(raw=" + raw + ")"
        }

        /** Snapshot of a model; renders itself but deliberately misses equality. */
        public class Snapshot$i(public val id: Long, public val label: String) {
            override fun toString(): String = "Snapshot$i(id=" + id + ", label=" + label + ")"
        }

        /** Position of a model cursor; keeps equality but deliberately misses toString. */
        public class Cursor$i(public val offset: Int) {
            override fun equals(other: Any?): Boolean = other is Cursor$i && other.offset == offset
            override fun hashCode(): Int = offset
        }

        /** Coordinate pair modeled as an exempted data class. */
        @IntentionallyDataClass(reason = ExemptionReason.API_DESIGN)
        public data class Point$i(val x: Int, val y: Int)

        /** Plain data class that the watchdog flags. */
        public data class Extent$i(val width: Int, val height: Int)
    """.trimIndent() + "\n"

    /**
     * Service hierarchy. Intended diagnostics: OPEN_API_WITHOUT_SUBCLASS_OPT_IN on the abstract
     * stage class, SUBCLASS_OPT_IN_WITHOUT_MARKERS on the markerless observer.
     */
    private fun servicesFile(i: Int): String = header(i, null) + """
        /** Contract for key resolvers; external implementations must opt in. */
        @SubclassOptInRequired(BenchExperimental::class)
        public interface Resolver$i {
            /** Resolves a key to its display form. */
            public fun resolve(key: String): String
        }

        /** Observer contract whose opt-in gate forgot its marker classes. */
        @SubclassOptInRequired
        public interface Observer$i {
            /** Called after each resolution. */
            public fun onResolved(key: String)
        }

        /** Base class of processing stages, left open without an opt-in gate. */
        public abstract class Stage$i {
            /** Executes the stage on one input. */
            public abstract fun execute(input: String): String
        }

        /** Deliberately open listener contract. */
        @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
        public interface Listener$i {
            /** Invoked once per event. */
            public fun onEvent(name: String)
        }

        /** Default resolver backed by simple uppercasing. */
        @OptIn(BenchExperimental::class)
        public class DefaultResolver$i : Resolver$i {
            override fun resolve(key: String): String = key.uppercase()
        }
    """.trimIndent() + "\n"

    /**
     * Builder DSL. Intended diagnostics: DSL_MARKER_WITHOUT_EXPLICIT_TARGETS on the local
     * marker, DSL_MARKER_NOOP_TARGET on the function target, DSL_MARKER_NOOP_TYPE_POSITION on
     * the annotated return type.
     */
    private fun dslFile(i: Int): String = header(i, "RoutesDsl") + """
        /** Legacy DSL marker kept without explicit targets. */
        @DslMarker
        public annotation class LooseDsl$i

        /** Marker whose function target has no DSL effect. */
        @DslMarker
        @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
        public annotation class MixedDsl$i

        /** Names the current route scope; the marker on the return type has no effect. */
        public fun label$i(): @BenchDsl String = "routes"

        /** Mutable scope collecting route definitions. */
        @BenchDsl
        public class RouteBuilder$i {
            private val routes = mutableListOf<String>()

            /** Registers one route. */
            public fun route(path: String) {
                routes += path
            }

            /** Snapshot of the registered routes. */
            public fun build(): List<String> = routes.toList()

            override fun equals(other: Any?): Boolean = other is RouteBuilder$i && other.routes == routes
            override fun hashCode(): Int = routes.hashCode()
            override fun toString(): String = "RouteBuilder$i(routes=" + routes + ")"
        }

        /** Entry point of the route DSL. */
        public fun routes$i(configure: RouteBuilder$i.() -> Unit): List<String> {
            val builder = RouteBuilder$i()
            builder.configure()
            return builder.build()
        }
    """.trimIndent() + "\n"

    /**
     * Top-level functions. Intended diagnostics: TOP_LEVEL_API_WITHOUT_JVM_NAME (no file
     * annotation on purpose), DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS, BOOLEAN_PARAMETER_PUBLIC_API,
     * REQUIRED_PARAMETER_AFTER_OPTIONAL, KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC.
     */
    private fun functionsFile(i: Int): String = header(i, null) + """
        /** Rendering modes of formatted labels. */
        @IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
        public enum class LabelMode$i {
            /** No decoration. */
            PLAIN,

            /** Adds brackets. */
            FANCY,
        }

        /** Formats a label; the default parameter is not mirrored for Java callers. */
        public fun formatLabel$i(value: String, mode: LabelMode$i = LabelMode$i.PLAIN): String =
            if (mode == LabelMode$i.FANCY) "[" + value + "]" else value

        /** Truncates text using a raw toggle. */
        public fun truncate$i(text: String, keepSuffix: Boolean): String =
            if (keepSuffix) text.takeLast(4) else text.take(4)

        /** Enables tracing; the toggle shape is an accepted part of this API. */
        @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
        public fun trace$i(enabled: Boolean): String = if (enabled) "on" else "off"

        /** Pads text; the defaults are mirrored to Java. */
        @JvmOverloads
        public fun pad$i(text: String, width: Int = 8, filler: Char = ' '): String =
            text.padEnd(width, filler)

        /** Joins parts, keeping the required list after the optional separator. */
        @JvmOverloads
        public fun join$i(separator: String = ", ", parts: List<String>): String =
            parts.joinToString(separator)

        /** Fetches one value; visible to Java despite the suspend shape. */
        public suspend fun fetch$i(key: String): String = key

        /** Fetches one value, hidden from Java callers. */
        @JvmSynthetic
        public suspend fun fetchHidden$i(key: String): String = key

        /** Emits one record. */
        public fun emit$i(target: String): Int = target.length

        /** Emits one record at the given level; parameter order matches the base overload. */
        public fun emit$i(target: String, level: Int): Int = target.length + level
    """.trimIndent() + "\n"

    /**
     * Collection-shaped API. Intended diagnostics: MUTABLE_COLLECTION_PUBLIC_API,
     * PAIR_OR_TRIPLE_PUBLIC_API, NULLABLE_BOOLEAN_PUBLIC_API.
     */
    private fun collectionsFile(i: Int): String = header(i, "Collections") + """
        /** Names of the built-in entries. */
        public fun entries$i(): List<String> = listOf("alpha", "beta")

        /** Mutable scratch buffer leaked into the API. */
        public fun scratch$i(): MutableList<String> = mutableListOf()

        /** Mutable accumulator kept mutable on purpose. */
        @IntentionallyMutableCollection(reason = ExemptionReason.API_DESIGN)
        public fun accumulator$i(): MutableList<Int> = mutableListOf()

        /** Inclusive bounds of the accepted range. */
        public fun bounds$i(): Pair<Int, Int> = Pair(1, 64)

        /** Tri-state flag with an unexplained third state. */
        public val flag$i: Boolean? = null

        /** Lookup table of entry weights. */
        public fun lookup$i(): Map<String, Int> = mapOf("alpha" to 1, "beta" to 2)
    """.trimIndent() + "\n"

    /**
     * Companion-object API. Intended diagnostics: COMPANION_API_WITHOUT_JVM_STATIC,
     * COMPANION_CONSTANT_WITHOUT_JVM_FIELD.
     */
    private fun companionFile(i: Int): String = header(i, null) + """
        /** Registry with factory-only construction. */
        public class Registry$i private constructor() {
            /** Looks one key up. */
            public fun lookup(key: String): Int = key.length

            /** Factories and defaults of [Registry$i]. */
            public companion object {
                /** Creates an empty registry. */
                @JvmStatic
                public fun create(): Registry$i = Registry$i()

                /** Parses a registry from text; reachable from Java only via Companion. */
                public fun parse(text: String): Registry$i {
                    require(text.isNotEmpty()) { "empty registry text" }
                    return Registry$i()
                }

                /** Default registry name. */
                public const val DEFAULT_NAME: String = "default"

                /** Version marker kept as an instance field of Companion. */
                public val VERSION: String = "1.0"

                /** Upper bound on registry entries. */
                @JvmField
                public val LIMIT: Int = 42
            }
        }
    """.trimIndent() + "\n"

    /**
     * Sealed hierarchy and enum. Intended diagnostics: EXHAUSTIVE_PUBLIC_API on the enum.
     */
    private fun sealedFile(i: Int): String = header(i, null) + """
        /** Shape of one report cell; the closed set of cases is part of the contract. */
        @IntentionallyExhaustive(reason = ExemptionReason.API_DESIGN)
        public sealed interface Cell$i {
            /** Width of the rendered cell. */
            public val width: Int
        }

        /** Text cell. */
        public class TextCell$i(override val width: Int, public val text: String) : Cell$i {
            override fun equals(other: Any?): Boolean =
                other is TextCell$i && other.width == width && other.text == text

            override fun hashCode(): Int = 31 * width + text.hashCode()
            override fun toString(): String = "TextCell$i(width=" + width + ", text=" + text + ")"
        }

        /** Empty spacer cell. */
        public object SpacerCell$i : Cell$i {
            override val width: Int = 1
        }

        /** Severity scale that users match exhaustively. */
        public enum class Severity$i {
            /** Informational entry. */
            INFO,

            /** Something failed. */
            FAILURE,
        }
    """.trimIndent() + "\n"

    /**
     * Inline functions and value classes. Intended diagnostics: MANGLED_JVM_NAME_PUBLIC_API,
     * INLINE_FUNCTION_WITH_LOGIC.
     */
    private fun inlineValueFile(i: Int): String = header(i, "Durations") + """
        /** Millisecond duration wrapper. */
        @JvmInline
        public value class Millis$i(public val value: Long)

        /** Wraps a raw count. */
        public fun toMillis$i(raw: Long): Millis$i = Millis$i(raw)

        /** Advances a duration; the value class parameter mangles the JVM name. */
        public fun advance$i(base: Millis$i, delta: Long): Millis$i = Millis$i(base.value + delta)

        /** Doubles a duration; the mangling is accepted for this Kotlin-first helper. */
        @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
        public fun doubled$i(base: Millis$i): Millis$i = Millis$i(base.value * 2)

        /** Sums block results; the loop body is baked into every call site. */
        public inline fun retry$i(times: Int, block: () -> Int): Int {
            var total = 0
            repeat(times) {
                total += block()
            }
            return total
        }

        /** Trivial forwarding wrapper. */
        public inline fun once$i(block: () -> Int): Int = block()
    """.trimIndent() + "\n"

    /**
     * Type aliases. Intended diagnostics: FUNCTION_TYPE_ALIAS_PUBLIC_API.
     */
    private fun typeAliasFile(i: Int): String = header(i, null) + """
        /** Callback invoked once per produced value. */
        public typealias Handler$i = (String) -> Unit

        /** Mapper alias kept as a plain function type on purpose. */
        @IntentionallyFunctionTypeAlias(reason = ExemptionReason.API_DESIGN)
        public typealias Mapper$i = (Int) -> String

        /** Shorthand for validated name lists. */
        public typealias Names$i = List<String>

        /** Sink that accepts produced values. */
        @IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
        public fun interface Sink$i {
            /** Accepts one value. */
            public fun accept(value: String)
        }
    """.trimIndent() + "\n"

    /**
     * Overload order. Intended diagnostic: INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS.
     */
    private fun interopFile(i: Int): String = header(i, "Interop") + """
        /** Renders a report line. */
        public fun render$i(name: String, width: Int): String = name + ":" + width

        /** Renders a padded report line; the first two parameters come in swapped order. */
        public fun render$i(width: Int, name: String, pad: Int): String =
            name + ":" + width + ":" + pad

        /** Published for inline use only. */
        @PublishedApi
        internal fun renderRaw$i(name: String): String = name

        /** Inline entry that goes through the published helper. */
        public inline fun renderInline$i(name: String): String = renderRaw$i(name)
    """.trimIndent() + "\n"
}
