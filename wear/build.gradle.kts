import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "de.gebetszeiten.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.gebetszeiten"
        minSdk = 30
        // Gleiches Ziel wie die Phone-App. Stand hier bis zum 20.09.2026 auf
        // 34 und wurde von Play abgewiesen: „derzeit auf API-Ebene 34
        // ausgerichtet, sollte jedoch eine API-Mindestebene von 35 haben".
        // Der compileSdk stand da laengst auf 36 — zurueckgeblieben war nur
        // das Ziel, und weil das Wear-Bundle seit Monaten nicht neu
        // hochgeladen wurde, fiel es nie auf.
        //
        // 36 statt der geforderten 35, damit Telefon und Uhr dasselbe Ziel
        // haben: zwei Module desselben Pakets mit verschiedenen Zielen sind
        // genau die Asymmetrie, die diesen Rueckstand ueberhaupt erzeugt hat.
        targetSdk = 36
        // Wear nutzt den 1000er-Block: Phone und Uhr teilen sich das Paket
        // de.gebetszeiten, Play verlangt paketweit eindeutige versionCodes —
        // die Phone-App hat 15..n laengst verbraucht. Phone bleibt < 1000.
        // 1016 ist verbrannt: Play reserviert einen Versionscode, sobald
        // ein Artefakt damit hochgeladen wurde — auch wenn es nie
        // ausgerollt wurde. Das erste 1016-Bundle scheiterte an
        // targetSdk 34 und liegt seitdem unbenutzbar in der
        // Artefakt-Bibliothek.
        //
        // Der Name zieht mit, weil dieses Modul Code und Name gekoppelt
        // fuehrt (1015 = 0.1.15, 1016 = 0.1.16). Zwei Artefakte mit
        // demselben Namen und verschiedenen Codes waeren in der
        // Bibliothek spaeter nicht mehr auseinanderzuhalten.
        versionCode = 1017
        versionName = "0.1.17"
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

    sourceSets {
        getByName("main") {
            // Amtliche Diyanet-Tabellen werden mit dem wear-Modul geteilt.
            assets.srcDir(rootProject.file("shared-assets"))
        }
    }

    flavorDimensions += "connectivity"
    productFlavors {
        create("offline") {
            dimension = "connectivity"
            isDefault = true
            applicationIdSuffix = ".offline"
            versionNameSuffix = "-offline"
        }
        create("online") { dimension = "connectivity" }
    }
}

// CalculationFillsGapsMigrationWiringTest liest WearSettings.kt zur Laufzeit
// von der Platte (quelltextlesender Waechter statt Robolectric). Ohne diese
// Deklaration gilt der Test als UP-TO-DATE, obwohl sich genau die Datei
// geaendert hat, gegen die er verteidigt — Praezedenz `mainQuellsatz` in
// app/build.gradle.kts.
tasks.withType<Test>().configureEach {
    inputs.dir(file("src/main/kotlin"))
        .withPropertyName("mainQuellsatz")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core-prayertimes"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.wear)

    // Tiles
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)

    // Complications
    implementation(libs.androidx.wear.complications.datasource.ktx)

    // ListenableFuture implementation for the TileService (avoids full Guava)
    implementation("androidx.concurrent:concurrent-futures:1.2.0")

    // Persisted location (DataStore)
    implementation(libs.androidx.datastore.preferences)

    // Phone-Sync: amtlicher Zeiten-Cache kommt als DataItem vom Handy. Bleibt
    // in beiden Flavors, anders als am Telefon: es ist der Bluetooth-Transport
    // zum gekoppelten Handy, kein Internetzugang, und WearSyncApplier /
    // WearSyncListenerService liegen in wear/src/main.
    implementation(libs.play.services.wearable)

    "onlineImplementation"(project(":net-diyanet"))

    testImplementation(libs.junit)
}
