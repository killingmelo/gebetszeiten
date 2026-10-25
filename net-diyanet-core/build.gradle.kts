import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(project(":core-prayertimes"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    // Auf Android bringt die Plattform org.json mit, auf der JVM (Desktop,
    // Unit-Tests) nicht — daher die Bibliothek.
    implementation("org.json:json:20240303")

    testImplementation(libs.junit)
}

// Java 17 wie core-prayertimes: das Android-Modul net-diyanet (D8/R8) muss
// den Bytecode lesen koennen. Gebaut mit dem laufenden JDK, keine
// Toolchain-Provisionierung noetig.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
