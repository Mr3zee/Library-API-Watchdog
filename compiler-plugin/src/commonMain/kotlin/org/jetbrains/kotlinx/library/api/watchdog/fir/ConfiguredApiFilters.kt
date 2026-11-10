package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

/**
 * The name and annotation filters of the watched API, in the semantics of the ABI validation
 * filters of the Kotlin Gradle plugin. [NamePatterns] gives the grammar.
 *
 * - An exclusion rule has priority over an inclusion rule.
 * - Without inclusion rules, every declaration that isn't excluded is watched.
 * - A rule on a declaration covers the declarations nested in it.
 * - A class with an included function, property, enum entry, or constructor is included, but
 *   its other members aren't. `isWatchedPublicApi` has this rule, because it needs the checker
 *   context.
 *
 * A constructor, an accessor, and a parameter don't have a name of their own. An annotation of
 * the matched `expect` declaration includes the `actual` too, so both get the same verdict.
 * The exclusion by annotation is [ConfiguredInternalApiAnnotations].
 */
internal class ConfiguredApiFilters(
    session: FirSession,
    excludedNames: Set<String>,
    includedNames: Set<String>,
    includedAnnotations: Set<String>,
) : FirExtensionSessionComponent(session) {
    private val excludedNames = NamePatterns(excludedNames)
    private val includedNames = NamePatterns(includedNames)
    private val includedAnnotations = NamePatterns(includedAnnotations)

    val hasInclusionRules: Boolean = !this.includedNames.isEmpty || !this.includedAnnotations.isEmpty

    /** The exclusion pattern that matches the name of [symbol], or null. Enclosing declarations don't count. */
    fun excludingPattern(symbol: FirBasedSymbol<*>): String? =
        if (excludedNames.isEmpty) null else symbol.filterName()?.let(excludedNames::firstMatch)

    /** Whether an inclusion rule matches the name or an annotation of [symbol]. Enclosing declarations don't count. */
    fun includes(symbol: FirBasedSymbol<*>): Boolean {
        if (!includedNames.isEmpty && symbol.filterName()?.let(includedNames::matches) == true) return true
        if (includedAnnotations.isEmpty) return false
        return symbol.hasIncludedAnnotationOnSelfOrAccessor() ||
                symbol.getSingleMatchedExpectForActualOrNull()?.hasIncludedAnnotationOnSelfOrAccessor() == true ||
                symbol is FirPropertyAccessorSymbol && symbol.propertySymbol.hasIncludedAnnotationOnSelfOrAccessor()
    }

    /** An accessor is a member of the class in the ABI dump, so `@get:` and `@set:` annotations count for the property. */
    private fun FirBasedSymbol<*>.hasIncludedAnnotationOnSelfOrAccessor(): Boolean =
        hasIncludedAnnotation() || this is FirPropertySymbol &&
                (getterSymbol?.hasIncludedAnnotation() == true || setterSymbol?.hasIncludedAnnotation() == true)

    /**
     * Whether the filters leave a declaration in the watched API. [chain] is the declaration, its
     * enclosing declarations, and its file.
     */
    fun watches(chain: List<FirBasedSymbol<*>>): Boolean {
        if (chain.any { excludingPattern(it) != null }) return false
        return !hasInclusionRules || chain.any(::includes)
    }

    private fun FirBasedSymbol<*>.hasIncludedAnnotation(): Boolean =
        resolvedAnnotationsWithClassIds.any { annotation ->
            val classId = annotation.toAnnotationClassLikeSymbol(session)?.classId ?: return@any false
            includedAnnotations.matches(classId.asSingleFqName().asString())
        }
}

/** The fully qualified name with `.` between all parts, or null for a declaration without a name of its own. */
private fun FirBasedSymbol<*>.filterName(): String? = when (this) {
    is FirClassLikeSymbol<*> -> classId.takeUnless { it.isLocal }?.asSingleFqName()?.asString()
    is FirPropertyAccessorSymbol -> propertySymbol.filterName()
    is FirNamedFunctionSymbol, is FirPropertySymbol, is FirEnumEntrySymbol, is FirFieldSymbol ->
        (this as FirCallableSymbol<*>).callableId?.asSingleFqName()?.asString()
    else -> null
}

internal val FirSession.configuredApiFilters: ConfiguredApiFilters?
        by FirSession.nullableSessionComponentAccessor()
