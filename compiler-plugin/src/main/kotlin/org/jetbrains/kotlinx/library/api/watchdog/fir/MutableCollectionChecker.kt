package org.jetbrains.kotlinx.library.api.watchdog.fir

import java.util.ArrayDeque
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.caches.firCachesFactory
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
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
    private val session: FirSession,
    private val severities: WatchdogDiagnosticSeverities,
) : ExposedTypeChecker(WatchdogClassIds.IntentionallyMutableCollection) {
    /**
     * A checker instance belongs to one FIR session. Public signatures repeatedly use the same
     * classifiers, so retain the hierarchy answer instead of rebuilding the complete transitive
     * supertype list for every occurrence.
     */
    private val isMutableCollectionByClassId =
        session.firCachesFactory.createCache<ClassId, Boolean, ConeClassLikeType> { _, type ->
            type.toClassSymbol(session)?.inheritsMutableCollection() == true
        }

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

    private fun ClassId.isMutableCollectionLike(type: ConeClassLikeType): Boolean {
        // Concrete implementations (ArrayList, java.util.HashMap, a hand-written MutableList
        // subtype, ...) expose the same mutators as the interfaces they implement.
        return this in mutableCollectionTypes || this in arrayTypes || isMutableCollectionByClassId.getValue(this, type)
    }

    /**
     * Walks direct FIR supertypes so the Boolean query can short-circuit without materializing the
     * compiler utility's complete hierarchy or its `(symbol, substitutor)` traversal pairs.
     */
    private fun FirClassSymbol<*>.inheritsMutableCollection(): Boolean {
        val pending = ArrayDeque<FirClassSymbol<*>>()
        val visited = hashSetOf<ClassId>()
        pending.addLast(this)

        while (pending.isNotEmpty()) {
            val symbol = pending.removeLast()
            if (!visited.add(symbol.classId)) continue
            if (symbol.classId in mutableCollectionTypes) return true

            for (superTypeRef in symbol.resolvedSuperTypeRefs) {
                val superType = superTypeRef.coneType as? ConeClassLikeType ?: continue
                if (superType.lookupTag.classId in mutableCollectionTypes) return true

                val superClassId = superType.lookupTag.classId
                when (isMutableCollectionByClassId.getValueIfComputed(superClassId)) {
                    true -> return true
                    false -> continue
                    null -> superType.toClassSymbol(session)?.let(pending::addLast)
                }
            }
        }
        return false
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
