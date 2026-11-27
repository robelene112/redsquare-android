package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceDestination
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpacePublicReference
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import java.nio.file.Path
import java.time.Clock

internal class SpacesCatchUpFixture(root: Path, node: SaitoWallet, private val other: SpaceClient,
    service: SpacesServiceStore, clock: Clock) : AutoCloseable {
    private val nodeUrl = "https://saito.davidengelen.nl"
    private val authority = node.publicKey
    private val nativeWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
    private val secondWallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "5")
    private val nativeStore = SqliteClientFixture(root.resolve("catch-up-native-author.sqlite").toString())
    private val secondStore = SqliteClientFixture(root.resolve("catch-up-reply-author.sqlite").toString())
    private val sender = SpaceClient(nativeWallet, nodeUrl, authority, nativeStore, clock) { service.exchange(it.envelope()) }
    private val second = SpaceClient(secondWallet, nodeUrl, authority, secondStore, clock) { service.exchange(it.envelope()) }
    private lateinit var space: String
    private lateinit var channel: String
    private lateinit var first: String
    private lateinit var secondThread: String
    private lateinit var hidden: SpaceDestination
    private var latest: SpaceDestination? = null
    private var revocableSearchChannel: String? = null
    private var revocableSearchMessage: String? = null

    suspend fun seed() {
        space = other.create(SpaceProfile("Catch up proof Space", visibility = SpaceVisibility.PUBLIC), listOf(SpaceChannelProfile("General")))
        other.flush(space)
        other.refresh(space)
        channel = other.channels(space).single().id
        val hiddenChannel = other.createPrivateChannel(space, SpaceChannelProfile("Restricted proof channel"), emptySet())
        other.flush(space)
        other.refresh(space, channel = hiddenChannel)
        val secret = other.send(space, "Needle that must stay private", hiddenChannel)
        other.flush(space)
        hidden = address(hiddenChannel, secret)
        for (member in listOf(sender, second)) {
            member.openPublic(space)
            member.joinPublic(space)
            member.flush(space)
            member.refresh(space, channel = channel)
        }
        first = sender.send(space, "First proof Needle", channel)
        secondThread = sender.send(space, "Separate proof conversation", channel)
        sender.flush(space)
    }

    fun config() = JsonObject().apply {
        addProperty("link", other.publicLink(space))
        addProperty("nativeWallet", nativeWallet.publicKey)
        addProperty("privateLink", hidden.link())
    }

    suspend fun createRevocableSearchChannel(): JsonObject {
        check(revocableSearchChannel == null)
        revocableSearchChannel = other.createPrivateChannel(space, SpaceChannelProfile("Temporary search channel"), setOf(nativeWallet.publicKey))
        other.flush(space)
        return JsonObject().apply { addProperty("channel", revocableSearchChannel) }
    }

    suspend fun admitSearchDevice(requests: List<SaitoTransaction>): JsonObject {
        val target = requireNotNull(revocableSearchChannel)
        val joined = requests.any { tx ->
            val original = SaitoEnvelope.decode(tx.envelope())
            original.author == nativeWallet.publicKey && original.body.get("kind")?.asString == "join-channel" &&
                original.body.get("channel_id")?.asString == target
        }
        if (joined) {
            other.admitPending(space)
            other.flush(space)
            if (revocableSearchMessage == null) {
                revocableSearchMessage = other.send(space, "Temporary authorized Needle", target)
                other.flush(space)
            }
        }
        return JsonObject().apply { addProperty("ready", joined && revocableSearchMessage != null) }
    }

    suspend fun revokeSearchChannel(hold: (String, String, String) -> Unit): JsonObject {
        val target = requireNotNull(revocableSearchChannel)
        check(revocableSearchMessage != null)
        val control = other.revokeChannelAccess(space, target, nativeWallet.publicKey)
        hold(nativeWallet.publicKey, channel, control)
        other.flush(space)
        return JsonObject().apply { addProperty("control", control); addProperty("channel", target) }
    }

    suspend fun initialReplies(): JsonObject {
        check(latest == null)
        reply(other, first, "Earlier proof reply")
        reply(second, secondThread, "Unrelated proof reply")
        return JsonObject().apply { addProperty("delivered", 2) }
    }

    suspend fun latestReply(): JsonObject {
        check(latest == null)
        latest = address(channel, reply(second, first, "Latest exact proof reply"), first)
        return observe()
    }

    suspend fun observe(): JsonObject {
        val target = requireNotNull(latest)
        val observed = requireNotNull(other.openDestination(target)?.record)
        check(observed.id == target.record && observed.author == secondWallet.publicKey && observed.text == "Latest exact proof reply")
        val hiddenDenied = runCatching { sender.openDestination(hidden) }.exceptionOrNull() is SpaceClient.ReadDenied
        check(hiddenDenied)
        return JsonObject().apply {
            addProperty("link", target.link())
            addProperty("reply", observed.id)
            addProperty("root", first)
            addProperty("privateDenied", hiddenDenied)
        }
    }

    private fun address(channel: String, record: String, thread: String? = null) =
        SpaceDestination(SpacePublicReference(nodeUrl, authority, space), channel, SpaceDestination.Kind.MESSAGE, record, thread)

    private suspend fun reply(author: SpaceClient, root: String, text: String): String {
        author.refresh(space, channel = channel)
        val result = author.reply(space, requireNotNull(author.message(space, root, channel)), text, channel)
        author.flush(space)
        return result
    }

    override fun close() {
        nativeStore.close()
        secondStore.close()
    }
}
