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
import org.jetbrains.kotlin.fir.types.contextParameterNumberForFunctionType
import org.jetbrains.kotlin.fir.types.isExtensionFunctionType
import org.jetbrains.kotlin.fir.types.type
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

internal class MutableCollectionChecker(
    private val session: FirSession,
    private val severities: WatchdogDiagnosticSeverities,
) : ExposedTypeChecker(WatchdogClassIds.IntentionallyMutableCollection) {
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

    context(context: CheckerContext)
    override fun ConeKotlinType.shouldCheckTypeArgument(index: Int): Boolean =
        !isExtensionFunctionType || index != contextParameterNumberForFunctionType

    /** A `vararg` parameter gets a copy of the array, so only the element type can leak a mutable collection. */
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun findVarargViolation(parameterType: ConeKotlinType): Name? =
        parameterType.typeArguments.firstNotNullOfOrNull { it.type?.findViolation() }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun report(source: KtSourceElement?, kind: String, name: Name, violation: Name) {
        val factory = severities[WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API] ?: return
        val fix = WatchdogDiagnosticMessages.parameterValueFor(
            diagnostic = WatchdogDiagnostics.MUTABLE_COLLECTION_PUBLIC_API.name,
            value = when (kind) {
                "parameter" -> "parameterFix"
                "typeParameter" -> "typeParameterFix"
                else -> "returnTypeFix"
            },
        )
        reporter.reportOn(
            source = source,
            factory = factory,
            a = WatchdogDiagnosticMessages.parameterValueFor(factory.name, kind),
            b = name,
            c = violation,
            d = fix,
        )
    }

    private fun ClassId.isMutableCollectionLike(type: ConeClassLikeType): Boolean {
        return this in mutableCollectionTypes || this in arrayTypes || isMutableCollectionByClassId.getValue(this, type)
    }

    /** A hand-written walk stops at the first match, unlike the compiler utility that builds the whole hierarchy. */
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
