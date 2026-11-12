import java.net.URI
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
}

// Public Android-app options, supplied independently for each application.
fun com.android.build.api.dsl.ApplicationBuildType.configurePush(variant: String, packageName: String) {
    val fields = linkedMapOf(
        "PACKAGE" to "Package", "APP_ID" to "FirebaseAppId", "API_KEY" to "FirebaseApiKey",
        "PROJECT_ID" to "FirebaseProjectId", "SENDER_ID" to "FirebaseSenderId", "RESOLVER" to "ResolverUrl")
    val values = fields.mapValues { (_, suffix) -> providers.gradleProperty("push${variant}$suffix").orElse("").get() }
    val configured = values.values.any { it.isNotEmpty() }
    if (configured) {
        require(values.values.all { it.isNotBlank() && it.length <= 512 && it.none { c -> c.isWhitespace() || c.code < 32 } }) {
            "Push configuration requires every option for $variant"
        }
        require(values.getValue("PACKAGE") == packageName) { "Push Android package does not match $variant" }
        val sender = values.getValue("SENDER_ID")
        require(sender.matches(Regex("[1-9][0-9]*")) &&
            values.getValue("APP_ID").matches(Regex("1:$sender:android:[0-9a-f]+"))) { "Invalid Firebase Android app/sender association" }
        val resolver = URI(values.getValue("RESOLVER"))
        require(resolver.scheme == "https" && !resolver.host.isNullOrBlank() &&
            resolver.rawUserInfo == null && resolver.rawQuery == null && resolver.rawFragment == null &&
            resolver.path == "/v1/wakes/resolve") { "Push resolver requires its HTTPS resolve URL" }
    }
    buildConfigField("boolean", "PUSH_CONFIGURED", configured.toString())
    values.forEach { (field, value) ->
        val literal = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "PUSH_$field", literal)
    }
}
fun com.android.build.api.dsl.ApplicationBuildType.configureGiphy(variant: String) {
    for (section in listOf("Posts", "Chat")) {
        val key = providers.gradleProperty("giphy${variant}${section}ApiKey").orElse("").get()
        require(key.length <= 256 && key.none { it.isWhitespace() || it.code < 32 }) { "Invalid Giphy Android/$section API option" }
        val literal = "\"" + key.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        buildConfigField("String", "GIPHY_${section.uppercase()}_API_KEY", literal)
    }
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
            configurePush("Debug", "nl.davidengelen.redsquare.dev")
            configureGiphy("Debug")
        }
        release {
            configurePush("Release", "nl.davidengelen.redsquare")
            configureGiphy("Release")
        }
        create("performance") {
            initWith(getByName("release"))
            applicationIdSuffix = ".performance"
            matchingFallbacks += listOf("release")
            isDebuggable = false
            isProfileable = true
            signingConfig = null
            configurePush("Performance", "nl.davidengelen.redsquare.performance")
            configureGiphy("Performance")
            buildConfigField("boolean", "REVIEW_BUILD", "false")
        }
    }
    sourceSets.getByName("androidTest").java.srcDir(rootProject.file("test-fixtures/kotlin"))
    packaging.resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// Compatibility specimens enter the instrumentation APK only, never the application.
abstract class PrepareFileshareReference : DefaultTask() {
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
}
val fileshareCompiler by configurations.creating
val prepareFileshareReference by tasks.registering(PrepareFileshareReference::class) {
    val pins = mapOf(
        "9a4ca7e9442eb5e872168ced361ce741d7d139d7" to "082cd20fd07c76b4cbd43e42ff87d9626bd9631410aa676a5a3b667fc2776406",
        "e6c937c686495a37d8fc94ecc894189e23a453c3" to "1de51975798726b58c00a043cc95cdca9e52c4b85def7d08da5644ee719b238c")
    inputs.property("pins", pins)
    inputs.property("compiler", "v20240317-ES2018") // Java17-compatible test tool; API29's stock WebView predates optional chaining.
    doLast {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        pins.forEach { (pin, hash) ->
            val bytes = URI("https://raw.githubusercontent.com/SaitoTech/saito/$pin/node/mods/fileshare/fileshare.js")
                .toURL().openConnection().apply { connectTimeout = 15_000; readTimeout = 15_000 }.getInputStream().use { it.readBytes() }
            check(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == hash)
            val original = directory.resolve("fileshare-$pin.raw.js").apply { writeBytes(bytes) }
            javaexec {
                classpath = fileshareCompiler
                mainClass.set("com.google.javascript.jscomp.CommandLineRunner")
                args("--compilation_level", "WHITESPACE_ONLY", "--language_in", "ECMASCRIPT_NEXT", "--language_out", "ECMASCRIPT_2018",
                    "--formatting", "PRETTY_PRINT", "--rewrite_polyfills", "false", "--js", original.absolutePath,
                    "--js_output_file", directory.resolve("fileshare-$pin.js").absolutePath)
            }.assertNormalExitValue()
        }
    }
}
// Preserve producer dependencies for every asset consumer, including both lint tasks.
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(prepareFileshareReference) { it.outputDirectory }
}

dependencies {
    add(fileshareCompiler.name, "com.google.javascript:closure-compiler:v20240317")
    implementation(project(":core"))
    implementation("io.github.webrtc-sdk:android:150.7871.04")
    // Opt-in diagnostics only; ordinary performance builds retain their release-equivalent inputs.
    if (providers.gradleProperty("scrollCompositionTracing").orNull == "true") {
        add("performanceImplementation", "androidx.compose.runtime:runtime-tracing")
        add("performanceImplementation", "androidx.tracing:tracing-perfetto:1.0.1")
        add("performanceImplementation", "androidx.tracing:tracing-perfetto-binary:1.0.1")
    }
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver3:5.4.0")
    implementation("com.google.code.gson:gson:2.12.1")
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.google.firebase:firebase-messaging:26.0.0")
    constraints {
        implementation("androidx.fragment:fragment:1.8.9") {
            because("Play services brings Fragment1.1, incompatible with ActivityResult wallet documents")
        }
    }
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("com.google.code.gson:gson:2.12.1")
    implementation("com.google.zxing:core:3.5.4")
    implementation(project(":rich-editor-compat"))
    androidTestImplementation("org.bouncycastle:bcprov-jdk18on:1.86")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
