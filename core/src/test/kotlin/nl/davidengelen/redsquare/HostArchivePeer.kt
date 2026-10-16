package nl.davidengelen.redsquare

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Test-only loopback peer that survives the emulator application's process. */
object HostArchivePeer {
    @JvmStatic fun main(arguments: Array<String>) {
        require(arguments.size == 2)
        val directory = File(arguments[0]).canonicalFile.also { require(it.isDirectory) }
        val root = JsonParser.parseString(File(arguments[1]).readText()).asJsonObject.getAsJsonObject("root")
        val mode = File(directory, "mode")
        val replyFile = File(directory, "reply.json")
        val peer = ControlledFeedPeer("[]", archiveResponse = { criteria ->
            require(criteria.get("field1").asString == "RedSquare" && criteria.get("flagged_ne").asInt == 1 &&
                criteria.get("limit").asInt == 1)
            if (mode.readText().trim() != "available") "{}" else {
                val reply = JsonParser.parseString(replyFile.readText()).asJsonObject
                val signature = criteria.get("sig").asString
                when (signature) {
                    reply.get("sig").asString -> "[$reply]"
                    root.get("sig").asString -> "[$root]"
                    else -> error("Unexpected exact-signature Archive lookup")
                }
            }
        })
        peer.start().use { server ->
            fun publish(name: String, row: JsonObject) {
                val temporary = File(directory, "$name.new")
                temporary.writeText(row.toString())
                Files.move(temporary.toPath(), File(directory, name).toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
            publish("ready.json", JsonObject().apply { addProperty("port", server.port) })
            // Hard lifecycle bound also covers an interrupted supervising harness.
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(8)
            while (mode.readText().trim() != "stop" && System.nanoTime() < deadline) {
                peer.assertHealthy()
                check(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                publish("observed.json", JsonObject().apply {
                    addProperty("requests", peer.requests.get())
                    addProperty("authenticatedConnections", peer.servedChallenges.get())
                    addProperty("outgoingWrites", 0)
                })
                Thread.sleep(50)
            }
            check(mode.readText().trim() == "stop") { "Host fixture lifetime exceeded" }
            peer.assertHealthy()
        }
    }
}
