package org.jetbrains.kotlinx.library.api.watchdog.fir

import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
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
import org.jetbrains.kotlin.diagnostics.warning2
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.NAME
import org.jetbrains.kotlin.diagnostics.rendering.CommonRenderers.STRING
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticParameterRenderer
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeAlias

/** The severity of a configurable diagnostic. [NONE] disables it. */
enum class WatchdogSeverity {
    ERROR,
    WARNING,
    NONE,
}

/**
 * A diagnostic with a severity chosen for each compilation. A factory has a fixed severity, so the
 * diagnostic has one factory for each severity under the same name. [exemptionCoversEnclosingClass]
 * is `true` when the exemption annotation on a class covers every member, and an annotation
 * ignore rule has the same scope.
 */
class ConfigurableWatchdogDiagnostic<out F : AbstractKtDiagnosticFactory>(
    val error: F,
    val warning: F,
    val exemptionCoversEnclosingClass: Boolean,
) {
    val name: String get() = error.name

    fun withSeverity(severity: WatchdogSeverity): F? = when (severity) {
        WatchdogSeverity.ERROR -> error
        WatchdogSeverity.WARNING -> warning
        WatchdogSeverity.NONE -> null
    }
}

/**
 * Severity overrides keyed by diagnostic name. An unlisted diagnostic has the `defaultSeverity`
 * of its `diagnostics.json` entry.
 */
class WatchdogDiagnosticSeverities(private val overrides: Map<String, WatchdogSeverity>) {
    /**
     * The factory with the configured severity, or null for `none`. A probe of [ExemptionProbe]
     * gets the factory with the default severity instead of null.
     */
    operator fun <F : AbstractKtDiagnosticFactory> get(diagnostic: ConfigurableWatchdogDiagnostic<F>): F? {
        val default = WatchdogDiagnosticMessages.defaultSeverityFor(diagnostic.name)
        val configured = overrides[diagnostic.name] ?: default
        val severity = if (configured == WatchdogSeverity.NONE && ExemptionProbe.everyDiagnosticEnabled) default else configured
        return diagnostic.withSeverity(severity)
    }

    /** Whether the configured severity isn't `none`. A probe doesn't change the answer. */
    fun isEnabled(diagnostic: ConfigurableWatchdogDiagnostic<*>): Boolean =
        (overrides[diagnostic.name] ?: WatchdogDiagnosticMessages.defaultSeverityFor(diagnostic.name)) != WatchdogSeverity.NONE

    companion object {
        val DEFAULT = WatchdogDiagnosticSeverities(emptyMap())
    }
}

object WatchdogDiagnostics : KtDiagnosticsContainer() {
    /** Must stay declared before the diagnostics: their delegate providers register into it. */
    val allDiagnostics: List<ConfigurableWatchdogDiagnostic<*>>
        field = mutableListOf()

    /** Parameters: class kind in words, declaration name, context-specific fix. */
    val OPEN_API_WITHOUT_SUBCLASS_OPT_IN by configurable3<String, Name, String>(KtDeclaration::class, NAME_IDENTIFIER)

    /** Parameter: the annotated type's name. */
    val SUBCLASS_OPT_IN_WITHOUT_MARKERS by configurable1<Name>(KtAnnotationEntry::class)

