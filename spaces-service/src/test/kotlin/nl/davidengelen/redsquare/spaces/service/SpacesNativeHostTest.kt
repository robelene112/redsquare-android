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
    private fun runHost(mode: String?) {
        val discovery = mode == "discovery-v1"
        val management = mode == "management-v1"
        val root = files.newFolder(mode ?: "host-start").toPath()
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
            assertEquals(if (discovery) 5 else if (management) 1 else 0, status.getAsJsonArray("communities").size())
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
