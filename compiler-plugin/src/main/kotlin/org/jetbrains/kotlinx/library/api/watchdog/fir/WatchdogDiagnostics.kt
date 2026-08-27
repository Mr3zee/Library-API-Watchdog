package org.jetbrains.kotlinx.library.api.watchdog.fir

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
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory4
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.KtDiagnosticsContainer
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies.NAME_IDENTIFIER
import org.jetbrains.kotlin.diagnostics.error2
import org.jetbrains.kotlin.diagnostics.error3
import org.jetbrains.kotlin.diagnostics.error4
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
 * Per-compilation severity overrides keyed by diagnostic name. Unlisted diagnostics are errors.
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

    /** Parameters: class kind, declaration name, context-specific fix. */
    val OPEN_API_WITHOUT_SUBCLASS_OPT_IN by configurable3<KtDeclaration, ClassKind, Name, String>(NAME_IDENTIFIER)

    /** Parameter: the annotated type's name. */
    val SUBCLASS_OPT_IN_WITHOUT_MARKERS by configurable1<KtAnnotationEntry, Name>()

    /** Parameters: class kind, declaration name, member wording kind, context-specific fix. */
    val EXHAUSTIVE_PUBLIC_API by configurable4<KtClassOrObject, ClassKind, Name, ClassKind, String>(NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name, context-specific documentation guidance. */
    val UNDOCUMENTED_PUBLIC_API by configurable3<KtDeclaration, String, Name, String>(NAME_IDENTIFIER)

    /** Parameter: the alias name. */
    val FUNCTION_TYPE_ALIAS_PUBLIC_API by configurable1<KtTypeAlias, Name>(NAME_IDENTIFIER)

    /** Parameter: the class name. */
    val DATA_CLASS_PUBLIC_API by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /** Parameters: the class name, generation-library hint, and IDEA Generate shortcut. */
    val STATEFUL_CLASS_WITHOUT_EQUALS by configurable3<KtClassOrObject, Name, String, String>(NAME_IDENTIFIER)

    /** Parameters: the class name, generation-library hint, and IDEA Generate shortcut. */
    val STATEFUL_CLASS_WITHOUT_HASH_CODE by configurable3<KtClassOrObject, Name, String, String>(NAME_IDENTIFIER)

    /** Parameters: the class name, generation-library hint, and IDEA Generate shortcut. */
    val STATEFUL_CLASS_WITHOUT_TO_STRING by configurable3<KtClassOrObject, Name, String, String>(NAME_IDENTIFIER)

    /** Parameters: declaration kind, declaration name, mutable type name, context-specific fix. */
    val MUTABLE_COLLECTION_PUBLIC_API by configurable4<KtElement, String, Name, Name, String>()

    /** Parameters: declaration kind, declaration name, tuple type name, its component names. */
    val PAIR_OR_TRIPLE_PUBLIC_API by configurable4<KtElement, String, Name, Name, String>()

    /** Parameters: the parameter name, the callable name, and the callable kind. */
    val REQUIRED_PARAMETER_AFTER_OPTIONAL by configurable3<KtParameter, Name, Name, String>(NAME_IDENTIFIER)

    /** Parameters: the two swapped parameter names, the callable name, and the callable kind. */
    val INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS by configurable4<KtDeclaration, Name, Name, Name, String>(
        CALLABLE_NAME_OR_CONSTRUCTOR_KEYWORD,
    )

    /** Parameters: the function name, the parameter name. */
    val BOOLEAN_PARAMETER_PUBLIC_API by configurable2<KtParameter, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val NULLABLE_BOOLEAN_PUBLIC_API by configurable2<KtElement, String, Name>()

    /** Parameters: the inlined declaration kind in words, the declaration name. */
    val INLINE_FUNCTION_WITH_LOGIC by configurable2<KtDeclaration, String, Name>(NAME_IDENTIFIER)

    /** Parameters: declaration kind, declaration name, value class name, context-specific fix. */
    val MANGLED_JVM_NAME_PUBLIC_API by configurable4<KtDeclaration, String, Name, Name, String>(NAME_IDENTIFIER)

    /** Parameters: the function name, what makes its shape Kotlin-only, in words. */
    val KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC by configurable2<KtDeclaration, Name, String>(NAME_IDENTIFIER)

    /** Parameters: the outer class name, the function name, the companion class name. */
    val COMPANION_API_WITHOUT_JVM_STATIC by configurable3<KtDeclaration, Name, Name, Name>(NAME_IDENTIFIER)

    /** Parameters: companion access path for Java, property, instance accessors in words, context-specific fix. */
    val COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS by configurable4<KtDeclaration, String, Name, String, String>(
        NAME_IDENTIFIER,
    )

    /** Parameters: facade class name and the file's Java-visible callable kinds. Emitted once per file. */
    val TOP_LEVEL_API_WITHOUT_JVM_NAME by configurable2<KtDeclaration, String, String>(NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS by configurable2<KtDeclaration, String, Name>(NAME_IDENTIFIER)

    /**
     * Parameters: the exemption annotation name, the reason that needs a description. Deliberately
     * not configurable, unlike the other diagnostics: the explanation requirement is what keeps
     * every exemption honest, so it is always an error.
     */
    val EXEMPTION_WITHOUT_EXPLANATION by error2<KtAnnotationEntry, Name, Name>()

    /**
     * Parameters: declaration kind, declaration name, exposed type's fully qualified name.
     * Gradle may disable the check, but whenever it runs this diagnostic is always an error.
     */
    val PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY by error3<KtElement, String, Name, String>()

    /**
     * Parameters: declaration kind, declaration name, internal type FQ name, annotation name.
     * A supported API exposing a type that explicitly has no supported contract is always an
     * error. The whole check has a Boolean off-switch, but its diagnostic can't be demoted.
     */
    val PUBLIC_TYPE_WITH_INTERNAL_API by error4<KtElement, String, Name, String, Name>()

    /** Parameters: the marker name, the no-op target name. */
    val DSL_MARKER_NOOP_TARGET by configurable2<KtExpression, Name, String>()

    /** Parameter: the marker name. */
    val DSL_MARKER_WITHOUT_EXPLICIT_TARGETS by configurable1<KtClassOrObject, Name>(NAME_IDENTIFIER)

    /** Parameters: the marker name, the type position in words. */
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

    private inline fun <reified P : PsiElement, A, B, C, D> configurable4(
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
    ) = configurableDiagnostic { name, severity ->
        KtDiagnosticFactory4<A, B, C, D>(name, severity, positioningStrategy, P::class, getRendererFactory())
    }
}

/**
 * Binds every diagnostic to its parameter renderers. The message texts themselves live in the
 * shared `diagnostics.json` and reach this file through the generated
 * [WatchdogDiagnosticMessages], keyed by diagnostic name, so the compiler and the documentation
 * website always speak about a check in the same words.
 */
private object WatchdogErrorMessages : BaseDiagnosticRendererFactory() {
    private val MEMBER_KIND = Renderer { classKind: ClassKind ->
        if (classKind == ClassKind.ENUM_CLASS) "an entry" else "a subtype"
    }

    override val MAP by KtDiagnosticFactoryToRendererMap("LibsApiWatchdog") { map ->
        map.put(
            diagnostic = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            rendererA = CLASS_KIND,
            rendererB = NAME,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS,
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API,
            rendererA = CLASS_KIND,
            rendererB = NAME,
            rendererC = MEMBER_KIND,
            rendererD = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.FUNCTION_TYPE_ALIAS_PUBLIC_API,
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DATA_CLASS_PUBLIC_API,
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_EQUALS,
            rendererA = NAME,
            rendererB = STRING,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_HASH_CODE,
            rendererA = NAME,
            rendererB = STRING,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.STATEFUL_CLASS_WITHOUT_TO_STRING,
            rendererA = NAME,
            rendererB = STRING,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = NAME,
            rendererD = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.PAIR_OR_TRIPLE_PUBLIC_API,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = NAME,
            rendererD = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.REQUIRED_PARAMETER_AFTER_OPTIONAL,
            rendererA = NAME,
            rendererB = NAME,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS,
            rendererA = NAME,
            rendererB = NAME,
            rendererC = NAME,
            rendererD = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.BOOLEAN_PARAMETER_PUBLIC_API,
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.NULLABLE_BOOLEAN_PUBLIC_API,
            rendererA = STRING,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.INLINE_FUNCTION_WITH_LOGIC,
            rendererA = STRING,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.EXEMPTION_WITHOUT_EXPLANATION,
            rendererA = NAME,
            rendererB = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.PUBLIC_TYPE_WITH_INTERNAL_API,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = STRING,
            rendererD = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_NOOP_TARGET,
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_WITHOUT_EXPLICIT_TARGETS,
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DSL_MARKER_NOOP_TYPE_POSITION,
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.MANGLED_JVM_NAME_PUBLIC_API,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = NAME,
            rendererD = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC,
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.COMPANION_API_WITHOUT_JVM_STATIC,
            rendererA = NAME,
            rendererB = NAME,
            rendererC = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = STRING,
            rendererD = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.TOP_LEVEL_API_WITHOUT_JVM_NAME,
            rendererA = STRING,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS,
            rendererA = STRING,
            rendererB = NAME,
        )
    }

    private fun KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory0>,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic.error, message)
        put(diagnostic.warning, message)
    }

    private fun <A> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory1<A>>,
        rendererA: DiagnosticParameterRenderer<A>?,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic.error, message, rendererA)
        put(diagnostic.warning, message, rendererA)
    }

    private fun <A, B> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory2<A, B>>,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic.error, message, rendererA, rendererB)
        put(diagnostic.warning, message, rendererA, rendererB)
    }

    private fun <A, B> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: KtDiagnosticFactory2<A, B>,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic, message, rendererA, rendererB)
    }

    private fun <A, B, C> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: KtDiagnosticFactory3<A, B, C>,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
        rendererC: DiagnosticParameterRenderer<C>?,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic, message, rendererA, rendererB, rendererC)
    }

    private fun <A, B, C> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory3<A, B, C>>,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
        rendererC: DiagnosticParameterRenderer<C>?,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic.error, message, rendererA, rendererB, rendererC)
        put(diagnostic.warning, message, rendererA, rendererB, rendererC)
    }

    private fun <A, B, C, D> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: KtDiagnosticFactory4<A, B, C, D>,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
        rendererC: DiagnosticParameterRenderer<C>?,
        rendererD: DiagnosticParameterRenderer<D>?,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic, message, rendererA, rendererB, rendererC, rendererD)
    }

    private fun <A, B, C, D> KtDiagnosticFactoryToRendererMap.put(
        diagnostic: ConfigurableWatchdogDiagnostic<KtDiagnosticFactory4<A, B, C, D>>,
        rendererA: DiagnosticParameterRenderer<A>?,
        rendererB: DiagnosticParameterRenderer<B>?,
        rendererC: DiagnosticParameterRenderer<C>?,
        rendererD: DiagnosticParameterRenderer<D>?,
    ) {
        val message = WatchdogDiagnosticMessages.messageFor(diagnostic.name)
        put(diagnostic.error, message, rendererA, rendererB, rendererC, rendererD)
        put(diagnostic.warning, message, rendererA, rendererB, rendererC, rendererD)
    }
}
