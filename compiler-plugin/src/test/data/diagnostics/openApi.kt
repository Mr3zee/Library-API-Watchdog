// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

@RequiresOptIn
public annotation class ExperimentalFooApi

// Unprotected subclassable API: should warn.

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedOpenClass<!>

public abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedAbstractClass<!>

public interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedInterface<!>

public fun interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedFunInterface<!> {
    public fun run()
}

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>PublicOpenOuter<!> {
    protected open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>ProtectedNestedOpenClass<!>
}

// Subclassing is gated with @SubclassOptInRequired: no warning.

@SubclassOptInRequired(ExperimentalFooApi::class)
public open class OptInProtectedClass

@SubclassOptInRequired(ExperimentalFooApi::class)
public interface OptInProtectedInterface

// @SubclassOptInRequired without marker classes gates nothing: should warn.

<!SUBCLASS_OPT_IN_WITHOUT_MARKERS!>@SubclassOptInRequired<!>
public open class OptInWithoutMarkersClass

<!SUBCLASS_OPT_IN_WITHOUT_MARKERS!>@SubclassOptInRequired()<!>
public interface OptInWithoutMarkersInterface

// Constructors are not visible outside the library, so subclassing is impossible: no warning.

public open class OpenClassWithInternalConstructor internal constructor()

public abstract class AbstractClassWithPrivateConstructor private constructor()

public open class OpenClassWithHiddenConstructors internal constructor() {
    private constructor(x: Int) : this()
}

// A public or protected constructor keeps the class subclassable: should warn. Unless the class
// has a public primary constructor, the accessible constructors are the declarations that open
// the class, so the diagnostic is anchored on them.

public open class OpenClassWithProtectedConstructor <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>protected constructor()<!>

public abstract class AbstractClassWithVisibleSecondaryConstructor private constructor() {
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>public constructor(x: Int) : this()<!>
}

// Every accessible secondary constructor is reported, while inaccessible siblings are ignored.
public abstract class MixedVisibilityConstructors private constructor() {
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>protected constructor(flag: Boolean) : this()<!>
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>public constructor(value: Int) : this()<!>
    private constructor(value: String) : this()
}

// Deliberately open: no warning.

@IntentionallyOpen
public open class DeliberatelyOpenClass

@IntentionallyOpen
public interface DeliberatelyOpenInterface

// Not subclassable: no warning.

public class FinalClass

public object SomeObject

public annotation class SomeAnnotationClass

// Not visible outside the library: no warning.

internal open class InternalOpenClass

private open class PrivateOpenClass

internal interface InternalInterface

internal class InternalContainer {
    open class NestedInsideInternal
}
