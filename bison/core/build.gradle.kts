// Everything about Bison that is not a screen: the card file's grammar, the comparison rules,
// the parameter engine, the rotation, the model, and the file the progress lives in.
//
// It is a plain JVM library rather than an Android one, and that is the point. The same rules
// have to hold on the phone and on the desktop - a card that counts as right in the lecture hall
// cannot count as wrong at a desk - and the only way to be sure of that is to have one copy of
// them that neither platform can reach into.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // matches the app module, so the classes coming out of here can be packed into an APK
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // org.json is part of Android and must not be packed into the APK beside it, so it is only
    // compiled against here. On the desktop and under these tests it comes from Maven, which is
    // also why these tests no longer need Robolectric: they used to run under it for org.json
    // alone, because a plain unit test got the mockable android.jar, whose constructors are
    // empty and whose methods throw.
    compileOnly("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
