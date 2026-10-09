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
        versionCode = providers.gradleProperty("reviewVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("reviewVersionName").orElse("0.1.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "REVIEW_BUILD", providers.gradleProperty("reviewBuild").orElse("false").get().toBooleanStrict().toString())
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
        }
    }
    packaging.resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver3:5.4.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("com.google.code.gson:gson:2.12.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
