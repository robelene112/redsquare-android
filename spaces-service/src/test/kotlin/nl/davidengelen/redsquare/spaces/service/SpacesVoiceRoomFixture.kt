package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import nl.davidengelen.redsquare.spaces.SpaceVoiceSignalKey
import java.nio.file.Path
import java.time.Clock

internal class SpacesVoiceRoomFixture(root: Path, private val authority: SaitoWallet, private val owner: SpaceClient,
    private val remote: SpacesServiceStore, private val clock: Clock) : AutoCloseable {
    private val wallets = (4..7).map { SaitoWallet.fromPrivateKey("0".repeat(63) + it) }
    private val stores = wallets.indices.map { SqliteClientFixture(root.resolve("room-actor-$it.sqlite").toString()) }
    private val actors = wallets.zip(stores).map { (wallet, store) ->
        SpaceClient(wallet, "https://saito.davidengelen.nl", authority.publicKey, store, clock) { remote.exchange(it.envelope()) }
    }
    private lateinit var space: String
    private lateinit var channel: String

    suspend fun seed() {
        space = owner.create(SpaceProfile("Room proof Space", visibility = SpaceVisibility.PUBLIC), listOf(
            SpaceChannelProfile("Open lounge", SpaceChannelKind.VOICE), SpaceChannelProfile("General", SpaceChannelKind.CHAT)))
        owner.flush(space)
        owner.refresh(space)
        channel = owner.channels(space).single { it.profile.kind == SpaceChannelKind.VOICE }.id
        actors.forEach { actor ->
            actor.openPublic(space)
            actor.joinPublic(space)
            actor.flush(space)
            actor.refresh(space)
        }
        owner.refresh(space)
    }

    fun configuration() = JsonObject().apply {
        addProperty("space", space)
        addProperty("channel", channel)
        addProperty("link", owner.publicLink(space))
        addProperty("authority", authority.publicKey)
        addProperty("nativeWallet", wallets.first().publicKey)
    }

    suspend fun observe() = owner.voiceRoom(space, channel).json()

    suspend fun removeNative(): JsonObject {
        val native = owner.voiceRoom(space, channel).participants.single { it.wallet == wallets.first().publicKey }
        return owner.moderateVoiceRoom(space, channel, native.session, remove = true).json()
    }

    private fun actor(body: JsonObject): SpaceClient = actors[body.get("peer").asInt.also { require(it in 1..3) }]

    suspend fun join(body: JsonObject) = actor(body).joinVoiceRoom(space, channel,
        SpaceVoiceSignalKey.Contact.read(body.getAsJsonObject("contact"))).json()

    suspend fun renew(body: JsonObject) = actor(body).renewVoiceRoom(space, channel,
        body.get("session").asString, body.get("receivedThrough").asLong).json()

    suspend fun signal(body: JsonObject) = actor(body).relayVoiceSignal(space, channel,
        body.get("session").asString, SpaceVoiceSignalKey.Packet.read(body.getAsJsonObject("packet"))).json()

    suspend fun leave(body: JsonObject) = actor(body).leaveVoiceRoom(space, channel, body.get("session").asString).json()

    override fun close() { stores.forEach(SqliteClientFixture::close) }
}
