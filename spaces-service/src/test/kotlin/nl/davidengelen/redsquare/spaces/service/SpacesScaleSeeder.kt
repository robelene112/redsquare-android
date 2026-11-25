package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceFileCipher
import nl.davidengelen.redsquare.spaces.SpaceFileDescriptor
import nl.davidengelen.redsquare.spaces.SpaceFileStage
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceStorageProviderClient
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.imageio.ImageIO

/** Owned release fixture. All content enters through the signed client and service.
 * The persisted service and fixture-author stores are reusable between measurements. */
object SpacesScaleSeeder {
    private const val node = "https://saito.davidengelen.nl"
    private const val origin = "https://spaces-benchmark-provider.example.test"
    private val clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC)
    private val channels = listOf(
        SpaceChannelProfile("General"),
        SpaceChannelProfile("Planning"),
        SpaceChannelProfile("Questions"),
        SpaceChannelProfile("Social"),
        SpaceChannelProfile("Support"),
        SpaceChannelProfile("Test conversation"),
        SpaceChannelProfile("Announcements", SpaceChannelKind.ANNOUNCEMENT),
        SpaceChannelProfile("Articles", SpaceChannelKind.ARTICLE),
        SpaceChannelProfile("Library", SpaceChannelKind.FILE),
        SpaceChannelProfile("Lounge", SpaceChannelKind.VOICE),
    )

    @JvmStatic fun main(args: Array<String>) = runBlocking<Unit> {
        require(args.size in 2..3 && (args.size == 2 || args[2] == "--smoke"))
        val root = Path.of(args[0])
        val contract = Files.readAllBytes(Path.of(args[1]))
        val volume = JsonParser.parseString(contract.toString(Charsets.UTF_8)).asJsonObject.getAsJsonObject("fixture")
        val smoke = args.size == 3
        val communityCount = if (smoke) 2 else volume.get("spaces").asInt
        val filesPerSpace = if (smoke) 3 else volume.get("fileEntriesPerLibrary").asInt
        val hotCount = if (smoke) 8 else volume.get("hotChannelMessages").asInt
        val otherVolumes = volume.getAsJsonObject("otherTextChannelVolumes")
        val regular = otherVolumes.get("channelsWith129Records").asInt
        val textChannels = channels.filter { it.kind in setOf(SpaceChannelKind.CHAT, SpaceChannelKind.ANNOUNCEMENT) }
        val expectedChatChannels = regular + otherVolumes.get("channelsWith128Records").asInt + 1
        require(expectedChatChannels == textChannels.size * volume.get("spaces").asInt)
        require(channels.size * volume.get("spaces").asInt == volume.get("channels").asInt)
        require(volume.get("seed").asInt == 147)
        Files.createDirectory(root)

        val authority = wallet(2)
        val owner = wallet(4)
        val provider = wallet(5)
        val communities = mutableListOf<SpaceClient.Community>()
        var hotChannel = ""
        val remotePath = root.resolve("service")
        var acceptedMessages = 0
        SpacesServiceStore(remotePath, authority, clock).use { service ->
            SpaceFileProviderServer(root.resolve("provider"), provider, origin, authority.publicKey,
                clock, 0, 128L * 1024 * 1024, 30 * 86_400_000L, service::checkFileAccess).use { server ->
                server.start()
                val storage = SpaceStorageProviderClient(origin) { url ->
                    URL("http://127.0.0.1:${server.port}${url.file}").openConnection() as HttpURLConnection
                }
                SqliteClientFixture(root.resolve("fixture-author.sqlite").toString()).use { local ->
                    val writer = SpaceClient(owner, node, authority.publicKey, local, clock) { service.exchange(it.envelope()) }
                    var ordinal = 0
                    for (number in 1..communityCount) {
                        val name = "Benchmark Space " + number.toString().padStart(3, '0')
                        val space = writer.create(SpaceProfile(name, visibility = SpaceVisibility.PUBLIC), channels)
                        writer.flush(space)
                        writer.refresh(space)
                        val actualChannels = writer.channels(space)
                        check(actualChannels.map { it.profile }.toSet() == channels.toSet())
                        communities.add(writer.community(space))
                        for (profile in textChannels) {
                            val channel = actualChannels.single { it.profile == profile }.id
                            val count = when {
                                ordinal == 0 -> hotCount
                                smoke -> 2
                                ordinal <= regular -> 129
                                else -> 128
                            }
                            if (ordinal == 0) hotChannel = channel
                            for (start in 1..count step 40) {
                                for (index in start..minOf(start + 39, count)) writer.send(space,
                                    "${profile.name} • $name • record $index. The previous regular history remains available when opening a saved reply.", channel)
                                writer.flush(space)
                            }
                            acceptedMessages += count
                            ordinal++
                        }
                        val library = actualChannels.single { it.profile.kind == SpaceChannelKind.FILE }.id
                        publishLibrary(writer, space, library, filesPerSpace, owner, authority, provider, storage)
                    }
                }
            }
        }
        val expectedMessages = if (smoke) acceptedMessages else volume.get("messages").asInt
        val expectedFiles = if (smoke) communityCount * filesPerSpace else volume.get("files").asInt
        DriverManager.getConnection("jdbc:sqlite:${remotePath.resolve("spaces.sqlite").toAbsolutePath()}").use { db ->
            db.createStatement().use { statement ->
                val byKind = statement.executeQuery("SELECT kind, COUNT(*) FROM records GROUP BY kind").use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getInt(2)) }
                }
                check(byKind["message"] == expectedMessages && byKind["file"] == expectedFiles) {
                    "Unexpected accepted content volumes $byKind"
                }
            }
            db.prepareStatement("SELECT COUNT(*) FROM records WHERE channel_id=? AND kind='message'").use { query ->
                query.setString(1, hotChannel)
                query.executeQuery().use { check(it.next() && it.getInt(1) == hotCount) }
            }
        }
        val manifest = JsonObject().apply {
            addProperty("fixture", if (smoke) "spaces-147-smoke" else "spaces-147-scale")
            addProperty("contractSha256", MessageDigest.getInstance("SHA-256").digest(contract).joinToString("") { "%02x".format(it) })
            addProperty("node", node)
            addProperty("authority", authority.publicKey)
            addProperty("owner", owner.publicKey)
            addProperty("spaces", communities.size)
            addProperty("channels", channels.size * communities.size)
            addProperty("acceptedMessages", expectedMessages)
            addProperty("acceptedFiles", expectedFiles)
            addProperty("hotSpace", communities.first().id)
            addProperty("hotChannel", hotChannel)
            addProperty("hotChannelMessages", hotCount)
            addProperty("unrelatedSpace", communities[1].id)
            addProperty("unrelatedChannel", communities[1].channel)
            addProperty("qualifiesForTimedRuns", !smoke)
        }
        Files.writeString(root.resolve("fixture.json"), manifest.toString() + "\n")
        println(manifest)
    }

    private suspend fun publishLibrary(writer: SpaceClient, space: String, channel: String, count: Int,
        owner: SaitoWallet, authority: SaitoWallet, provider: SaitoWallet, storage: SpaceStorageProviderClient) {
        val offer = storage.offer(space, owner.publicKey)
        val consent = writer.approveFileStorage(space, offer)
        writer.flush(space)
        val approved = requireNotNull(writer.fileStorageApproval(space, consent))
        writer.activateFileStorage(space, approved, storage.commit(approved))
        writer.flush(space)
        val scope = SpaceFileCipher.Scope(authority.publicKey, space, channel)
        repeat(count) { index ->
            val (name, mime, payload) = reference(index)
            val encrypted = ByteArrayOutputStream()
            val sealed = SpaceFileCipher.seal(scope, payload.size.toLong(), payload.inputStream(), encrypted)
            val access = writer.fileUploadPermission(space, channel, sealed)
            val stage = SpaceFileStage.read(storage.put(access, encrypted.toByteArray().inputStream()),
                authority.publicKey, provider.publicKey)
            writer.publishFile(space, channel, SpaceFileDescriptor(name, mime, sealed), stage)
            writer.flush(space)
        }
    }

    private fun reference(index: Int): Triple<String, String, ByteArray> = when (index % 3) {
        0 -> Triple("Reference image $index.png", "image/png", imageSample)
        1 -> Triple("Reference audio $index.wav", "audio/wav", ByteBuffer.allocate(204).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(196); put("WAVEfmt ".toByteArray()); putInt(16)
            putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(160)
        }.array())
        else -> Triple("Reference document $index.txt", "text/plain", "Owned fixture document $index".toByteArray())
    }

    private val imageSample by lazy {
        val image = BufferedImage(24, 24, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val pixel = ((70 + 4 * x) shl 16) or ((112 + 3 * y) shl 8) or 132
            image.setRGB(x, y, pixel)
        }
        ByteArrayOutputStream().also { check(ImageIO.write(image, "png", it)) }.toByteArray()
    }

    private fun wallet(index: Int) = SaitoWallet.fromPrivateKey("0".repeat(63) + index)
}
