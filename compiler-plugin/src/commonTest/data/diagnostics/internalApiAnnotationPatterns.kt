// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// INTERNAL_API_ANNOTATIONS: third.party.**.Internal* third.party.Exact?
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -TOP_LEVEL_API_WITHOUT_JVM_NAME

// FILE: markers.kt
package third.party.markers

/** Matches `third.party.**.Internal*`. */
public annotation class InternalApi

/** Matches `third.party.**.Internal*`. */
public annotation class InternalCoroutinesApi

/** The wildcard `*` doesn't match the `.` of a nested class, so the rule doesn't match. */
public object Internal {
    /** Doesn't match a rule. */
    public annotation class Marker
}

// FILE: exact.kt
package third.party

/** Matches `third.party.Exact?`. */
public annotation class Exact1

/** `?` matches one character, so the rule doesn't match. */
public annotation class Exact

/** `**` matches the `.` after `party`, but `third.party.**.Internal*` needs one more `.`. */
public annotation class InternalInRootPackage

// FILE: usage.kt
package consumer

import third.party.Exact
import third.party.Exact1
import third.party.InternalInRootPackage
import third.party.markers.Internal
import third.party.markers.InternalApi
import third.party.markers.InternalCoroutinesApi

@InternalApi
public open class MarkedInternal

@InternalCoroutinesApi
public open class MarkedInternalCoroutines

@Exact1
public open class MarkedExact

@Internal.Marker
public class <!UNDOCUMENTED_PUBLIC_API!>MarkedWithNested<!>

@Exact
public class <!UNDOCUMENTED_PUBLIC_API!>MarkedWithShorterName<!>

@InternalInRootPackage
public class <!UNDOCUMENTED_PUBLIC_API!>MarkedInRootPackage<!>

/** A marker from a pattern counts for the exposure check too. */
public fun leaked(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>MarkedInternal<!> = MarkedInternal()
