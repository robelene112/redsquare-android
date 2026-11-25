package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceProfile
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
