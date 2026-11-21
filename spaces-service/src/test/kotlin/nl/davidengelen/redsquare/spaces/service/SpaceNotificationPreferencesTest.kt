package nl.davidengelen.redsquare.spaces.service

import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SpaceNotificationPreferencesTest {
    @get:Rule val files = TemporaryFolder()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)
    @Test fun channelPoliciesRemainLocalAndDurableWhilePersonalDefaultsExcludeOrdinaryActivity() = runBlocking<Unit> {
        val owner = SaitoWallet.fromPrivateKey("1".padStart(64, '0'))
        val service = SaitoWallet.fromPrivateKey("2".padStart(64, '0'))
        val cache = files.newFile("notification-policies.sqlite").absolutePath
        var calls = 0
        SpacesServiceStore(files.newFolder("notification-policies-service").toPath(), service, clock).use { remote ->
            var space = ""; var general = ""; var other = ""
            SqliteClientFixture(cache).use { local ->
                val client = SpaceClient(owner, "fixture-node", service.publicKey, local, clock) { calls++; remote.exchange(it.envelope()) }
                space = client.create(SpaceProfile("Scoped personal notifications", visibility = SpaceVisibility.PUBLIC)); client.flush(space); client.refresh(space)
                general = client.community(space).channel
                other = client.createChannel(space, SpaceChannelProfile("another channel")); client.flush(space); client.refresh(space)
                val defaults = client.notificationPolicy(space, general)
                assertEquals(SpaceNotificationMode.PERSONAL, defaults.mode)
                assertFalse(defaults.shouldNotify(ownMessage = false))
                assertTrue(defaults.shouldNotify(ownMessage = false, mentioned = true))
                assertTrue(defaults.shouldNotify(ownMessage = false, repliedToMe = true))
                assertFalse(defaults.shouldNotify(ownMessage = true, mentioned = true, repliedToMe = true, everyone = true))
                val boundary = calls
                client.setNotificationPolicy(space, SpaceNotificationPolicy(SpaceNotificationMode.ALL, allowEveryone = false), general)
                client.setNotificationPolicy(space, SpaceNotificationPolicy(SpaceNotificationMode.SILENT), other)
                assertEquals(boundary, calls)
                assertTrue(client.notificationPolicy(space, general).shouldNotify(ownMessage = false))
                val personalWithoutEveryone = SpaceNotificationPolicy(allowEveryone = false)
                assertFalse(personalWithoutEveryone.shouldNotify(ownMessage = false, everyone = true))
                assertTrue(personalWithoutEveryone.shouldNotify(ownMessage = false, mentioned = true, everyone = true))
                assertTrue(client.notificationPolicy(space, general).shouldNotify(ownMessage = false, everyone = true))
                assertFalse(client.notificationPolicy(space, other).shouldNotify(ownMessage = false, mentioned = true, repliedToMe = true, everyone = true))
            }
            val boundary = calls
            SqliteClientFixture(cache).use { reopened ->
                val offline = SpaceClient(owner, "fixture-node", service.publicKey, reopened, clock) { error("Local policy must not contact a peer") }
                assertEquals(SpaceNotificationPolicy(SpaceNotificationMode.ALL, false), offline.notificationPolicy(space, general))
                assertEquals(SpaceNotificationMode.SILENT, offline.notificationPolicy(space, other).mode)
                offline.selectChannel(space, other)
                assertEquals(SpaceNotificationMode.SILENT, offline.notificationPolicy(space).mode)
                assertEquals(SpaceNotificationMode.ALL, offline.notificationPolicy(space, general).mode)
            }
            assertEquals(boundary, calls)
        }
    }
}
