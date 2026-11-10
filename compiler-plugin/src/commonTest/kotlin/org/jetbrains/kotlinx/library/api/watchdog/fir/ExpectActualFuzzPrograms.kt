package org.jetbrains.kotlinx.library.api.watchdog.fir

import kotlin.random.Random

/** Where an annotation or a KDoc of an `expect`/`actual` pair is. */
internal enum class Side {
    EXPECT, ACTUAL, BOTH;

    val onExpect: Boolean get() = this != ACTUAL
    val onActual: Boolean get() = this != EXPECT
}

/**
 * The renderings of one declaration:
 *
 * - [EXPECT] and [ACTUAL]: the two halves of the multiplatform program.
 * - [COMMON_VIEW]: the `expect` as a plain declaration, as a common consumer sees it.
 * - [JVM_VIEW]: the `actual` as a plain declaration, as a JVM consumer sees it. It has the default
 *   values, the KDoc, and the exemptions of the `expect` too, because an `actual` inherits them.
 *   It has the `@SubclassOptInRequired` of the `actual` only.
 */
internal enum class Form { EXPECT, ACTUAL, COMMON_VIEW, JVM_VIEW }

/** The source range of a generated element, with a path that is the same in every rendering. */
internal class Span(val start: Int, val end: Int, val path: String)

internal class RenderedFile(val name: String, val text: String, val spans: List<Span>, val exemptions: List<FuzzExemption>)

/** A multiplatform program with a common and a JVM module, and its two single-module views. */
internal class ExpectActualProgram(
    val index: Int,
    private val directives: String,
    /** The paths of the top-level common-only declarations. */
    val commonOnlyPaths: Set<String>,
    val mpp: List<RenderedFile>,
    val jvmView: List<RenderedFile>,
    val commonView: RenderedFile,
) {
    val exemptions: List<FuzzExemption> get() = mpp.flatMap { it.exemptions }

    fun renderMpp(blanked: List<FuzzExemption> = emptyList()): String = buildString {
        append(header(multiplatform = true))
        mpp.forEachIndexed { index, file ->
            append(if (index == 0) "// MODULE: common\n" else "// MODULE: jvm()()(common)\n")
            append("// FILE: ").append(file.name).append('\n')
            val text = StringBuilder(file.text)
            for (exemption in blanked.filter { it.file == file.name }) {
                for (offset in exemption.start until exemption.end) text.setCharAt(offset, ' ')
            }
            append(text)
        }
    }

    fun renderJvmView(): String = buildString {
        append(header(multiplatform = false))
        for (file in jvmView) append("// FILE: ").append(file.name).append('\n').append(file.text)
    }

    fun renderCommonView(): String = buildString {
        append(header(multiplatform = false))
        append("// FILE: ").append(commonView.name).append('\n').append(commonView.text)
    }

    private fun header(multiplatform: Boolean): String = buildString {
        appendLine("// RUN_PIPELINE_TILL: FRONTEND")
        appendLine("// DISABLE_NEXT_PHASE_SUGGESTION")
        if (multiplatform) appendLine("// LANGUAGE: +MultiPlatformProjects")
        appendLine("// EXPLICIT_API_MODE: WARNING")
        append(directives)
        appendLine()
    }
}

private const val REASON = "(reason = ExemptionReason.API_DESIGN)"

private const val PREAMBLE = """
@RequiresOptIn
public annotation class Unstable

@InternalAnnotationMarker
public annotation class Hidden

@JvmInline
public value class Id(public val raw: Int)

@Hidden
public class HiddenType
"""

private const val FILTER_PREAMBLE = """
/** The inclusion annotation. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY, AnnotationTarget.TYPEALIAS)
public annotation class Keep
"""

private enum class Origin { PAIR, COMMON_ONLY, JVM_ONLY }

private var annotationCount = 0

/** [id] distinguishes the copies of one annotation of a pair from the annotations that overloads share. */
private class Ann(val name: String, val side: Side) {
    val id: Int = annotationCount++
}

private class TypeUse(val text: String, val value: String, val inner: String? = null, val exemption: Ann? = null)

private class Param(val name: String, val type: TypeUse, val exemptions: List<Ann>, val default: String?, val vararg: Boolean) {
    fun withoutDefault(): Param = Param(name, type, exemptions, null, vararg)
}

private class TypeParam(val bound: String, val exemption: Ann?, val reified: Boolean)

private sealed class Decl(val id: String) {
    var origin: Origin = Origin.PAIR
    var doc: Side? = null
    var hidden: Boolean = false

    /** The inclusion annotation `@Keep`. */
    var keep: Side? = null
    var visibility: String = "public"
    val exemptions: MutableList<Ann> = mutableListOf()
}

private class Fn(id: String, val name: String) : Decl(id) {
    /** `""`, `open`, `abstract`, or `interface` for an abstract interface member. */
    var modality: String = ""
    var isInline = false
    var isSuspend = false
    var typeParam: TypeParam? = null
    var receiver: TypeUse? = null
    val params = mutableListOf<Param>()
    var returns: TypeUse? = null
    var logic = false
    val jvmAnnotations = mutableListOf<String>()
}

private class Prop(id: String) : Decl(id) {
    var modality: String = ""
    var isVar = false
    lateinit var type: TypeUse

    /** `field`, `getter`, `inlineGetter`, or `abstract`. */
    var shape: String = "field"
    val jvmAnnotations = mutableListOf<String>()
}

private class Entry(val name: String, val doc: Side?)

private enum class Kind(val keyword: String) {
    FINAL("class"), OPEN("open class"), ABSTRACT("abstract class"), SEALED("sealed class"), INTERFACE("interface"),
    FUN_INTERFACE("fun interface"), OBJECT("object"), ENUM("enum class"), COMPANION("companion object"),
}

/** How the JVM module actualizes a class. */
private sealed class ActualForm {
    /** An `actual` class. [keyword] replaces the class keyword: `open class` for a final `expect`, or `data class`. */
    class Declared(val keyword: String?) : ActualForm()

    /** `actual typealias` to [impl], a JVM-only class of the program. */
    class OwnAlias(val impl: Cls) : ActualForm()

    /** `actual typealias` to [target]. [stub] is the class keyword of the target in [Form.JVM_VIEW]. */
    class JdkAlias(val target: String, val stub: String) : ActualForm()
}

/** [properties] makes the parameters properties. An `expect` declares them as plain parameters and body properties. */
private class Ctor(val id: String, val visibility: String, val params: List<Param>, val properties: Boolean)

