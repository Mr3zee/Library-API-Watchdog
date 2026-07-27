package org.jetbrains.kotlinx.libs.api.watchdog.fir

import com.intellij.psi.PsiElement
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.diagnostics.AbstractKtDiagnosticFactory
import org.jetbrains.kotlin.diagnostics.AbstractSourceElementPositioningStrategy
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory1
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory2
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory3
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.CLASS_KIND
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.NAME
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.diagnostics.rendering.Renderer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeAlias

/** Severity with which a configurable watchdog diagnostic is reported, or [NONE] to disable it. */
enum class WatchdogSeverity {
    ERROR,
    WARNING,
    NONE,
}

/**
 * A diagnostic whose severity is chosen per compilation. A diagnostic factory bakes its severity
 * in at construction, so each configurable diagnostic keeps an [error] and a [warning] factory
 * under the same diagnostic name and the checkers pick one of them - or none - at report time.
 */
class ConfigurableWatchdogDiagnostic<out F : AbstractKtDiagnosticFactory>(
    val error: F,
    val warning: F,
) {
    val name: String get() = error.name

    /** The factory reporting with [severity], or null when the diagnostic is disabled. */
    fun withSeverity(severity: WatchdogSeverity): F? = when (severity) {
        WatchdogSeverity.ERROR -> error
        WatchdogSeverity.WARNING -> warning
        WatchdogSeverity.NONE -> null
    }
}

/**
 * Per-compilation severity overrides keyed by diagnostic name; unlisted diagnostics are errors.
 * Returns null for diagnostics overridden to [WatchdogSeverity.NONE]: their check is disabled,
 * and [WatchdogFirCheckers] doesn't even register a checker all of whose diagnostics are
 * disabled.
 */
class WatchdogDiagnosticSeverities(private val overrides: Map<String, WatchdogSeverity>) {
    operator fun <F : AbstractKtDiagnosticFactory> get(diagnostic: ConfigurableWatchdogDiagnostic<F>): F? =
        diagnostic.withSeverity(overrides[diagnostic.name] ?: WatchdogSeverity.ERROR)

    fun isEnabled(diagnostic: ConfigurableWatchdogDiagnostic<*>): Boolean = this[diagnostic] != null

    companion object {
        /** Every diagnostic reported with its default severity, an error. */
        val DEFAULT = WatchdogDiagnosticSeverities(emptyMap())
    }
}

object WatchdogDiagnostics : KtDiagnosticsContainer() {
    /**
     * Every diagnostic whose severity can be configured, for CLI option validation. Must be
     * declared before the diagnostics themselves: their delegate providers register into it
     * during class initialization.
     */
    val allDiagnostics: List<ConfigurableWatchdogDiagnostic<*>>
        field = mutableListOf()

    /** Parameters: class kind, declaration name. Reported on the class or on a constructor. */
    val OPEN_API_WITHOUT_SUBCLASS_OPT_IN by configurable2<KtDeclaration, ClassKind, Name>(NAME_IDENTIFIER)

    val SUBCLASS_OPT_IN_WITHOUT_MARKERS by configurable0<KtAnnotationEntry>()

