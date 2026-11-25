package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
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

class SpaceArticlePolicyTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun receivedTimeoutRejectsAnOlderQueuedArticleWhileOtherChannelsAndReadingStillWork() = runBlocking<Unit> {
        val clock = SpacesDiscoveryFixture.AdvancingClock()
        val authority = SaitoWallet.create()
        val member = SaitoWallet.create()
        val reviewed = SpaceArticleDraft("Old writing", "", "Writing reviewed before the staff action.", true)
        val next = SpaceArticleDraft("Next draft", "", "Writing kept as a separate draft.")
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { memberStore ->
                    var held: String? = null
                    fun client(wallet: SaitoWallet, store: SqliteClientFixture) =
                        SpaceClient(wallet, "fixture-node", authority.publicKey, store, clock) {
                            val envelope = it.envelope()
                            if (it.id == held) throw java.io.IOException("Owned fixture held this original before receiving it")
                            service.exchange(envelope)
                        }
                    val owner = client(SaitoWallet.create(), ownerStore)
                    val contributor = client(member, memberStore)
                    val space = owner.create(SpaceProfile("Writers", visibility = SpaceVisibility.PUBLIC), listOf(
                        SpaceChannelProfile("Writing", SpaceChannelKind.ARTICLE),
                        SpaceChannelProfile("Independent channel", SpaceChannelKind.ARTICLE)))
                    owner.flush(space); owner.refresh(space)
                    contributor.openPublic(space)
                    contributor.joinPublic(space); contributor.flush(space); contributor.refresh(space)
                    owner.refresh(space)
                    val first = owner.community(space).channel
                    val second = owner.channels(space).single { it.id != first }.id
                    val kept = SpaceArticleContent.Original("Available to read", "", "Reading remains available.")
                    val readable = owner.publishArticle(space, kept, first)
                    owner.flush(space)
                    val queued = contributor.publishArticle(space, reviewed.content(), first, reviewed)
                    val delayed = contributor.publishArticle(space, reviewed.content().copy(title = "Held before delivery"), first)
                    held = delayed
                    contributor.saveArticleDraft(space, first, null, next)

                    val until = clock.millis() + 60_000
                    owner.setWriteRestriction(space, member.publicKey, first, until)
                    owner.flush(space)
                    // The submitting device has intentionally never read the new control.
                    assertTrue(runCatching { contributor.flush(space) }.exceptionOrNull() is java.io.IOException)
                    contributor.refresh(space, channel = first)
                    val rejected = contributor.timeline(space, channel = first).single { it.id == queued }
                    assertTrue(rejected.rejected)
                    assertEquals(reviewed.content(), SpaceArticleContent.decode(requireNotNull(rejected.text)))
                    assertEquals(next, contributor.articleDraft(space, first, null))
                    assertEquals(kept, contributor.article(space, readable, first)?.content)
                    assertFalse(contributor.community(space, first).canSend)
                    assertTrue(contributor.community(space, second).canSend)
                    assertThrows(IllegalArgumentException::class.java) { contributor.publishArticle(space, next.content(), first) }
                    owner.refresh(space, channel = first)
                    assertNull(owner.article(space, queued, first))
                    val allowed = contributor.publishArticle(space, next.content(), second)

                    clock.offset += 60_000
                    held = null
                    contributor.flush(space); owner.refresh(space, channel = second)
                    assertEquals(next.content(), owner.article(space, allowed, second)?.content)
                    assertTrue(contributor.timeline(space, channel = first).single { it.id == delayed }.rejected)
                    assertTrue(contributor.community(space, first).canSend)
                    owner.archiveChannel(space, first, true); owner.flush(space)
                    val archived = contributor.publishArticle(space, next.content(), first)
                    contributor.flush(space); contributor.refresh(space, channel = first)
                    assertTrue(contributor.timeline(space, channel = first).single { it.id == archived }.rejected)
                    assertEquals(kept, contributor.article(space, readable, first)?.content)
                }
            }
        }
    }
}