private class Cls(id: String, val kind: Kind) : Decl(id) {
    var optIn: Side? = null
    var typeParam: TypeParam? = null
    var primary: Ctor? = null

    /** A constructor of the `actual` only. [extraIsPrimary] makes it the primary constructor. */
    var extraCtor: Ctor? = null
    var extraIsPrimary = false
    val members = mutableListOf<Decl>()
    val entries = mutableListOf<Entry>()
    val extraEntries = mutableListOf<Entry>()
    var actualForm: ActualForm = ActualForm.Declared(null)
    var sealedParent: Cls? = null

    /** The `expect` class with an `actual typealias` to this class. */
    var aliasOf: Cls? = null
}

private class Alias(id: String, val target: String) : Decl(id)

private class Model(val fileAnnotations: Map<String, List<String>>, val decls: List<Decl>)

private class SampleType(val text: String, val value: String)

private val simpleTypes = listOf(
    SampleType("Int", "0"),
    SampleType("String", "\"\""),
    SampleType("Boolean", "true"),
    SampleType("Boolean?", "null"),
    SampleType("MutableList<Int>", "mutableListOf()"),
    SampleType("List<Int>", "emptyList()"),
    SampleType("Pair<Int, Int>", "0 to 0"),
    SampleType("Triple<Int, Int, Int>?", "null"),
    SampleType("Id", "Id(0)"),
    SampleType("HiddenType?", "null"),
    SampleType("() -> Unit", "{}"),
    SampleType("suspend () -> Int", "{ 0 }"),
    SampleType("Int.() -> Int", "{ this }"),
)

private val innerTypes = listOf("MutableSet<Int>", "Set<Int>", "Pair<Int, Int>", "Boolean?", "Int")

private object Exemptions {
    val classLevel = listOf(
        "IntentionallyOpen", "IntentionallyExhaustive", "IntentionallyUndocumented", "IntentionallyDataClass",
        "IntentionallyWithoutEquals", "IntentionallyWithoutHashCode", "IntentionallyWithoutToString",
        "IntentionallyWithoutEqualsHashCodeOrToString", "IntentionallyMangledJvmName", "IntentionallyKotlinOnlyApi",
        "IntentionallyNonStaticCompanionApi",
    )
    val function = listOf(
        "IntentionallyUndocumented", "IntentionallyMutableCollection", "IntentionallyPairOrTriple",
        "IntentionallyBooleanParameter", "IntentionallyNullableBoolean", "IntentionallyRequiredParameterAfterOptional",
        "IntentionallyInlinedLogic", "IntentionallyMangledJvmName", "IntentionallyKotlinOnlyApi",
        "IntentionallyNonStaticCompanionApi", "IntentionallyWithoutJvmOverloads", "IntentionallyInconsistentParameterOrder",
    )
    val property = listOf(
        "IntentionallyUndocumented", "IntentionallyMutableCollection", "IntentionallyPairOrTriple",
        "IntentionallyNullableBoolean", "IntentionallyInlinedLogic", "IntentionallyMangledJvmName",
        "IntentionallyNonStaticCompanionApi",
    )
    val parameter = listOf(
        "IntentionallyMutableCollection", "IntentionallyPairOrTriple", "IntentionallyBooleanParameter",
        "IntentionallyNullableBoolean", "IntentionallyMangledJvmName",
    )
    val typeUsage = listOf("IntentionallyMutableCollection", "IntentionallyPairOrTriple", "IntentionallyNullableBoolean")

    /** The class-level exemptions that cover every member. */
    val enclosing = setOf("IntentionallyMangledJvmName", "IntentionallyKotlinOnlyApi", "IntentionallyNonStaticCompanionApi")
}

/**
 * Generates multiplatform programs. The two halves of each `expect`/`actual` pair differ in the ways
 * that the compiler accepts:
 *
 * - The `actual` has its own KDoc, exemptions, `@SubclassOptInRequired`, and JVM annotations.
 * - A final `expect` class has an `open` or a `data` `actual`.
 * - The `actual` class has more constructors, members, or enum entries.
 * - An `actual typealias` expands to a JVM-only class of the program or to a JDK class.
 *
 * Common-only and JVM-only declarations are next to the pairs and share overload names with them.
 * As in [FuzzProgramGenerator], one declaration never has the same exemption twice, and a member
 * never repeats an exemption of its class that covers the members.
 */
internal class ExpectActualProgramGenerator(private val seed: Long, private val filters: Boolean = false) {
    private val configurableDiagnostics: List<String> = WatchdogDiagnostics.allDiagnostics.map { it.name }

    fun generate(index: Int): ExpectActualProgram {
        val random = Random(seed * 1_000_003L + index)
        val model = ModelBuilder(random).build()
        val directives = buildString {
            val overrides = severityOverrides(random)
            if (overrides.isNotEmpty()) appendLine("// DIAGNOSTIC_SEVERITIES: ${overrides.joinToString(" ")}")
            if (filters) append(FilterRules(random, "fuzz.ea$index").directives(model))
        }
        val renderer = Renderer(model, "fuzz.ea$index", filters)
        return ExpectActualProgram(
            index = index,
            directives = directives,
            commonOnlyPaths = model.decls.filter { it.origin == Origin.COMMON_ONLY }.mapTo(hashSetOf()) { it.id },
            mpp = listOf(renderer.file("common.kt", Form.EXPECT), renderer.file("jvm.kt", Form.ACTUAL)),
            jvmView = listOf(renderer.file("common.kt", Form.JVM_VIEW, commonModule = true), renderer.file("jvm.kt", Form.JVM_VIEW)),
            commonView = renderer.file("common.kt", Form.COMMON_VIEW),
        )
    }

    private fun severityOverrides(random: Random): List<String> {
        if (random.nextInt(10) < 7) return emptyList()
        return configurableDiagnostics.shuffled(random).take(1 + random.nextInt(3)).map { name ->
            "$name:${listOf("none", "warning", "error")[random.nextInt(3)]}"
        }
    }
}

/**
 * Random name rules and `@Keep` annotations for a model. A rule and the annotation give both halves of a
 * pair the same verdict, so the views stay equivalent. The `@Keep` of a pair is on the `expect` or on
 * both halves, as the docs say. An inclusion doesn't match a JVM-only member of a pair class. Such a rule
 * includes the `actual` class without its `expect` class. The checks that report the pair on the
 * `expect` class then don't run in the multiplatform program, but they run in the JVM view.
 */
