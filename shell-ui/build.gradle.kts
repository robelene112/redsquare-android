plugins { id("com.android.library"); kotlin("android"); kotlin("plugin.compose") }
android {
    namespace = "nl.davidengelen.redsquare.shell"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc13")
}
