// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// EXCLUDED_NAMES: test.internal.** test.api.Hidden test.api.Container.Nested test.api.Container.hiddenMember test.api.Container.hiddenProperty test.api.hiddenFunction test.api.hiddenProperty test.api.HiddenAlias test.api.*Impl test.api.Shape? test.api.Canvas.paint test.api.Factory.Companion
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// FILE: internal.kt
// `test.internal.**` excludes every declaration of the package and of its subpackages.
package test.internal

public open class InternalByPackage {
    public fun member(flag: Boolean) {}

    public class Nested
}

public fun internalFunction(flag: Boolean) {}

public val internalProperty: MutableList<Int> = mutableListOf()

public typealias InternalCallback = () -> Unit

public typealias InternalName = String

// FILE: deeper.kt
package test.internal.deeper

public open class InDeeperPackage

// FILE: similar.kt
// `test.internal.**` doesn't match the package `test.internals`.
package test.internals

public class <!UNDOCUMENTED_PUBLIC_API!>InSimilarPackage<!>

// FILE: api.kt
package test.api

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility
import test.internal.InternalByPackage
import test.internal.InternalName

// A rule without a wildcard matches the whole name only.
public open class Hidden {
    public fun member(flag: Boolean) {}
}

public class <!UNDOCUMENTED_PUBLIC_API!>HiddenNot<!>

/** A rule on a nested class or on a member doesn't exclude the enclosing class. */
public class Container {
    public open class Nested {
        public fun deep(flag: Boolean) {}
    }

    public class <!UNDOCUMENTED_PUBLIC_API!>WatchedNested<!>

    public fun hiddenMember(flag: Boolean) {}

    public fun <!UNDOCUMENTED_PUBLIC_API!>watchedMember<!>() {}

    public val hiddenProperty: MutableList<Int> = mutableListOf()

    /** Watched. */
    public val watchedProperty: <!MUTABLE_COLLECTION_PUBLIC_API!>MutableList<Int><!> = mutableListOf()
}

// A rule on a function name excludes every overload.
public fun hiddenFunction() {}

public fun hiddenFunction(flag: Boolean) {}

// The file facade check reports the first watched function of the file.
public fun <!TOP_LEVEL_API_WITHOUT_JVM_NAME, UNDOCUMENTED_PUBLIC_API!>hiddenFunctionNot<!>() {}

public val hiddenProperty: MutableList<Int> = mutableListOf()

public typealias HiddenAlias = () -> Unit

// `*` doesn't match a `.`, so `test.api.*Impl` doesn't match a nested class.
public open class DefaultImpl

/** Documented. */
public class ImplHolder {
    public class <!UNDOCUMENTED_PUBLIC_API!>NestedImpl<!>
}

// `?` matches one character.
public open class Shape1

public class <!UNDOCUMENTED_PUBLIC_API!>Shape<!>

public class <!UNDOCUMENTED_PUBLIC_API!>Shape12<!>

/** A watched declaration that exposes an excluded type is reported. */
public fun leakedReturn(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalByPackage<!> = InternalByPackage()

/** A rule on an enclosing class covers the nested class. */
public fun leakedNested(value: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalByPackage.Nested<!>) {}

/** An excluded type alias. */
public fun leakedAlias(name: <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalName<!>) {}

/** An excluded type argument. */
public fun leakedArgument(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>List<Hidden><!> = emptyList()

/** An excluded supertype. */
public class LeakedSupertype : <!PUBLIC_TYPE_WITH_INTERNAL_API!>InternalByPackage<!>()

/** The exemption applies to an excluded type too. */
@IntentionallyExposedInternalApiForBackwardsCompatibility
public fun exemptedLeak(): InternalByPackage = InternalByPackage()

/** An excluded declaration can use an excluded type. */
public class Shape2(public val value: InternalByPackage)

/** The excluded member of the receiver class isn't an overload sibling of the extension. */
public class Canvas {
    public fun paint(x: Int, y: Int) {}

    /** Watched. */
    public fun stroke(x: Int, y: Int) {}
}

/** Its sibling is excluded. */
public fun Canvas.paint(y: Int, x: Int, scale: Double) {}

/** Its sibling is watched. */
public fun Canvas.<!INCONSISTENT_PARAMETER_ORDER_IN_OVERLOADS!>stroke<!>(y: Int, x: Int, scale: Double) {}

/** A rule on the companion object covers its members. */
public class Factory {
    public companion object {
        public fun create(): Factory = Factory()
    }
}

/** A watched companion object. */
public class WatchedFactory {
    /** Documented. */
    public companion object {
        /** Documented. */
        public fun <!COMPANION_API_WITHOUT_JVM_STATIC!>create<!>(): WatchedFactory = WatchedFactory()
    }
}
