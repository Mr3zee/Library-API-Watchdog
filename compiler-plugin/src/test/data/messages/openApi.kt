// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API

package foo.bar

public open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>OpenClass<!>

public abstract class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>AbstractClass<!>

public interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>Interface<!>

public fun interface <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>FunInterface<!> {
    public fun run()
}

<!SUBCLASS_OPT_IN_WITHOUT_MARKERS!>@SubclassOptInRequired<!>
public open class MarkerlessClass

<!SUBCLASS_OPT_IN_WITHOUT_MARKERS!>@SubclassOptInRequired()<!>
public interface MarkerlessInterface
