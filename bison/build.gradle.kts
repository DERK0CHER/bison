// Versions pinned deliberately: this build cannot be run locally, so a resolution that is
// that combination is known to resolve and compile in CI here.
//
// AGP 9 brings Kotlin support itself, so `org.jetbrains.kotlin.android` must NOT be applied;
// only the Compose compiler plugin is still a separate plugin, and its version has to match
// the Kotlin version AGP bundles.
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("io.github.takahirom.roborazzi") version "1.64.0" apply false
}
