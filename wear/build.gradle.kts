plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "de.gebetszeiten.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.gebetszeiten"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
}
