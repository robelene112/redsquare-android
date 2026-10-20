package nl.davidengelen.redsquare

import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import nl.davidengelen.redsquare.chat.ChatHistoryStore
import nl.davidengelen.redsquare.chat.ChatAlertSettings
import nl.davidengelen.redsquare.chat.receiveChat
import nl.davidengelen.redsquare.moderation.AndroidPersonalControls
import nl.davidengelen.redsquare.notifications.ChatPushSettings
import nl.davidengelen.redsquare.notifications.SocialPushWakeScheduling
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual worker, recipient-proof HTTP, encrypted history and Android shade; opaque wake injection only. */
abstract class PrivatePushWorkFixture {
    protected enum class Journey { WORKER_FIRST, ORDINARY_FIRST, RECEIPT_RECOVERY, BLOCKED_PENDING, SCOPED_PREVIEW, MUTED_PENDING }

    protected fun privateJourney(journey: Journey = Journey.WORKER_FIRST) {
        val scoped = journey == Journey.SCOPED_PREVIEW
        val mutedPending = journey == Journey.MUTED_PENDING
        val backend = if (scoped) SaitoBackend.OFFICIAL else SaitoBackend.OWNER
        val ordinaryFirst = journey != Journey.WORKER_FIRST && !scoped
        val ledgerUnavailable = journey == Journey.RECEIPT_RECOVERY || journey == Journey.BLOCKED_PENDING
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        check(!BuildConfig.PUSH_CONFIGURED)
        fun activities(): List<MainActivity> {
            var result = emptyList<MainActivity>()
            instrumentation.runOnMainSync {
                val monitor = ActivityLifecycleMonitorRegistry.getInstance()
                result = Stage.values().filter { it != Stage.DESTROYED }
                    .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>().distinct()
            }
            return result
        }
        fun assertNoActivity() = assertTrue(activities().isEmpty())
        fun finishActivities() {
            val active = activities()
            instrumentation.runOnMainSync { active.forEach { it.finish() } }
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (activities().isNotEmpty() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
            assertNoActivity()
        }
        assertNoActivity()
        // Published unsafe fixture identities; fresh transport fixture, not independent encryption interoperability.
        val sender = SaitoWallet.fromPrivateKey("0".repeat(63) + "2")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "1").let {
            if (scoped) it.withChatContact(sender.publicKey) else it
        }
        val text = "Keep this private text on the receiving device"
        val original = PrivateChat.create(sender, wallet.publicKey, text, System.currentTimeMillis())
        val opaque = (if (mutedPending) "79" else if (scoped) "51" else if (journey == Journey.BLOCKED_PENDING) "50" else if (ledgerUnavailable) "48" else if (ordinaryFirst) "47" else "46").repeat(32)
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val previousJson = previous.exportJson()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val retained = ChatFixtureState(context, listOf("chatpush.${wallet.publicKey}.", "socialpush.${wallet.publicKey}.",
            "chat.${backend.id}.${wallet.publicKey}.", "chatwork.${backend.id}.${wallet.publicKey}.",
            "chatreactions.${backend.id}.${wallet.publicKey}.", "groupchat.${backend.id}.${wallet.publicKey}.", "chat-alerts.${wallet.publicKey}.", "personal.${wallet.publicKey}."), wallet.publicKey)
        val failure = AtomicReference<Throwable?>()
        val resolves = AtomicInteger()
        val originals = AtomicInteger()
        val gateway = mockwebserver3.MockWebServer().apply {
            dispatcher = object : mockwebserver3.Dispatcher() {
                override fun dispatch(request: mockwebserver3.RecordedRequest): mockwebserver3.MockResponse = try {
                    require(request.method == "POST" && request.headers["X-Saito-Owner"] == wallet.publicKey)
                    val body = JsonParser.parseString(requireNotNull(request.body).utf8()).asJsonObject
                    require(body == JsonParser.parseString("{\"version\":1,\"wake\":\"$opaque\"}"))
                    val operation = when (request.url.encodedPath) {
                        "/v1/wakes/resolve" -> "resolve"
                        "/v1/wakes/original" -> "original"
                        else -> error("Unexpected owned private wake route")
                    }
                    require(SaitoWallet.verify("Saito push $operation v1\n${request.url}\n$opaque".toByteArray(),
                        requireNotNull(request.headers["X-Saito-Signature"]), wallet.publicKey))
                    mockwebserver3.MockResponse.Builder().body(JsonObject().apply {
                        addProperty("version", 1)
                        if (operation == "resolve") {
                            resolves.incrementAndGet()
                            addProperty("wallet", wallet.publicKey); addProperty("backend", backend.id)
                            addProperty("signature", original.id); addProperty("kind", "private-chat")
                        } else {
                            originals.incrementAndGet(); addProperty("envelope", original.envelope())
                        }
                    }.toString()).build()
                } catch (caught: Throwable) {
                    failure.set(caught); mockwebserver3.MockResponse.Builder().code(500).build()
                }
            }
        }
        val work = WorkManager.getInstance(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val tag = "chat:${wallet.publicKey}:${backend.id}:${original.id}"
        check(manager.activeNotifications.none { it.tag == tag })
        val device = UiDevice.getInstance(instrumentation)
        val capturePrefix = if (mutedPending) "muted-pending" else if (scoped) "scoped-preview" else if (journey == Journey.BLOCKED_PENDING) "blocked-pending" else if (ledgerUnavailable) "receipt-storage" else if (ordinaryFirst) "ordinary-first" else "worker"
        fun capture(name: String) = captureNativeScreen(File(context.cacheDir, "chat-alert-evidence/private-$capturePrefix-$name.png"))
        fun assertSilent(label: String) {
            val silentUntil = SystemClock.elapsedRealtime() + 2_000
            do {
                assertTrue(label, manager.activeNotifications.none { it.tag == tag })
                SystemClock.sleep(50)
            } while (SystemClock.elapsedRealtime() < silentUntil)
        }
        fun await(id: UUID) {
            val deadline = SystemClock.elapsedRealtime() + 40_000
            while (SystemClock.elapsedRealtime() < deadline) {
                failure.get()?.let { throw AssertionError("Owned private gateway rejected receipt", it) }
                val info = work.getWorkInfoById(id).get(5, TimeUnit.SECONDS)
                if (info?.state?.isFinished == true) {
                    assertEquals("Private receipt must complete successfully", WorkInfo.State.SUCCEEDED, info.state)
                    return
                }
                SystemClock.sleep(50)
            }
            fail("Private receipt did not complete within40seconds")
        }
        var primary: Throwable? = null
        var stopped = false
        var obstructionCleanup: (() -> Unit)? = null
        val peer = if (scoped) ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" }) else null
        var peerServer: mockwebserver3.MockWebServer? = null
        fun requireStopped() { check(stopped); assertNoActivity() }
        try {
            retained.prepare()
            wallets.replace(previous, wallet); backends.save(backend)
            ChatPushSettings(context, wallet.publicKey).setEnabled(true)
            if (journey == Journey.WORKER_FIRST) {
                // Independent private delivery must survive an unfinished protected group record.
                val group = SaitoWallet.fromPrivateKey("0".repeat(63) + "9").publicKey
                check(File(context.noBackupFilesDir, "groupchat.${backend.id}.${wallet.publicKey}.$group.v1.new").createNewFile())
            }
            peerServer = peer?.start()
            gateway.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            withFailureCapture({ capture("failure") }) {
                fun schedule() = runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.enqueue(context, opaque,
                    "http://127.0.0.1:${gateway.port}/v1/wakes/resolve",
                    peerServer?.let { "ws://127.0.0.1:${it.port}/wsopen" }) }
                if (scoped) {
                    val obstruction = File(context.noBackupFilesDir, "chatpush.${wallet.publicKey}.${backend.id}.ledger.v1.new")
                    check(obstruction.mkdir())
                    var receiptFailure: Throwable? = null
                    val first: UUID
                    try {
                        first = schedule()
                        val deadline = SystemClock.elapsedRealtime() + 40_000
                        var pending = false
                        while (!pending && SystemClock.elapsedRealtime() < deadline) {
                            failure.get()?.let { throw AssertionError("Owned private gateway rejected receipt", it) }
                            val info = work.getWorkInfoById(first).get(5, TimeUnit.SECONDS)
                            assertFalse("The obstructed receipt must remain retryable", info?.state?.isFinished == true)
                            pending = info?.state == WorkInfo.State.ENQUEUED && info.runAttemptCount >= 1
                            if (!pending) SystemClock.sleep(50)
                        }
                        assertTrue("The actual worker must retain a pending receipt", pending)
                        assertEquals(text, ChatHistoryStore(context, wallet, backend, sender.publicKey).load().entries.single().message.text)
                        assertSilent("A retained pending receipt must not publish before bookkeeping recovers")
                        assertNoActivity()
                        backends.save(SaitoBackend.OWNER)
                    } catch (caught: Throwable) { receiptFailure = caught; throw caught }
                    finally { cleanupAfterTest(receiptFailure, { check(obstruction.delete()) }) }
                    await(first)
                    assertSilent("The Official receipt must not alert on the Owner backend")
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                    backends.save(backend)
                    val replacement = SaitoWallet.fromPrivateKey("0".repeat(63) + "3")
                    wallets.replace(wallet, replacement)
                    await(schedule())
                    assertSilent("An inactive wallet's receipt must not alert under its replacement")
                    assertEquals(replacement.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(backend, backends.load())
                    assertEquals(1, resolves.get()); assertEquals(1, originals.get())
                    assertEquals(text, ChatHistoryStore(context, wallet, backend, sender.publicKey).load().entries.single().message.text)
                    assertNoActivity()
                    capture("inactive-scopes-silent")
                    wallets.replace(replacement, wallet)
                    ChatAlertSettings(context, wallet.publicKey).setPreviewsEnabled(true)
                    await(schedule())
                    val publishedBy = SystemClock.elapsedRealtime() + 5_000
                    while (manager.activeNotifications.none { it.tag == tag } && SystemClock.elapsedRealtime() < publishedBy)
                        SystemClock.sleep(25)
                    val preview = manager.activeNotifications.single { it.tag == tag }
                    assertEquals(1, resolves.get()); assertEquals(1, originals.get())
                    assertEquals("private-chats", preview.notification.channelId)
                    assertEquals(text, preview.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                    assertNoActivity()
                    assertTrue(device.openNotification())
                    val row = requireNotNull(device.wait(Until.findObject(By.text(text)), 10_000))
                    capture("preview-alert")
                    row.click()
                    assertTrue("Tapping the actual card must show the cached message in the app",
                        device.wait(Until.hasObject(By.pkg(context.packageName).text(text)), 20_000))
                    assertTrue("The destination must be the exact sender's conversation",
                        device.wait(Until.hasObject(By.pkg(context.packageName).text(sender.publicKey)), 20_000))
                    assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(backend, backends.load())
                    requireNotNull(peer).assertHealthy()
                    assertTrue(peer.transactions.isEmpty() && peer.reports.isEmpty() && peer.relayedMessages.isEmpty())
                    capture("exact-conversation")
                    finishActivities()
                    val dismissedBy = SystemClock.elapsedRealtime() + 5_000
                    while (manager.activeNotifications.any { it.tag == tag } && SystemClock.elapsedRealtime() < dismissedBy) SystemClock.sleep(25)
                    await(schedule())
                    assertSilent("The opened private receipt must not alert again")
                    assertEquals(1, ChatHistoryStore(context, wallet, backend, sender.publicKey).load().entries.size)
                    assertNoActivity()
                    return@withFailureCapture
                }
                if (mutedPending) {
                    val history = ChatHistoryStore(context, wallet, backend, sender.publicKey)
                    val conversation = PrivateChat.conversationId(wallet.publicKey, sender.publicKey)
                    val controls = AndroidPersonalControls(context, wallet.publicKey)
                    val obstruction = File(context.noBackupFilesDir, "chatpush.${wallet.publicKey}.${backend.id}.ledger.v1.new")
                    check(obstruction.mkdir())
                    obstructionCleanup = {
                        if (obstruction.exists()) {
                            check(obstruction.isDirectory && obstruction.listFiles()!!.isEmpty())
                            check(obstruction.delete())
                        }
                    }
                    var receiptFailure: Throwable? = null
                    var drained = false
                    try {
                        receiveChat(context, wallet, backend, original.applicationRequest(1).drop(5).toByteArray())
                        val saved = history.load().entries.single().message
                        assertEquals(original.id, saved.id); assertEquals(text, saved.text)
                        assertSilent("Unavailable bookkeeping must retain readable private history without publishing")
                        val before = controls.load()
                        controls.save(before, before.copy(mutedConversations = before.mutedConversations + conversation))
                        assertTrue(conversation in controls.load().mutedConversations)
                        await(schedule())
                        drained = true
                        assertSilent("A muted private receipt must finish while optional bookkeeping remains unavailable")
                        assertEquals(1, resolves.get()); assertEquals(1, originals.get())
                        assertNoActivity()
                        capture("muted-completed")
                    } catch (caught: Throwable) { receiptFailure = caught; throw caught }
                    finally {
                        cleanupAfterTest(receiptFailure,
                            { if (!drained) { runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.cancelAndAwait(context, opaque) }; drained = true } },
                            { check(drained); obstructionCleanup?.invoke(); obstructionCleanup = null })
                    }
                    val muted = controls.load()
                    controls.save(muted, muted.copy(mutedConversations = muted.mutedConversations - conversation))
                    assertFalse(conversation in controls.load().mutedConversations)
                    await(schedule())
                    assertSilent("Unmuting after bookkeeping repair must not revive an old private alert")
                    val saved = history.load().entries
                    assertEquals(1, saved.size); assertEquals(original.id, saved.single().message.id)
                    assertEquals(sender.publicKey, saved.single().message.author); assertEquals(text, saved.single().message.text)
                    assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(backend, backends.load())
                    assertNoActivity()
                    capture("unmuted-still-silent")
                    return@withFailureCapture
                }
                if (ordinaryFirst) {
                    val obstruction = File(context.noBackupFilesDir, "chatpush.${wallet.publicKey}.owner.ledger.v1.new")
                    if (ledgerUnavailable) check(obstruction.mkdir())
                    var receiptFailure: Throwable? = null
                    try {
                        receiveChat(context, wallet, SaitoBackend.OWNER, original.applicationRequest(1).drop(5).toByteArray())
                        if (ledgerUnavailable) {
                            val retainedMessage = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey)
                                .load().entries.single { it.message.id == original.id }
                            assertEquals(text, retainedMessage.message.text)
                            assertSilent("Unavailable alert bookkeeping must keep ordinary Chat readable without publishing")
                            capture("retained-before-recovery")
                        }
                    } catch (caught: Throwable) { receiptFailure = caught; throw caught }
                    finally { if (ledgerUnavailable) cleanupAfterTest(receiptFailure, { check(obstruction.delete()) }) }
                }
                if (journey == Journey.BLOCKED_PENDING) {
                    val controls = AndroidPersonalControls(context, wallet.publicKey)
                    val before = controls.load()
                    controls.save(before, before.copy(blocked = before.blocked + sender.publicKey))
                    await(schedule())
                    assertSilent("A pending private message must stay silent while its sender is blocked")
                    val blocked = controls.load()
                    controls.save(blocked, blocked.copy(blocked = blocked.blocked - sender.publicKey))
                    await(schedule())
                    assertSilent("Unblocking must not revive an old suppressed private notification")
                    val saved = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey).load().entries
                    assertEquals(1, saved.size)
                    assertEquals(text, saved.single().message.text)
                    assertTrue(resolves.get() > 0 && originals.get() > 0)
                    assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                    assertEquals(SaitoBackend.OWNER, backends.load())
                    assertNoActivity()
                    capture("unblocked-still-silent")
                    return@withFailureCapture
                }
                if (!ordinaryFirst || ledgerUnavailable) await(schedule())
                assertNoActivity()
                val publishedBy = SystemClock.elapsedRealtime() + 5_000
                while (manager.activeNotifications.none { it.tag == tag } && SystemClock.elapsedRealtime() < publishedBy)
                    SystemClock.sleep(25)
                val alert = manager.activeNotifications.singleOrNull { it.tag == tag }
                assertNotNull("Chat-only consent must resolve, decrypt, retain and display its private alert", alert)
                if (!ordinaryFirst || ledgerUnavailable) assertTrue(resolves.get() > 0 && originals.get() > 0)
                assertEquals("private-chats", alert!!.notification.channelId)
                assertEquals("New message", alert.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                val saved = ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey).load().entries
                    .single { it.message.id == original.id }
                assertEquals(sender.publicKey, saved.message.author); assertEquals(text, saved.message.text)
                assertTrue(device.openNotification())
                assertNotNull(device.wait(Until.findObject(By.text("New message")), 10_000))
                capture("generic-alert")
                val card = visibleNotificationCard(device, "New message", 5_000)
                val dismissal = JsonObject().apply {
                    addProperty("clearable", alert.isClearable)
                    addProperty("ongoing", alert.isOngoing)
                    addProperty("flags", alert.notification.flags)
                    addProperty("cardBounds", runCatching { card.visibleBounds.toShortString() }.getOrNull())
                    addProperty("displayWidth", device.displayWidth)
                    addProperty("displayHeight", device.displayHeight)
                }
                fun recordDismissal() {
                    runCatching {
                        File(context.cacheDir, "chat-alert-evidence/private-$capturePrefix-dismissal.json")
                            .writeText(dismissal.toString())
                    }
                }
                recordDismissal()
                val gestureStarted = SystemClock.elapsedRealtime()
                card.swipe(Direction.RIGHT, 1.0f)
                dismissal.addProperty("gestureMillis", SystemClock.elapsedRealtime() - gestureStarted)
                val hidden = device.wait(Until.gone(By.text("New message")), 5_000)
                dismissal.addProperty("textHidden", hidden)
                dismissal.addProperty("ownedNotificationStillActive",
                    runCatching { manager.activeNotifications.any { it.tag == tag } }.getOrNull())
                recordDismissal()
                assertTrue(hidden)
                val dismissedBy = SystemClock.elapsedRealtime() + 5_000
                while (manager.activeNotifications.any { it.tag == tag } && SystemClock.elapsedRealtime() < dismissedBy)
                    SystemClock.sleep(25)
                assertTrue("Android must observe the physical dismissal before redelivery", manager.activeNotifications.none { it.tag == tag })
                device.pressBack()
                await(schedule())
                assertSilent("Redelivery must not re-alert a dismissed private message")
                assertTrue(resolves.get() > 0 && originals.get() > 0)
                assertEquals(1, ChatHistoryStore(context, wallet, SaitoBackend.OWNER, sender.publicKey).load().entries.size)
                assertEquals(wallet.exportJson(), wallets.loadExisting().exportJson())
                assertEquals(SaitoBackend.OWNER, backends.load())
                assertNoActivity()
                capture("deduplicated")
            }
        } catch (caught: Throwable) { primary = caught; throw caught }
        finally {
            cleanupAfterTest(primary,
                { runBlocking(Dispatchers.IO) { SocialPushWakeScheduling.cancelAndAwait(context, opaque) }; finishActivities(); stopped = true },
                { gateway.close() }, { peerServer?.close() }, { manager.cancel(tag, 0) }, { device.pressBack() },
                { requireStopped(); obstructionCleanup?.invoke(); obstructionCleanup = null },
                { requireStopped(); retained.restoreFiles() }, { requireStopped(); retained.restorePreferences() },
                { requireStopped(); wallets.replace(wallets.loadExisting(), previous); check(wallets.loadExisting().exportJson() == previousJson) },
                { requireStopped(); backends.save(previousBackend); check(backends.load() == previousBackend) })
        }
    }
}
