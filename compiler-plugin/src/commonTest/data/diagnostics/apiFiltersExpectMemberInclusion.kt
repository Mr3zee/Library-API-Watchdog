// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// INCLUDED_ANNOTATIONS: test.PublicApi
// INCLUDED_NAMES: test.WorkerByName.start test.PlatformMember.platformOnly
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME -ACTUAL_ANNOTATIONS_NOT_MATCH_EXPECT

// An inclusion rule on a member of an `expect` class includes the class through the member. The
// `actual` class and an `actual typealias` get the verdict of the `expect` class. A rule on a
// member that only the `actual` class declares includes only the `actual` class.

// MODULE: common
// FILE: common.kt

package test

/** Includes a declaration. */
public annotation class PublicApi

public expect open class <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN, UNDOCUMENTED_PUBLIC_API!>MemberOnly<!>() {
    @PublicApi
    public fun <!UNDOCUMENTED_PUBLIC_API!>included<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false)

    public fun other(flag: Boolean = false)
}

/** The `actual typealias` expands to an open JDK class. */
public expect class Worker() {
    @PublicApi
    public fun <!UNDOCUMENTED_PUBLIC_API!>start<!>()
}

/** The same with a name rule. */
public expect class WorkerByName() {
    public fun <!UNDOCUMENTED_PUBLIC_API!>start<!>()
}

/** Not included. */
public expect class NotIncluded() {
    public fun start()
}

public expect open class PlatformMember()

public expect class <!UNDOCUMENTED_PUBLIC_API!>Handle<!>() {
    @PublicApi
    public fun <!UNDOCUMENTED_PUBLIC_API!>close<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean)

    public fun other(flag: Boolean)
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package test

public actual open class MemberOnly {
    public actual fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS!>included<!>(flag: Boolean) {}

    public actual fun other(flag: Boolean) {}
}

// The `expect` class is outside the filters, so `OPEN_API_WITHOUT_SUBCLASS_OPT_IN` doesn't report the pair.
public actual open class PlatformMember {
    public fun <!DEFAULT_PARAMETERS_WITHOUT_JVM_OVERLOADS, UNDOCUMENTED_PUBLIC_API!>platformOnly<!>(<!BOOLEAN_PARAMETER_PUBLIC_API!>flag<!>: Boolean = false) {}
}

public actual typealias <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>Worker<!> = java.lang.Thread

public actual typealias <!OPEN_API_WITHOUT_SUBCLASS_OPT_IN!>WorkerByName<!> = java.lang.Thread

public actual typealias NotIncluded = java.lang.Thread

// The class of the typealias has its own name, so the rules of the `expect` class don't include it.
public actual typealias Handle = HandleImpl

public class HandleImpl {
    public fun close(flag: Boolean) {}

    public fun other(flag: Boolean) {}
}