    /** Parameters: class kind in words, declaration name, what users can't add in words, context-specific fix. */
    val EXHAUSTIVE_PUBLIC_API by configurable4<String, Name, String, String>(KtClassOrObject::class, NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val UNDOCUMENTED_PUBLIC_API by configurable2<String, Name>(KtDeclaration::class, NAME_IDENTIFIER)

    /** Parameter: the alias name. */
    val FUNCTION_TYPE_ALIAS_PUBLIC_API by configurable1<Name>(KtTypeAlias::class, NAME_IDENTIFIER)

    /** Parameter: the class name. */
    val DATA_CLASS_PUBLIC_API by configurable1<Name>(KtClassOrObject::class, NAME_IDENTIFIER)

    /** Parameters: the class name, generation-library hint, and IDEA Generate shortcut. */
    val STATEFUL_CLASS_WITHOUT_EQUALS by configurable3<Name, String, String>(KtClassOrObject::class, NAME_IDENTIFIER)

    /** Parameters: the class name, generation-library hint, and IDEA Generate shortcut. */
    val STATEFUL_CLASS_WITHOUT_HASH_CODE by configurable3<Name, String, String>(KtClassOrObject::class, NAME_IDENTIFIER)

    /** Parameters: the class name, generation-library hint, and IDEA Generate shortcut. */
    val STATEFUL_CLASS_WITHOUT_TO_STRING by configurable3<Name, String, String>(KtClassOrObject::class, NAME_IDENTIFIER)

    /** Parameters: declaration kind, declaration name, mutable type name, context-specific fix. */
    val MUTABLE_COLLECTION_PUBLIC_API by configurable4<String, Name, Name, String>(KtElement::class)

    /** Parameters: declaration kind, declaration name, tuple type name, its component names. */
    val PAIR_OR_TRIPLE_PUBLIC_API by configurable4<String, Name, Name, String>(KtElement::class)

    /** Parameters: the parameter name, the callable name, and the callable kind. */
    val REQUIRED_PARAMETER_AFTER_OPTIONAL by configurable3<Name, Name, String>(KtParameter::class, NAME_IDENTIFIER)

    /** Parameters: this signature, the other signatures, the differing positions, and the callable kind. */
    val INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS by configurable4<String, String, String, String>(PSI_ELEMENT)

    /** Parameters: the function name, the parameter name. */
    val BOOLEAN_PARAMETER_PUBLIC_API by configurable2<Name, Name>(KtParameter::class, NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val NULLABLE_BOOLEAN_PUBLIC_API by configurable2<String, Name>(KtElement::class)

    /** Parameters: the inlined declaration kind in words, the declaration name. */
    val INLINE_FUNCTION_WITH_LOGIC by configurable2<String, Name>(KtDeclaration::class, NAME_IDENTIFIER)

    /** Parameters: declaration kind, declaration name, value class name, context-specific fix. */
    val MANGLED_JVM_NAME_PUBLIC_API by configurable4<String, Name, Name, String>(KtDeclaration::class, NAME_IDENTIFIER, exemptionCoversEnclosingClass = true)

    /** Parameters: the function name, what makes its shape Kotlin-only, in words. */
    val KOTLIN_ONLY_API_WITHOUT_JVM_SYNTHETIC by configurable2<Name, String>(KtDeclaration::class, NAME_IDENTIFIER, exemptionCoversEnclosingClass = true)

    /** Parameters: the outer class name, the function name, the companion class name. */
    val COMPANION_API_WITHOUT_JVM_STATIC by configurable3<Name, Name, Name>(KtDeclaration::class, NAME_IDENTIFIER, exemptionCoversEnclosingClass = true)

    /** Parameters: companion access path for Java, property, instance accessors in words, context-specific fix. */
    val COMPANION_PROPERTY_WITHOUT_STATIC_ACCESS by configurable4<String, Name, String, String>(
        KtDeclaration::class,
        NAME_IDENTIFIER,
        exemptionCoversEnclosingClass = true,
    )

    /** Parameters: facade class name, Java-visible callable kinds of the file. Reported one time for each file. */
    val TOP_LEVEL_API_WITHOUT_JVM_NAME by configurable2<String, String>(KtDeclaration::class, NAME_IDENTIFIER)

    /** Parameters: declaration kind in words, declaration name. */
    val DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS by configurable2<String, Name>(KtDeclaration::class, NAME_IDENTIFIER)

    /** Parameters: the exemption annotation name, the reason that needs a description. */
    val EXEMPTION_WITHOUT_EXPLANATION by error2<KtAnnotationEntry, Name, Name>()

    /** Parameters: the exemption annotation name, the exempted element in words. */
    val REDUNDANT_EXEMPTION by warning2<KtAnnotationEntry, Name, String>()

    /** Parameters: declaration kind, declaration name, exposed type FQ name, exemption snippet. */
    val PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY by error4<KtElement, String, Name, String, String>()

    /** Parameters: declaration kind, declaration name, internal type FQ name, annotation name. */
    val PUBLIC_TYPE_WITH_INTERNAL_API by error4<KtElement, String, Name, String, Name>()

    /** Parameters: the marker name, the no-op target name. */
    val DSL_MARKER_NOOP_TARGET by configurable2<Name, String>(KtExpression::class)

    /** Parameter: the marker name. */
    val DSL_MARKER_WITHOUT_EXPLICIT_TARGETS by configurable1<Name>(KtClassOrObject::class, NAME_IDENTIFIER)

    /** Parameters: the marker name, the type position in words. */
    val DSL_MARKER_NOOP_TYPE_POSITION by configurable2<Name, String>(KtAnnotationEntry::class)

    override fun getRendererFactory(): BaseDiagnosticRendererFactory = WatchdogErrorMessages

    private fun <F : AbstractKtDiagnosticFactory> configurableDiagnostic(
        exemptionCoversEnclosingClass: Boolean,
        createFactory: (name: String, severity: Severity) -> F,
    ): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, ConfigurableWatchdogDiagnostic<F>>> =
        PropertyDelegateProvider { _, property ->
            val diagnostic = ConfigurableWatchdogDiagnostic(
                error = createFactory(property.name, Severity.ERROR),
                warning = createFactory(property.name, Severity.WARNING),
                exemptionCoversEnclosingClass = exemptionCoversEnclosingClass,
            )
            allDiagnostics += diagnostic
            ReadOnlyProperty { _, _ -> diagnostic }
        }

    private fun configurable0(
        psiType: KClass<*>,
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        exemptionCoversEnclosingClass: Boolean = false,
    ) = configurableDiagnostic(exemptionCoversEnclosingClass) { name, severity ->
        KtDiagnosticFactory0(name, severity, positioningStrategy, psiType, getRendererFactory())
    }

    private fun <A> configurable1(
        psiType: KClass<*>,
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        exemptionCoversEnclosingClass: Boolean = false,
    ) = configurableDiagnostic(exemptionCoversEnclosingClass) { name, severity ->
        KtDiagnosticFactory1<A>(name, severity, positioningStrategy, psiType, getRendererFactory())
    }

    private fun <A, B> configurable2(
        psiType: KClass<*>,
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        exemptionCoversEnclosingClass: Boolean = false,
    ) = configurableDiagnostic(exemptionCoversEnclosingClass) { name, severity ->
        KtDiagnosticFactory2<A, B>(name, severity, positioningStrategy, psiType, getRendererFactory())
    }

    private fun <A, B, C> configurable3(
        psiType: KClass<*>,
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        exemptionCoversEnclosingClass: Boolean = false,
    ) = configurableDiagnostic(exemptionCoversEnclosingClass) { name, severity ->
        KtDiagnosticFactory3<A, B, C>(name, severity, positioningStrategy, psiType, getRendererFactory())
    }

    private fun <A, B, C, D> configurable4(
        psiType: KClass<*>,
        positioningStrategy: AbstractSourceElementPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        exemptionCoversEnclosingClass: Boolean = false,
    ) = configurableDiagnostic(exemptionCoversEnclosingClass) { name, severity ->
        KtDiagnosticFactory4<A, B, C, D>(name, severity, positioningStrategy, psiType, getRendererFactory())
    }
}

/**
 * `PsiElement`, for a diagnostic on a token. A class literal puts `com.intellij` in the bytecode,
 * which links in the CLI or in `kotlin-compiler-embeddable` only, so the lookup searches the
 * supertypes of [KtElement].
 */
private val PSI_ELEMENT: KClass<*> =
    generateSequence(listOf<Class<*>>(KtElement::class.java)) { level ->
        level.flatMap { it.interfaces.asList() }.takeIf { it.isNotEmpty() }
    }.flatten().first { it.simpleName == "PsiElement" }.kotlin

/** The message texts come from `diagnostics.json` through the generated [WatchdogDiagnosticMessages]. */
private object WatchdogErrorMessages : BaseDiagnosticRendererFactory() {
    override val MAP by KtDiagnosticFactoryToRendererMap("LibsApiWatchdog") { map ->
        map.put(
            diagnostic = WatchdogDiagnostics.OPEN_API_WITHOUT_SUBCLASS_OPT_IN,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.SUBCLASS_OPT_IN_WITHOUT_MARKERS,
            rendererA = NAME,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.EXHAUSTIVE_PUBLIC_API,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = STRING,
            rendererD = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.UNDOCUMENTED_PUBLIC_API,
            rendererA = STRING,
            rendererB = NAME,
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
            rendererA = STRING,
            rendererB = STRING,
            rendererC = STRING,
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
            diagnostic = WatchdogDiagnostics.REDUNDANT_EXEMPTION,
            rendererA = NAME,
            rendererB = STRING,
        )
        map.put(
            diagnostic = WatchdogDiagnostics.PUBLIC_TYPE_FROM_NON_TRANSITIVE_DEPENDENCY,
            rendererA = STRING,
            rendererB = NAME,
            rendererC = STRING,
            rendererD = STRING,
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