private class FilterRules(private val random: Random, private val packageName: String) {
    private val excluded = mutableListOf<String>()
    private val included = mutableListOf<String>()
    private var annotated = false

    private fun chance(percent: Int): Boolean = random.nextInt(100) < percent

    fun directives(model: Model): String {
        for (decl in model.decls) visit(decl, packageName, inPairClass = false)
        return buildString {
            if (excluded.isNotEmpty()) appendLine("// EXCLUDED_NAMES: ${excluded.distinct().joinToString(" ")}")
            if (included.isNotEmpty()) appendLine("// INCLUDED_NAMES: ${included.distinct().joinToString(" ")}")
            if (annotated) appendLine("// INCLUDED_ANNOTATIONS: $packageName.Keep")
        }
    }

    private fun visit(decl: Decl, prefix: String, inPairClass: Boolean) {
        val simpleName = when (decl) {
            is Fn -> decl.name
            is Cls -> if (decl.kind == Kind.COMPANION) "Companion" else decl.id
            else -> decl.id
        }
        val name = "$prefix.$simpleName"
        val inclusionAllowed = !(inPairClass && decl.origin == Origin.JVM_ONLY)
        if (chance(6)) excluded += name
        if (inclusionAllowed && chance(10)) included += name
        if (inclusionAllowed && chance(10)) {
            decl.keep = if (decl.origin == Origin.PAIR && random.nextBoolean()) Side.EXPECT else Side.BOTH
            annotated = true
        }
        if (decl is Cls) {
            for (member in decl.members) visit(member, name, inPairClass = decl.origin == Origin.PAIR)
            for (entry in decl.entries + decl.extraEntries) if (chance(6)) excluded += "$name.${entry.name}"
        }
    }
}

private class ModelBuilder(private val random: Random) {
    private var counter = 0
    private fun id(prefix: String): String = "$prefix${++counter}"
    private fun chance(percent: Int): Boolean = random.nextInt(100) < percent
    private fun <T> pick(list: List<T>): T = list[random.nextInt(list.size)]

    /** A JVM-only member of an `actual` class is on the [Side.ACTUAL] side. */
    private fun side(origin: Origin): Side = when (origin) {
        Origin.COMMON_ONLY -> Side.BOTH
        Origin.JVM_ONLY -> Side.ACTUAL
        Origin.PAIR -> when (random.nextInt(10)) {
            in 0..4 -> Side.EXPECT
            in 5..7 -> Side.ACTUAL
            else -> Side.BOTH
        }
    }

    private fun docSide(origin: Origin): Side? = if (origin != Origin.PAIR) {
        if (chance(50)) side(origin) else null
    } else when (random.nextInt(10)) {
        in 0..3 -> Side.EXPECT
        4 -> Side.ACTUAL
        5 -> Side.BOTH
        else -> null
    }

    private fun exemptions(pool: List<String>, origin: Origin, forbidden: Set<String>): List<Ann> {
        val count = when (random.nextInt(10)) {
            in 0..3 -> 0
            in 4..8 -> 1
            else -> 2
        }
        return (pool - forbidden).shuffled(random).take(count).map { Ann(it, side(origin)) }
    }

    fun build(): Model {
        val decls = mutableListOf<Decl>()
        val overloads = mutableMapOf<String, Fn>()
        repeat(3 + random.nextInt(4)) {
            val origin = when (random.nextInt(10)) {
                in 0..5 -> Origin.PAIR
                in 6..7 -> Origin.COMMON_ONLY
                else -> Origin.JVM_ONLY
            }
            val decl = when (random.nextInt(10)) {
                in 0..4 -> topLevelClass(origin)
                in 5..7 -> {
                    val original = if (overloads.isNotEmpty() && chance(50)) pick(overloads.values.toList()) else null
                    function(origin, Kind.FINAL, topLevel = true, overloadOf = original, forbidden = emptySet())
                        .also { if (original == null) overloads[it.name] = it }
                }
                8 -> property(origin, Kind.FINAL, topLevel = true, forbidden = emptySet())
                else -> if (origin == Origin.PAIR) property(origin, Kind.FINAL, topLevel = true, forbidden = emptySet()) else typeAlias(origin)
            }
            decls += decl
            if (decl is Cls) {
                (decl.actualForm as? ActualForm.OwnAlias)?.let { decls += it.impl }
                if (decl.kind == Kind.SEALED && decl.origin != Origin.COMMON_ONLY && decl.actualForm is ActualForm.Declared && chance(70)) {
                    decls += Cls(id("Sub"), Kind.FINAL).apply {
                        this.origin = Origin.JVM_ONLY
                        doc = Side.ACTUAL
                        visibility = decl.visibility
                        sealedParent = decl
                    }
                }
            }
        }
        val fileAnnotations = mapOf("common.kt" to fileAnnotations(), "jvm.kt" to fileAnnotations())
        return Model(fileAnnotations, decls.distinctFunctionSignatures())
    }

    /** Removes a function when an earlier function of the same module has the same signature. */
    private fun List<Decl>.distinctFunctionSignatures(): List<Decl> {
        val seen = mutableSetOf<String>()
        return filter { decl ->
            if (decl !is Fn) return@filter true
            val signature = decl.name + (decl.receiver?.text ?: "") +
                    decl.params.joinToString(",", "(", ")") { (if (it.vararg) "*" else "") + it.type.text }
            val modules = when (decl.origin) {
                Origin.PAIR, Origin.COMMON_ONLY -> listOf("common", "jvm")
                Origin.JVM_ONLY -> listOf("jvm")
            }
            val unique = modules.none { "$it:$signature" in seen }
            if (unique) modules.forEach { seen += "$it:$signature" }
            unique
        }
    }

    private fun fileAnnotations(): List<String> = buildList {
        if (chance(20)) add("@file:JvmName(\"Facade${random.nextInt(1000)}\")")
        if (chance(40)) add("@file:IntentionallyDefaultFacadeName$REASON")
    }

    private fun typeAlias(origin: Origin): Alias = Alias(id("Alias"), pick(listOf("(Int) -> Unit", "List<Int>", "HiddenType"))).also {
        it.origin = origin
        it.doc = docSide(origin)
        it.exemptions += exemptions(listOf("IntentionallyUndocumented", "IntentionallyFunctionTypeAlias"), origin, emptySet())
    }

    private fun topLevelClass(origin: Origin): Cls {
        val kind = pick(listOf(Kind.FINAL, Kind.FINAL, Kind.OPEN, Kind.ABSTRACT, Kind.SEALED, Kind.INTERFACE, Kind.FUN_INTERFACE, Kind.OBJECT, Kind.ENUM))
        return cls(origin, kind, depth = 0, forbidden = emptySet())
    }

