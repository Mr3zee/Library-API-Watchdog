package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.resolve.lookupSuperTypes
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

/**
 * Reports [mutable collection types](https://kotlinlang.org/docs/api-guidelines-predictability.html#avoid-exposing-mutable-state)
 * in publicly visible signatures, type parameter bounds and type arguments included
 * (`List<MutableList<Int>>` still hands out a mutable collection; see [ExposedTypeChecker] for
 * the shared sweep). Once a mutable collection is shared across the API boundary, it is unclear
 * whether user-side and library-side mutations affect each other, so the API should accept and
 * return read-only types, handing out defensive copies where needed. Authors acknowledge
 * deliberate sharing with `@IntentionallyMutableCollection`.
 *
 * A type counts as mutable when it is one of the `kotlin.collections` mutable interfaces, any
 * classifier implementing them (`ArrayList`, a hand-written `MutableList` subtype, ...), or an
 * array - the guideline treats arrays as mutable collections as well. Flexible (Java platform)
 * types don't declare their mutability in Kotlin sources, so only the read-only upper bound is
 * inspected.
 */
internal class MutableCollectionChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : ExposedTypeChecker(WatchdogClassIds.IntentionallyMutableCollection) {
    context(context: CheckerContext)
    override fun ConeKotlinType.violatingClassifier(): Name? {
        val type = this as? ConeClassLikeType ?: return null
        val classId = type.lookupTag.classId
        return classId.shortClassName.takeIf { classId.isMutableCollectionLike(type) }
    }

    /**
     * A `vararg` parameter receives a defensive copy of the array, so only the declared element
     * type - the array's type argument - can leak a mutable collection, not the array itself.
     */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun findVarargViolation(parameterType: ConeKotlinType): Name? =
        parameterType.typeArguments.firstNotNullOfOrNull { it.type?.findViolation() }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: Name) {
        val factory = severities[WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API] ?: return
        reporter.reportOn(
            source = source,
            factory = factory,
            a = kind,
            b = name,
            c = violation,
        )
    }

    context(context: CheckerContext)
    private fun ClassId.isMutableCollectionLike(type: ConeClassLikeType): Boolean {
        if (this in mutableCollectionTypes || this in arrayTypes) {
            return true
        }

        // Concrete implementations (ArrayList, java.util.HashMap, a hand-written MutableList
        // subtype, ...) expose the same mutators as the interfaces they implement.
        val symbol = type.toClassSymbol() ?: return false
        return lookupSuperTypes(symbol, lookupInterfaces = true, deep = true, useSiteSession = context.session)
            .any { it.lookupTag.classId in mutableCollectionTypes }
    }

    private val mutableCollectionTypes: Set<ClassId> = setOf(
        StandardClassIds.MutableIterable,
        StandardClassIds.MutableIterator,
        StandardClassIds.MutableListIterator,
        StandardClassIds.MutableCollection,
        StandardClassIds.MutableList,
        StandardClassIds.MutableSet,
        StandardClassIds.MutableMap,
        StandardClassIds.MutableMapEntry,
    )

    private val arrayTypes: Set<ClassId> = buildSet {
        add(StandardClassIds.Array)
        addAll(StandardClassIds.primitiveArrayTypeByElementType.values)
        addAll(StandardClassIds.unsignedArrayTypeByElementType.values)
    }
}
