package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceDestination
import nl.davidengelen.redsquare.spaces.SpaceNotificationMode
import nl.davidengelen.redsquare.spaces.SpaceNotificationPolicy
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock

class SpaceActivityTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun newRepliesBubbleTheSameThreadGroupAndOpenTheLatestExactReply() = runBlocking<Unit> {
        val clock = Clock.systemUTC()
        val serviceKey = SaitoWallet.create()
        val stores = List(3) { SqliteClientFixture(files.newFile().absolutePath) }
        try {
            SpacesServiceStore(files.newFolder().toPath(), serviceKey, clock).use { service ->
                val wallets = stores.map { SaitoWallet.create() }
                val (owner, first, second) = stores.zip(wallets).map { (store, wallet) ->
                    SpaceClient(wallet, "https://spaces.example.test", serviceKey.publicKey, store, clock) {
                        service.exchange(it.envelope())
                    }
                }
                val space = owner.create(SpaceProfile("Activity", visibility = SpaceVisibility.PUBLIC))
                owner.flush(space)
                owner.refresh(space)
                val channel = owner.channels(space).single().id
                val threadA = owner.send(space, "First conversation", channel)
                val threadB = owner.send(space, "Second conversation", channel)
                owner.flush(space)
                for (member in listOf(first, second)) {
                    member.openPublic(space)
                    member.joinPublic(space)
                    member.flush(space)
                    member.refresh(space)
                }
                first.reply(space, requireNotNull(first.message(space, threadA, channel)), "First reply", channel)
                first.flush(space)
                val replyB = second.reply(space, requireNotNull(second.message(space, threadB, channel)), "Different conversation", channel)
                second.flush(space)
                owner.refreshAttention(space, channel)
                val originalPage = owner.activityPage().groups
                assertEquals(listOf(threadB, threadA), originalPage.map { it.target.thread })
                assertEquals(replyB, originalPage.first().target.record)
                assertTrue(originalPage.all { it.unread })
                val delivery = owner.pendingActivityNotifications(null, 0, limit = 1).groups.single()
                assertEquals(originalPage.first(), delivery)
                owner.markActivityNotified(delivery)
                assertEquals(originalPage.last(), owner.pendingActivityNotifications(null, 0, limit = 1).groups.single())
                owner.markActivityNotified(originalPage.last())
                assertTrue(owner.pendingActivityNotifications(null, 0, limit = 1).groups.isEmpty())
                val firstGroup = originalPage.single { it.target.thread == threadA }
                owner.markActivitySeen(firstGroup)
                assertFalse(owner.activityPage().groups.single { it.id == firstGroup.id }.unread)

                val latestReply = second.reply(space, requireNotNull(second.message(space, threadA, channel)), "Latest reply", channel)
                second.flush(space)
                owner.refreshAttention(space, channel)
                val updated = owner.activityPage().groups
                assertEquals(listOf(firstGroup.id, originalPage.first().id), updated.map { it.id })
                val group = updated.first()
                assertEquals(2L, group.actorCount)
                assertTrue(group.unread)
                assertEquals(group, owner.pendingActivityNotifications(null, 0, limit = 1).groups.single())
                assertEquals(SpaceDestination.Kind.MESSAGE, group.target.kind)
                assertEquals(latestReply, group.target.record)
                assertEquals("Latest reply", owner.openDestination(group.target)?.record?.text)

                second.deleteMessage(space, requireNotNull(second.message(space, latestReply, channel)), channel)
                second.flush(space)
                owner.refreshAttention(space, channel)
                val remaining = owner.activityPage().groups.single { it.id == firstGroup.id }
                assertEquals("First reply", owner.openDestination(remaining.target)?.record?.text)
                assertEquals(1L, remaining.actorCount)
                assertFalse(remaining.unread)
                val mention = second.send(space, "For @${wallets.first().publicKey}", channel)
                second.flush(space)
                owner.refreshAttention(space, channel)
                assertTrue(owner.activityPage().groups.any { it.target.record == mention })
                second.editMessage(space, requireNotNull(second.message(space, mention, channel)), "General note", channel)
                second.flush(space)
                owner.refreshAttention(space, channel)
                assertTrue(owner.activityPage().groups.none { it.target.record == mention })

                owner.setNotificationPolicy(space, SpaceNotificationPolicy(SpaceNotificationMode.SILENT), channel)
                assertTrue(owner.activityPage().groups.isEmpty())
                assertTrue(owner.pendingActivityNotifications(null, 0).groups.isEmpty())
                owner.refreshUnread(space)
                repeat(5) { owner.refreshNextAttention(space) }
                assertFalse("The current attention projection must leave the catch-up queue", owner.refreshNextAttention(space))
                assertTrue(owner.attention(space, channel).hasUnread == true)
            }
        } finally { stores.forEach(SqliteClientFixture::close) }
    }
}
