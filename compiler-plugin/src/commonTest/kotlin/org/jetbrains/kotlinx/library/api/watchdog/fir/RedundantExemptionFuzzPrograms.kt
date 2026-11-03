package org.jetbrains.kotlinx.library.api.watchdog.fir

import kotlin.random.Random

/** One exemption annotation of a generated file: the `@` to the closing parenthesis. */
internal class FuzzExemption(val file: String, val annotation: String, val start: Int, val end: Int)

internal class FuzzFile(val name: String, val text: String, val exemptions: List<FuzzExemption>)

/** A generated multi-file test-data program. [render] blanks one exemption with spaces, so offsets don't move. */
internal class FuzzProgram(val index: Int, val header: String, val files: List<FuzzFile>) {
    val exemptions: List<FuzzExemption> get() = files.flatMap { it.exemptions }

    fun render(blanked: FuzzExemption? = null): String = buildString {
        append(header)
        for (file in files) {
            append("// FILE: ").append(file.name).append('\n')
            if (blanked == null || blanked.file != file.name) {
                append(file.text)
            } else {
                append(file.text, 0, blanked.start)
                repeat(blanked.end - blanked.start) { append(' ') }
                append(file.text, blanked.end, file.text.length)
            }
        }
    }
}

/**
 * Generates programs with exemptions on every target the annotations accept. Blanking one exemption
 * is an exact oracle only when the redundancy of each exemption is independent of the others. The
 * generator keeps it independent with these rules:
 *
 * - One declaration never carries the same exemption twice (declaration and parameter, parameter
 *   and its type), because the declaration-level one covers the others.
 * - A class-level exemption that covers the members (`MangledJvmName`, `KotlinOnlyApi`,
 *   `NonStaticCompanionApi`) never repeats inside the class.
 * - No `IntentionallyInconsistentParameterOrder`, because the check reads the exemptions of the
 *   other overloads too. No `IntentionallyNonTransitiveDependency`, because the check needs a
 *   dependency setup.
 * - No `@Suppress` and no annotation ignore rules, because both keep a needed exemption.
 */
internal class FuzzProgramGenerator(private val seed: Long) {
    private val configurableDiagnostics: List<String> = WatchdogDiagnostics.allDiagnostics.map { it.name }

    fun generate(index: Int): FuzzProgram {
        val random = Random(seed * 1_000_003L + index)
        val builder = ProgramBuilder(random, "fuzz.p$index")
        val header = buildString {
            appendLine("// RUN_PIPELINE_TILL: FRONTEND")
            appendLine("// DISABLE_NEXT_PHASE_SUGGESTION")
            appendLine("// EXPLICIT_API_MODE: WARNING")
            val overrides = severityOverrides(random)
            if (overrides.isNotEmpty()) appendLine("// DIAGNOSTIC_SEVERITIES: ${overrides.joinToString(" ")}")
            appendLine()
        }
        val fileCount = if (random.nextInt(3) == 0) 2 else 1
        val files = List(fileCount) { builder.file(it) }
        return FuzzProgram(index, header, files)
    }

