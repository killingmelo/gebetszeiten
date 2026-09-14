plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.gebetszeiten.net"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

// `WorldIndexIntegrityTest` oeffnet `locations-world.tsv` zur Laufzeit direkt
// im Arbeitsbaum statt ueber den Klassenpfad (Praezedenz: die entsprechende
// Deklaration in app/build.gradle.kts fuer CompositeFetcherContractTest &
// Verwandte). Ohne diese Eingabe gilt der Test als UP-TO-DATE, auch wenn sich
// die Datei geaendert hat.
tasks.withType<Test>().configureEach {
    inputs.file(file("src/main/assets/official/locations-world.tsv"))
        .withPropertyName("worldIndexAsset")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core-prayertimes"))
    implementation(libs.androidx.core.ktx)
    // Nicht im Versionskatalog: `androidx.core.ktx` und `:core-prayertimes`
    // bringen kotlinx-coroutines nicht mit (im `app`-Modul kommt es
    // transitiv ueber `androidx.datastore.preferences`, 1.9.0 aufgeloest -
    // siehe `gradlew :app:dependencies`). Ohne diese Zeile fehlen
    // `Dispatchers`/`withContext`/`async` in allen sechs Abrufer-Dateien.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
