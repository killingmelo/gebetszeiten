plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

dependencies {
    implementation(project(":core-prayertimes"))
    implementation(project(":net-diyanet-core"))
    // net-diyanet-core kennt org.json nur compileOnly (Android bringt es mit);
    // auf der Desktop-JVM muss die Bibliothek mit ins Paket.
    implementation("org.json:json:20240303")
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

// Programmordner mit Gebetszeiten.exe: build/package/Gebetszeiten/ (kein MSI,
// das braeuchte WiX). jpackage kommt aus einer Java-Toolchain mit vollem JDK;
// das JBR von Android Studio hat keins. Auf dem Entwicklungs-PC ist das
// Temurin 25 (Gradle findet es selbst), anderes JDK per -PjpackageJdk=<Version>.
// Die gebundelte Laufzeit erzeugt jpackage mit eben diesem JDK.
val jpackageJdk = providers.gradleProperty("jpackageJdk").orElse("25").map { JavaLanguageVersion.of(it) }
val jpackageLauncher = javaToolchains.launcherFor { languageVersion.set(jpackageJdk) }

tasks.register<Exec>("packageApp") {
    group = "distribution"
    description = "Baut desktop/build/package/Gebetszeiten/Gebetszeiten.exe (jpackage app-image)."
    dependsOn("installDist")
    val libDir = layout.buildDirectory.dir("install/desktop/lib")
    val icon = layout.projectDirectory.file("src/main/resources/icon.ico")
    val dest = layout.buildDirectory.dir("package")
    val launcher = jpackageLauncher
    inputs.dir(libDir)
    inputs.file(icon)
    outputs.dir(dest.map { it.dir("Gebetszeiten") })
    doFirst {
        val jpackage = launcher.get().executablePath.asFile.parentFile.resolve("jpackage.exe")
        check(jpackage.isFile) { "Kein jpackage in ${jpackage.parentFile} (volles JDK noetig, -PjpackageJdk=<Version>)" }
        // jpackage ueberschreibt kein vorhandenes Programmbild.
        dest.get().dir("Gebetszeiten").asFile.deleteRecursively()
        executable = jpackage.absolutePath
        args(
            "--type", "app-image",
            "--name", "Gebetszeiten",
            "--input", libDir.get().asFile.absolutePath,
            "--main-jar", "desktop.jar",
            "--main-class", "de.gebetszeiten.desktop.MainKt",
            "--icon", icon.asFile.absolutePath,
            "--dest", dest.get().asFile.absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8",
        )
    }
}
