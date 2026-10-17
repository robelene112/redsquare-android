plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.12.1")
    testImplementation("junit:junit:4.13.2")
}
