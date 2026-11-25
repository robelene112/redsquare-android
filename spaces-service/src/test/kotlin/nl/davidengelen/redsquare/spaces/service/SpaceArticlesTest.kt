package nl.davidengelen.redsquare.spaces.service

import com.google.gson.JsonObject
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoEnvelope
import nl.davidengelen.redsquare.saito.SaitoTransaction
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.saito.StackLink
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceArticleDraft
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

    @Test fun acceptedAuthorEditRetainsPublicationIdentityAndCannotBeMadeByAnotherMember() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        val author = SaitoWallet.create()
        val member = SaitoWallet.create()
        val updated = SpaceArticleDraft("Reviewed correction", "New subtitle", "A genuinely revised document.", true)
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { writerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { memberStore ->
                    val writer = SpaceClient(author, "fixture-node", authority.publicKey, writerStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val reader = SpaceClient(member, "fixture-node", authority.publicKey, memberStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val space = writer.create(SpaceProfile("Editorial room"),
                        listOf(SpaceChannelProfile("Articles", SpaceChannelKind.ARTICLE)))
                    writer.flush(space)
                    val link = writer.invite(space)
                    writer.flush(space)
                    reader.join(link); reader.flush(space); writer.admitPending(space); writer.flush(space); reader.refresh(space)
                    val original = writer.publishArticle(space, SpaceArticleContent.Original("First version", "", "Original body."))
                    writer.flush(space); reader.refresh(space)
                    val channel = writer.community(space).channel
                    assertThrows(IllegalArgumentException::class.java) {
                        reader.editArticle(space, original, updated.content(), channel)
                    }
                    writer.saveArticleDraft(space, channel, original, updated)
                    val edit = writer.editArticle(space, original, updated.content(), channel, updated)
                    assertNotEquals(original, edit)
                    assertNull(writer.articleDraft(space, channel, original))
                    assertFalse(requireNotNull(reader.article(space, original)).edited)
                    writer.flush(space); reader.refresh(space)
                    val revised = requireNotNull(reader.article(space, original))
                    assertEquals(original, revised.id)
                    assertEquals(updated.content(), revised.content)
                    assertTrue(revised.edited)
                }
            }
        }
    }

    @Test fun queuingAnArticleConsumesOnlyItsReviewedDraftAndPreservesOtherWriting() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        val author = SaitoWallet.create()
        val storage = files.newFile().absolutePath
        val reviewed = SpaceArticleDraft("Reviewed", "", "First article's approved body.", true)
        val revision = SpaceArticleDraft("Revision idea", "", "An independent edit draft.", false)
        val newer = SpaceArticleDraft("New", "", "Writing started after the first submission.", true)
        val otherChannel = SpaceArticleDraft("Separate", "", "Writing in a different channel.", false)
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            fun open(store: SqliteClientFixture) = SpaceClient(author, "fixture-node", authority.publicKey, store, clock) {
                service.exchange(it.envelope())
            }
            lateinit var space: String
            lateinit var first: String
            lateinit var second: String
            lateinit var original: String
            SqliteClientFixture(storage).use { store ->
                val writer = open(store)
                space = writer.create(SpaceProfile("Local writing"), listOf(
                    SpaceChannelProfile("Main reading room", SpaceChannelKind.ARTICLE),
                    SpaceChannelProfile("Other reading room", SpaceChannelKind.ARTICLE)))
                writer.flush(space)
                writer.refresh(space)
                val channels = writer.channels(space)
                first = channels.single { it.profile?.name == "Main reading room" }.id
                second = channels.single { it.profile?.name == "Other reading room" }.id
                writer.saveArticleDraft(space, second, null, otherChannel)
                writer.saveArticleDraft(space, first, null, reviewed)
                original = writer.publishArticle(space, reviewed.content(), first, reviewed)
                writer.flush(space)
                assertNull(writer.articleDraft(space, first, null))
                writer.saveArticleDraft(space, first, original, revision)
                writer.saveArticleDraft(space, first, null, newer)
            }
            SqliteClientFixture(storage).use { reopened ->
                val writer = open(reopened)
                assertEquals(newer, writer.articleDraft(space, first, null))
                assertEquals(revision, writer.articleDraft(space, first, original))
                assertEquals(otherChannel, writer.articleDraft(space, second, null))
                assertEquals(reviewed.content(), writer.article(space, original, first)?.content)
            }
        }
    }

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
