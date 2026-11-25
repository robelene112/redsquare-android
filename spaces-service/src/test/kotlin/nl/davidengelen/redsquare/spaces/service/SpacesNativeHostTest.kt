package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Exercise the host's actual public process/HTTP boundary before paying for an
 * emulator boot. A compiled Kotlin main alone does not prove a Java entrypoint. */
class SpacesNativeHostTest {
    @get:Rule val files = TemporaryFolder()
    @Test(timeout = 45_000) fun theActualFixtureProcessAdvertisesOwnedPortsAndStopsCleanly() = runHost(null)
    @Test(timeout = 45_000) fun theDiscoveryHostSeedsRealPublicPrivateAndUnlistedCommunitiesBeforeReadiness() = runHost("discovery-v1")
    @Test(timeout = 45_000) fun theManagementHostSeedsIndependentRolesAndNonemptyChannelHistoryBeforeReadiness() = runHost("management-v1")
    @Test(timeout = 45_000) fun thePrivateManagementHostSeedsARealLongRosterAndAnIndependentReader() = runHost("private-management-v1")
    @Test(timeout = 45_000) fun theReadinessHostUsesARealIndependentKeyHolderBeforeNativeAdmission() = runHost("private-readiness-v1")
    @Test(timeout = 55_000) fun theFileLibraryStartsAndRestartsAnIndependentOperatorBehindTls() = runHost("file-library-v1")
    private fun runHost(mode: String?) {
        val discovery = mode == "discovery-v1"
        val management = mode == "management-v1"
        val privateManagement = mode == "private-management-v1"
        val privateReadiness = mode == "private-readiness-v1"
        val fileLibrary = mode == "file-library-v1"
        val root = files.newFolder(mode ?: "host-start").toPath()
        if (fileLibrary) {
            val keytool = Path.of(System.getProperty("java.home"), "bin/keytool")
            val certificate = ProcessBuilder(keytool.toString(), "-genkeypair", "-keyalg", "RSA", "-keysize", "2048",
                "-validity", "1", "-dname", "CN=127.0.0.1", "-ext", "SAN=IP:127.0.0.1", "-alias", "owned-provider",
                "-storetype", "PKCS12", "-storepass", "owned-fixture", "-keystore", root.resolve("files-fixture.p12").toString())
                .redirectErrorStream(true).redirectOutput(root.resolve("certificate.log").toFile()).start()
            try {
                check(certificate.waitFor(12, TimeUnit.SECONDS) && certificate.exitValue() == 0) {
                    "Could not issue an ephemeral test certificate"
                }
            } finally { if (certificate.isAlive) certificate.destroyForcibly() }
        }
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-Djava.library.path=" + System.getProperty("java.library.path"),
            "-cp", System.getProperty("spaces.native.fixtureClasspath"), SpacesNativeHost::class.java.name, root.toString(),
            *(mode?.let { arrayOf(it) } ?: emptyArray()))
            .redirectErrorStream(true).redirectOutput(root.resolve("host.log").toFile()).start()
        var primary: Throwable? = null
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            val ready = root.resolve("ready.json")
            while (!Files.exists(ready)) {
                check(process.isAlive) { "Owned fixture stopped: " + Files.readString(root.resolve("host.log")) }
                check(System.nanoTime() < deadline) { "Owned fixture readiness exceeded30s" }
                Thread.sleep(25)
            }
            val metadata = JsonParser.parseString(Files.readString(ready)).asJsonObject
            assertTrue(metadata.get("peerPort").asInt in 1024..65535)
            val port = metadata.get("controlPort").asInt
            assertTrue(port in 1024..65535)
            val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
            fun command(action: String): String {
                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/$action"))
                    .timeout(Duration.ofSeconds(5)).POST(HttpRequest.BodyPublishers.ofString("{}")).build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofString())
                assertEquals(200, response.statusCode())
                return response.body()
            }
            val status = JsonParser.parseString(command("status")).asJsonObject
            assertEquals(if (discovery) 5 else if (management || privateManagement || privateReadiness || fileLibrary) 1 else 0, status.getAsJsonArray("communities").size())
            if (fileLibrary) {
                val configuration = JsonParser.parseString(command("file-library")).asJsonObject
                assertEquals("https://127.0.0.1:${metadata.get("providerPort").asInt}", configuration.get("origin").asString)
                assertEquals(0, JsonParser.parseString(command("file-provider-downloads")).asJsonObject.get("nativeDownloads").asInt)
                command("file-provider-restart")
                assertEquals(2L, Files.list(root).use { entries -> entries.filter { it.fileName.toString().startsWith("file-provider-") }.count() })
            }
            if (discovery) {
                val fixtures = JsonParser.parseString(command("fixtures")).asJsonObject
                assertEquals(setOf("public", "unlisted", "nonchat", "private", "expiring"), fixtures.keySet())
                assertTrue(fixtures.getAsJsonObject("unlisted").get("link").asString.startsWith("saito://spaces/community#"))
                assertTrue(fixtures.getAsJsonObject("private").get("link").asString.startsWith("saito://spaces/join#"))
                assertTrue(status.getAsJsonArray("communities").any { row -> row.asJsonObject.getAsJsonArray("received").any { it.asString == "Builders message 27" } })
            }
            if (management) {
                val fixtures = JsonParser.parseString(command("management-fixtures")).asJsonObject
                val received = JsonParser.parseString(command("management-status")).asJsonObject
                assertEquals(fixtures.get("space").asString, received.get("space").asString)
                assertEquals("OWNER", received.getAsJsonObject("members").get(fixtures.get("owner").asString).asString)
                assertEquals("MEMBER", received.getAsJsonObject("members").get(fixtures.get("member").asString).asString)
                assertEquals(2, received.getAsJsonObject("members").size())
                assertEquals(setOf("general", "workshop", "announcements"), received.getAsJsonObject("channels").entrySet()
                    .map { it.value.asJsonObject.get("name").asString }.toSet())
                assertTrue(received.getAsJsonObject("channels").entrySet().all { !it.value.asJsonObject.get("archived").asBoolean })
                assertEquals(0, received.getAsJsonArray("originals").size())
                assertEquals(0, received.getAsJsonArray("dropped").size())
            }
            if (privateManagement) {
                assertEquals((0 until 18).map { "Private management General $it" },
                    status.getAsJsonArray("communities").single().asJsonObject.getAsJsonArray("received").map { it.asString })
                val fixtures = JsonParser.parseString(command("private-management-fixtures")).asJsonObject
                val received = JsonParser.parseString(command("private-management-status")).asJsonObject
                assertEquals(fixtures.get("space").asString, received.get("space").asString)
                assertEquals(26, received.getAsJsonObject("members").size())
                assertEquals("OWNER", received.getAsJsonObject("members").get(fixtures.get("owner").asString).asString)
                assertEquals("MEMBER", received.getAsJsonObject("members").get(fixtures.get("member").asString).asString)
                assertEquals(listOf(fixtures.get("general").asString), received.getAsJsonObject("channels").keySet().toList())
                assertEquals(listOf(fixtures.get("general").asString), received.getAsJsonArray("outsiderChannels").map { it.asString })
                assertEquals(0, received.getAsJsonArray("originals").size())
                assertEquals(0, received.getAsJsonArray("dropped").size())
            }
            if (privateReadiness) {
                val fixtures = JsonParser.parseString(command("readiness-fixtures")).asJsonObject
                val received = JsonParser.parseString(command("readiness-status")).asJsonObject
                assertEquals(fixtures.get("space").asString, received.get("space").asString)
                assertEquals(6, received.getAsJsonObject("members").size())
                assertEquals("OWNER", received.getAsJsonObject("members").get(fixtures.get("owner").asString).asString)
                assertEquals("ADMIN", received.getAsJsonObject("members").get(fixtures.get("holder").asString).asString)
                assertEquals("MEMBER", received.getAsJsonObject("members").get(fixtures.get("actor").asString).asString)
                assertEquals(listOf(fixtures.get("holder").asString), received.getAsJsonArray("keyHolders").map { it.asString })
                assertEquals(setOf(fixtures.get("actor").asString, fixtures.get("allowed").asString),
                    received.getAsJsonArray("allowedMembers").map { it.asString }.toSet())
                assertEquals(0, received.getAsJsonArray("originals").size())
            }
            command("stop")
            check(process.waitFor(10, TimeUnit.SECONDS)) { "Owned fixture did not stop" }
            assertEquals(Files.readString(root.resolve("host.log")), 0, process.exitValue())
            assertTrue(Files.exists(root.resolve("result.json")))
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            try {
                if (process.isAlive) process.destroyForcibly()
                check(process.waitFor(5, TimeUnit.SECONDS)) { "Owned fixture process cleanup failed" }
            } catch (cleanup: Throwable) { if (primary == null) throw cleanup else primary.addSuppressed(cleanup) }
        }
    }
}
