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
    @Test(timeout = 45_000) fun theActualFixtureProcessAdvertisesOwnedPortsAndStopsCleanly() {
        val root = files.newFolder("host-start").toPath()
        val process = ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(),
            "-Djava.library.path=" + System.getProperty("java.library.path"),
            "-cp", System.getProperty("spaces.native.fixtureClasspath"), SpacesNativeHost::class.java.name, root.toString())
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
            assertEquals(0, JsonParser.parseString(command("status")).asJsonObject.getAsJsonArray("communities").size())
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
