import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    api(libs.adhan)

    testImplementation(libs.junit)
}

// Target Java 17 bytecode so the Android app module (D8/R8) can consume this
// pure-Kotlin library. Compiled with the running JDK (21), no toolchain
// auto-provisioning required.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
