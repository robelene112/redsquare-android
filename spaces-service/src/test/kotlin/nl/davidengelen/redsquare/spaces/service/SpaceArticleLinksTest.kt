package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceArticleReference
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

class SpaceArticleLinksTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    @Test fun publicReferenceReadsOnlyTheExactArticleAndDoesNotResetChannelPagination() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { writerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { readerStore ->
                    var offline = false
                    val writer = SpaceClient(SaitoWallet.create(), "fixture-node", authority.publicKey, writerStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val reader = SpaceClient(SaitoWallet.create(), "fixture-node", authority.publicKey, readerStore, clock) {
                        if (offline) throw java.io.IOException("Owned fixture disconnected")
                        service.exchange(it.envelope())
                    }
                    val space = writer.create(SpaceProfile("Public reading", visibility = SpaceVisibility.PUBLIC), listOf(
                        SpaceChannelProfile("Reading", SpaceChannelKind.ARTICLE),
                        SpaceChannelProfile("Other reading", SpaceChannelKind.ARTICLE)))
                    writer.flush(space); writer.refresh(space)
                    val channel = writer.channels(space).single { it.profile?.name == "Reading" }.id
                    val other = writer.channels(space).single { it.profile?.name == "Other reading" }.id
                    val document = SpaceArticleContent.Original("Earlier article", "", "Read this exact article.")
                    val article = writer.publishArticle(space, document, channel)
                    repeat(52) { writer.publishArticle(space, document.copy(title = "Newer article ${it + 1}"), channel) }
                    repeat(2) { writer.flush(space) }
                    reader.openPublic(space); reader.refresh(space, channel = channel)
                    val before = reader.historyWindow(space, channel = channel)
                    assertTrue(before.from > 0)
                    assertNull(reader.article(space, article, channel))
                    val link = writer.articleLink(space, channel, article)
                    assertEquals(article, SpaceArticleReference.parse(link).article)
                    assertEquals(document, reader.openArticleLink(link)?.content)
                    assertEquals(document, reader.cachedArticleLink(link)?.content)
                    assertEquals(before.from, reader.historyWindow(space, channel = channel).from)
                    val wrong = SpaceArticleReference.parse(link).copy(channel = other).link()
                    assertNull(reader.openArticleLink(wrong))
                    offline = true
                    assertTrue(runCatching { reader.openArticleLink(link) }.exceptionOrNull() is java.io.IOException)
                    assertEquals(document, reader.cachedArticleLink(link)?.content)
                }
            }
        }
    }

    @Test fun learningAReferenceCannotGrantPrivateAccess() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { writerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { strangerStore ->
                    val writer = SpaceClient(SaitoWallet.create(), "fixture-node", authority.publicKey, writerStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val reader = SpaceClient(SaitoWallet.create(), "fixture-node", authority.publicKey, strangerStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val space = writer.create(SpaceProfile("Private articles"), listOf(SpaceChannelProfile("Reading", SpaceChannelKind.ARTICLE)))
                    writer.flush(space); writer.refresh(space)
                    val id = writer.publishArticle(space, SpaceArticleContent.Original("Private draft", "", "Private content."))
                    writer.flush(space)
                    val link = writer.articleLink(space, writer.community(space).channel, id)
                    assertTrue(runCatching { reader.openArticleLink(link) }.exceptionOrNull() is SpaceClient.ReadDenied)
                    assertNull(reader.cachedArticleLink(link))
                    assertTrue(reader.communities().isEmpty())
                }
            }
        }
    }

    @Test fun signedDeparturePreventsReopeningASavedPrivateLink() = runBlocking<Unit> {
        val authority = SaitoWallet.create()
        val member = SaitoWallet.create()
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { writerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { memberStore ->
                    val writer = SpaceClient(SaitoWallet.create(), "fixture-node", authority.publicKey, writerStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val reader = SpaceClient(member, "fixture-node", authority.publicKey, memberStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val space = writer.create(SpaceProfile("Private library"), listOf(SpaceChannelProfile("Reading", SpaceChannelKind.ARTICLE)))
                    writer.flush(space)
                    val invitation = writer.inviteFor(space, member.publicKey)
                    writer.flush(space)
                    reader.join(invitation); reader.flush(space); writer.admitPending(space); writer.flush(space); reader.refresh(space)
                    val article = SpaceArticleContent.Original("Previously readable", "", "Limited to members.")
                    val id = writer.publishArticle(space, article)
                    writer.flush(space)
                    val link = writer.articleLink(space, writer.community(space).channel, id)
                    assertEquals(article, reader.openArticleLink(link)?.content)
                    writer.removeMember(space, member.publicKey); writer.flush(space)
                    assertTrue(runCatching { reader.openArticleLink(link) }.exceptionOrNull() is SpaceClient.ReadDenied)
                    assertNull(reader.cachedArticleLink(link))
                }
            }
        }
    }
}
