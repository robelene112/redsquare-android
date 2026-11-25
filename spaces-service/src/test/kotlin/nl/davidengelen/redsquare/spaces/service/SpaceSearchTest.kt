package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceArticleContent
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceDestination
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceSearchCursor
import nl.davidengelen.redsquare.spaces.SpaceSearchFilter
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock

class SpaceSearchTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun searchPagesOnlyAcceptedAccessibleContentAndRespectsChannelAndType() = runBlocking<Unit> {
        val clock = Clock.systemUTC()
        val authority = SaitoWallet.create()
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { readerStore ->
                    fun client(store: SqliteClientFixture, wallet: SaitoWallet = SaitoWallet.create()) = SpaceClient(wallet,
                        "https://spaces.example.test", authority.publicKey, store, clock) {
                        service.exchange(it.envelope())
                    }
                    val owner = client(ownerStore)
                    val readerWallet = SaitoWallet.create()
                    val reader = client(readerStore, readerWallet)
                    val space = owner.create(SpaceProfile("Searchable Space", visibility = SpaceVisibility.PUBLIC), listOf(
                        SpaceChannelProfile("General", SpaceChannelKind.CHAT),
                        SpaceChannelProfile("Articles", SpaceChannelKind.ARTICLE)))
                    owner.flush(space)
                    owner.refresh(space)
                    val chat = owner.channels(space).single { it.profile.kind == SpaceChannelKind.CHAT }.id
                    val articles = owner.channels(space).single { it.profile.kind == SpaceChannelKind.ARTICLE }.id
                    val hidden = owner.createPrivateChannel(space, SpaceChannelProfile("Private notes"), emptySet())
                    owner.flush(space)
                    owner.refresh(space, channel = hidden)
                    val secret = owner.send(space, "A Needle in private notes", hidden)
                    val first = owner.send(space, "A Needle in a message", chat)
                    owner.flush(space)
                    val reply = owner.reply(space, requireNotNull(owner.message(space, first, chat)), "A Needle in a reply", chat)
                    val article = owner.publishArticle(space, SpaceArticleContent.Original("Needle guide", "", "An article body"), articles)
                    owner.flush(space)
                    owner.send(space, "Unsent Needle should stay out of search", chat)

                    reader.openPublic(space)
                    reader.refresh(space, channel = chat)
                    reader.refreshAttention(space, channel = chat)
                    reader.refresh(space, channel = articles)
                    fun find(client: SpaceClient, filter: SpaceSearchFilter = SpaceSearchFilter()) = buildList {
                        var cursor: SpaceSearchCursor? = null
                        repeat(12) {
                            val page = client.searchCached(space, "nEeDlE", filter, cursor, limit = 1)
                            addAll(page.hits)
                            cursor = page.next
                            if (cursor == null) return@buildList
                        }
                        fail("Search cursors must terminate")
                    }
                    val public = find(reader)
                    assertEquals(setOf(first, reply, article), public.map { it.address.record }.toSet())
                    assertEquals(public.size, public.map { it.address }.toSet().size)
                    assertEquals(first, public.single { it.address.record == reply }.address.thread)
                    assertEquals(listOf(article), find(reader, SpaceSearchFilter(kind = SpaceDestination.Kind.ARTICLE)).map { it.address.record })
                    assertTrue(find(reader, SpaceSearchFilter(channel = hidden)).isEmpty())
                    assertEquals(setOf(first, reply), find(reader, SpaceSearchFilter(channel = chat)).map { it.address.record }.toSet())
                    assertEquals(listOf(secret), find(owner, SpaceSearchFilter(channel = hidden)).map { it.address.record })

                    reader.joinPublic(space)
                    reader.flush(space)
                    owner.refresh(space)
                    owner.grantChannelAccess(space, hidden, readerWallet.publicKey)
                    owner.flush(space)
                    reader.refresh(space)
                    reader.flush(space)
                    owner.admitPending(space)
                    owner.flush(space)
                    reader.refresh(space, channel = hidden)
                    val permitted = owner.send(space, "Needle shared with approved members", hidden)
                    owner.flush(space)
                    reader.refresh(space, channel = hidden)
                    val saved = find(reader, SpaceSearchFilter(channel = hidden)).single { it.address.record == permitted }
                    val beforeRevocation = reader.readScopeRevision
                    assertNotNull(reader.cachedDestination(saved.address))
                    owner.revokeChannelAccess(space, hidden, readerWallet.publicKey)
                    owner.flush(space)
                    assertTrue(runCatching { reader.openDestination(saved.address) }.exceptionOrNull() is SpaceClient.ReadDenied)
                    assertTrue(reader.readScopeRevision > beforeRevocation)
                    assertTrue(find(reader, SpaceSearchFilter(channel = hidden)).isEmpty())
                    assertNull(reader.cachedDestination(saved.address))
                    assertEquals(setOf(first, reply), find(reader, SpaceSearchFilter(channel = chat)).map { it.address.record }.toSet())
                }
            }
        }
    }
}