    private fun cls(origin: Origin, kind: Kind, depth: Int, forbidden: Set<String>): Cls {
        val cls = Cls(id(if (kind == Kind.COMPANION) "Companion" else "C"), kind)
        cls.origin = origin
        cls.doc = docSide(origin)
        if (kind != Kind.COMPANION) {
            cls.hidden = chance(6)
            cls.visibility = if (depth == 0 && chance(8)) "internal" else "public"
        }
        cls.exemptions += exemptions(Exemptions.classLevel, origin, forbidden)
        val memberForbidden = forbidden + cls.exemptions.map { it.name }.filter { it in Exemptions.enclosing }
        if (kind in listOf(Kind.OPEN, Kind.ABSTRACT, Kind.INTERFACE) && chance(30)) cls.optIn = side(origin)

        // A JDK `actual typealias` needs an `expect` without members. Of the `actual` annotations, the
        // typealias can have only `@IntentionallyOpen`.
        if (origin == Origin.PAIR && depth == 0 && chance(25)) {
            val jdk = when (kind) {
                Kind.FINAL -> ActualForm.JdkAlias("java.lang.StringBuilder", "class")
                Kind.OPEN -> ActualForm.JdkAlias("java.lang.Thread", "open class")
                Kind.ABSTRACT -> ActualForm.JdkAlias("java.io.InputStream", "abstract class")
                Kind.INTERFACE -> ActualForm.JdkAlias("java.lang.Runnable", "interface")
                Kind.ENUM -> ActualForm.JdkAlias("java.util.concurrent.TimeUnit", "enum class")
                else -> null
            }
            if (jdk != null) {
                cls.actualForm = jdk
                cls.hidden = false
                cls.optIn = cls.optIn?.let { Side.EXPECT }
                // The compiler doesn't compare the annotations of a typealias with its `expect`, so the halves have no copies.
                val expectOnly = cls.exemptions.map { if (it.name == "IntentionallyOpen" && it.side != Side.BOTH) it else Ann(it.name, Side.EXPECT) }
                cls.exemptions.clear()
                cls.exemptions += expectOnly
                if (kind == Kind.ENUM) {
                    listOf("SECONDS", "MINUTES", "HOURS").shuffled(random).take(1 + random.nextInt(3)).forEach {
                        cls.entries += Entry(it, docSide(origin))
                    }
                }
                return cls
            }
        }

        if (kind in listOf(Kind.FINAL, Kind.OPEN, Kind.ABSTRACT, Kind.INTERFACE) && chance(15)) {
            cls.typeParam = TypeParam(pick(listOf("MutableCollection<Int>", "Collection<Int>", "Any")), null, reified = false)
        }
        if (kind in listOf(Kind.FINAL, Kind.OPEN, Kind.ABSTRACT, Kind.SEALED)) constructors(cls)
        when (kind) {
            Kind.ENUM -> {
                repeat(1 + random.nextInt(2)) { cls.entries += Entry(id("E"), docSide(origin)) }
                if (origin == Origin.PAIR && chance(40)) cls.extraEntries += Entry(id("X"), Side.ACTUAL)
                return cls
            }
            Kind.FUN_INTERFACE -> {
                cls.members += function(origin, kind, topLevel = false, abstract = true, forbidden = memberForbidden)
                return cls
            }
            else -> {}
        }
        repeat(random.nextInt(4)) {
            cls.members += when (random.nextInt(10)) {
                in 0..5 -> function(origin, kind, topLevel = false, forbidden = memberForbidden)
                in 6..8 -> property(origin, kind, topLevel = false, forbidden = memberForbidden)
                else -> if (depth == 0 && kind !in listOf(Kind.INTERFACE, Kind.COMPANION)) {
                    cls(origin, pick(listOf(Kind.FINAL, Kind.OPEN, Kind.ABSTRACT, Kind.INTERFACE, Kind.ENUM)), depth + 1, memberForbidden)
                } else function(origin, kind, topLevel = false, forbidden = memberForbidden)
            }
        }
        if (kind in listOf(Kind.FINAL, Kind.OPEN, Kind.ABSTRACT, Kind.SEALED, Kind.INTERFACE) && depth == 0 && chance(30)) {
            cls.members += cls(origin, Kind.COMPANION, depth + 1, memberForbidden)
        }
        if (origin == Origin.PAIR && kind != Kind.INTERFACE && chance(20)) {
            cls.members += function(Origin.JVM_ONLY, kind, topLevel = false, forbidden = memberForbidden)
        }
        // A typealias can't have the internal API marker of its `expect`.
        if (origin == Origin.PAIR && depth == 0 && !cls.hidden && cls.typeParam == null && cls.primary?.properties != true && cls.extraCtor == null &&
            kind in listOf(Kind.FINAL, Kind.OPEN, Kind.ABSTRACT, Kind.INTERFACE) && chance(15)
        ) {
            ownAlias(cls)
        }
        return cls
    }

    /** Makes the `actual` of [cls] a typealias to a JVM-only class with the members of the `expect`. */
    private fun ownAlias(cls: Cls) {
        val impl = Cls(id("Impl"), cls.kind)
        impl.origin = Origin.JVM_ONLY
        impl.doc = if (chance(60)) Side.BOTH else null
        impl.hidden = chance(20)
        impl.primary = cls.primary
        impl.optIn = cls.optIn?.takeIf { it.onActual }?.let { Side.ACTUAL }
        impl.exemptions += cls.exemptions.filter { it.side.onActual }.map { Ann(it.name, Side.ACTUAL) }
        impl.members += cls.members.filter { it.origin == Origin.PAIR }
        impl.aliasOf = cls
        cls.actualForm = ActualForm.OwnAlias(impl)
        cls.withoutDefaults()
        impl.primary = cls.primary
    }

    /** An `expect` that an `actual typealias` actualizes can't have default values. */
    private fun Cls.withoutDefaults() {
        primary?.let { primary = Ctor(it.id, it.visibility, it.params.map { param -> param.withoutDefault() }, it.properties) }
        for (member in members) when (member) {
            is Fn -> for (i in member.params.indices) member.params[i] = member.params[i].withoutDefault()
            is Cls -> member.withoutDefaults()
            else -> {}
        }
    }

