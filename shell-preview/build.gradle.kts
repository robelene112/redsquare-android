plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.compose") }
android {
    namespace = "nl.davidengelen.redsquare.design"
    compileSdk = 36
    defaultConfig {
        applicationId = "nl.davidengelen.redsquare.design"
        minSdk = 29; targetSdk = 36
        versionCode = providers.gradleProperty("designVersion").orElse("1").get().toInt()
        versionName = "design.$versionCode"
    }
    buildFeatures { compose = true; buildConfig = true }
    buildTypes { debug { applicationIdSuffix = ".dev" } }
    sourceSets.getByName("main").assets.srcDir(rootProject.file("prototype/native-shell"))
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":shell-ui"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
}
