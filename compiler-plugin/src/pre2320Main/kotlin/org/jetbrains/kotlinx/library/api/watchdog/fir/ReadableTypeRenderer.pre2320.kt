package org.jetbrains.kotlinx.library.api.watchdog.fir

import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.ConeDefinitelyNotNullType
import org.jetbrains.kotlin.fir.types.ConeErrorType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeConflictingProjection
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionIn
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionOut
import org.jetbrains.kotlin.fir.types.ConeTypeProjection
import org.jetbrains.kotlin.fir.types.contextParameterNumberForFunctionType
import org.jetbrains.kotlin.fir.types.isExtensionFunctionType
import org.jetbrains.kotlin.fir.types.isMarkedNullable
import org.jetbrains.kotlin.fir.types.renderReadable
import org.jetbrains.kotlin.name.ClassId

// The texts are the texts of `ConeTypeRenderer.renderFunctionType` of Kotlin 2.3.20.
internal actual fun ConeKotlinType.renderReadableCompat(): String {
    if (this !is ConeClassLikeType || this is ConeErrorType || typeArguments.isEmpty()) return renderReadable()
    val classId = lookupTag.classId
    val arguments = typeArguments.map { it.renderReadableCompat() }
    val nullability = if (isMarkedNullable) "?" else ""
    if (!classId.isFunctionType()) {
        return arguments.joinToString(prefix = "${classId.relativeClassName}<", postfix = ">$nullability")
    }

    var parameters = arguments.dropLast(1)
    val functionType = buildString {
        if (classId.isSuspendFunctionType()) append("suspend ")
        val contextParameterCount = contextParameterNumberForFunctionType
        if (contextParameterCount > 0) {
            append(parameters.take(contextParameterCount).joinToString(prefix = "context(", postfix = ") "))
            parameters = parameters.drop(contextParameterCount)
        }
        if (isExtensionFunctionType && parameters.isNotEmpty()) {
            val receiver = parameters.first()
            append(if (typeArguments[contextParameterCount].needsParentheses()) "($receiver)" else receiver)
            append('.')
            parameters = parameters.drop(1)
        }
        append(parameters.joinToString(prefix = "(", postfix = ") -> "))
        append(arguments.last())
    }
    return if (isMarkedNullable) "($functionType)?" else functionType
}

private fun ConeTypeProjection.renderReadableCompat(): String = when (this) {
    is ConeKotlinType -> renderReadableCompat()
    is ConeKotlinTypeProjectionIn -> "in ${type.renderReadableCompat()}"
    is ConeKotlinTypeProjectionOut -> "out ${type.renderReadableCompat()}"
    is ConeKotlinTypeConflictingProjection -> "CONFLICTING-PROJECTION ${type.renderReadableCompat()}"
    else -> "*"
}

private fun ConeTypeProjection.needsParentheses(): Boolean =
    this !is ConeKotlinType ||
        this is ConeDefinitelyNotNullType ||
        this is ConeClassLikeType && lookupTag.classId.isFunctionType() && !isMarkedNullable

private fun ClassId.isFunctionType(): Boolean =
    asString().removePrefix("kotlin/Function").toIntOrNull() != null || isSuspendFunctionType()

private fun ClassId.isSuspendFunctionType(): Boolean =
    asString().removePrefix("kotlin/coroutines/SuspendFunction").toIntOrNull() != null
