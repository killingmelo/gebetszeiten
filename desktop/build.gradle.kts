plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":core-prayertimes"))
    implementation(project(":net-diyanet-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")

    testImplementation(libs.junit)
}

// Der Desktop laeuft auf einem eigenen JDK 21 (jpackage, Task 10); die
// Bibliotheken darunter bleiben bei Java 17.
kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("de.gebetszeiten.desktop.MainKt")
}

// Dieselben amtlichen Tabellen wie die Handy-App: official/locations-de.tsv,
// official/tables/*.tsv (shared-assets) und official/locations-world.tsv
// (net-diyanet) liegen im Klassenpfad.
sourceSets {
    main {
        resources.srcDir(rootProject.file("shared-assets"))
        resources.srcDir(rootProject.file("net-diyanet/src/main/assets"))
    }
}
