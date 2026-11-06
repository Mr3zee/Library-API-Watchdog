// RUN_PIPELINE_TILL: FRONTEND
// LANGUAGE: +MultiPlatformProjects
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -UNDOCUMENTED_PUBLIC_API -TOP_LEVEL_API_WITHOUT_JVM_NAME -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING

// The check reports the `expect` declaration and reads the exemption there. The `actual` declaration
// carries a copy, which the compiler requires.

// MODULE: common
// FILE: common.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility
import org.jetbrains.kotlinx.library.api.watchdog.InternalAnnotationMarker

@InternalAnnotationMarker
@Target(AnnotationTarget.CLASS)
public annotation class LibInternal

@LibInternal
public class Hidden

public expect fun leak(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>Hidden<!>

@IntentionallyExposedInternalApiForBackwardsCompatibility
public expect fun exempted(): Hidden

<!REDUNDANT_EXEMPTION, REDUNDANT_EXEMPTION{METADATA}!>@IntentionallyExposedInternalApiForBackwardsCompatibility<!>
public expect fun plain(): Int

public expect class Holder() {
    @IntentionallyExposedInternalApiForBackwardsCompatibility
    public fun exemptedMember(): Hidden

    public fun leakedMember(): <!PUBLIC_TYPE_WITH_INTERNAL_API!>Hidden<!>
}

// MODULE: jvm()()(common)
// FILE: jvm.kt

package foo.bar

import org.jetbrains.kotlinx.library.api.watchdog.IntentionallyExposedInternalApiForBackwardsCompatibility

public actual fun leak(): Hidden = Hidden()

@IntentionallyExposedInternalApiForBackwardsCompatibility
public actual fun exempted(): Hidden = Hidden()

@IntentionallyExposedInternalApiForBackwardsCompatibility
public actual fun plain(): Int = 0

public actual class Holder {
    @IntentionallyExposedInternalApiForBackwardsCompatibility
    public actual fun exemptedMember(): Hidden = Hidden()

    public actual fun leakedMember(): Hidden = Hidden()
}
