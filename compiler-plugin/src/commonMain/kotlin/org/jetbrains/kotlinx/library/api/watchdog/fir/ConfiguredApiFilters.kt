package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.declarations.getSingleMatchedExpectForActualOrNull
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassLikeSymbol
import org.jetbrains.kotlin.fir.extensions.FirExtensionSessionComponent
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirEnumEntrySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFieldSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFileSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.load.kotlin.PackagePartClassUtils
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.jvm.isJvm

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
    fun watches(chain: List<FirBasedSymbol<*>>): Boolean =
        !excludes(chain) && (!hasInclusionRules || includesAny(chain))

    fun excludes(chain: List<FirBasedSymbol<*>>): Boolean =
        !excludedNames.isEmpty &&
                (chain.any { excludingPattern(it) != null } || facadeClassName(chain)?.let(excludedNames::matches) == true)

    fun includesAny(chain: List<FirBasedSymbol<*>>): Boolean =
        chain.any(::includes) || !includedNames.isEmpty && facadeClassName(chain)?.let(includedNames::matches) == true

    /**
     * The JVM facade class of a top-level function or property. The validation tools match a name
     * rule against it in a JVM dump. A declaration of a common module is also in the klib dumps,
     * which don't have the class, so it gets null.
     */
    @OptIn(SymbolInternals::class)
    private fun facadeClassName(chain: List<FirBasedSymbol<*>>): String? {
        if (chain.any { it is FirClassLikeSymbol<*> }) return null
        if (chain.none { it is FirNamedFunctionSymbol || it is FirPropertySymbol }) return null
        val fileSymbol = chain.firstNotNullOfOrNull { it as? FirFileSymbol } ?: return null
        val module = fileSymbol.moduleData
        if (!module.platform.isJvm() || module.isCommon) return null
        val file = fileSymbol.fir
        val name = fileSymbol.resolvedAnnotationsWithArguments
            .getAnnotationByClassId(JvmStandardClassIds.Annotations.JvmName, session)
            ?.getStringArgumentCompat(Name.identifier("name"), session)
            ?: PackagePartClassUtils.getFilePartShortName(file.name)
        return file.packageDirective.packageFqName.child(Name.identifier(name)).asString()
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
