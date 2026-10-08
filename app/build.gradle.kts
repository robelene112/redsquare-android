plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}
android {
    namespace = "nl.davidengelen.redsquare"
    compileSdk = 36
    defaultConfig {
        applicationId = "nl.davidengelen.redsquare"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    packaging.resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("com.google.code.gson:gson:2.12.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