    private fun constructors(cls: Cls) {
        val origin = cls.origin
        fun visibility(): String = when (cls.kind) {
            Kind.SEALED -> "protected"
            Kind.FINAL -> pick(listOf("public", "public", "internal"))
            else -> pick(listOf("public", "public", "internal", "protected"))
        }
        if (chance(55)) {
            val properties = cls.kind == Kind.FINAL && origin == Origin.PAIR && chance(40)
            val count = if (properties) 1 + random.nextInt(2) else random.nextInt(3)
            cls.primary = Ctor(id("k"), visibility(), List(count) { parameter(origin, inline = false, forbidden = emptySet()) }, properties)
            if (properties && chance(50)) cls.actualForm = ActualForm.Declared("data class")
        }
        if (origin == Origin.PAIR && cls.kind == Kind.FINAL && cls.primary?.properties != true && chance(20)) {
            cls.actualForm = ActualForm.Declared("open class")
        }
        if (origin == Origin.PAIR && cls.kind != Kind.SEALED && chance(35)) {
            if (cls.primary == null && chance(50)) {
                cls.extraCtor = Ctor(id("k"), visibility(), emptyList(), properties = false)
                cls.extraIsPrimary = true
            } else {
                // The last `Long` parameter makes the signature differ from the primary constructor.
                val params = List(random.nextInt(2)) { parameter(Origin.JVM_ONLY, inline = false, forbidden = emptySet()) } +
                        Param(id("a"), TypeUse("Long", "0L"), emptyList(), null, vararg = false)
                cls.extraCtor = Ctor(id("k"), visibility(), params, properties = false)
            }
        }
    }

    private fun parameter(origin: Origin, inline: Boolean, forbidden: Set<String>, allowExemptions: Boolean = true, vararg: Boolean = false): Param {
        val own = if (allowExemptions) exemptions(Exemptions.parameter, origin, forbidden).take(1) else emptyList()
        val type = type(origin, inline = inline, noValueClass = vararg, allowExemptions = allowExemptions, forbidden = forbidden + own.map { it.name })
        val default = if (!vararg && chance(25)) type.value else null
        return Param(id("a"), type, own, default, vararg)
    }

    private fun type(origin: Origin, inline: Boolean = false, noValueClass: Boolean = false, allowExemptions: Boolean = true, forbidden: Set<String>): TypeUse {
        val allowed = simpleTypes.filter { !(inline && it.text.startsWith("suspend")) && !(noValueClass && it.text == "Id") }
        val pool = (Exemptions.typeUsage - forbidden).takeIf { allowExemptions && it.isNotEmpty() }
        return when (random.nextInt(10)) {
            in 0..6 -> pick(allowed).let { TypeUse(it.text, it.value) }
            in 7..8 -> {
                val inner = pick(innerTypes)
                val exemption = pool?.takeIf { chance(70) }?.let { Ann(pick(it), side(origin)) }
                TypeUse("List<$inner>", "emptyList()", inner = inner, exemption = exemption)
            }
            else -> {
                val spec = pick(allowed.filter { !it.text.contains("->") })
                TypeUse(spec.text, spec.value, exemption = pool?.let { Ann(pick(it), side(origin)) })
            }
        }
    }

    /** [overloadOf] gives the function the name and the shuffled parameters of another top-level function. */
    fun function(origin: Origin, owner: Kind, topLevel: Boolean, forbidden: Set<String>, abstract: Boolean = false, overloadOf: Fn? = null): Fn {
        val fn = Fn(id("f"), overloadOf?.name ?: "f${++counter}")
        fn.origin = origin
        fn.doc = docSide(origin)
        fn.hidden = chance(5)
        val inInterface = owner == Kind.INTERFACE || owner == Kind.FUN_INTERFACE
        // A sealed class has no abstract members, so its JVM-only subclass compiles.
        val abstractAllowed = owner == Kind.ABSTRACT && origin != Origin.JVM_ONLY
        val isAbstract = abstract || inInterface && origin != Origin.JVM_ONLY || abstractAllowed && chance(40)
        fn.modality = when {
            isAbstract && inInterface -> "interface"
            isAbstract -> "abstract"
            owner in listOf(Kind.OPEN, Kind.ABSTRACT) && chance(30) -> "open"
            else -> ""
        }
        fn.visibility = when {
            inInterface -> "public"
            isAbstract -> pick(listOf("public", "public", "protected", "internal"))
            chance(8) -> "internal"
            owner in listOf(Kind.OPEN, Kind.ABSTRACT, Kind.SEALED) && chance(8) -> "protected"
            else -> "public"
        }
        fn.isInline = !isAbstract && !inInterface && fn.modality != "open" && chance(20)
        fn.isSuspend = !isAbstract && owner != Kind.FUN_INTERFACE && chance(15)
        val shared = overloadOf?.params.orEmpty().flatMap { param -> param.exemptions.map { it.name } + listOfNotNull(param.type.exemption?.name) }
        fn.exemptions += exemptions(Exemptions.function, origin, forbidden + shared)
        val own = forbidden + fn.exemptions.map { it.name }
        if (overloadOf != null) {
            fn.params += overloadOf.params.filter { it.type.text != "T" }.shuffled(random)
            if (chance(30)) fn.params += parameter(origin, inline = fn.isInline, forbidden = own)
        } else {
            if (owner != Kind.FUN_INTERFACE && chance(15)) {
                fn.typeParam = TypeParam(
                    pick(listOf("MutableCollection<Int>", "Collection<Int>", "Any")),
                    (listOf("IntentionallyMutableCollection") - own).takeIf { chance(40) && it.isNotEmpty() }?.let { Ann(it.first(), side(origin)) },
                    reified = fn.isInline && chance(40),
                )
            }
            if (!isAbstract && chance(10)) {
                fn.receiver = pick(listOf(TypeUse("List<Int>", ""), TypeUse("MutableList<Int>", ""), TypeUse("Id", ""), TypeUse("Int", "")))
            }
            val count = random.nextInt(4)
            repeat(count) { index -> fn.params += parameter(origin, inline = fn.isInline, forbidden = own, vararg = index == count - 1 && chance(10)) }
            if (fn.typeParam != null) fn.params += Param(id("t"), TypeUse("T", ""), emptyList(), null, vararg = false)
        }
        if (isAbstract) for (i in fn.params.indices) fn.params[i] = fn.params[i].withoutDefault()
        if (chance(60)) {
            fn.returns = if (fn.typeParam != null && chance(50)) TypeUse("T", fn.params.last().name) else type(origin, inline = fn.isInline, forbidden = own)
        }
        fn.logic = fn.isInline && chance(50)
        if (!isAbstract && !inInterface) {
            // A common source set can use only the JVM annotations with `@OptionalExpectation`.
            val common = origin == Origin.COMMON_ONLY
            if (!fn.isSuspend && fn.params.any { it.default != null } && chance(40)) fn.jvmAnnotations += "@JvmOverloads"
            if (!common && fn.modality != "open" && chance(8)) fn.jvmAnnotations += "@JvmSynthetic"
            if (topLevel && !fn.isSuspend && fn.typeParam?.reified != true && chance(10)) fn.jvmAnnotations += "@JvmName(\"${fn.name}${++counter}\")"
            if ((owner == Kind.OBJECT || owner == Kind.COMPANION) && fn.visibility == "public" && !fn.isSuspend && chance(40)) {
                fn.jvmAnnotations += "@JvmStatic"
            }
        }
        return fn
    }

