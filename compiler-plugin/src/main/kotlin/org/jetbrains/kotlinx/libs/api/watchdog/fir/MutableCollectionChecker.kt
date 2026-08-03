package org.jetbrains.kotlinx.libs.api.watchdog.fir

import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneType
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
    private val collectionKindByClassId = mutableMapOf<ClassId, CollectionKind>()

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

        cachedMutableCollectionResult()?.let { return it }

        // Concrete implementations (ArrayList, java.util.HashMap, a hand-written MutableList
        // subtype, ...) expose the same mutators as the interfaces they implement.
        val result = type.toClassSymbol()?.inheritsMutableCollection() == true
        collectionKindByClassId[this] = result.toCollectionKind()
        return result
    }

    /**
     * Walks direct FIR supertypes so the Boolean query can short-circuit without materializing the
     * compiler utility's complete hierarchy or its `(symbol, substitutor)` traversal pairs.
     */
    context(context: CheckerContext)
    private fun FirClassSymbol<*>.inheritsMutableCollection(): Boolean {
        if (classId in mutableCollectionTypes) return true
        classId.cachedMutableCollectionResult()?.let { return it }
        collectionKindByClassId[classId] = CollectionKind.CHECKING

        var result = false
        for (superTypeRef in resolvedSuperTypeRefs) {
            val superType = superTypeRef.coneType as? ConeClassLikeType ?: continue
            if (superType.lookupTag.classId in mutableCollectionTypes ||
                superType.toClassSymbol()?.inheritsMutableCollection() == true
            ) {
                result = true
                break
            }
        }
        collectionKindByClassId[classId] = result.toCollectionKind()
        return result
    }

    /** `CHECKING` is a cycle back-edge and contributes no mutable supertype by itself. */
    private fun ClassId.cachedMutableCollectionResult(): Boolean? = when (collectionKindByClassId[this]) {
        CollectionKind.MUTABLE -> true
        CollectionKind.OTHER, CollectionKind.CHECKING -> false
        null -> null
    }

    private fun Boolean.toCollectionKind(): CollectionKind =
        if (this) CollectionKind.MUTABLE else CollectionKind.OTHER

    private enum class CollectionKind {
        CHECKING,
        MUTABLE,
        OTHER,
    }

    private companion object {
        val mutableCollectionTypes: Set<ClassId> = setOf(
            StandardClassIds.MutableIterable,
            StandardClassIds.MutableIterator,
            StandardClassIds.MutableListIterator,
            StandardClassIds.MutableCollection,
            StandardClassIds.MutableList,
            StandardClassIds.MutableSet,
            StandardClassIds.MutableMap,
            StandardClassIds.MutableMapEntry,
        )

        val arrayTypes: Set<ClassId> = buildSet {
            add(StandardClassIds.Array)
            addAll(StandardClassIds.primitiveArrayTypeByElementType.values)
            addAll(StandardClassIds.unsignedArrayTypeByElementType.values)
        }
    }
}
