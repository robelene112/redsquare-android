plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "com.mohamedrejeb.richeditor.compose"
    compileSdk = 36
    defaultConfig { minSdk = 21; consumerProguardFiles("proguard-rules.pro") }
    buildFeatures { compose = true }
    sourceSets.getByName("main").java.srcDirs("src/commonMain/kotlin", "src/androidMain/kotlin")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3")
    implementation("com.mohamedrejeb.ksoup:ksoup-html:0.5.0")
    implementation("com.mohamedrejeb.ksoup:ksoup-entities:0.5.0")
    implementation("org.jetbrains:markdown:0.7.3")
}
