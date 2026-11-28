package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import nl.davidengelen.redsquare.saito.SaitoEnvelope
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
                val readerDatabase = files.newFile().absolutePath
                SqliteClientFixture(readerDatabase).use { readerStore ->
                    val owner = SpaceClient(SaitoWallet.create(), "https://spaces.example.test", authority.publicKey, ownerStore, clock) {
                        service.exchange(it.envelope())
                    }
                    val readerWallet = SaitoWallet.create()
                    val held = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    val invalidations = mutableListOf<Pair<String, Long>>()
                    var holdContent = false
                    val reader = SpaceClient(readerWallet, "https://spaces.example.test", authority.publicKey, readerStore, clock,
                        onReadScopeChanged = { changed, revision -> invalidations.add(changed to revision) }) { request ->
                        val envelope = request.envelope()
                        val response = service.exchange(envelope)
                        if (holdContent) {
                            val body = SaitoEnvelope.decode(envelope).body
                            if (body.get("request")?.asString == "read" && body.getAsJsonObject("scope")?.get("stream")?.asString == "messages") {
                                held.complete(Unit)
                                release.await()
                            }
                        }
                        response
                    }
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
                    val queued = owner.send(space, "Needle waiting to be sent", chat)

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
                    val otherSpace = owner.create(SpaceProfile("Other community", visibility = SpaceVisibility.PUBLIC))
                    owner.flush(otherSpace)
                    reader.openPublic(otherSpace)
                    reader.joinPublic(otherSpace)
                    reader.flush(otherSpace)
                    owner.refresh(otherSpace)
                    val searchedRevision = reader.readScopeRevision(space)
                    val otherRevision = reader.readScopeRevision(otherSpace)
                    val activityRevision = reader.readScopeRevision
                    owner.setAdministrator(otherSpace, readerWallet.publicKey, administrator = true)
                    owner.flush(otherSpace)
                    reader.refresh(otherSpace)
                    assertEquals("ADMIN", reader.members(otherSpace).single { it.publicKey == readerWallet.publicKey }.role.name)
                    assertTrue(reader.readScopeRevision(otherSpace) > otherRevision)
                    assertTrue(reader.readScopeRevision > activityRevision)
                    assertEquals("A role change in the other Space leaves this Search valid", searchedRevision, reader.readScopeRevision(space))
                    assertEquals(saved.address, find(reader, SpaceSearchFilter(channel = hidden)).single { it.address.record == permitted }.address)
                    val beforeRevocation = reader.readScopeRevision(space)
                    assertNotNull(reader.cachedDestination(saved.address))
                    assertTrue(reader.searchChannels(space).any { it.id == hidden })
                    owner.revokeChannelAccess(space, hidden, readerWallet.publicKey)
                    owner.flush(space)
                    holdContent = true
                    val refreshing = async { reader.refresh(space, channel = chat) }
                    try {
                        withTimeout(5_000) { held.await() }
                        assertFalse("The unrelated content read is still waiting", refreshing.isCompleted)
                        assertTrue(reader.readScopeRevision(space) > beforeRevocation)
                        assertEquals(space to reader.readScopeRevision(space), invalidations.last())
                        assertFalse(reader.searchChannels(space).any { it.id == hidden })
                        assertTrue(find(reader, SpaceSearchFilter(channel = hidden)).isEmpty())
                        assertEquals(setOf(first, reply, queued), find(reader, SpaceSearchFilter(channel = chat)).map { it.address.record }.toSet())
                    } finally { release.complete(Unit) }
                    refreshing.await()
                    assertTrue(runCatching { reader.openDestination(saved.address) }.exceptionOrNull() is SpaceClient.ReadDenied)
                    assertTrue(reader.readScopeRevision(space) > beforeRevocation)
                    assertTrue(find(reader, SpaceSearchFilter(channel = hidden)).isEmpty())
                    assertFalse(reader.destinationAccessAllowed(saved.address))
                    assertNull(reader.cachedDestination(saved.address))
                    assertEquals(setOf(first, reply, queued), find(reader, SpaceSearchFilter(channel = chat)).map { it.address.record }.toSet())
                    SqliteClientFixture(readerDatabase).use { reopened ->
                        val offline = SpaceClient(readerWallet, "https://spaces.example.test", authority.publicKey, reopened, clock) {
                            error("Opening a saved Search view must not contact the node")
                        }
                        assertFalse(offline.searchChannels(space).any { it.id == hidden })
                        assertFalse(offline.destinationAccessAllowed(saved.address))
                        assertNull(offline.cachedDestination(saved.address))
                        assertEquals(setOf(first, reply, queued), find(offline, SpaceSearchFilter(channel = chat)).map { it.address.record }.toSet())
                    }
                }
            }
        }
    }
}
