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
 * Reports mutable collection classifiers and arrays found by the [ExposedTypeChecker] signature
 * sweep. Implementations and subtypes of the Kotlin mutable collection interfaces count. Flexible
 * types are inspected through their upper bound.
 *
 * A `vararg` array is handled separately so only a mutable element type is reported.
 */
internal class MutableCollectionChecker(
    private val severities: WatchdogDiagnosticSeverities,
) : ExposedTypeChecker(WatchdogClassIds.IntentionallyMutableCollection) {
    /**
     * A checker instance belongs to one FIR session. Public signatures repeatedly use the same
     * classifiers, so retain the hierarchy answer instead of rebuilding the complete transitive
     * supertype list for every occurrence.
     */
    private val mutableCollectionLikeByClassId = mutableMapOf<ClassId, Boolean>()

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

        return mutableCollectionLikeByClassId.getOrPut(this) {
            // Concrete implementations (ArrayList, java.util.HashMap, a hand-written MutableList
            // subtype, ...) expose the same mutators as the interfaces they implement.
            val symbol = type.toClassSymbol() ?: return@getOrPut false
            lookupSuperTypes(symbol, lookupInterfaces = true, deep = true, useSiteSession = context.session)
                .any { it.lookupTag.classId in mutableCollectionTypes }
        }
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
