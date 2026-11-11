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
 * The filters of the watched API, with the semantics of the ABI validation filters of the Kotlin
 * Gradle plugin. The exclusion by annotation is [ConfiguredInternalApiAnnotations].
 * `isWatchedPublicApi` adds the rule for a class with an included member, because the rule needs
 * the checker context.
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

    /** Enclosing declarations don't count. */
    fun excludingPattern(symbol: FirBasedSymbol<*>): String? =
        if (excludedNames.isEmpty) null else symbol.filterName()?.let(excludedNames::firstMatch)

    /**
     * The enclosing declarations of [symbol] don't count. The annotations of the matched `expect`
     * declaration and of its enclosing declarations count, because the `actual` can be in another file.
     */
    fun includes(symbol: FirBasedSymbol<*>): Boolean {
        if (!includedNames.isEmpty && symbol.filterName()?.let(includedNames::matches) == true) return true
        if (includedAnnotations.isEmpty) return false
        if (symbol.hasIncludedAnnotationOnSelfOrAccessor()) return true
        if (symbol is FirPropertyAccessorSymbol && symbol.propertySymbol.hasIncludedAnnotationOnSelfOrAccessor()) return true
        val expect = symbol.getSingleMatchedExpectForActualOrNull() ?: return false
        return expect.hasIncludedAnnotationOnSelfOrAccessor() ||
                expect.enclosingDeclarationsOfSymbol().any { it.hasIncludedAnnotation() }
    }

    /** An accessor is a member of the class in the ABI dump, so `@get:` and `@set:` annotations count for the property. */
    private fun FirBasedSymbol<*>.hasIncludedAnnotationOnSelfOrAccessor(): Boolean =
        hasIncludedAnnotation() || this is FirPropertySymbol &&
                (getterSymbol?.hasIncludedAnnotation() == true || setterSymbol?.hasIncludedAnnotation() == true)

    /** [chain] is the declaration, its enclosing declarations, and its file. */
    fun watches(chain: List<FirBasedSymbol<*>>): Boolean {
        if (chain.any { excludingPattern(it) != null }) return false
        return !hasInclusionRules || chain.any(::includes)
    }

    private fun FirBasedSymbol<*>.hasIncludedAnnotation(): Boolean =
        apiAnnotations().any { annotation ->
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
