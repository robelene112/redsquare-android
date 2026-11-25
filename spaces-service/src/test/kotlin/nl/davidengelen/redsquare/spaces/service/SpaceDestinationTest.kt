package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceChannelKind
import nl.davidengelen.redsquare.spaces.SpaceChannelProfile
import nl.davidengelen.redsquare.spaces.SpaceClient
import nl.davidengelen.redsquare.spaces.SpaceDestination
import nl.davidengelen.redsquare.spaces.SpaceDestination.Kind
import nl.davidengelen.redsquare.spaces.SpaceProfile
import nl.davidengelen.redsquare.spaces.SpacePublicReference
import nl.davidengelen.redsquare.spaces.SpaceVisibility
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock

class SpaceDestinationTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun anExactLinkLoadsTheAcceptedReplyWithoutJoiningOrReplacingAnotherDraft() = runBlocking<Unit> {
        val clock = Clock.systemUTC()
        val authority = SaitoWallet.create()
        val node = "https://spaces.example.test"
        SpacesServiceStore(files.newFolder().toPath(), authority, clock).use { service ->
            SqliteClientFixture(files.newFile().absolutePath).use { ownerStore ->
                SqliteClientFixture(files.newFile().absolutePath).use { browserStore ->
                    fun client(store: SqliteClientFixture) = SpaceClient(SaitoWallet.create(), node, authority.publicKey, store, clock) {
                        service.exchange(it.envelope())
                    }
                    val owner = client(ownerStore)
                    val browser = client(browserStore)
                    val space = owner.create(SpaceProfile("Reading without joining", visibility = SpaceVisibility.PUBLIC), listOf(
                        SpaceChannelProfile("General", SpaceChannelKind.CHAT),
                        SpaceChannelProfile("Lounge", SpaceChannelKind.VOICE)))
                    owner.flush(space)
                    owner.refresh(space)
                    val chat = owner.channels(space).single { it.profile.kind == SpaceChannelKind.CHAT }.id
                    val room = owner.channels(space).single { it.profile.kind == SpaceChannelKind.VOICE }.id
                    val parent = owner.send(space, "First subject", chat)
                    val other = owner.send(space, "Another subject", chat)
                    owner.flush(space)
                    val reply = owner.reply(space, requireNotNull(owner.message(space, parent, chat)), "The exact reply", chat)
                    owner.flush(space)
                    val public = SpacePublicReference(node, authority.publicKey, space)
                    val roomLink = SpaceDestination(public, room, Kind.ROOM)
                    assertEquals(roomLink, browser.openDestination(roomLink)?.address)
                    assertFalse(browser.community(space, room).joined)
                    assertTrue(owner.voiceRoom(space, room).participants.isEmpty())

                    browser.saveDraft(space, "Existing independent draft", chat)
                    val target = SpaceDestination(public, chat, Kind.MESSAGE, reply, parent)
                    val opened = requireNotNull(browser.openDestination(target))
                    assertEquals(parent, opened.parent?.id)
                    assertEquals(reply, opened.record?.id)
                    assertEquals("The exact reply", opened.record?.text)
                    assertEquals("Existing independent draft", browser.draft(space, chat))
                    assertNull(browser.openDestination(SpaceDestination(public, chat, Kind.MESSAGE, reply, other)))
                    assertTrue(runCatching { browser.openDestination(SpaceDestination(public, chat, Kind.ROOM)) }
                        .exceptionOrNull() is SpaceClient.ReadDenied)
                    assertEquals("Another subject", browser.openDestination(SpaceDestination(public, chat, Kind.THREAD, other))?.parent?.text)
                    assertTrue(runCatching { browser.openDestination(target.copy(community = public.copy(node = "https://other.example.test"))) }
                        .exceptionOrNull() is SpaceClient.DifferentNode)

                    val hidden = owner.create(SpaceProfile("Private Space"), listOf(SpaceChannelProfile("Hidden", SpaceChannelKind.CHAT)))
                    owner.flush(hidden)
                    val hiddenChannel = owner.channels(hidden).single().id
                    val privateLink = SpaceDestination(public.copy(spaceId = hidden), hiddenChannel, Kind.CHANNEL)
                    assertTrue(runCatching { browser.openDestination(privateLink) }.exceptionOrNull() is SpaceClient.ReadDenied)
                    assertNull(browser.cachedDestination(privateLink))
                }
            }
        }
    }
}
