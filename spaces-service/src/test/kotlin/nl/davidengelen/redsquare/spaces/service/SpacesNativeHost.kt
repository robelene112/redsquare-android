package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.davidengelen.redsquare.ControlledFeedPeer
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceRole
import nl.davidengelen.redsquare.spaces.SpaceClient
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Disposable host peer survives an Android process kill. Public unsafe keys,
 * real service/SQLite/MLS and independent received effects; no public network. */
object SpacesNativeHost {
    @JvmStatic fun main(args: Array<String>): Unit = runBlocking {
        require(args.size in 1..2 && (args.size == 1 || args[1] in setOf("article-channels-v1", "conversation-overlays-v1", "discovery-v1", "management-v1", "private-management-v1", "private-readiness-v1", "category-management-v1", "governance-v1", "membership-v1", "private-ownership-v1")))
        val articleMode = args.getOrNull(1) == "article-channels-v1"
        val conversationMode = args.getOrNull(1) == "conversation-overlays-v1"
        val discoveryMode = args.getOrNull(1) == "discovery-v1"
        val privateOwnershipMode = args.getOrNull(1) == "private-ownership-v1"
        val membershipMode = privateOwnershipMode || args.getOrNull(1) == "membership-v1"
        val governanceMode = args.getOrNull(1) == "governance-v1"
        val categoryMode = args.getOrNull(1) == "category-management-v1"
        val managementMode = args.getOrNull(1) in setOf("management-v1", "category-management-v1", "governance-v1", "membership-v1", "private-ownership-v1")
        val privateManagementMode = args.getOrNull(1) == "private-management-v1"
        val privateReadinessMode = args.getOrNull(1) == "private-readiness-v1"
        val root = Path.of(args[0])
        require(Files.isDirectory(root) && !Files.exists(root.resolve("ready.json")))
        val service = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val guest = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
        val managedMember = if (managementMode || privateManagementMode || privateReadinessMode) SaitoWallet.fromPrivateKey("0".repeat(63) + "4") else null
        val clock = if (discoveryMode) SpacesDiscoveryFixture.AdvancingClock() else Clock.systemUTC()
        val stopping = AtomicBoolean()
        val lock = Mutex()
        val workers = Executors.newFixedThreadPool(2)
        val started = System.nanoTime()
        try { SpacesServiceStore(root.resolve("service"), service, clock).use { remote ->
            SqliteClientFixture(root.resolve("independent-client.sqlite").toString()).use { local ->
                val managedLocal = if (managementMode || privateManagementMode || privateReadinessMode) SqliteClientFixture(root.resolve("management-member.sqlite").toString()) else null
                managedLocal.use { memberStore ->
                    val client = SpaceClient(guest, "https://saito.davidengelen.nl", service.publicKey, local, clock) { remote.exchange(it.envelope()) }
                    val discovery = if (discoveryMode) SpacesDiscoveryFixture(client, clock as SpacesDiscoveryFixture.AdvancingClock).also { it.seed() } else null
                    val sent = mutableSetOf<String>()
                    var membership: SpacesMembershipFixture? = null
                    var privateManagement: SpacesPrivateManagementFixture? = null
                    var privateReadiness: SpacesPrivateReadinessFixture? = null
                    val peer = ControlledFeedPeer("[]", advertisedServices = if (discoveryMode || managementMode || privateManagementMode || privateReadinessMode) "archive||;spaces-v1||;spaces-directory-v1||;spaces-unread-v1||;spaces-channel-archive-v1||;spaces-role-management-v1||;spaces-public-channel-edit-v1||" + (if (privateManagementMode || privateReadinessMode || governanceMode || privateOwnershipMode) ";spaces-private-channel-access-v1||;spaces-private-channel-edit-v1||" else "") + (if (categoryMode || governanceMode) ";spaces-category-management-v1||" else "") + (if (governanceMode || membershipMode) ";spaces-membership-management-v1||;spaces-ownership-management-v1||;spaces-visibility-management-v1||;spaces-dissolution-v1||" else "") else if (conversationMode) "archive||;spaces-v1||;spaces-unread-v1||" else "archive||;spaces-v1||", peer = service,
                        archiveResponse = { "[]" }, registryResponse = if (discoveryMode || managementMode || privateManagementMode || privateReadinessMode) ({ criteria ->
                            JsonObject().apply {
                                if (criteria.get("request")?.asString == "registry query" &&
                                    criteria.getAsJsonArray("keys").any { it.asString == guest.publicKey }) addProperty(guest.publicKey, "BuildersHost@saito")
                                if (managedMember != null && criteria.get("request")?.asString == "registry query" &&
                                    criteria.getAsJsonArray("keys").any { it.asString == managedMember.publicKey }) addProperty(managedMember.publicKey, "MemberOfTheLab@saito")
                                if (criteria.get("request")?.asString == "registry query") {
                                    val keys = criteria.getAsJsonArray("keys").map { it.asString }.toSet()
                                    membership?.names?.forEach { (key, name) -> if (key in keys) addProperty(key, name) }
                                    privateManagement?.names?.forEach { (key, name) -> if (key in keys) addProperty(key, name) }
                                    privateReadiness?.names?.forEach { (key, name) -> if (key in keys) addProperty(key, name) }
                                }
                            }.toString()
                        }) else null)
                    val management = if (managementMode && !governanceMode && !membershipMode) SpacesManagementFixture(client, SpaceClient(requireNotNull(managedMember),
                        "https://saito.davidengelen.nl", service.publicKey, requireNotNull(memberStore), clock) { remote.exchange(it.envelope()) }, peer, categoryMode).also { it.seed() } else null
                    val governance = if (governanceMode) SpacesGovernanceFixture(client, SpaceClient(requireNotNull(managedMember),
                        "https://saito.davidengelen.nl", service.publicKey, requireNotNull(memberStore), clock) { remote.exchange(it.envelope()) },
                        peer, remote, service, clock, System.getenv("SPACES_PRIVATE_MANAGEMENT_CASE") == "category-access").also { it.seed() } else null
                    if (membershipMode) membership = SpacesMembershipFixture(client,
                        SpaceClient(requireNotNull(managedMember), "https://saito.davidengelen.nl", service.publicKey,
                            requireNotNull(memberStore), clock) { remote.exchange(it.envelope()) },
                        peer, remote, service, clock, root, privateOwnershipMode).also { it.seed() }
                    if (privateManagementMode) privateManagement = SpacesPrivateManagementFixture(client,
                        SpaceClient(requireNotNull(managedMember), "https://saito.davidengelen.nl", service.publicKey,
                            requireNotNull(memberStore), clock) { remote.exchange(it.envelope()) }, peer, root, service, clock, remote).also { it.seed() }
                    if (privateReadinessMode) privateReadiness = SpacesPrivateReadinessFixture(local,
                        requireNotNull(memberStore), peer, root, service, clock, remote).also { it.seed() }
                    peer.spacesResponse = { original ->
                        privateReadiness?.exchangeFromPeer(original) ?: remote.exchange(original.envelope()).also { discovery?.afterExchange(original) }
                    }
                    if (articleMode) peer.withheldSpacesContentKind = "article"
                    peer.start().use { socket ->
                        val control = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 8)
                        control.executor = workers
                        control.createContext("/") { request ->
                            try {
                                require(request.requestMethod == "POST")
                                val input = request.requestBody.use { it.readNBytes(32_769) }.also { require(it.size <= 32_768) }
                                val body = JsonParser.parseString(input.toString(Charsets.UTF_8)).asJsonObject
                                val result = runBlocking { lock.withLock {
                                    when (request.requestURI.path) {
                                        "/join" -> {
                                            val link = body.get("link").asString
                                            val preview = client.preview(link)
                                            check(client.communities().none { it.id == preview.spaceId })
                                            val space = client.join(link)
                                            client.flush(space)
                                            JsonObject().apply { addProperty("space", space); addProperty("previewDidNotJoin", true) }
                                        }
                                        "/invitation" -> {
                                            val space = client.create("Builders on another device")
                                            client.flush(space)
                                            val link = client.invite(space)
                                            client.flush(space)
                                            JsonObject().apply { addProperty("link", link); addProperty("space", space) }
                                        }
                                        "/protected-chat-invitation" -> {
                                            val recipient = body.get("recipient").asString
                                            val space = client.createProtectedChat("Protected native conversation", recipient)
                                            client.flush(space)
                                            val link = client.inviteFor(space, recipient)
                                            client.flush(space)
                                            JsonObject().apply { addProperty("link", link); addProperty("space", space) }
                                        }
                                        "/protected-chat-proof" -> {
                                            val space = body.get("space").asString
                                            val text = body.get("text").asString
                                            client.refresh(space)
                                            val originals = peer.spacesRequests.filter { transaction ->
                                                val content = SaitoEnvelope.decode(transaction.envelope()).body
                                                content.get("space_id")?.asString == space && content.get("content_kind")?.asString == "message"
                                            }
                                            val confirmed = originals.firstOrNull { original -> client.message(space, original.id)?.text == text }
                                            val matching = originals.filter { it.id == confirmed?.id }
                                            val contents = originals.map { SaitoEnvelope.decode(it.envelope()).body }
                                            JsonObject().apply {
                                                addProperty("readByIndependentPeer", client.messages(space).count { it == text } == 1)
                                                addProperty("encryptedOriginals", contents.count { it.has("ciphertext") && !it.has("text") && !it.toString().contains(text) })
                                                addProperty("contentOriginals", originals.size)
                                                addProperty("original", confirmed?.id)
                                                addProperty("originalSubmissions", matching.size)
                                                addProperty("exactOriginal", matching.map { it.envelope() }.distinct().size == 1)
                                            }
                                        }
                                        "/article-invitation" -> {
                                            check(articleMode)
                                            val space = client.create(SpaceProfile("Writing circle"), listOf(
                                                SpaceChannelProfile("Reading room", SpaceChannelKind.ARTICLE),
                                                SpaceChannelProfile("Other reading room", SpaceChannelKind.ARTICLE)))
                                            client.flush(space); client.refresh(space)
                                            val channel = client.community(space).channel
                                            val link = client.inviteFor(space, body.get("recipient").asString)
                                            client.flush(space)
                                            JsonObject().apply {
                                                addProperty("link", link); addProperty("space", space); addProperty("channel", channel)
                                            }
                                        }
                                        "/article-proof" -> {
                                            check(articleMode)
                                            val space = body.get("space").asString
                                            val channel = body.get("channel").asString
                                            val author = body.get("author").asString
                                            val expected = SpaceArticleContent.Original(body.get("title").asString,
                                                body.get("subtitle").asString, body.get("markdown").asString)
                                            client.refresh(space, channel)
                                            val submissions = peer.spacesRequests.filter { transaction ->
                                                val signed = SaitoEnvelope.decode(transaction.envelope())
                                                signed.author == author && signed.body.get("space_id")?.asString == space &&
                                                    signed.body.get("channel_id")?.asString == channel &&
                                                    signed.body.get("content_kind")?.asString == "article"
                                            }
                                            val received = client.timeline(space, channel = channel).filter {
                                                it.isArticle && !it.pending && !it.rejected && it.author == author
                                            }
                                            val publication = received.singleOrNull()?.takeIf { client.article(space, it.id, channel)?.content == expected }
                                            val matching = submissions.filter { it.id == publication?.id }
                                            JsonObject().apply {
                                                addProperty("receivedCount", received.size)
                                                addProperty("original", publication?.id)
                                                addProperty("link", publication?.let { client.articleLink(space, channel, it.id) })
                                                addProperty("originalSubmissions", matching.size)
                                                addProperty("exactOriginal", matching.isNotEmpty() && matching.size == submissions.size &&
                                                    submissions.map { it.envelope() }.distinct().size == 1)
                                                addProperty("encryptedOriginals", submissions.count {
                                                    val wire = SaitoEnvelope.decode(it.envelope()).body
                                                    val serialized = wire.toString()
                                                    wire.has("ciphertext") && !wire.has("text") && !wire.has("markdown") &&
                                                        !serialized.contains(expected.title) && !serialized.contains(expected.subtitle) &&
                                                        !serialized.contains(expected.markdown.lineSequence().first())
                                                })
                                            }
                                        }
                                        "/attention-message" -> {
                                            check(conversationMode)
                                            val space = body.get("space").asString
                                            client.refresh(space)
                                            val mention = body.get("mention").asBoolean
                                            val owner = client.members(space).single { it.role == SpaceRole.OWNER }.publicKey
                                            val text = if (mention) "A personal message for @$owner" else "An ordinary unread message"
                                            val id = client.send(space, text)
                                            client.flush(space)
                                            val received = requireNotNull(client.message(space, id))
                                            check(received.position != null && !received.pending)
                                            JsonObject().apply {
                                                addProperty("id", id); addProperty("position", requireNotNull(received.position)); addProperty("text", text)
                                            }
                                        }
                                        "/populate" -> {
                                            val space = body.get("space").asString
                                            check(client.community(space).joined)
                                            val from = body.get("from")?.asInt ?: 0
                                            val count = body.get("count")?.asInt ?: 20
                                            require(from in 0..100 && count in 1..60)
                                            var last = ""
                                            repeat(count) { last = client.send(space, "Fixture line ${from + it}") }
                                            client.flush(space); client.flush(space)
                                            val accepted = requireNotNull(client.message(space, last))
                                            JsonObject().apply {
                                                addProperty("space", space); addProperty("id", accepted.id)
                                                addProperty("position", requireNotNull(accepted.position)); addProperty("count", count)
                                            }
                                        }
                                        "/message-consumption" -> {
                                            val space = body.get("space").asString; val id = body.get("id").asString
                                            requireNotNull(client.message(space, id))
                                            // A new authenticated Android request acknowledges an
                                            // earlier committed message page via its durable cursor.
                                            // No fixture/client data is injected into Android.
                                            val acknowledgement = peer.spacesRequests.lastOrNull { transaction ->
                                                val signed = SaitoEnvelope.decode(transaction.envelope()); val read = signed.body
                                                val scope = read.getAsJsonObject("scope")
                                                read.get("request")?.asString == "read" && scope?.get("stream")?.asString == "messages" &&
                                                    scope.get("space_id")?.asString == space && scope.getAsJsonObject("reaction_cursors")?.has(id) == true
                                            }
                                            JsonObject().apply {
                                                addProperty("message", id); addProperty("observed", acknowledgement != null)
                                                acknowledgement?.let {
                                                    addProperty("request", it.envelope()); addProperty("author", SaitoEnvelope.decode(it.envelope()).author)
                                                }
                                            }
                                        }
                                        "/message-observation", "/react-to-message" -> {
                                            val space = body.get("space").asString
                                            val expected = body.get("id")?.asString
                                            val position = body.get("position")?.asLong
                                            if (position != null) client.older(space, before = Math.addExact(position, 1))
                                            val parent = body.get("parent")?.asString?.let { requireNotNull(client.message(space, it)) }
                                            if (parent != null) client.refreshThread(space, parent)
                                            val message = if (expected != null) requireNotNull(client.message(space, expected))
                                                else (if (parent == null) client.timeline(space) else client.thread(space, parent)).single { it.text == body.get("text").asString }
                                            if (request.requestURI.path == "/react-to-message") {
                                                listOf("👍", "❤️", "🔥", "🎉", "🚀", "😂", "🤔", "👀").forEach { emoji -> client.setReaction(space, message, emoji, true) }
                                                client.flush(space)
                                            }
                                            val received = requireNotNull(client.message(space, message.id))
                                            JsonObject().apply {
                                                addProperty("id", received.id); addProperty("position", requireNotNull(received.position))
                                                addProperty("text", received.text); addProperty("edited", received.edited); addProperty("deleted", received.deleted)
                                                addProperty("pinned", received.pinned); addProperty("pinPending", received.pinPending)
                                                parent?.let { addProperty("parent", it.id) }
                                                add("reactions", com.google.gson.JsonArray().apply { received.reactions.forEach { choice -> add(JsonObject().apply {
                                                    addProperty("emoji", choice.emoji); choice.count?.let { addProperty("count", it) }; addProperty("selected", choice.selected)
                                                }) } })
                                            }
                                        }
                                        "/hold" -> { peer.withholdSpacesReply = true; JsonObject() }
                                        "/release" -> { peer.withholdSpacesReply = false; JsonObject() }
                                        "/article-hold-read" -> {
                                            check(articleMode)
                                            peer.holdNextSpacesArticleRead()
                                            JsonObject()
                                        }
                                        "/article-held-read" -> {
                                            check(articleMode)
                                            JsonObject().apply { addProperty("held", peer.isSpacesArticleReadHeld()) }
                                        }
                                        "/article-release-read" -> {
                                            check(articleMode)
                                            JsonObject().apply { addProperty("released", peer.releaseSpacesArticleRead()) }
                                        }
                                        "/drop-next-read" -> {
                                            check(discoveryMode && peer.withheldSpacesReads.isEmpty())
                                            peer.withholdNextSpacesReadReply.set(true); JsonObject()
                                        }
                                        "/read-recovery" -> {
                                            check(discoveryMode)
                                            val dropped = peer.withheldSpacesReads.singleOrNull()
                                            val requests = peer.spacesRequests.toList()
                                            val position = requests.indexOfFirst { it.id == dropped }
                                            val resumed = if (position < 0) null else requests.drop(position + 1).firstOrNull {
                                                SaitoEnvelope.decode(it.envelope()).body.get("request")?.asString == "read"
                                            }
                                            JsonObject().apply {
                                                dropped?.let { addProperty("dropped", it) }
                                                resumed?.let {
                                                    addProperty("resumed", it.id)
                                                    addProperty("author", SaitoEnvelope.decode(it.envelope()).author)
                                                }
                                            }
                                        }
                                        "/stop" -> { stopping.set(true); JsonObject() }
                                        "/status" -> status(client, peer)
                                        else -> requireNotNull(management?.command(request.requestURI.path, body)
                                            ?: privateManagement?.command(request.requestURI.path, body)
                                            ?: privateReadiness?.command(request.requestURI.path, body)
                                            ?: membership?.command(request.requestURI.path, body) ?: governance?.command(request.requestURI.path, body)
                                            ?: discovery?.command(request.requestURI.path, body)) { "Unknown owned fixture command" }
                                    }
                                } }
                                val bytes = result.toString().toByteArray(Charsets.UTF_8)
                                request.sendResponseHeaders(200, bytes.size.toLong())
                                request.responseBody.use { it.write(bytes) }
                            } catch (_: Exception) {
                                val bytes = "{\"fixture_error\":true}".toByteArray()
                                request.sendResponseHeaders(409, bytes.size.toLong()); request.responseBody.use { it.write(bytes) }
                            } finally { request.close() }
                        }
                        control.start()
                        try {
                            Files.writeString(root.resolve("ready.json"), JsonObject().apply {
                                addProperty("peerPort", socket.port); addProperty("controlPort", control.address.port)
                                addProperty("service", service.publicKey); addProperty("guest", guest.publicKey)
                            }.toString())
                            while (!stopping.get()) {
                                check(System.nanoTime() - started < TimeUnit.MINUTES.toNanos(15)) { "Owned host lifetime exceeded" }
                                lock.withLock {
                                    // Private management has an explicit independent reader;
                                    // the legacy host must not publish or request an extra device.
                                    membership?.maintain()
                                    governance?.maintain()
                                    if (!privateManagementMode && !privateReadinessMode && !governanceMode && !membershipMode) for (community in client.communities()) {
                                        client.flush(community.id)
                                        client.refresh(community.id)
                                        if (client.community(community.id).joined) {
                                            client.admitPending(community.id)
                                            client.flush(community.id)
                                            val conversation = client.channels(community.id).any { channel ->
                                                channel.id == community.channel &&
                                                    channel.profile.kind in setOf(SpaceChannelKind.CHAT, SpaceChannelKind.ANNOUNCEMENT)
                                            }
                                            if (!managementMode && !articleMode && conversation && community.id !in sent && client.community(community.id).canSend) {
                                                client.send(community.id, "Reply from independent member")
                                                sent += community.id
                                            }
                                            client.flush(community.id)
                                        }
                                    }
                                }
                                delay(200)
                            }
                            peer.assertHealthy()
                            Files.writeString(root.resolve("result.json"), lock.withLock { (membership?.command("/membership-status", JsonObject()) ?: governance?.command("/governance-status", JsonObject()) ?: status(client, peer)).toString() })
                        } finally { control.stop(0) }
                    }
                }
            }
        }
        } finally {
            workers.shutdownNow()
            check(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun status(client: SpaceClient, peer: ControlledFeedPeer) = JsonObject().apply {
        add("communities", com.google.gson.JsonArray().apply {
            client.communities().forEach { community -> add(JsonObject().apply {
                addProperty("id", community.id); addProperty("joined", community.joined)
                add("received", com.google.gson.JsonArray().apply { client.messages(community.id).forEach(::add) })
            }) }
        })
        add("originals", com.google.gson.JsonArray().apply {
            peer.spacesRequests.filter { SaitoEnvelope.decode(it.envelope()).body.get("request").asString == "event" }
                .forEach { original -> add(JsonObject().apply {
                    addProperty("id", original.id)
                    addProperty("kind", SaitoEnvelope.decode(original.envelope()).body.get("kind").asString)
                }) }
        })
    }
}
