import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Optional local signing: create keystore.properties (gitignored) to sign release builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "de.gebetszeiten"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.gebetszeiten"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "0.2.0"
    }

    flavorDimensions += "connectivity"
    productFlavors {
        // Puristisch netzfreie Variante (kein INTERNET) — aus dem Quellcode
        // baubar, nicht mehr die veroeffentlichte App.
        create("offline") {
            dimension = "connectivity"
            isDefault = true
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
        }
        // Veroeffentlichte App (de.gebetszeiten): amtliche Diyanet-Zeiten
        // on demand, DE-Bundle + Berechnung als Offline-Fallback.
        create("online") {
            dimension = "connectivity"
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    sourceSets {
        getByName("main") {
            // Amtliche Diyanet-Tabellen werden mit dem wear-Modul geteilt.
            assets.srcDir(rootProject.file("shared-assets"))
        }
    }
}

// Sieben Unit-Tests oeffnen zur Laufzeit Dateien direkt im Arbeitsbaum, statt
// sie ueber den Klassenpfad zu beziehen:
//
//   CountdownIconAssetsTest, CountdownGlyphShapeTest  -> die Drawables und
//       tools/notification-icons/icons.sha256
//   OfficialAssetsIntegrityTest                       -> shared-assets/official
//   NoNetworkInSharedCodeTest                         -> die fuenf Manifeste
//       (inkl. net-diyanet), den geteilten Quellsatz und die beiden
//       build.gradle.kts (Modulkante net-diyanet)
//   OngoingWiringTest, PrayerAlarmSchedulerWiringTest,
//   CalculationFallbackMigrationWiringTest             -> app/src/main/kotlin
//
// Diese Pfade sind fuer Gradle keine Task-Eingaben: die Kotlin-Quellen von
// app/src/main und core-prayertimes sind es mittelbar ueber die Uebersetzung,
// aber die Drawables, die Assets, die Manifeste, wear/ (haengt an keiner
// Uebersetzung dieses Moduls) und app/src/offline (nur im offline-Flavor
// uebersetzt) nicht.
//
// Bei app/src/main/kotlin reicht das Mittelbare NICHT: ueber die Uebersetzung
// haengt der Test nur an Aenderungen, die den Bytecode veraendern. Genau die
// Aenderungen, gegen die `OngoingWiringTest` verteidigt, koennen aber
// bytecode-gleich sein — eine Umformatierung der Aufrufliste etwa. Also steht
// der Quellsatz hier ausdruecklich.
//
// Ohne Deklaration gilt der Test genau dann als UP-TO-DATE, wenn eingetreten
// ist, wogegen er verteidigt: jemand hat eine dieser Dateien von Hand
// nachgebessert. Er biss erst mit `--rerun-tasks`, und das steht in keiner
// Definition of Done durchgaengig. Also stehen die gelesenen Pfade hier.
tasks.withType<Test>().configureEach {
    inputs.dir(file("src/main/res/drawable"))
        .withPropertyName("countdownDrawables")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // DisplayStepBoundariesTest rechnet die Kostenangabe in
    // `settings_remaining_cost` nach; ohne diese Zeile bliebe er gruen,
    // wenn jemand nur den Text aendert.
    inputs.file(file("src/main/res/values/strings.xml"))
        .withPropertyName("deutscheTexte")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("tools/notification-icons/icons.sha256"))
        .withPropertyName("countdownIconManifest")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("shared-assets/official"))
        .withPropertyName("officialAssets")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.files(
        file("src/main/AndroidManifest.xml"),
        file("src/offline/AndroidManifest.xml"),
        file("src/online/AndroidManifest.xml"),
        rootProject.file("wear/src/main/AndroidManifest.xml"),
        rootProject.file("net-diyanet/src/main/AndroidManifest.xml"),
    )
        .withPropertyName("flavorManifeste")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(file("src/offline"))
        .withPropertyName("offlineQuellsatz")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(file("src/online"))
        .withPropertyName("onlineQuellsatz")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(file("src/main/kotlin"))
        .withPropertyName("mainQuellsatz")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("wear/src/main"))
        .withPropertyName("wearQuellsatz")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // NoNetworkInSharedCodeTest prueft seit Aufgabe 3 zusaetzlich, dass
    // :net-diyanet nur als onlineImplementation eingebunden ist.
    inputs.files(
        rootProject.file("app/build.gradle.kts"),
        rootProject.file("wear/build.gradle.kts"),
    )
        .withPropertyName("modulkanten")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core-prayertimes"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.androidx.datastore.preferences)

    // Wear-Sync: nur der online-Flavor pusht den amtlichen Cache zur Uhr —
    // der offline-Flavor bleibt gms-frei.
    "onlineImplementation"(libs.play.services.wearable)

    // Die Diyanet-Abrufer samt INTERNET-Berechtigung: nur der online-Flavor
    // bindet sie ein, der offline-Flavor sieht das Modul gar nicht.
    "onlineImplementation"(project(":net-diyanet"))

    testImplementation(libs.junit)
    // Echte org.json-Implementierung für JVM-Tests (im mockable android.jar
    // sind die JSON-Klassen nur Stubs) — genutzt vom Geocoder-Parse-Test.
    testImplementation("org.json:json:20240303")
}
