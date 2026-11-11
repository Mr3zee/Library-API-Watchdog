package org.jetbrains.kotlinx.library.api.watchdog

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider

/** Filter rules in the name grammar of the compiler plugin options. */
internal data class ApiFilterRules(
    val excludedNames: Set<String> = emptySet(),
    val excludedAnnotations: Set<String> = emptySet(),
    val includedNames: Set<String> = emptySet(),
    val includedAnnotations: Set<String> = emptySet(),
) {
    /**
     * The rules without those that the compiler plugin rejects. Such a rule comes from another
     * tool, so it doesn't fail the build.
     */
    fun withoutUnusableRules(): ApiFilterRules = ApiFilterRules(
        excludedNames.usableRules(),
        excludedAnnotations.usableRules(),
        includedNames.usableRules(),
        includedAnnotations.usableRules(),
    )

    private fun Set<String>.usableRules(): Set<String> = map(String::trim).filterTo(linkedSetOf(), ::isUsableRule)

    companion object {
        fun isUsableRule(rule: String): Boolean = rule.isNotBlank() && rule.none { it.isWhitespace() || it == ':' }
    }
}

/**
 * The filters of one validation tool, without a merge. When both tools validate the project, the ABI
 * validation of the Kotlin Gradle plugin wins. Call it after the build scripts of the project and of
 * its parent projects ran, because the Binary Compatibility Validator plugin has plain mutable sets.
 */
internal fun Project.abiValidationFilterRules(): ApiFilterRules =
    (kotlinAbiValidationFilterRules() ?: binaryCompatibilityValidatorFilterRules() ?: ApiFilterRules())
        .withoutUnusableRules()

/**
 * Reflection reads the filters, because the `exclude` and `include` properties have the names
 * `excluded` and `included` before Kotlin 2.3.20, and the getters with the old names throw since
 * Kotlin 2.4.
 */
private fun Project.kotlinAbiValidationFilterRules(): ApiFilterRules? {
    val filters = activeKotlinAbiValidation()?.property("getFilters") ?: return null
    val exclude = filters.property("getExclude") ?: filters.property("getExcluded")
    val include = filters.property("getInclude") ?: filters.property("getIncluded")
    return ApiFilterRules(
        excludedNames = exclude.stringSet("getByNames"),
        excludedAnnotations = exclude.stringSet("getAnnotatedWith"),
        includedNames = include.stringSet("getByNames"),
        includedAnnotations = include.stringSet("getAnnotatedWith"),
    )
}

/**
 * The `abiValidation` extension when the ABI validation of the Kotlin Gradle plugin validates this
 * project, or null.
 *
 * - Kotlin 2.2 and 2.3 register `abiValidation` as an extension of the `kotlin` extension, and
 *   its `enabled` flag turns the validation on.
 * - The `abiValidation` getter of Kotlin 2.4 and later turns the validation on, so the function
 *   calls the getter only when the check task of an active validation exists.
 */
internal fun Project.activeKotlinAbiValidation(): Any? {
    val kotlin = extensions.findByName("kotlin") ?: return null
    val legacy = (kotlin as? ExtensionAware)?.extensions?.findByName("abiValidation")
    if (legacy != null) {
        return legacy.takeIf { (it.property("getEnabled") as? Provider<*>)?.orNull == true }
    }
    if (KOTLIN_ABI_VALIDATION_TASK_NAME !in tasks.names) return null
    return kotlin.property("getAbiValidation")
}

/**
 * The `apiValidation` extension when the Binary Compatibility Validator plugin validates this
 * project, or null. The plugin validates the project that applies it and the projects below it,
 * with the extension of the applying project, which is the nearest parent project with the
 * extension. It doesn't register the `apiCheck` task for a project of `ignoredProjects`, and
 * `validationDisabled` turns the tasks off.
 *
 * The function reads a parent project only when this project has the task, so a build without the
 * plugin stays compatible with project isolation.
 */
internal fun Project.activeBinaryCompatibilityValidator(): Any? {
    if (BCV_CHECK_TASK_NAME !in tasks.names) return null
    val apiValidation = generateSequence(this) { it.parent }
        .firstNotNullOfOrNull { it.extensions.findByName(BCV_EXTENSION_NAME) }
        ?: return null
    return apiValidation.takeIf { it.property("getValidationDisabled") == false }
}

private fun Project.binaryCompatibilityValidatorFilterRules(): ApiFilterRules? {
    val apiValidation = activeBinaryCompatibilityValidator() ?: return null
    return ApiFilterRules(
        excludedNames = apiValidation.stringSet("getIgnoredPackages").mapTo(linkedSetOf(), ::packageRule) +
                apiValidation.stringSet("getIgnoredClasses").mapTo(linkedSetOf(), ::dottedName),
        excludedAnnotations = apiValidation.stringSet("getNonPublicMarkers").mapTo(linkedSetOf(), ::dottedName),
        includedNames = apiValidation.stringSet("getPublicPackages").mapTo(linkedSetOf(), ::packageRule) +
                apiValidation.stringSet("getPublicClasses").mapTo(linkedSetOf(), ::dottedName),
        includedAnnotations = apiValidation.stringSet("getPublicMarkers").mapTo(linkedSetOf(), ::dottedName),
    )
}

/** The plugin matches a package and its subpackages. */
private fun packageRule(packageName: String): String = if (packageName.isBlank()) packageName else "$packageName.**"

/** The plugin separates a nested class with `$`. */
private fun dottedName(binaryName: String): String = binaryName.replace('$', '.')

private fun Any.property(getterName: String): Any? = runCatching {
    javaClass.getMethod(getterName).invoke(this)
}.getOrNull()

private fun Any?.stringSet(getterName: String): Set<String> {
    val value = this?.property(getterName)
    val elements = if (value is Provider<*>) value.orNull else value
    return (elements as? Iterable<*>)?.filterIsInstanceTo(linkedSetOf<String>()).orEmpty()
}

private const val KOTLIN_ABI_VALIDATION_TASK_NAME = "checkKotlinAbi"
private const val BCV_EXTENSION_NAME = "apiValidation"
private const val BCV_CHECK_TASK_NAME = "apiCheck"
