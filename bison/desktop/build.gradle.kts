// Bison at a desk.
//
// Half the set is typed - syntax, param, trace - and a C answer typed on a phone keyboard costs
// more attention than the answer does. So the same cards, the same rules and the same progress,
// on a machine with a keyboard.
//
// It shares :core with the phone and nothing else. The interface is written twice because the
// two Compose artifacts cannot be mixed in one build; everything that decides whether an answer
// is right is written once, which is the half that matters.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // on Android this comes from the platform; here it has to be packed in
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}

compose.desktop {
    application {
        mainClass = "net.bison.desktop.MainKt"

        nativeDistributions {
            packageName = "Bison"
            packageVersion = "1.0.0"
            description = "Karteikarten fuer Softwarewerkzeuge und Softwaretechnik"
        }
    }
}
