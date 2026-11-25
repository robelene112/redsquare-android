package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import java.nio.file.Path
import java.time.Clock

internal class SpacesModerationFixture(root: Path, node: SaitoWallet, private val owner: SpaceClient,
    private val service: SpacesServiceStore, clock: Clock) : AutoCloseable {
    private val wallets = listOf(4, 5).map { SaitoWallet.fromPrivateKey("0".repeat(63) + it) }
    private val stores = wallets.indices.map { SqliteClientFixture(root.resolve("moderation-actor-$it.sqlite").toString()) }
    private val actors = wallets.zip(stores).map { (wallet, store) ->
        SpaceClient(wallet, "https://saito.davidengelen.nl", node.publicKey, store, clock) { service.exchange(it.envelope()) }
    }
    private lateinit var space: String
    private lateinit var general: String
    private lateinit var separate: String
    private lateinit var message: String

    suspend fun seed() {
        space = owner.create(SpaceProfile("Moderation proof Space", visibility = SpaceVisibility.PUBLIC), listOf(
            SpaceChannelProfile("General", SpaceChannelKind.CHAT), SpaceChannelProfile("Separate", SpaceChannelKind.CHAT)))
        owner.flush(space)
        owner.refresh(space)
        general = owner.channels(space).single { it.profile.name == "General" }.id
        separate = owner.channels(space).single { it.profile.name == "Separate" }.id
        message = owner.send(space, "Moderation proof message", general)
        owner.flush(space)
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
        addProperty("link", owner.publicLink(space))
        addProperty("nativeWallet", wallets.first().publicKey)
        addProperty("message", message)
    }

    suspend fun observeReport(): JsonObject {
        val report = owner.messageReports(space).reports.single()
        val restricted = runCatching { actors.first().messageReports(space) }.exceptionOrNull() is SpaceClient.ReadDenied
        return report.json().apply { addProperty("reporterDeniedStaffRead", restricted) }
    }

    suspend fun promoteReporter(): JsonObject {
        val report = observeReport()
        check(report.get("reporterDeniedStaffRead").asBoolean)
        owner.setAdministrator(space, wallets.first().publicKey, true)
        owner.flush(space)
        return report
    }

    suspend fun observeRestrictions(): JsonObject {
        val member = actors.last()
        member.refresh(space, channel = general)
        member.refresh(space, channel = separate)
        val changes = owner.adminLog(space).events.filter { it.actor == wallets.first().publicKey && it.member == wallets.last().publicKey }
        return JsonObject().apply {
            addProperty("generalWritable", member.community(space, general).canSend)
            addProperty("separateWritable", member.community(space, separate).canSend)
            addProperty("generalReadable", member.messages(space, general) == listOf("Moderation proof message"))
            addProperty("actionsRecorded", changes.size == 3 && changes.count { it.action == "paused-writing" && it.channel == null } == 1 &&
                changes.count { it.channel == general && it.action in setOf("paused-writing", "resumed-writing") } == 2)
        }
    }

    override fun close() { stores.forEach(SqliteClientFixture::close) }
}
