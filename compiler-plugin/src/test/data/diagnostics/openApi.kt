// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -EXEMPTION_WITHOUT_EXPLANATION

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyOpen

@RequiresOptIn
public annotation class ExperimentalFooApi

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedOpenClass<!>

public abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedAbstractClass<!>

public interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedInterface<!>

public fun interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>UnprotectedFunInterface<!> {
    public fun run()
}

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>PublicOpenOuter<!> {
    protected open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>ProtectedNestedOpenClass<!>
}

@SubclassOptInRequired(ExperimentalFooApi::class)
public open class OptInProtectedClass

@SubclassOptInRequired(ExperimentalFooApi::class)
public interface OptInProtectedInterface

<!SUBCLASS_OPT_IN_WITHOUT_MARKERS!>@SubclassOptInRequired<!>
public open class OptInWithoutMarkersClass

<!SUBCLASS_OPT_IN_WITHOUT_MARKERS!>@SubclassOptInRequired()<!>
public interface OptInWithoutMarkersInterface

public open class OpenClassWithInternalConstructor internal constructor()

public abstract class AbstractClassWithPrivateConstructor private constructor()

public open class OpenClassWithHiddenConstructors internal constructor() {
    private constructor(x: Int) : this()
}

// Without a public primary constructor, the diagnostic goes on each accessible constructor.

public open class OpenClassWithProtectedConstructor <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>protected constructor()<!>

public abstract class AbstractClassWithVisibleSecondaryConstructor private constructor() {
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>public constructor(x: Int) : this()<!>
}

public abstract class MixedVisibilityConstructors private constructor() {
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>protected constructor(flag: Boolean) : this()<!>
    <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>public constructor(value: Int) : this()<!>
    private constructor(value: String) : this()
}

@IntentionallyOpen
public open class DeliberatelyOpenClass

@IntentionallyOpen
public interface DeliberatelyOpenInterface

public class FinalClass

public object SomeObject

public annotation class SomeAnnotationClass

internal open class InternalOpenClass

private open class PrivateOpenClass

internal interface InternalInterface

internal class InternalContainer {
    open class NestedInsideInternal
}
