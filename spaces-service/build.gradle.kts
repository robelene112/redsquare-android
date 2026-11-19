plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.12.1")
    // Server-only durable ordering, official stable release3.54.1.0.
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
sourceSets.test { java.srcDir(rootProject.file("test-fixtures/kotlin")) }
application { mainClass.set("nl.davidengelen.redsquare.spaces.service.SpacesServiceMain") }
tasks.test {
    dependsOn(":core:prepareSpaceMlsHost")
    val native = project(":core").layout.buildDirectory.dir("generated/spaces-mls-host")
    inputs.dir(native).withPropertyName("spacesMlsHost")
    systemProperty("java.library.path", native.get().asFile.absolutePath)
    systemProperty("spaces.service.runtimeClasspath", sourceSets.main.get().runtimeClasspath.asPath)
    systemProperty("spaces.native.fixtureClasspath", sourceSets.test.get().runtimeClasspath.asPath)
}

// Only isolated rehearsal/host fixtures use this classpath. No fixture classes
// or dependencies enter the service distribution or Android application.
tasks.register("writeHostFixtureClasspath") {
    dependsOn(tasks.named("testClasses"), ":core:prepareSpaceMlsHost")
    val target = layout.buildDirectory.file("host-fixture-classpath.txt")
    doLast { target.get().asFile.writeText(sourceSets.test.get().runtimeClasspath.asPath) }
}
