// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// The `actual` gets the report when only it is open: `open` on a final `expect`, a public or protected
// constructor of the `actual` only, or a typealias to an open class of a dependency. When the `expect`
// gets the report, the `actual` reports only the constructors that it adds.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

@RequiresOptIn
public annotation class Marker

public expect abstract class ImplicitConstructor

public expect abstract class InternalConstructor

public expect class FinalToOpen

public expect abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>ReportedOnExpect<!>()

public expect abstract class ProtectedConstructors <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>protected constructor()<!>

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public expect abstract class ExemptOnExpect

@SubclassOptInRequired(Marker::class)
public expect abstract class OptInOnExpectOnly()

@SubclassOptInRequired(Marker::class)
public expect abstract class OptInOnBoth()

public expect open class AliasToThread

public expect abstract class AliasToInputStream

public expect class AliasToStringBuilder

public expect interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>AliasToRunnable<!>

@SubclassOptInRequired(Marker::class)
public expect interface OptInAliasToRunnable

public expect open class ExemptAlias

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public expect open class ExemptAliasOnExpect

public expect open class AliasToOwnClass

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.ExemptionReason
import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

public actual abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>ImplicitConstructor<!>

public actual abstract class InternalConstructor internal constructor()

public actual open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>FinalToOpen<!>

public actual abstract class ReportedOnExpect actual constructor()

public actual abstract class ProtectedConstructors protected actual constructor() {
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>protected constructor(value: Int) : this()<!>
}

public actual abstract class ExemptOnExpect

public actual abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>OptInOnExpectOnly<!> actual constructor()

@SubclassOptInRequired(Marker::class)
public actual abstract class OptInOnBoth actual constructor()

public actual typealias <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>AliasToThread<!> = java.lang.Thread

public actual typealias <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>AliasToInputStream<!> = java.io.InputStream

public actual typealias AliasToStringBuilder = java.lang.StringBuilder

public actual typealias AliasToRunnable = java.lang.Runnable

public actual typealias <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>OptInAliasToRunnable<!> = java.lang.Runnable

@IntentionallyOpen(reason = ExemptionReason.API_DESIGN)
public actual typealias ExemptAlias = java.lang.Thread

public actual typealias ExemptAliasOnExpect = java.lang.Thread

public actual typealias AliasToOwnClass = OwnOpenClass

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>OwnOpenClass<!>
