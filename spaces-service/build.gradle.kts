plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.12.1")
    // Server-only durable ordering, official stable release3.54.1.0.
    implementation("org.xerial:sqlite-jdbc:3.54.1.0")
    testImplementation("junit:junit:4.13.2")
}
application { mainClass.set("nl.davidengelen.redsquare.spaces.service.SpacesServiceMain") }
tasks.test {
    dependsOn(":core:prepareSpaceMlsHost")
    val native = project(":core").layout.buildDirectory.dir("generated/spaces-mls-host")
    inputs.dir(native).withPropertyName("spacesMlsHost")
    systemProperty("java.library.path", native.get().asFile.absolutePath)
    systemProperty("spaces.service.runtimeClasspath", sourceSets.main.get().runtimeClasspath.asPath)
}
