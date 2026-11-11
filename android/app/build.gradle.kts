import java.util.Properties
// This lets us read settings from the local key file.plugins {
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val mapsSecrets = Properties()
val mapsSecretsFile = rootProject.file("secrets.properties")
// This locates the key file in the Android project's root folder.

if (mapsSecretsFile.exists()) {
    mapsSecretsFile.inputStream().use { stream ->
        mapsSecrets.load(stream)
    }
}
// This reads the file and closes it afterward.

android {
    namespace = "io.github.cataylor5.ibs"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.cataylor5.ibs"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] =
            mapsSecrets.getProperty("MAPS_API_KEY", "")
// This supplies the key to the Android manifest during the build.
    }

    buildTypes {
        debug {
            buildConfigField("String", "RESTROOM_API_BASE_URL", '"' + "http://10.0.2.2:3000/" + '"')
        }
        release {
            buildConfigField("String", "RESTROOM_API_BASE_URL", '"'.toString() + '"')
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

// Navigation bundles the Maps SDK; exclude the standalone copy from every dependency.
configurations.configureEach {
    exclude(group = "com.google.android.gms", module = "play-services-maps")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation("com.google.android.libraries.navigation:navigation:8.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation(libs.maps.compose)
// This adds Google's map components for Compose.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.6.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.play.services.location)
// This adds Google's device-location service.
    implementation(libs.places)
    implementation("com.google.android.material:material:1.12.0")
// Supplies the Material View theme attributes required by the Places search widget.
// This adds Google's Places SDK for destination search.
    implementation("androidx.media3:media3-exoplayer:1.11.1")
// Plays the video stored inside the app.

    implementation("androidx.media3:media3-ui:1.11.1")
// Provides the view that displays the video.

    // Force newer versions to resolve a namespace conflict in older transitive dependencies
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.1.0")
}