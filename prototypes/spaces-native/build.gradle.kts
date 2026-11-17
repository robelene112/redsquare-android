plugins {
    id("com.android.application") version "8.10.1"
    id("com.android.library") version "8.10.1" apply false
    kotlin("android") version "2.1.20"
    kotlin("plugin.compose") version "2.1.20"
}
val sharedTheme by tasks.registering(Copy::class) {
    from("../../app/src/main/kotlin/nl/davidengelen/redsquare/ui/RedSquareTheme.kt")
    into(layout.buildDirectory.dir("generated/shared-theme"))
}
android {
    namespace = "nl.davidengelen.redsquare"
    compileSdk = 36
    defaultConfig {
        applicationId = "nl.davidengelen.redsquare.spaces.prototype"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "Spaces prototype 1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    sourceSets.getByName("main") {
        java.srcDir(layout.buildDirectory.dir("generated/shared-theme"))
        res.srcDir("../../app/src/main/res")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes.getByName("release") { isMinifyEnabled = false }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { dependsOn(sharedTheme) }
tasks.matching { it.name.startsWith("lint") }.configureEach { dependsOn(sharedTheme) }
dependencies {
    implementation(project(":rich-editor-compat"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