    fun property(origin: Origin, owner: Kind, topLevel: Boolean, forbidden: Set<String>): Prop {
        val prop = Prop(id("v"))
        prop.origin = origin
        prop.doc = docSide(origin)
        prop.hidden = chance(5)
        val isAbstract = owner == Kind.INTERFACE && origin != Origin.JVM_ONLY ||
                owner == Kind.ABSTRACT && origin != Origin.JVM_ONLY && chance(30)
        prop.modality = when {
            isAbstract && owner == Kind.INTERFACE -> "interface"
            isAbstract -> "abstract"
            owner in listOf(Kind.OPEN, Kind.ABSTRACT) && chance(30) -> "open"
            else -> ""
        }
        prop.shape = when {
            isAbstract -> "abstract"
            owner == Kind.INTERFACE -> "getter"
            prop.modality == "open" -> pick(listOf("field", "getter"))
            else -> pick(listOf("field", "field", "getter", "inlineGetter"))
        }
        prop.isVar = prop.shape == "field" && chance(25)
        prop.visibility = if (owner != Kind.INTERFACE && chance(8)) "internal" else "public"
        prop.exemptions += exemptions(Exemptions.property, origin, forbidden)
        prop.type = type(origin, forbidden = forbidden + prop.exemptions.map { it.name })
        if (!topLevel && (owner == Kind.OBJECT || owner == Kind.COMPANION) && prop.visibility == "public" && chance(40)) {
            prop.jvmAnnotations += "@JvmStatic"
        }
        return prop
    }
}

private class Out(val name: String, val record: Boolean) {
    val text = StringBuilder()
    val spans = mutableListOf<Span>()
    val exemptions = mutableListOf<FuzzExemption>()
    var indent = ""

    fun raw(s: String): Out { text.append(s); return this }
    fun line(s: String = "") { if (s.isNotEmpty()) text.append(indent).append(s); text.append('\n') }
    fun startLine() { text.append(indent) }
    fun endLine() { text.append('\n') }

    inline fun span(path: String, block: () -> Unit) {
        val start = text.length
        block()
        spans += Span(start, text.length, path)
    }

    inline fun indented(block: () -> Unit) {
        val previous = indent
        indent += "    "
        block()
        indent = previous
    }

    fun build(): RenderedFile = RenderedFile(name, text.toString(), spans, exemptions)
}

private class Renderer(private val model: Model, private val packageName: String, private val filters: Boolean) {
    /** [commonModule] selects the common-only declarations of [Form.JVM_VIEW] instead of the pairs and the JVM-only ones. */
    fun file(name: String, form: Form, commonModule: Boolean = false): RenderedFile {
        val mpp = form == Form.EXPECT || form == Form.ACTUAL
        val out = Out(name, record = mpp)
        val common = form == Form.EXPECT || form == Form.COMMON_VIEW || form == Form.JVM_VIEW && commonModule
        for (annotation in model.fileAnnotations.getValue(if (common) "common.kt" else "jvm.kt")) {
            val start = out.text.length
            out.line(annotation)
            if (mpp && annotation.startsWith("@file:Intentionally")) {
                out.exemptions += FuzzExemption(name, "IntentionallyDefaultFacadeName", start, start + annotation.length)
            }
        }
        out.line()
        out.line("package $packageName")
        out.line()
        out.line("import org.jetbrains.kotlinx.library.api.watchdog.*")
        if (common) {
            out.raw(PREAMBLE)
            if (filters) out.raw(FILTER_PREAMBLE)
        }
        out.line()
        for (decl in model.decls) {
            // A pair renders in its form. A common-only or JVM-only declaration is the same plain text everywhere.
            val context = when (decl.origin) {
                Origin.PAIR -> if (form == Form.JVM_VIEW && commonModule) null else Context(out, form, pair = true)
                Origin.COMMON_ONLY -> if (common) Context(out, Form.JVM_VIEW, pair = false) else null
                Origin.JVM_ONLY -> when {
                    common -> null
                    decl is Cls && decl.aliasOf != null -> Context(out, Form.ACTUAL, pair = true, keywords = false)
                    else -> Context(out, Form.JVM_VIEW, pair = false)
                }
            } ?: continue
            context.declaration(decl, decl.id, topLevel = true)
            out.line()
        }
        return out.build()
    }

    /**
     * [pair] makes the context read the [Side] of each annotation. [keywords] is false for the class
     * of an `actual typealias`. That class renders the `actual` side without the `actual` keyword.
     */
    private class Context(val out: Out, val form: Form, val pair: Boolean, val keywords: Boolean = true) {
        /** The path of the declaration that the next annotations belong to. */
        var owner: String = ""

        fun present(side: Side): Boolean = !pair || when (form) {
            Form.EXPECT, Form.COMMON_VIEW -> side.onExpect
            Form.ACTUAL -> side.onActual
            Form.JVM_VIEW -> true
        }

        fun presentForJvm(side: Side): Boolean = !pair || when (form) {
            Form.EXPECT, Form.COMMON_VIEW -> side.onExpect
            Form.ACTUAL, Form.JVM_VIEW -> side.onActual
        }

        fun isPairHalf(decl: Decl): Boolean = pair && decl.origin == Origin.PAIR

        /** The class of an `actual typealias` isn't an `actual`, so its copy of an exemption is independent. */
        fun exemption(ann: Ann) {
            val start = out.text.length
            out.raw("@").raw(ann.name).raw(REASON)
            if (!out.record) return
            val group = "${ann.id}@$owner".takeIf { pair && keywords && ann.side == Side.BOTH }
            out.exemptions += FuzzExemption(out.name, ann.name, start, out.text.length, group)
        }

