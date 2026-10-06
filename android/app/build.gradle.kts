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
    compileSdk {
        version = release(37)
    }

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
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
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
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}