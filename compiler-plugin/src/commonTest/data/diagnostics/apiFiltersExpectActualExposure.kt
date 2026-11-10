// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.internal.**
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// A class of the common module is a class of the module in the platform module too. So a platform
// declaration that exposes an excluded common class is reported.

// MODULE: common
// FILE: internal.kt

package test.internal

public class CommonModel

public expect class ExpectedModel()

// FILE: common.kt

package test.api

import test.internal.CommonModel
import test.internal.ExpectedModel

/** Exposes an excluded common class in common code. */
public fun loadInCommon(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>CommonModel<!> = CommonModel()

/** Exposes an excluded `expect` class in common code. */
public fun expectedInCommon(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>ExpectedModel<!> = ExpectedModel()

// MODULE: jvm()()(common)
// FILE: actual.kt

package test.internal

public actual class ExpectedModel

// FILE: jvm.kt

package test.api

import test.internal.CommonModel
import test.internal.ExpectedModel

/** Exposes an excluded common class. */
public fun loadOnJvm(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>CommonModel<!> = CommonModel()

/** Exposes the excluded `actual` class. */
public fun expectedOnJvm(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>ExpectedModel<!> = ExpectedModel()
