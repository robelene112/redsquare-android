plugins { kotlin("jvm"); application }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core"))
    implementation("com.google.code.gson:gson:2.12.1")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.54.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver3:5.4.0")
}
kotlin.sourceSets.test { resources.srcDir(rootProject.file("core/src/test/resources")) }

application { mainClass.set("nl.davidengelen.redsquare.gateway.GatewayMain") }
tasks.test { systemProperty("gateway.runtimeClasspath", sourceSets.main.get().runtimeClasspath.asPath) }