        fun annotationLines(anns: List<Ann>) {
            for (ann in anns) if (present(ann.side)) { out.startLine(); exemption(ann); out.endLine() }
        }

        fun doc(side: Side?) {
            if (side != null && present(side)) out.line("/** Documented. */")
        }

        fun keep(decl: Decl) {
            decl.keep?.let { if (present(it)) out.line("@Keep") }
        }

        fun keyword(decl: Decl, topLevel: Boolean): String = when {
            !keywords || !isPairHalf(decl) -> ""
            form == Form.EXPECT -> if (topLevel) "expect " else ""
            form == Form.ACTUAL -> "actual "
            else -> ""
        }

        /** Whether the JVM annotations of [decl] apply: the `actual` side and any plain declaration. */
        fun jvmSide(decl: Decl): Boolean = !isPairHalf(decl) || form == Form.ACTUAL || form == Form.JVM_VIEW

        /** An `expect` declaration without a body. */
        fun bodyless(decl: Decl): Boolean = isPairHalf(decl) && form == Form.EXPECT

        /** The stub body of an `expect` in [Form.COMMON_VIEW]. */
        fun stub(decl: Decl): Boolean = isPairHalf(decl) && form == Form.COMMON_VIEW

        fun declaration(decl: Decl, path: String, topLevel: Boolean) {
            when (decl) {
                is Fn -> function(decl, path, topLevel)
                is Prop -> property(decl, path, topLevel)
                is Cls -> cls(decl, path, topLevel)
                is Alias -> alias(decl, path)
            }
        }

        fun alias(alias: Alias, path: String) {
            owner = path
            doc(alias.doc)
            keep(alias)
            annotationLines(alias.exemptions)
            out.span(path) { out.startLine(); out.raw("${alias.visibility} typealias ${alias.id} = ${alias.target}"); out.endLine() }
        }

        fun type(type: TypeUse) {
            val exemption = type.exemption?.takeIf { present(it.side) }
            if (type.inner != null) {
                out.raw("List<")
                if (exemption != null) { exemption(exemption); out.raw(" ") }
                out.raw(type.inner).raw(">")
            } else {
                if (exemption != null) { exemption(exemption); out.raw(" ") }
                out.raw(type.text)
            }
        }

        fun typeParameter(typeParam: TypeParam, path: String) {
            out.raw("<")
            out.span("$path.<T>") {
                typeParam.exemption?.takeIf { present(it.side) }?.let { exemption(it); out.raw(" ") }
                if (typeParam.reified) out.raw("reified ")
                out.raw("T : ${typeParam.bound}")
            }
            out.raw(">")
        }

        /**
         * [classPath] gives the parameters the paths of the properties they declare, in every form.
         * [asProperties] writes them as `val` parameters.
         */
        fun parameters(params: List<Param>, path: String, withDefaults: Boolean, classPath: String? = null, asProperties: Boolean = false, owner: Decl? = null) {
            out.raw("(")
            params.forEachIndexed { index, param ->
                if (index > 0) out.raw(", ")
                out.span(if (classPath != null) "$classPath.${param.name}" else "$path.${param.name}") {
                    for (ann in param.exemptions) if (present(ann.side)) { exemption(ann); out.raw(" ") }
                    if (param.vararg) out.raw("vararg ")
                    if (asProperties) out.raw("public ").raw(owner?.let { keyword(it, topLevel = false) } ?: "").raw("val ")
                    out.raw("${param.name}: ")
                    type(param.type)
                    if (withDefaults && param.default != null) out.raw(" = ${param.default}")
                }
            }
            out.raw(")")
        }

        fun function(fn: Fn, path: String, topLevel: Boolean) {
            owner = path
            doc(fn.doc)
            keep(fn)
            if (fn.hidden) out.line("@Hidden")
            annotationLines(fn.exemptions)
            if (jvmSide(fn)) fn.jvmAnnotations.forEach { out.line(it) }
            out.span(path) {
                out.startLine()
                out.raw(fn.visibility).raw(" ")
                out.raw(keyword(fn, topLevel))
                when (fn.modality) {
                    "abstract" -> out.raw("abstract ")
                    "open" -> out.raw("open ")
                }
                if (fn.isInline) out.raw("inline ")
                if (fn.isSuspend) out.raw("suspend ")
                out.raw("fun ")
                fn.typeParam?.let { typeParameter(it, path); out.raw(" ") }
                fn.receiver?.let { type(it); out.raw(".") }
                out.raw(fn.name)
                parameters(fn.params, path, withDefaults = !(isPairHalf(fn) && form == Form.ACTUAL))
                fn.returns?.let { out.raw(": "); type(it) }
                body(fn)
            }
        }

        fun body(fn: Fn) {
            if (fn.modality == "abstract" || fn.modality == "interface" || bodyless(fn)) { out.endLine(); return }
            if (stub(fn)) { out.raw(if (fn.returns == null) " {}" else " = TODO()"); out.endLine(); return }
            val value = fn.returns?.value
            when {
                fn.logic && value == null -> { out.raw(" {"); out.endLine(); out.indented { out.line("var i = 0"); out.line("i += 1") }; out.line("}") }
                fn.logic -> { out.raw(" {"); out.endLine(); out.indented { out.line("var i = 0"); out.line("i += 1"); out.line("return $value") }; out.line("}") }
                value == null -> { out.raw(" {}"); out.endLine() }
                else -> { out.raw(" = $value"); out.endLine() }
            }
        }

        fun property(prop: Prop, path: String, topLevel: Boolean) {
            owner = path
            doc(prop.doc)
            keep(prop)
            if (prop.hidden) out.line("@Hidden")
            annotationLines(prop.exemptions)
            if (jvmSide(prop)) prop.jvmAnnotations.forEach { out.line(it) }
            out.span(path) {
                out.startLine()
                out.raw(prop.visibility).raw(" ")
                out.raw(keyword(prop, topLevel))
                when (prop.modality) {
                    "abstract" -> out.raw("abstract ")
                    "open" -> out.raw("open ")
                }
                if (prop.shape == "inlineGetter" && !bodyless(prop) && !stub(prop)) out.raw("inline ")
                out.raw(if (prop.isVar) "var " else "val ")
                out.raw("${prop.id}: ")
                type(prop.type)
                when {
                    prop.shape == "abstract" || bodyless(prop) -> out.endLine()
                    stub(prop) -> {
                        out.endLine()
                        out.indented { out.line("get() = TODO()"); if (prop.isVar) out.line("set(value) {}") }
                    }
                    prop.shape == "field" -> { out.raw(" = ${prop.type.value}"); out.endLine() }
                    prop.shape == "getter" -> { out.endLine(); out.indented { out.line("get() = ${prop.type.value}") } }
                    else -> {
                        out.endLine()
                        out.indented { out.line("get() {"); out.indented { out.line("var i = 0"); out.line("i += 1"); out.line("return ${prop.type.value}") }; out.line("}") }
                    }
                }
            }
        }