    /** Parameters: class kind, declaration name, class kind again for the member wording. */
    val EXHAUSTIVE_PUBLIC_API by configurable3<KtClassOrObject, ClassKind, Name, ClassKind>(NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val UNDOCUMENTED_PUBLIC_API by configurable2<KtDeclaration, String, Name>(NAME_IDENTIFIER)

    /** Parameter: the alias name. */
    val FUNCTION_TYPE_ALIAS_PUBLIC_API by configurable1<KtTypeAlias, Name>(NAME_IDENTIFIER)

    /** Parameter: the class name. */
    val DATA_CLASS_PUBLIC_API by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /** Parameter: the class name. */
    val STATEFUL_CLASS_WITHOUT_TO_STRING by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /**
     * Parameters: declaration kind in words, declaration name, the mutable type's name. Reported
     * on the offending type reference.
     */
    val MUTABLE_COLLECTION_PUBLIC_API by configurable3<KtElement, String, Name, Name>()

    /**
     * Parameters: declaration kind in words, declaration name, the tuple type's name. Reported
     * on the offending type reference.
     */
    val PAIR_OR_TRIPLE_PUBLIC_API by configurable3<KtElement, String, Name, Name>()

    /** Parameters: the parameter name, the callable name. Reported on the parameter name. */
    val REQUIRED_PARAMETER_AFTER_OPTIONAL by configurable2<KtParameter, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: the two swapped parameter names, the callable name. */
    val INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS by configurable3<KtDeclaration, Name, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: the function name, the parameter name. Reported on the parameter name. */
    val BOOLEAN_PARAMETER_PUBLIC_API by configurable2<KtParameter, Name, Name>(NAME_IDENTIFIER)

    /**
     * Parameters: declaration kind in words, declaration name. Reported on the offending type
     * reference.
     */
    val NULLABLE_BOOLEAN_PUBLIC_API by configurable2<KtElement, String, Name>()

    /** Parameters: the inlined declaration kind in words, the declaration name. */
    val INLINE_FUNCTION_WITH_LOGIC by configurable2<KtDeclaration, String, Name>(NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name, the value class's name. */
    val MANGLED_JVM_NAME_PUBLIC_API by configurable3<KtDeclaration, String, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: the function name, what makes its shape Kotlin-only, in words. */
    val KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC by configurable2<KtDeclaration, Name, String>(NAME_IDENTIFIER)

    /** Parameters: the outer class name, the function name. */
    val COMPANION_API_WITHOUT_JVM_STATIC by configurable2<KtDeclaration, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: the outer class name, the property name. */
    val COMPANION_CONSTANT_WITHOUT_JVM_FIELD by configurable2<KtDeclaration, Name, Name>(NAME_IDENTIFIER)

    /** Parameter: the facade class name. Reported once per file, on its first facade member. */
    val TOP_LEVEL_API_WITHOUT_JVM_NAME by configurable1<KtDeclaration, String>(NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS by configurable2<KtDeclaration, String, Name>(NAME_IDENTIFIER)

    /**
     * Parameters: the exemption annotation name, the reason that needs a description. Reported
     * on the annotation entry. Deliberately not configurable, unlike the other diagnostics: the
     * explanation requirement is what keeps every exemption honest, so it is always an error.
     */
    val EXEMPTION_WITHOUT_EXPLANATION by error2<KtAnnotationEntry, Name, Name>()

    /** Parameters: the marker name, the no-op target name. Reported on the `@Target` argument. */
    val DSL_MARKER_NOOP_TARGET by configurable2<KtExpression, Name, String>()

    /** Parameter: the marker name. */
    val DSL_MARKER_WITHOUT_EXPLICIT_TARGETS by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /** Parameters: the marker name, the type position in words. Reported on the annotation entry. */
    val DSL_MARKER_NOOP_TYPE_POSITION by configurable2<KtAnnotationEntry, Name, String>()

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = WatchdogErrorMessages

    /** Builds the error/warning factory pair, deriving the diagnostic name from the property. */
    private fun <F : AbstractKtDiagnosticFactory> configurableDiagnostic(
        createFactory: (name: String, severity: Severity) -> F,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ConfigurableWatchdogDiagnostic<F>>> =
        PropertyDelegateProvider { _, property ->
            val diagnostic = ConfigurableWatchdogDiagnostic(
                error = createFactory(property.name, Severity.ERROR),
                warning = createFactory(property.name, Severity.WARNING),
            )
            allDiagnostics += diagnostic
            ReadOnlyProperty { _, _ -> diagnostic }
        }

    private inline fun <reified P : PsiElement> configurable0(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory0(name, severity, positioningStrategy, P::class, getRendererFactory())
    }

    private inline fun <reified P : PsiElement, A> configurable1(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory1<A>(name, severity, positioningStrategy, P::class, getRendererFactory())
    }

    private inline fun <reified P : PsiElement, A, B> configurable2(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory2<A, B>(name, severity, positioningStrategy, P::class, getRendererFactory())
    }

    private inline fun <reified P : PsiElement, A, B, C> configurable3(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory3<A, B, C>(name, severity, positioningStrategy, P::class, getRendererFactory())
    }
}

private object WatchdogErrorMessages : BaseDiagnosticRendererFactory() {
    private val MEMBER_KIND = Renderer { classKind: ClassKind ->
        if (classKind == ClassKind.ENUM_CLASS) "an entry" else "a subtype"
    }

    override val MAP by KtDiagnosticFactoryToRendererMap("LibsApiWatchdog") { map ->
        map.put(
            diagnostic = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            message = "The {0} ''{1}'' can be subclassed outside the library without restriction, " +
                    "which makes it hard to evolve. Mark it with @SubclassOptInRequired to control " +
                    "external subclassing, or with @IntentionallyOpen if unrestricted subclassing " +
                    "is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/open-api-without-subclass-opt-in.html " +
                    "for details.",
            rendererA = CLASS_KIND,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS,
            message = "@SubclassOptInRequired lists no marker classes, so it doesn't restrict " +
                    "external subclassing. Pass at least one opt-in marker class " +
                    "with a description of why the subclassing is restricted. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/subclass-opt-in-without-markers.html " +
                    "for details.",
        )
        map.put(
            diagnostic = WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API,
            message = "The {0} ''{1}'' can be matched exhaustively by users, so adding {2} later is " +
                    "a breaking change. Mark it with @IntentionallyExhaustive if this " +
                    "exhaustive shape is an intended part of the API. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/exhaustive-public-api.html " +
                    "for details.",
            rendererA = CLASS_KIND,
            rendererB = NAME,
            rendererC = MEMBER_KIND,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API,
            message = "The {0} ''{1}'' is part of the public API but has no KDoc. Document it " +
                    "so users don't have to guess its purpose and usage contract, or mark it " +
                    "with @IntentionallyUndocumented if leaving it undocumented is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/undocumented-public-api.html " +
                    "for details.",
            rendererA = STRING,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API,
            message = "The type alias ''{0}'' abbreviates a function type, so users bind to the " +
                    "bare function shape: the alias is erased from the compiled API and cannot " +
                    "evolve into a richer abstraction later. Declare a `fun interface` instead to " +
                    "keep lambda ergonomics behind a stable nominal type, or mark the alias with " +
                    "@IntentionallyFunctionTypeAlias if exposing the function type is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/function-type-alias-public-api.html " +
                    "for details.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DATA_CLASS_PUBLIC_API,
            message = "The data class ''{0}'' bakes its constructor property list into the " +
                    "compiled API through the generated `copy` and `componentN` functions and the " +
                    "constructor itself, so adding, removing, or reordering a property later is a " +
                    "breaking change. Declare a regular class and implement " +
                    "`equals`/`hashCode`/`toString` explicitly, or mark the class with " +
                    "@IntentionallyDataClass if this property list is an intended, stable part " +
                    "of the API. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/data-class-public-api.html " +
                    "for details.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING,
            message = "The class ''{0}'' holds state - at least one property with a backing " +
                    "field - but neither declares nor inherits a `toString` implementation, so " +
                    "instances render as the opaque class-name-with-hash-code default and reveal " +
                    "nothing in logs and debugger output. Override `toString` to render the " +
                    "current state, or mark the class with @IntentionallyWithoutToString if the " +
                    "opaque rendering is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/stateful-class-without-to-string.html " +
                    "for details.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API,
            message = "The {0} ''{1}'' exposes the mutable collection type ''{2}''. Once a " +
                    "mutable collection is shared across the API boundary, it is unclear whether " +
                    "user-side and library-side mutations affect each other, and the library " +
                    "can no longer evolve its internal representation freely. Accept and return " +
                    "read-only types instead (arrays count as mutable collections too), handing " +
                    "out defensive copies where needed, or mark the declaration with " +
                    "@IntentionallyMutableCollection if sharing the collection is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/mutable-collection-public-api.html " +
                    "for details.",
            rendererA = STRING,
            rendererB = NAME,
            rendererC = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API,
            message = "The {0} ''{1}'' exposes the tuple type ''{2}''. Tuple components carry " +
                    "no domain meaning: at the use site `first`/`second`/`third` and positional " +
                    "destructuring reveal nothing about the values, and the fixed shape cannot " +
                    "evolve - adding a value means switching to a different type, breaking " +
                    "users. Declare a small class with descriptively named properties " +
                    "instead, or mark the declaration with @IntentionallyPairOrTriple if " +
                    "exposing the tuple is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/pair-or-triple-public-api.html " +
                    "for details.",
            rendererA = STRING,
            rendererB = NAME,
            rendererC = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL,
            message = "The parameter ''{0}'' of ''{1}'' is required but declared after an " +
                    "optional parameter, so it cannot be passed positionally without re-stating " +
                    "the defaults in front of it. Declare parameters from the general to the " +
                    "specific: essential inputs first, optional inputs - defaulted and vararg " +
                    "parameters - last. Move the required parameter in front of the optional " +
                    "ones, or mark the declaration with @IntentionallyRequiredParameterAfterOptional " +
                    "if this order is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/required-parameter-after-optional.html " +
                    "for details.",
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS,
            message = "The parameters ''{0}'' and ''{1}'' of ''{2}'' appear in the opposite " +
                    "order in another overload. Users transfer their expectations between " +
                    "overloads, so an inconsistent order of same-named parameters invites " +
                    "silently swapped arguments. Keep shared parameters in the same relative " +
                    "order across overloads, or mark the declaration with " +
                    "@IntentionallyInconsistentParameterOrder if the differing order is intended. " +
                    "See https://mr3zee.github.io/libs-api-watchdog/inconsistent-parameter-order-in-overloads.html " +
                    "for details.",
            rendererA = NAME,
            rendererB = NAME,
            rendererC = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API,
            message = "The function ''{0}'' takes the Boolean parameter ''{1}''. At the call " +
                    "site a positional `true`/`false` argument reveals nothing about its " +
                    "meaning, and users cannot be forced to use named arguments. Introduce " +
                    "separate, descriptively named functions for each mode, or replace the " +
                    "parameter with an enum class, or mark it with " +
                    "@IntentionallyBooleanParameter if the Boolean parameter is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/boolean-parameter-public-api.html " +
                    "for details.",
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API,
            message = "The {0} ''{1}'' exposes a nullable Boolean. `Boolean?` models three " +
                    "states but names only two of them, so every use site has to know what " +
                    "`null` stands for, and three-state logic hides in two-branch `if`s. " +
                    "Replace it with an enum class naming all three states, or drop the third " +
                    "state, or mark the declaration with @IntentionallyNullableBoolean if the " +
                    "nullable Boolean is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/nullable-boolean-public-api.html " +
                    "for details.",
            rendererA = STRING,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC,
            message = "The {0} ''{1}'' does more than delegate to a non-inline " +
                    "function. The compiler copies an inline body into every user binary, so " +
                    "logic placed there - and its bugs - stays frozen in users compiled " +
                    "against an old library version until they recompile. Extract the logic " +
                    "into a non-inline function (@PublishedApi internal if it should stay out " +
                    "of the public API) and delegate to it, or mark the declaration with " +
                    "@IntentionallyInlinedLogic if inlining the logic is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/inline-function-with-logic.html " +
                    "for details.",
            rendererA = STRING,
            rendererB = NAME,
        )
        map.put(
            WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
            "The @{0} exemption doesn't explain why it is applied: the {1} reason doesn't " +
                    "speak for itself, and the description is empty. Pass a self-explanatory " +
                    "reason (FOR_BACKWARDS_COMPATIBILITY, API_DESIGN), or describe the " +
                    "motivation in the description argument. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/exemption-without-explanation.html " +
                    "for details.",
            NAME,
            NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET,
            message = "The DSL marker ''{0}'' allows the {1} annotation target, but a DSL marker " +
                    "only takes effect on classifier declarations (CLASS, ANNOTATION_CLASS), type " +
                    "usages (TYPE), and type aliases (TYPEALIAS). Applied to a {1} element the " +
                    "marker restricts nothing and only gives a false sense of receiver scope " +
                    "control. Remove the target from @Target, or mark the marker with " +
                    "@IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility if the target " +
                    "must stay for compatibility with existing users. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/dsl-marker-noop-target.html " +
                    "for details.",
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS,
            message = "The DSL marker ''{0}'' declares no explicit @Target, so it defaults to " +
                    "targets like functions and properties where a DSL marker has no effect, while " +
                    "the effective type usage (TYPE) and type alias (TYPEALIAS) targets stay " +
                    "unavailable. Declare @Target(CLASS, TYPE, TYPEALIAS) or a subset of it, or " +
                    "mark the marker with @IntentionallyWrongDslMarkerTargetsForBackwardsCompatibility " +
                    "if the default targets must stay for compatibility with existing users. " +
                    "See https://mr3zee.github.io/libs-api-watchdog/dsl-marker-without-explicit-targets.html " +
                    "for details.",
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION,
            message = "The DSL marker ''{0}'' has no effect on this {1}: scope control only reacts " +
                    "to markers on the type of an implicit value - a receiver type, a context " +
                    "parameter type, or a function type with such implicit values. A named value " +
                    "is always accessed explicitly, so the marker restricts nothing here. Move it " +
                    "to the class or to a receiver position, or remove it. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/dsl-marker-noop-type-position.html " +
                    "for details.",
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API,
            message = "The {0} ''{1}'' has the value class ''{2}'' in its signature, so its " +
                    "compiled JVM name is mangled - or, for a constructor, hidden behind a " +
                    "synthetic one - and Java sources cannot call it. Kotlin users are " +
                    "unaffected. Give the compiled code a Java-callable shape with @JvmName " +
                    "(@get:JvmName/@set:JvmName on property accessors) or with @JvmExposeBoxed, " +
                    "or mark the declaration with @IntentionallyMangledJvmName if Java callers " +
                    "are not supported. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/mangled-jvm-name-public-api.html " +
                    "for details.",
            rendererA = STRING,
            rendererB = NAME,
            rendererC = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC,
            message = "The function ''{0}'' {1}. Kotlin callers see the intended shape, but the " +
                    "function still lands in the API surface Java sources see. Hide the " +
                    "Kotlin-only shape from Java with @JvmSynthetic, or provide a Java-friendly " +
                    "alternative alongside - a blocking or CompletableFuture-returning bridge " +
                    "for a suspend function, a `fun interface` parameter in place of a Kotlin " +
                    "function type - or mark the function with @IntentionallyKotlinOnlyApi if " +
                    "leaving the shape visible to Java is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/kotlin-only-api-without-jvm-synthetic.html for details.",
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC,
            message = "The companion object function ''{1}'' compiles to an instance method on " +
                    "the nested Companion class, so Java callers have to reach it as " +
                    "''{0}.Companion.{1}(...)''. Mark it with @JvmStatic to additionally compile " +
                    "a static ''{0}.{1}(...)'' entry point for Java callers - Kotlin call sites " +
                    "are unaffected - or hide it from Java with @JvmSynthetic, or mark it with " +
                    "@IntentionallyNonStaticCompanionApi if the companion-instance access path " +
                    "is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/companion-api-without-jvm-static.html " +
                    "for details.",
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.COMPANION_CONSTANT_WITHOUT_JVM_FIELD,
            message = "The companion object property ''{1}'' compiles to an instance getter on " +
                    "the nested Companion class, so Java callers have to read it through " +
                    "''{0}.Companion''. Expose the value on ''{0}'' itself: as a static field " +
                    "with @JvmField, as a compile-time constant with `const val` (primitives " +
                    "and strings), or as a static getter with @JvmStatic - or hide the property " +
                    "from Java with @get:JvmSynthetic, or mark it with " +
                    "@IntentionallyNonStaticCompanionApi if the companion-instance access path " +
                    "is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/companion-constant-without-jvm-field.html " +
                    "for details.",
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME,
            message = "This file''s public top-level functions and properties compile into the " +
                    "facade class ''{0}'', a name derived from the file name: it reads as an " +
                    "implementation detail at Java call sites, and renaming the file - invisible " +
                    "to Kotlin callers - renames the facade and breaks Java callers. Choose and " +
                    "pin the facade name deliberately with @file:JvmName, or mark the file with " +
                    "@file:IntentionallyDefaultFacadeName if the derived name is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/top-level-api-without-jvm-name.html " +
                    "for details. " +
                    "Reported once per file, on its first public top-level function or property.",
            rendererA = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS,
            message = "The {0} ''{1}'' declares default parameter values, but for Java callers " +
                    "the defaults don't exist: only the full signature is compiled, and every " +
                    "argument must be spelled out. Mark the {0} with @JvmOverloads to also " +
                    "compile the overloads that let Java callers omit defaulted parameters - " +
                    "trailing ones only: a defaulted parameter in the middle of the list still " +
                    "cannot be skipped from Java, and adding a parameter later stays binary " +
                    "incompatible either way - or mark the {0} with " +
                    "@IntentionallyWithoutJvmOverloads if serving Java callers the full " +
                    "signature only is intended. See " +
                    "https://mr3zee.github.io/libs-api-watchdog/default-parameters-without-jvm-overloads.html " +
                    "for details.",
            rendererA = STRING,
            rendererB = NAME,
        )
    }

    private fun KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory0>,
        message: String,
    ) {
        put(diagnostic.error, message)
        put(diagnostic.warning, message)
    }

    private fun <A> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory1<A>>,
        message: String,
        rendererA: DiagnosticParameterRenderer<A>?,
    ) {
        put(diagnostic.error, message, rendererA)
        put(diagnostic.warning, message, rendererA)
    }

    private fun <A, B> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory2<A, B>>,
        message: String,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
    ) {
        put(diagnostic.error, message, rendererA, rendererB)
        put(diagnostic.warning, message, rendererA, rendererB)
    }

    private fun <A, B, C> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory3<A, B, C>>,
        message: String,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
        rendererC: DiagnosticParameterRenderer<C>?,
    ) {
        put(diagnostic.error, message, rendererA, rendererB, rendererC)
        put(diagnostic.warning, message, rendererA, rendererB, rendererC)
    }
}
