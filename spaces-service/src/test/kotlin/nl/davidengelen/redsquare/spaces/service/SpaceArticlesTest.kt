package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.StackLink
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceArticlesTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    @Test fun publicChannelSharesOnlyAnAuthenticatedExactStackRevision() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        val curator = SaitoWallet.create()
        val visitor = SaitoWallet.create()
        val stackAuthor = SaitoWallet.create()
        val body = JsonObject().apply {
            addProperty("module", "Stack")
            addProperty("request", "create stack post request")
            add("data", JsonObject().apply {
                addProperty("type", "stack_post")
                addProperty("title", "A public article on Stack")
                addProperty("content", "This body remains in its original Stack article.")
            })
        }
        val publicArticle = SaitoTransaction.create(stackAuthor, body, clock.millis(), listOf(stackAuthor.publicKey))
        val restricted = SaitoTransaction.create(stackAuthor, body.deepCopy().apply { addProperty("access_hash", "restricted") },
            clock.millis(), listOf(stackAuthor.publicKey))
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { curatorStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { visitorStore ->
                    val writes = mutableListOf<String>()
                    val writer = SpaceClient(curator, "fixture-node", authority.publicKey, curatorStore, clock) { tx ->
                        val envelope = tx.envelope()
                        if (SaitoEnvelope.decode(envelope).body.get("request").asString == "event") writes += envelope
                        service.exchange(envelope)
                    }
                    val reader = SpaceClient(visitor, "fixture-node", authority.publicKey, visitorStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val space = writer.create(SpaceProfile("Article channel", visibility = SpaceVisibility.PUBLIC),
                        listOf(SpaceChannelProfile("Reading", SpaceChannelKind.ARTICLE)))
                    writer.flush(space); writer.refresh(space)
                    assertThrows(IllegalArgumentException::class.java) {
                        writer.shareStackArticle(space, SaitoBackend.OFFICIAL, restricted.envelope())
                    }
                    val share = writer.shareStackArticle(space, SaitoBackend.OFFICIAL, publicArticle.envelope())
                    writer.flush(space)
                    reader.openPublic(space)
                    reader.refresh(space)
                    val received = reader.article(space, share)
                    assertEquals(SpaceArticleContent.PublicStack(StackLink(SaitoBackend.OFFICIAL, stackAuthor.publicKey, publicArticle.id)),
                        received?.content)
                    assertEquals(curator.publicKey, received?.author)
                    val published = writes.map(SaitoEnvelope::decode).single { it.id == share }
                    assertEquals("public-content", published.body.get("kind").asString)
                    assertFalse(published.body.get("text").asString.contains("This body remains in its original Stack article."))
                }
            }
        }
    }

    @Test fun privateArticleIsEncryptedAndReadableByAnIndependentAdmittedWallet() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        val author = SaitoWallet.create()
        val reader = SaitoWallet.create()
        val unpublishedTitle = "Notes for our reading room"
        val draft = SpaceArticleContent.Original(unpublishedTitle, "A private subtitle", "## Chapter one\n\nThis stays inside the Space.")
        val writes = mutableListOf<String>()
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { authorStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { readerStore ->
                    val writer = SpaceClient(author, "fixture-node", authority.publicKey, authorStore, clock) { tx ->
                        val envelope = tx.envelope()
                        if (SaitoEnvelope.decode(envelope).body.get("request").asString == "event") writes += envelope
                        service.exchange(envelope)
                    }
                    val recipient = SpaceClient(reader, "fixture-node", authority.publicKey, readerStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val space = writer.create(SpaceProfile("Reading room"),
                        listOf(SpaceChannelProfile("Articles", SpaceChannelKind.ARTICLE)))
                    writer.flush(space)
                    val invite = writer.invite(space)
                    writer.flush(space)
                    recipient.join(invite)
                    recipient.flush(space); writer.admitPending(space); writer.flush(space); recipient.refresh(space)

                    val id = writer.publishArticle(space, draft)
                    writer.flush(space); recipient.refresh(space)
                    assertEquals(draft, recipient.article(space, id)?.content)
                    assertEquals(author.publicKey, recipient.article(space, id)?.author)
                    val submitted = writes.map(SaitoEnvelope::decode).single { it.id == id }
                    assertEquals("Spaces", submitted.body.get("module").asString)
                    assertEquals("article", submitted.body.get("content_kind").asString)
                    assertTrue(submitted.body.has("ciphertext"))
                    assertFalse(submitted.body.toString().contains(unpublishedTitle))
                    assertFalse(submitted.body.toString().contains("This stays inside the Space."))
                }
            }
        }
    }
}
