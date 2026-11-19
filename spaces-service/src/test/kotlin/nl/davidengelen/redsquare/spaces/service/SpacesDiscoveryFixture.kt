package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Only the owned 138 host mode seeds these actual accepted communities. */
internal class SpacesDiscoveryFixture(private val client: SpaceClient, private val clock: AdvancingClock) {
    class AdvancingClock : java.time.Clock() {
        @Volatile var offset = 0L
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): java.time.Clock { require(zone == ZoneOffset.UTC); return this }
        override fun instant(): Instant = Instant.ofEpochMilli(millis())
        override fun millis(): Long = System.currentTimeMillis() + offset
    }
    private var released = CountDownLatch(0)
    @Volatile private var held = false
    @Volatile private var entered = false
    @Volatile private var coldPublicObservation: JsonObject? = null
    private val fixtures = JsonObject()
    suspend fun seed() {
        val image = java.awt.image.BufferedImage(24, 24, java.awt.image.BufferedImage.TYPE_INT_RGB).apply {
            for (x in 0 until width) for (y in 0 until height) setRGB(x, y, if (x + y < 24) 0xa35b43 else 0x487e82)
        }
        val jpeg = java.io.ByteArrayOutputStream().also { check(javax.imageio.ImageIO.write(image, "jpeg", it)) }.toByteArray()
        val icon = SpaceProfile.IMAGE_PREFIX + java.util.Base64.getEncoder().encodeToString(jpeg)
        suspend fun create(key: String, profile: SpaceProfile, channels: List<SpaceChannelProfile>) {
            val id = client.create(profile, channels)
            repeat(channels.size + 1) { client.flush(id) }
            fixtures.add(key, JsonObject().apply {
                addProperty("space", id); addProperty("name", profile.name)
                if (profile.visibility == SpaceVisibility.PUBLIC) addProperty("link", client.publicLink(id))
            })
        }
        create("public", SpaceProfile("Saito Builders", "Build thoughtful things together.", SpaceVisibility.PUBLIC,
            houseRules = "Be kind. Share ideas, never private keys.", image = icon), listOf(
                SpaceChannelProfile("general"), SpaceChannelProfile("workshop", category = "Conversations"),
                SpaceChannelProfile("announcements", SpaceChannelKind.ANNOUNCEMENT)))
        val public = fixtures.getAsJsonObject("public").get("space").asString
        repeat(28) { client.send(public, "Builders message $it") }; client.flush(public)
        create("unlisted", SpaceProfile("Independent Reading", "A public community reached by its fixed link.", SpaceVisibility.PUBLIC, listed = false, image = icon),
            listOf(SpaceChannelProfile("general")))
        create("nonchat", SpaceProfile("Open Reading Room", "Articles and files.", SpaceVisibility.PUBLIC, image = icon),
            listOf(SpaceChannelProfile("reading room", SpaceChannelKind.ARTICLE)))
        create("private", SpaceProfile("Private Builders", "Invitation-only discussion.", houseRules = "Respect this private room.", image = icon),
            listOf(SpaceChannelProfile("general"), SpaceChannelProfile("design")))
        val private = fixtures.getAsJsonObject("private").get("space").asString
        val invitation = client.invite(private); client.flush(private)
        fixtures.getAsJsonObject("private").addProperty("link", invitation)
        create("expiring", SpaceProfile("Short Invitation"), listOf(SpaceChannelProfile("general")))
        val expiring = fixtures.getAsJsonObject("expiring").get("space").asString
        val shortLink = client.invite(expiring, expiresAfterMillis = 60_000); client.flush(expiring)
        fixtures.getAsJsonObject("expiring").addProperty("link", shortLink)
    }
    fun afterExchange(original: SaitoTransaction) {
        val body = SaitoEnvelope.decode(original.envelope()).body
        if (held && body.get("request")?.asString == "read" && body.getAsJsonObject("scope")?.get("stream")?.asString == "control" &&
            body.getAsJsonObject("scope")?.get("space_id")?.asString == fixtures.getAsJsonObject("public").get("space").asString) {
            entered = true
            check(released.await(10, TimeUnit.SECONDS)) { "Owned held public read exceeded 10 seconds" }
        }
    }
    suspend fun command(path: String, body: JsonObject): JsonObject? = when (path) {
        "/fixtures" -> fixtures.deepCopy()
        "/held-public" -> JsonObject().apply { addProperty("entered", entered); addProperty("armed", held) }
        "/observe-cached-public" -> {
            check(held && released.count == 1L)
            require(body.keySet() == setOf("pid", "owner", "space"))
            require(body.get("pid").asInt > 0 && SaitoWallet.isPublicKey(body.get("owner").asString))
            require(body.get("space").asString == fixtures.getAsJsonObject("public").get("space").asString)
            body.deepCopy().apply {
                addProperty("publicReadBarrierArmed", true)
                addProperty("publicReadObserved", entered)
            }.also { coldPublicObservation = it.deepCopy() }
        }
        "/cached-public-observation" -> requireNotNull(coldPublicObservation).deepCopy()
        "/directory" -> JsonObject().apply {
            add("names", com.google.gson.JsonArray().apply { client.discover().items.forEach { add(it.profile.name) } })
        }
        "/channel-received" -> {
            val space = body.get("space").asString
            val channel = client.channels(space).single { it.profile.name == body.get("channel").asString }.id
            client.refresh(space, channel = channel)
            JsonObject().apply { add("received", com.google.gson.JsonArray().apply {
                client.timeline(space, channel = channel).filter { !it.pending && !it.rejected && !it.missingKeys && it.text != null }.forEach { message ->
                    add(JsonObject().apply { addProperty("id", message.id); addProperty("author", message.author); addProperty("text", message.text) })
                }
            }) }
        }
        "/hold-public" -> { released = CountDownLatch(1); entered = false; coldPublicObservation = null; held = true; JsonObject() }
        "/release-public" -> { held = false; released.countDown(); JsonObject() }
        "/expire" -> { clock.offset = 120_000; JsonObject() }
        "/reset-clock" -> { clock.offset = 0; JsonObject() }
        "/revoke-private" -> {
            val space = fixtures.getAsJsonObject("private").get("space").asString
            client.revokeInvitation(space); client.flush(space); JsonObject()
        }
        "/channel-populate" -> {
            val space = body.get("space").asString
            val channel = client.channels(space).single { it.profile.name == body.get("channel").asString }.id
            val count = body.get("count")?.asInt ?: 3
            require(count in 1..30)
            repeat(count) { client.send(space, "${body.get("channel").asString} update $it", channel = channel) }
            client.flush(space); JsonObject()
        }
        "/membership" -> JsonObject().apply {
            val space = body.get("space").asString
            add("members", com.google.gson.JsonArray().apply { client.members(space).forEach { add(it.publicKey) } })
        }
        else -> null
    }
}
