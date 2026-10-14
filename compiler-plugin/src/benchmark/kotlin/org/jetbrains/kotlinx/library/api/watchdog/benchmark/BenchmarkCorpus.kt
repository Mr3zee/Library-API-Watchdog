package org.jetbrains.kotlinx.library.api.watchdog.benchmark

import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates the benchmark corpus. The same file count gives byte-identical sources. Each template
 * triggers a fixed set of diagnostics, so the benchmark measures report construction too. The templates use
 * the exemption annotations, so the annotations library must be on the compile classpath.
 */
internal object BenchmarkCorpus {

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
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason")
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyBooleanParameter")
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyDataClass")
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExhaustive")
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyFunctionTypeAlias")
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMangledJvmName")
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyMutableCollection")
        appendLine("import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen")
        appendLine("import watchdog.bench.support.BenchDsl")
        appendLine("import watchdog.bench.support.BenchExperimental")
        appendLine()
    }

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
     * Triggers DATA_CLASS_PUBLIC_API, STATEFUL_CLASS_WITHOUT_EQUALS, STATEFUL_CLASS_WITHOUT_HASH_CODE,
     * STATEFUL_CLASS_WITHOUT_TO_STRING, and UNDOCUMENTED_PUBLIC_API.
     */
    private fun modelsFile(i: Int): String = header(i, null) + """
        /** Identifier of bench entities. */
        public class ModelId$i(public val raw: Long) {
            public fun shifted(delta: Long): ModelId$i = ModelId$i(raw + delta)

            override fun equals(other: Any?): Boolean = other is ModelId$i && other.raw == raw
            override fun hashCode(): Int = raw.hashCode()
            override fun toString(): String = "ModelId$i(raw=" + raw + ")"
        }

        /** Snapshot of a model. Renders itself but deliberately misses equality. */
        public class Snapshot$i(public val id: Long, public val label: String) {
            override fun toString(): String = "Snapshot$i(id=" + id + ", label=" + label + ")"
        }

        /** Position of a model cursor. Keeps equality but deliberately misses toString. */
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

    /** Triggers OPEN_API_WITHOUT_SUBCLASS_OPT_IN and SUBCLASS_OPT_IN_WITHOUT_MARKERS. */
    private fun servicesFile(i: Int): String = header(i, null) + """
        /** Contract for key resolvers. External implementations must opt in. */
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
     * Triggers DSL_MARKER_WITHOUT_EXPLICIT_TARGETS, DSL_MARKER_NOOP_TARGET, DSL_MARKER_NOOP_TYPE_POSITION,
     * and KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC.
     */
    private fun dslFile(i: Int): String = header(i, "RoutesDsl") + """
        /** Legacy DSL marker kept without explicit targets. */
        @DslMarker
        public annotation class LooseDsl$i

        /** Marker whose function target has no DSL effect. */
        @DslMarker
        @Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
        public annotation class MixedDsl$i

        /** Names the current route scope. The marker on the return type has no effect. */
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
     * Triggers TOP_LEVEL_API_WITHOUT_JVM_NAME, DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS,
     * BOOLEAN_PARAMETER_PUBLIC_API, REQUIRED_PARAMETER_AFTER_OPTIONAL, and
     * KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC.
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

        /** Formats a label. The default parameter is not mirrored for Java callers. */
        public fun formatLabel$i(value: String, mode: LabelMode$i = LabelMode$i.PLAIN): String =
            if (mode == LabelMode$i.FANCY) "[" + value + "]" else value

        /** Truncates text using a raw toggle. */
        public fun truncate$i(text: String, keepSuffix: Boolean): String =
            if (keepSuffix) text.takeLast(4) else text.take(4)

        /** Enables tracing. The toggle shape is an accepted part of this API. */
        @IntentionallyBooleanParameter(reason = ExemptionReason.API_DESIGN)
        public fun trace$i(enabled: Boolean): String = if (enabled) "on" else "off"

        /** Pads text. The defaults are mirrored to Java. */
        @JvmOverloads
        public fun pad$i(text: String, width: Int = 8, filler: Char = ' '): String =
            text.padEnd(width, filler)

        /** Joins parts, keeping the required list after the optional separator. */
        @JvmOverloads
        public fun join$i(separator: String = ", ", parts: List<String>): String =
            parts.joinToString(separator)

        /** Fetches one value. Visible to Java despite the suspend shape. */
        public suspend fun fetch$i(key: String): String = key

        /** Fetches one value, hidden from Java callers. */
        @JvmSynthetic
        public suspend fun fetchHidden$i(key: String): String = key

        /** Emits one record. */
        public fun emit$i(target: String): Int = target.length

        /** Emits one record at the given level. Parameter order matches the base overload. */
        public fun emit$i(target: String, level: Int): Int = target.length + level
    """.trimIndent() + "\n"

    /** Triggers MUTABLE_COLLECTION_PUBLIC_API, PAIR_OR_TRIPLE_PUBLIC_API, and NULLABLE_BOOLEAN_PUBLIC_API. */
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

    /** Triggers COMPANION_API_WITHOUT_JVM_STATIC and COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS. */
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

                /** Parses a registry from text. Reachable from Java only via Companion. */
                public fun parse(text: String): Registry$i {
                    require(text.isNotEmpty()) { "empty registry text" }
                    return Registry$i()
                }

                /** Default registry name. */
                public const val DEFAULT_NAME: String = "default"

                /** Version marker kept behind an instance getter on Companion. */
                public val VERSION: String = "1.0"

                /** Upper bound on registry entries. */
                @JvmField
                public val LIMIT: Int = 42
            }
        }
    """.trimIndent() + "\n"

    /** Triggers EXHAUSTIVE_PUBLIC_API. */
    private fun sealedFile(i: Int): String = header(i, null) + """
        /** Shape of one report cell. The closed set of cases is part of the contract. */
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

    /** Triggers MANGLED_JVM_NAME_PUBLIC_API and INLINE_FUNCTION_WITH_LOGIC. */
    private fun inlineValueFile(i: Int): String = header(i, "Durations") + """
        /** Millisecond duration wrapper. */
        @JvmInline
        public value class Millis$i(public val value: Long)

        /** Wraps a raw count. */
        public fun toMillis$i(raw: Long): Millis$i = Millis$i(raw)

        /** Advances a duration. The value class parameter mangles the JVM name. */
        public fun advance$i(base: Millis$i, delta: Long): Millis$i = Millis$i(base.value + delta)

        /** Doubles a duration. The mangling is accepted for this Kotlin-first helper. */
        @IntentionallyMangledJvmName(reason = ExemptionReason.API_DESIGN)
        public fun doubled$i(base: Millis$i): Millis$i = Millis$i(base.value * 2)

        /** Sums block results. The loop body is baked into every call site. */
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

    /** Triggers FUNCTION_TYPE_ALIAS_PUBLIC_API. */
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

    /** Triggers INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS. */
    private fun interopFile(i: Int): String = header(i, "Interop") + """
        /** Renders a report line. */
        public fun render$i(name: String, width: Int): String = name + ":" + width

        /** Renders a padded report line. The first two parameters come in swapped order. */
        public fun render$i(width: Int, name: String, pad: Int): String =
            name + ":" + width + ":" + pad

        /** Published for inline use only. */
        @PublishedApi
        internal fun renderRaw$i(name: String): String = name

        /** Inline entry that goes through the published helper. */
        public inline fun renderInline$i(name: String): String = renderRaw$i(name)
    """.trimIndent() + "\n"
}