        fun cls(cls: Cls, path: String, topLevel: Boolean) {
            val actualForm = cls.actualForm
            val pairHalf = isPairHalf(cls)
            val actualHalf = pairHalf && (form == Form.ACTUAL || form == Form.JVM_VIEW)
            if (actualHalf && actualForm !is ActualForm.Declared) {
                aliasActual(cls, path, actualForm)
                return
            }
            val expectHalf = pairHalf && form == Form.EXPECT
            val stubHalf = pairHalf && form == Form.COMMON_VIEW
            owner = path
            doc(cls.doc)
            keep(cls)
            if (cls.hidden) out.line("@Hidden")
            annotationLines(cls.exemptions)
            cls.optIn?.let { if (presentForJvm(it)) out.line("@SubclassOptInRequired(Unstable::class)") }
            val classKeyword = (actualForm as? ActualForm.Declared)?.keyword?.takeIf { actualHalf } ?: cls.kind.keyword
            val primary = cls.primary
            val extra = cls.extraCtor?.takeIf { actualHalf || !pair }
            val bodyProperties = primary?.properties == true && (expectHalf || stubHalf)
            out.span(path) {
                out.startLine()
                out.raw(cls.visibility).raw(" ")
                out.raw(keyword(cls, topLevel))
                out.raw(classKeyword)
                if (cls.kind != Kind.COMPANION) out.raw(" ").raw(cls.id)
                cls.typeParam?.let { typeParameter(it, path) }
                when {
                    primary != null -> out.span("$path.${primary.id}") {
                        out.raw(" ${primary.visibility} ")
                        if (actualHalf && form == Form.ACTUAL && keywords) out.raw("actual ")
                        out.raw("constructor")
                        parameters(
                            primary.params, "$path.${primary.id}",
                            withDefaults = !(actualHalf && form == Form.ACTUAL),
                            classPath = path.takeIf { primary.properties },
                            asProperties = primary.properties && !bodyProperties,
                            owner = cls,
                        )
                    }
                    extra != null && cls.extraIsPrimary -> out.span("$path.${extra.id}") { out.raw(" ${extra.visibility} constructor()") }
                    stubHalf && cls.kind in listOf(Kind.FINAL, Kind.OPEN, Kind.ABSTRACT, Kind.SEALED) -> out.raw(" private constructor()")
                }
                cls.sealedParent?.let { parent ->
                    out.raw(" : ${parent.id}(${parent.primary?.params.orEmpty().joinToString { it.type.value }})")
                }
                val members = cls.members.filter { it.origin != Origin.JVM_ONLY || actualHalf || !pair }
                val secondary = extra?.takeIf { !cls.extraIsPrimary }
                // The parser reads a bodiless object, a KDoc, and a declaration differently when spaces replace an annotation between them.
                if (cls.kind in listOf(Kind.FUN_INTERFACE, Kind.ENUM, Kind.OBJECT, Kind.COMPANION) || members.isNotEmpty() || secondary != null || bodyProperties) {
                    out.raw(" {"); out.endLine()
                    out.indented {
                        if (cls.kind == Kind.ENUM) entries(cls, path, withExtra = actualHalf || !pair)
                        // The exemptions of a `val` parameter of the `actual` are on the constructor parameter of the `expect`.
                        if (bodyProperties) for (param in primary.params) {
                            out.span("$path.${param.name}") {
                                out.startLine(); out.raw("public val ${param.name}: ").raw(param.type.text); out.endLine()
                                if (stubHalf) out.indented { out.line("get() = TODO()") }
                            }
                        }
                        owner = path
                        if (secondary != null) out.span("$path.${secondary.id}") {
                            out.startLine(); out.raw("${secondary.visibility} constructor")
                            parameters(secondary.params, "$path.${secondary.id}", withDefaults = true)
                            if (primary != null) out.raw(" : this(${primary.params.joinToString { it.type.value }})")
                            out.raw(" {}"); out.endLine()
                        }
                        for (member in members) {
                            out.line()
                            declaration(member, "$path.${member.id}", topLevel = false)
                        }
                    }
                    out.line("}")
                } else {
                    out.endLine()
                }
            }
        }

        fun entries(cls: Cls, path: String, withExtra: Boolean) {
            val all = if (withExtra) cls.entries + cls.extraEntries else cls.entries
            all.forEachIndexed { index, entry ->
                doc(entry.doc)
                out.span("$path.${entry.name}") {
                    out.startLine(); out.raw(entry.name).raw(if (index == all.lastIndex) ";" else ","); out.endLine()
                }
            }
        }

        fun aliasActual(cls: Cls, path: String, actualForm: ActualForm) {
            val keyword = if (form == Form.ACTUAL) "actual " else ""
            when (actualForm) {
                is ActualForm.OwnAlias -> {
                    doc(cls.doc)
                    keep(cls)
                    out.span(path) { out.startLine(); out.raw("${cls.visibility} ${keyword}typealias ${cls.id} = ${actualForm.impl.id}"); out.endLine() }
                }
                is ActualForm.JdkAlias -> if (form == Form.ACTUAL) {
                    doc(cls.doc)
                    keep(cls)
                    annotationLines(cls.exemptions)
                    out.span(path) { out.startLine(); out.raw("${cls.visibility} actual typealias ${cls.id} = ${actualForm.target}"); out.endLine() }
                } else {
                    // A JVM consumer sees the JDK class under the name of the `expect`, with the exemptions of the `expect`.
                    doc(cls.doc)
                    keep(cls)
                    annotationLines(cls.exemptions)
                    out.span(path) {
                        out.startLine(); out.raw("${cls.visibility} ${actualForm.stub} ${cls.id}")
                        if (cls.kind == Kind.ENUM) {
                            out.raw(" {"); out.endLine(); out.indented { entries(cls, path, withExtra = true) }; out.line("}")
                        } else {
                            out.endLine()
                        }
                    }
                }
                is ActualForm.Declared -> error("${cls.id} has an actual class")
            }
        }
    }
}
