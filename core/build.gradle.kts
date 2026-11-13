plugins { kotlin("jvm") }
kotlin {
    jvmToolchain(17)
    sourceSets.test { kotlin.srcDir(rootProject.file("test-fixtures/kotlin")) }
}
dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("com.google.code.gson:gson:2.12.1")
    // The Schubfach formatter preserves Rust-compatible shortest float digits on
    // Android and JVM17; their platform Double.toString implementations differ.
    implementation("com.fasterxml.jackson.core:jackson-core:2.23.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.4.0")
}

// Host fixture only. No peer fixture or test dependency enters core.main/the APK.
tasks.register("writeHostPeerClasspath") {
    dependsOn(tasks.named("testClasses"))
    val target = layout.buildDirectory.file("host-peer-classpath.txt")
    doLast { target.get().asFile.writeText(sourceSets.test.get().runtimeClasspath.asPath) }
}

// Exercise the actual Android transport at the existing binary peer seam on JVM.
// Only Android logging/build flags are shimmed; wire, signing and ownership are real.
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileTestKotlin") {
    source(listOf("SaitoPeer.kt", "SaitoLocalState.kt", "RealtimeIncoming.kt").map {
        rootProject.file("app/src/main/kotlin/nl/davidengelen/redsquare/saito/$it")
    })
}
