plugins { kotlin("jvm") }
kotlin {
    jvmToolchain(17)
    sourceSets.test { kotlin.srcDir(rootProject.file("test-fixtures/kotlin")) }
}
dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")
    implementation("com.google.code.gson:gson:2.12.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.4.0")
}

// Host fixture only. No peer fixture or test dependency enters core.main/the APK.
tasks.register("writeHostPeerClasspath") {
    dependsOn(tasks.named("testClasses"))
    val target = layout.buildDirectory.file("host-peer-classpath.txt")
    doLast { target.get().asFile.writeText(sourceSets.test.get().runtimeClasspath.asPath) }
}
