plugins {
    id("com.android.application")
    // Kotlin itself comes from AGP 9; only the Compose compiler plugin is applied separately
    id("org.jetbrains.kotlin.plugin.compose")
    // renders the real Compose screens to PNGs in a plain JVM test, so the UI can be looked at
    // without a device - there is no Android emulator available in this project's CI
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "net.bison"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.bison"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            // signed with the debug key: this is a sideloaded build, not a store release
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true

            // The only way anybody sees a failure here is the CI log, and by default that log
            // says "AssertionError at SomeTest.kt:78" and nothing else - so the message the test
            // went to the trouble of writing is thrown away exactly when it is wanted.
            all {
                it.testLogging {
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    events("failed")
                    showStandardStreams = true
                }
            }
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.64.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.64.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // On a real device, for the handful of things only a real device decides. These tests drive
    // the app from outside it - real touch events at real times - because the questions they
    // answer are about the input system and the clock, and a test that supplies its own of
    // either would be asking itself.
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
}