    private fun severityOverrides(random: Random): List<String> {
        if (random.nextInt(10) < 6) return emptyList()
        val count = 1 + random.nextInt(4)
        return configurableDiagnostics.shuffled(random).take(count).map { name ->
            val severity = listOf("none", "none", "warning", "error")[random.nextInt(4)]
            "$name:$severity"
        }
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
"""

private object Pools {
    val classLevel = listOf(
        "IntentionallyOpen", "IntentionallyExhaustive", "IntentionallyUndocumented", "IntentionallyDataClass",
        "IntentionallyWithoutEquals", "IntentionallyWithoutHashCode", "IntentionallyWithoutToString",
        "IntentionallyWithoutEqualsHashCodeOrToString", "IntentionallyMangledJvmName", "IntentionallyKotlinOnlyApi",
        "IntentionallyNonStaticCompanionApi",
    )
    val annotationClassOnly = listOf("IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility")
    val typeAlias = listOf("IntentionallyUndocumented", "IntentionallyFunctionTypeAlias")
    val function = listOf(
        "IntentionallyUndocumented", "IntentionallyMutableCollection", "IntentionallyPairOrTriple",
        "IntentionallyBooleanParameter", "IntentionallyNullableBoolean", "IntentionallyRequiredParameterAfterOptional",
        "IntentionallyInlinedLogic", "IntentionallyMangledJvmName", "IntentionallyKotlinOnlyApi",
        "IntentionallyNonStaticCompanionApi", "IntentionallyWithoutJvmOverloads",
    )
    val property = listOf(
        "IntentionallyUndocumented", "IntentionallyMutableCollection", "IntentionallyPairOrTriple",
        "IntentionallyNullableBoolean", "IntentionallyInlinedLogic", "IntentionallyMangledJvmName",
        "IntentionallyNonStaticCompanionApi",
    )
    val constructor = listOf(
        "IntentionallyUndocumented", "IntentionallyMutableCollection", "IntentionallyPairOrTriple",
        "IntentionallyNullableBoolean", "IntentionallyRequiredParameterAfterOptional", "IntentionallyMangledJvmName",
        "IntentionallyWithoutJvmOverloads",
    )
    val valueParameter = listOf(
        "IntentionallyMutableCollection", "IntentionallyPairOrTriple", "IntentionallyBooleanParameter",
        "IntentionallyNullableBoolean", "IntentionallyMangledJvmName",
    )
    val typeParameter = listOf("IntentionallyMutableCollection", "IntentionallyPairOrTriple", "IntentionallyNullableBoolean")
    val typeUsage = typeParameter

    /** The class-level exemptions that cover every member. */
    val enclosing = setOf("IntentionallyMangledJvmName", "IntentionallyKotlinOnlyApi", "IntentionallyNonStaticCompanionApi")
}

/** A type with an expression of that type. */
private class TypeSpec(val text: String, val value: String)

private val simpleTypes = listOf(
    TypeSpec("Int", "0"),
    TypeSpec("String", "\"\""),
    TypeSpec("Boolean", "true"),
    TypeSpec("Boolean?", "null"),
    TypeSpec("MutableList<Int>", "mutableListOf()"),
    TypeSpec("List<Int>", "emptyList()"),
    TypeSpec("MutableSet<String>", "mutableSetOf()"),
    TypeSpec("Pair<Int, Int>", "0 to 0"),
    TypeSpec("Triple<Int, Int, Int>?", "null"),
    TypeSpec("Id", "Id(0)"),
    TypeSpec("() -> Unit", "{}"),
    TypeSpec("suspend () -> Int", "{ 0 }"),
    TypeSpec("Int.() -> Int", "{ this }"),
    TypeSpec("(Int) -> Int", "{ it }"),
)

/** The element types that a type-usage exemption can annotate inside `List<...>`. */
private val innerTypes = listOf(
    TypeSpec("MutableSet<Int>", ""), TypeSpec("Set<Int>", ""), TypeSpec("Pair<Int, Int>", ""),
    TypeSpec("Boolean?", ""), TypeSpec("Int", ""), TypeSpec("MutableList<String>?", ""),
)

private enum class ClassKind(val keyword: String) {
    FINAL("class"), OPEN("open class"), ABSTRACT("abstract class"), INTERFACE("interface"),
    FUN_INTERFACE("fun interface"), OBJECT("object"), ENUM("enum class"), SEALED("sealed class"),
    DATA("data class"), ANNOTATION("annotation class"), VALUE("value class"), DATA_OBJECT("data object"),
}

private enum class Scope { TOP_LEVEL, CLASS, OBJECT, COMPANION, INTERFACE_COMPANION, INTERFACE, VALUE_CLASS }

private class FileBuilder(val name: String) {
    private val text = StringBuilder()
    val exemptions = mutableListOf<FuzzExemption>()
    var indent = ""

    fun raw(s: String): FileBuilder { text.append(s); return this }
    fun line(s: String = "") { if (s.isNotEmpty()) text.append(indent).append(s); text.append('\n') }
    fun startLine() { text.append(indent) }
    fun endLine() { text.append('\n') }

    fun exemption(annotation: String, siteTarget: String = "") {
        val start = text.length
        val args = if (annotation == "IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility") "" else REASON
        text.append('@').append(siteTarget).append(annotation).append(args)
        exemptions += FuzzExemption(name, annotation, start, text.length)
    }

    inline fun indented(block: () -> Unit) {
        val previous = indent
        indent += "    "
        block()
        indent = previous
    }

    fun build(): FuzzFile = FuzzFile(name, text.toString(), exemptions)
}

private class ProgramBuilder(private val random: Random, private val packageName: String) {
    private var counter = 0
    private fun name(prefix: String): String = "$prefix${++counter}"
    private fun chance(percent: Int): Boolean = random.nextInt(100) < percent
    private fun <T> pick(list: List<T>): T = list[random.nextInt(list.size)]

    fun file(index: Int): FuzzFile {
        val file = FileBuilder("file$index.kt")
        if (chance(20)) file.line("@file:JvmName(\"Facade$index\")")
        if (chance(50)) {
            file.startLine(); file.exemption("IntentionallyDefaultFacadeName", siteTarget = "file:"); file.endLine()
        }
        file.line()
        file.line("package $packageName")
        file.line()
        file.line("import org.jetbrains.kotlinx.library.api.watchdog.*")
        if (index == 0) file.raw(PREAMBLE)
        file.line()
        repeat(3 + random.nextInt(4)) {
            when (random.nextInt(10)) {
                in 0..3 -> file.classDeclaration(Scope.TOP_LEVEL, depth = 0, forbidden = emptySet())
                in 4..6 -> file.function(Scope.TOP_LEVEL, forbidden = emptySet())
                in 7..8 -> file.property(Scope.TOP_LEVEL, forbidden = emptySet())
                else -> file.typeAlias()
            }
            file.line()
        }
        return file.build()
    }

    // ---- declarations ------------------------------------------------------------------------

    private fun FileBuilder.kdoc() {
        if (chance(50)) line("/** Documented. */")
    }

    /** Writes each of [annotations] on its own line. */
    private fun FileBuilder.annotationLines(annotations: List<String>) {
        for (annotation in annotations) {
            startLine(); exemption(annotation); endLine()
        }
    }

    private fun pickExemptions(pool: List<String>, forbidden: Set<String>, max: Int = 2): List<String> {
        val count = when (random.nextInt(10)) {
            in 0..1 -> 0
            in 2..7 -> 1
            else -> max
        }
        return (pool - forbidden).shuffled(random).take(count)
    }

    private fun visibility(scope: Scope): String = when (random.nextInt(20)) {
        0, 1 -> "internal"
        2 -> "private"
        3 -> if (scope == Scope.CLASS) "protected" else "public"
        else -> "public"
    }

    /** `@Hidden` makes the declaration internal API. `@PublishedApi internal` makes it binary API only. */
    private fun FileBuilder.apiMarkers(): String {
        if (chance(8)) line("@Hidden")
        return if (chance(6)) "@PublishedApi internal" else ""
    }

    private fun FileBuilder.classDeclaration(scope: Scope, depth: Int, forbidden: Set<String>) {
        val kind = pick(ClassKind.entries.filter { depth == 0 || it != ClassKind.ANNOTATION })
        val className = name("C")
        kdoc()
        val published = apiMarkers()
        val pool = if (kind == ClassKind.ANNOTATION) Pools.classLevel + Pools.annotationClassOnly else Pools.classLevel
        val own = pickExemptions(pool, forbidden)
        annotationLines(own)
        if (kind == ClassKind.ANNOTATION) {
            if (chance(50)) line("@DslMarker")
            if (chance(60)) line(pick(listOf("@Target(AnnotationTarget.CLASS)", "@Target(AnnotationTarget.FUNCTION)", "@Target(AnnotationTarget.TYPE, AnnotationTarget.CLASS)")))
        }
        if (kind == ClassKind.VALUE) line("@JvmInline")
        if (kind in listOf(ClassKind.OPEN, ClassKind.ABSTRACT, ClassKind.INTERFACE) && chance(25)) {
            line("@SubclassOptInRequired(Unstable::class)")
        }
        val visibility = published.ifEmpty { visibility(scope) }
        startLine()
        raw("$visibility ${kind.keyword} $className")
        val memberForbidden = forbidden + own.filter { it in Pools.enclosing }
        val typeParameter = kind in listOf(ClassKind.FINAL, ClassKind.OPEN, ClassKind.ABSTRACT, ClassKind.INTERFACE) && chance(20)
        if (typeParameter) typeParameters(memberForbidden)
        var hasPrimaryConstructor = false
        when (kind) {
            ClassKind.DATA -> { propertyParameters(memberForbidden, minimum = 1, allProperties = true); hasPrimaryConstructor = true }
            ClassKind.VALUE -> { raw("(public val raw: Int)"); hasPrimaryConstructor = true }
            ClassKind.FINAL, ClassKind.OPEN, ClassKind.ABSTRACT, ClassKind.SEALED -> if (chance(50)) {
                hasPrimaryConstructor = true
                val annotated = chance(30)
                if (annotated) { raw(" "); exemption(pick(Pools.constructor - memberForbidden)); raw(" constructor") }
                propertyParameters(memberForbidden, minimum = 0, required = annotated)
            }
            else -> {}
        }
        when (kind) {
            ClassKind.ANNOTATION, ClassKind.DATA_OBJECT -> { endLine(); return }
            ClassKind.FUN_INTERFACE -> {
                raw(" {"); endLine()
                indented { line("public fun ${name("call")}(x: Int): Int") }
                line("}")
                return
            }
            else -> {}
        }
        raw(" {"); endLine()
        indented {
            if (kind == ClassKind.ENUM) line("A, B;")
            val memberScope = when (kind) {
                ClassKind.OBJECT -> Scope.OBJECT
                ClassKind.INTERFACE -> Scope.INTERFACE
                ClassKind.VALUE -> Scope.VALUE_CLASS
                else -> Scope.CLASS
            }
            repeat(random.nextInt(4)) {
                line()
                when (random.nextInt(10)) {
                    in 0..4 -> function(memberScope, memberForbidden, abstractAllowed = kind == ClassKind.ABSTRACT || kind == ClassKind.INTERFACE)
                    in 5..7 -> if (kind != ClassKind.VALUE) property(memberScope, memberForbidden) else function(memberScope, memberForbidden)
                    8 -> if (depth < 2 && kind != ClassKind.VALUE && kind != ClassKind.INTERFACE) classDeclaration(memberScope, depth + 1, memberForbidden) else function(memberScope, memberForbidden)
                    else -> if (!hasPrimaryConstructor && memberScope == Scope.CLASS && kind != ClassKind.ENUM && kind != ClassKind.SEALED) secondaryConstructor(memberForbidden) else function(memberScope, memberForbidden)
                }
            }
            if (kind in listOf(ClassKind.FINAL, ClassKind.OPEN, ClassKind.ABSTRACT, ClassKind.ENUM, ClassKind.SEALED, ClassKind.INTERFACE) && chance(30)) {
                line()
                companion(memberForbidden, inInterface = kind == ClassKind.INTERFACE)
            }
        }
        line("}")
    }

    private fun FileBuilder.companion(forbidden: Set<String>, inInterface: Boolean) {
        val own = if (chance(40)) listOf("IntentionallyNonStaticCompanionApi") - forbidden else emptyList()
        annotationLines(own)
        val named = chance(30)
        line("public companion object${if (named) " Factory" else ""} {")
        val memberForbidden = forbidden + own
        val scope = if (inInterface) Scope.INTERFACE_COMPANION else Scope.COMPANION
        indented {
            repeat(1 + random.nextInt(3)) {
                if (chance(50)) function(scope, memberForbidden) else property(scope, memberForbidden)
            }
        }
        line("}")
    }

    private fun FileBuilder.typeParameters(forbidden: Set<String>) {
        raw("<")
        val exemption = pickExemptions(Pools.typeParameter, forbidden, max = 1).firstOrNull()
        if (exemption != null) { exemption(exemption); raw(" ") }
        raw("T : ${pick(listOf("MutableCollection<Int>", "Collection<Int>", "Any", "Pair<Int, Int>"))}>")
    }

    /** Primary constructor parameters, each one a property or a plain parameter. */
    private fun FileBuilder.propertyParameters(forbidden: Set<String>, minimum: Int, allProperties: Boolean = false, required: Boolean = false) {
        val count = minimum + random.nextInt(3)
        if (count == 0 && !required && chance(50)) return
        raw("(")
        repeat(count) { index ->
            if (index > 0) raw(", ")
            val isProperty = allProperties || chance(60)
            val used = mutableSetOf<String>()
            if (isProperty && chance(50)) {
                val pool = (Pools.property.filter { it in Pools.valueParameter } + "IntentionallyUndocumented") - forbidden
                val annotation = pick(pool)
                used += annotation
                val target = pick(listOf("", "", "property:", "param:"))
                if (target == "param:" && annotation !in Pools.valueParameter) exemption(annotation, "property:") else exemption(annotation, target)
                raw(" ")
            } else if (!isProperty && chance(50)) {
                val annotation = pick(Pools.valueParameter - forbidden)
                used += annotation
                exemption(annotation); raw(" ")
            }
            if (isProperty) raw("${visibility(Scope.CLASS).let { if (it == "protected") "public" else it }} ${if (chance(30)) "var" else "val"} ")
            raw("${name("p")}: ")
            type(forbidden + used)
            if (chance(20)) raw(" = ${lastValue}")
        }
        raw(")")
    }

    private var lastValue: String = "0"

    /**
     * Writes a type, with a type-usage exemption inside `List<...>` or on the type itself. An inline function
     * can't take a suspend function type or a function-typed context parameter. A vararg and a `@JvmField`
     * property can't hold a value class.
     */
    private fun FileBuilder.type(
        forbidden: Set<String>,
        inline: Boolean = false,
        noValueClass: Boolean = false,
        contextParameter: Boolean = false,
    ) {
        val allowed = simpleTypes.filter {
            !(inline && it.text.startsWith("suspend")) && !(noValueClass && it.text == "Id") && !(contextParameter && it.text.contains("->"))
        }
        when (random.nextInt(10)) {
            in 0..6 -> { val spec = pick(allowed); raw(spec.text); lastValue = spec.value }
            7, 8 -> {
                val inner = pick(innerTypes)
                raw("List<")
                val exemption = (Pools.typeUsage - forbidden).takeIf { it.isNotEmpty() && chance(80) }?.let(::pick)
                if (exemption != null) { exemption(exemption); raw(" ") }
                raw(inner.text).raw(">")
                lastValue = "emptyList()"
            }
            else -> {
                val spec = pick(allowed.filter { !it.text.contains("->") })
                val exemption = (Pools.typeUsage - forbidden).takeIf { it.isNotEmpty() }?.let(::pick)
                if (exemption != null) { exemption(exemption); raw(" ") }
                raw(spec.text); lastValue = spec.value
            }
        }
    }

    private fun FileBuilder.function(scope: Scope, forbidden: Set<String>, abstractAllowed: Boolean = false) {
        val functionName = name("f")
        kdoc()
        val published = if (scope == Scope.INTERFACE) "" else apiMarkers()
        val own = pickExemptions(Pools.function, forbidden)
        annotationLines(own)
        val isAbstract = abstractAllowed && chance(30)
        val isInline = !isAbstract && scope != Scope.INTERFACE && chance(20)
        val isSuspend = !isAbstract && chance(20)
        val reified = isInline && chance(30)
        val declarationForbidden = forbidden + own
        val visibility = when {
            published.isNotEmpty() -> published
            scope == Scope.INTERFACE -> if (isAbstract || chance(80)) "public" else "private"
            isAbstract -> pick(listOf("public", "protected", "internal"))
            else -> visibility(scope)
        }
        val jvmOverloads = !isAbstract && !isSuspend && scope != Scope.INTERFACE && chance(15)
        if (jvmOverloads) line("@JvmOverloads")
        if (scope in listOf(Scope.OBJECT, Scope.COMPANION, Scope.INTERFACE_COMPANION) && visibility == "public" && !isSuspend && chance(30)) line("@JvmStatic")
        if (!isAbstract && scope != Scope.INTERFACE && chance(10)) line("@JvmSynthetic")
        if (!isAbstract && scope != Scope.INTERFACE && scope != Scope.VALUE_CLASS && !isSuspend && !reified && chance(10)) line("@JvmName(\"${functionName}Jvm\")")
        val contextParameter = chance(10)
        if (contextParameter) {
            startLine(); raw("context(")
            val exemption = (Pools.valueParameter - declarationForbidden).takeIf { chance(50) }?.let(::pick)
            if (exemption != null) { exemption(exemption); raw(" ") }
            raw("c: "); type(declarationForbidden + listOfNotNull(exemption), inline = isInline, contextParameter = isInline); raw(")"); endLine()
        }
        startLine()
        raw(visibility)
        if (isAbstract && scope != Scope.INTERFACE) raw(" abstract")
        if (isInline) raw(" inline")
        if (isSuspend) raw(" suspend")
        raw(" fun ")
        var typeParameterUsed = false
        if (chance(15)) {
            raw("<")
            val exemption = pickExemptions(Pools.typeParameter, declarationForbidden, max = 1).firstOrNull()
            if (exemption != null) { exemption(exemption); raw(" ") }
            if (reified) raw("reified ")
            raw("T : ${pick(listOf("MutableCollection<Int>", "Collection<Int>", "Any"))}> ")
            typeParameterUsed = true
        }
        if (chance(10)) {
            val exemption = (Pools.typeUsage - declarationForbidden).takeIf { chance(50) }?.let(::pick)
            if (exemption != null) { exemption(exemption); raw(" ") }
            raw(pick(listOf("MutableList<Int>", "List<Int>", "Int")) + ".")
        }
        raw(functionName)
        val typeParameterArgument = parameters(declarationForbidden, typeParameterUsed, forceDefault = jvmOverloads, inline = isInline)
        val returnsUnit = chance(40)
        val returnValue: String
        if (returnsUnit) {
            returnValue = ""
        } else {
            raw(": ")
            if (typeParameterArgument != null && chance(50)) {
                raw("T"); returnValue = typeParameterArgument
            } else {
                type(declarationForbidden); returnValue = lastValue
            }
        }
        if (isAbstract || (scope == Scope.INTERFACE && visibility == "public" && chance(50) && !returnsUnit)) {
            endLine()
            return
        }
        body(isInline, returnsUnit, returnValue)
        if (scope == Scope.TOP_LEVEL && chance(10) && !isSuspend) localDeclarations(declarationForbidden)
    }

    /** Returns the name of the parameter of type `T`, or null without a type parameter. */
    private fun FileBuilder.parameters(forbidden: Set<String>, typeParameterUsed: Boolean, forceDefault: Boolean = false, inline: Boolean = false): String? {
        raw("(")
        var hasDefault = false
        val count = if (forceDefault) 1 + random.nextInt(3) else random.nextInt(4)
        var typeParameterArgument: String? = null
        repeat(count) { index ->
            if (index > 0) raw(", ")
            val used = mutableSetOf<String>()
            if (chance(50)) {
                val annotation = pick(Pools.valueParameter - forbidden)
                used += annotation
                exemption(annotation); raw(" ")
            }
            val isVararg = index == count - 1 && chance(10)
            if (isVararg) raw("vararg ")
            val parameterName = name("a")
            raw("$parameterName: ")
            if (typeParameterUsed && typeParameterArgument == null && !isVararg) {
                raw("T"); typeParameterArgument = parameterName
            } else {
                type(forbidden + used, inline = inline, noValueClass = isVararg)
                val last = index == count - 1
                if (!isVararg && (chance(30) || forceDefault && last && !hasDefault)) { raw(" = $lastValue"); hasDefault = true }
            }
        }
        if (typeParameterUsed && typeParameterArgument == null) {
            if (count > 0) raw(", ")
            raw("t: T")
            typeParameterArgument = "t"
        }
        raw(")")
        return typeParameterArgument
    }

    private fun FileBuilder.body(isInline: Boolean, returnsUnit: Boolean, returnValue: String) {
        val logic = isInline && chance(50)
        when {
            logic && returnsUnit -> { raw(" {"); endLine(); indented { line("var i = 0"); line("i += 1") }; line("}") }
            logic -> { raw(" {"); endLine(); indented { line("var i = 0"); line("i += 1"); line("return $returnValue") }; line("}") }
            returnsUnit -> { raw(" {}"); endLine() }
            else -> { raw(" = $returnValue"); endLine() }
        }
    }

    private fun FileBuilder.localDeclarations(forbidden: Set<String>) {
        line("public fun ${name("outer")}() {")
        indented {
            val classExemption = pick(Pools.classLevel - forbidden)
            startLine(); exemption(classExemption); endLine()
            line("data class Local(val x: Int)")
            val functionExemption = pick(Pools.function - forbidden)
            startLine(); exemption(functionExemption); endLine()
            line("fun local(on: Boolean): Boolean = on")
            line("local(Local(1).x > 0)")
        }
        line("}")
    }

    private fun FileBuilder.property(scope: Scope, forbidden: Set<String>) {
        val propertyName = name("v")
        kdoc()
        val published = if (scope == Scope.INTERFACE) "" else apiMarkers()
        val own = pickExemptions(Pools.property, forbidden)
        annotationLines(own)
        val declarationForbidden = forbidden + own
        val shape = when (scope) {
            Scope.INTERFACE -> pick(listOf("abstract", "getter"))
            Scope.OBJECT, Scope.COMPANION -> pick(listOf("plain", "plain", "getter", "const", "jvmField", "jvmStatic", "lateinit", "inlineGetter"))
            Scope.INTERFACE_COMPANION -> pick(listOf("plain", "getter", "const", "jvmStatic", "inlineGetter"))
            Scope.TOP_LEVEL -> pick(listOf("plain", "plain", "getter", "const", "lateinit", "inlineGetter", "receiver"))
            else -> pick(listOf("plain", "plain", "getter", "lateinit", "inlineGetter"))
        }
        val visibility = when {
            published.isNotEmpty() -> published
            scope == Scope.INTERFACE -> "public"
            shape == "jvmField" -> "public"
            shape == "jvmStatic" -> "public"
            else -> visibility(scope)
        }
        when (shape) {
            "jvmField" -> line("@JvmField")
            "jvmStatic" -> line("@JvmStatic")
        }
        startLine()
        raw(visibility)
        when (shape) {
            "const" -> { raw(" const val $propertyName: Int = 1"); endLine() }
            "lateinit" -> { raw(" lateinit var $propertyName: String"); endLine() }
            "abstract" -> { raw(" val $propertyName: "); type(declarationForbidden); endLine() }
            "getter" -> {
                raw(" val $propertyName: "); type(declarationForbidden); endLine()
                indented { line("get() = $lastValue") }
            }
            "inlineGetter" -> {
                raw(" inline val $propertyName: "); type(declarationForbidden); endLine()
                indented {
                    if (chance(50)) line("get() = $lastValue") else { line("get() {"); indented { line("var i = 0"); line("i += 1"); line("return $lastValue") }; line("}") }
                }
            }
            "receiver" -> {
                raw(" val ")
                val exemption = (Pools.typeUsage - declarationForbidden).takeIf { chance(50) }?.let(::pick)
                if (exemption != null) { exemption(exemption); raw(" ") }
                raw("${pick(listOf("MutableList<Int>", "List<Int>", "Id"))}.$propertyName: Int"); endLine()
                indented { line("get() = 0") }
            }
            else -> {
                raw(if (chance(30)) " var " else " val ")
                raw("$propertyName: "); type(declarationForbidden, noValueClass = shape == "jvmField")
                raw(" = $lastValue"); endLine()
            }
        }
    }

    private fun FileBuilder.secondaryConstructor(forbidden: Set<String>) {
        kdoc()
        val own = pickExemptions(Pools.constructor, forbidden)
        annotationLines(own)
        startLine()
        raw("${visibility(Scope.CLASS)} constructor")
        parameters(forbidden + own, typeParameterUsed = false)
        raw(" {}"); endLine()
    }

    private fun FileBuilder.typeAlias() {
        kdoc()
        val own = pickExemptions(Pools.typeAlias, emptySet())
        annotationLines(own)
        startLine()
        raw("${visibility(Scope.TOP_LEVEL)} typealias ${name("Alias")} = ")
        when (random.nextInt(3)) {
            0 -> raw("(Int) -> Unit")
            1 -> raw("suspend (String) -> Int")
            else -> type(own.toSet())
        }
        endLine()
    }
}
