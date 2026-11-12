package nl.davidengelen.redsquare

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import nl.davidengelen.redsquare.fileshare.*
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

abstract class FileshareFixture {
    @get:Rule val ui = createEmptyComposeRule()

    protected fun transfer(incoming: Boolean, cancel: Boolean = false, unknown: Boolean = false,
        denyNotifications: Boolean = false, preparationCancel: Boolean = false, viewportWidth: Int? = null, large: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" && android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val author = SaitoWallet.fromPrivateKey("0".repeat(63) + "1")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "2").withChatContact(author.publicKey)
        val wallets = AndroidWalletStore(context)
        val previous = wallets.loadOrCreate()
        val backends = AndroidBackendStore(context)
        val previousBackend = backends.load()
        val files = ChatFixtureState(context, listOf("fileshare.", "personal.${wallet.publicKey}.",
            "chat.owner.${wallet.publicKey}.", "chatwork.owner.${wallet.publicKey}.", "chatreactions.owner.${wallet.publicKey}."), wallet.publicKey)
        val device = UiDevice.getInstance(instrumentation)
        val permitted = android.os.Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val evidence = File(context.cacheDir, "fileshare-evidence").apply { mkdirs() }
        val browserRef = AtomicReference<FileshareBrowserPeer>()
        val fixture = ControlledFeedPeer("[]", advertisedServices = "archive||;relay||", archiveResponse = { "[]" },
            relayResponse = { browserRef.get().relay(it) })
        if (large && android.os.Build.VERSION.SDK_INT == 29) {
            val declared = context.getSystemService(android.app.ActivityManager::class.java).memoryClass.toLong() * 1024 * 1024
            val actual = Runtime.getRuntime().maxMemory()
            File(evidence, "large-heap-precondition.json").writeText("{\"declaredBytes\":$declared,\"actualBytes\":$actual}")
            assertEquals("Require actual VM heap parity with Android's declared memory class", declared, actual)
        }
        val bytes = ByteArray(if (large) 4_194_317 else 150_007) { ((it * 13 + 7) % 251).toByte() }
        val sourceName = if (preparationCancel) "unknown-stalled-source.bin" else if (unknown) "unknown-source.bin" else "source.bin"
        val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$sourceName")
        val destination = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/saved.bin")
        val draft = "Keep this unsent message"
        val delayedChatReturn = incoming && !cancel && !unknown && !denyNotifications && !preparationCancel && viewportWidth == null && !large
        var session: FileSession? = null
        var restoreLegacyNotifications: (() -> Unit)? = null
        var primary: Throwable? = null
        fun waitUntil(goal: () -> Boolean) = ui.waitUntil(30_000) {
            browserRef.get()?.errors?.firstOrNull()?.let { throw AssertionError("Browser peer: $it") }
            goal()
        }
        try {
            files.prepare(); backends.save(SaitoBackend.OWNER); wallets.replace(wallets.loadOrCreate(), wallet)
            if (!permitted && !denyNotifications) device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            if (!incoming) context.contentResolver.call(source, "prepare", sourceName, Bundle().apply { putByteArray("bytes", bytes) })
            if (unknown) context.contentResolver.query(source, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)!!.use {
                check(it.moveToFirst() && it.isNull(0))
            }
            context.contentResolver.call(destination, "prepare", "saved.bin", Bundle().apply { putByteArray("bytes", byteArrayOf()) })
            Intents.init()
            Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(source).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(android.app.Instrumentation.ActivityResult(Activity.RESULT_OK,
                Intent().setData(destination).addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)))
            fixture.start().use { server ->
                val endpoint = "ws://127.0.0.1:${server.port}/wsopen"
                val pin = if (incoming) "9a4ca7e9442eb5e872168ced361ce741d7d139d7" else "e6c937c686495a37d8fc94ecc894189e23a453c3"
                FileshareBrowserPeer(wallet, fixture, pin).use { browser ->
                    browserRef.set(browser)
                    ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)
                        .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)
                        .putExtra("chatDraftLoadFixtureDelayMs", if (delayedChatReturn) 3000L else 0L)).use { activity ->
                        withFailureCapture({
                            File(evidence, "failure-state-${incoming}-${cancel}.json").writeText(com.google.gson.JsonObject().apply {
                                addProperty("liveWork", FileshareRuntime.hasLiveWork())
                                addProperty("screenOn", device.isScreenOn)
                                addProperty("browserSignedChunks", browser.signedChunks.get())
                                addProperty("browserAcknowledgedChunk", browser.acknowledgedChunk.get())
                                addProperty("browserReadFinished", browser.uploadReadFinished.get())
                                addProperty("browserErrors", browser.errors.size)
                                addProperty("authenticatedSockets", fixture.authenticatedConnectionIds.size)
                                addProperty("incomingAcknowledgements", fixture.incomingAcknowledgements.size)
                                addProperty("relayedMessages", fixture.relayedMessages.size)
                                add("transfers", com.google.gson.JsonArray().apply { session?.transfers?.value?.forEach { value ->
                                    add(com.google.gson.JsonObject().apply {
                                        addProperty("phase", value.phase.name); addProperty("bytes", value.bytes); addProperty("size", value.offer.size)
                                    })
                                } })
                            }.toString())
                            captureNativeScreen(File(evidence, "failure-${incoming}-${cancel}.png"))
                        }) {
                            waitUntil { ui.onAllNodesWithText("Chat").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNodeWithText("Chat").performClick()
                            waitUntil { ui.onAllNodesWithText(author.publicKey).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNodeWithText(author.publicKey).performClick()
                            waitUntil { ui.onAllNodes(hasText("Message") and hasSetTextAction() and isEnabled())
                                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNodeWithText("Message").performClick().performTextInput(draft)
                            if (focusedWindowImeBottom() > 0) device.pressBack()
                            session = FileshareRuntime.session(context, wallet, SaitoBackend.OWNER, endpoint, author.publicKey)
                            waitUntil { session!!.ready.value }
                            if (denyNotifications) {
                                if (android.os.Build.VERSION.SDK_INT < 33) {
                                    val manager = context.getSystemService(NotificationManager::class.java)
                                    val original = manager.areNotificationsEnabled()
                                    fun setNotifications(enabled: Boolean, returnToChat: Boolean) {
                                        val settings = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        if (returnToChat) activity.onActivity { current -> current.startActivity(settings) }
                                        else context.startActivity(settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        val by = androidx.test.uiautomator.By.pkg("com.android.settings").clickable(true)
                                            .hasDescendant(androidx.test.uiautomator.By.res("com.android.settings", "switch_text").text("Show notifications"))
                                        val toggle = requireNotNull(device.wait(androidx.test.uiautomator.Until.findObject(by), 10_000))
                                        if (manager.areNotificationsEnabled() != enabled) toggle.click()
                                        val deadline = android.os.SystemClock.uptimeMillis() + 5000
                                        while (manager.areNotificationsEnabled() != enabled && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(25)
                                        assertEquals(enabled, manager.areNotificationsEnabled())
                                        if (returnToChat) {
                                            device.pressBack()
                                            waitUntil { device.currentPackageName == context.packageName }
                                        } else device.pressHome()
                                    }
                                    restoreLegacyNotifications = { setNotifications(original, false) }
                                    setNotifications(false, true)
                                }
                                check(!FileshareNotifications.available(context))
                                ui.onNodeWithContentDescription("Add media").performClick()
                                ui.onNodeWithText("Send a file").performClick()
                                val explanation = context.getString(nl.davidengelen.redsquare.R.string.fileshare_notifications_required)
                                waitUntil {
                                    val deny = device.findObject(androidx.test.uiautomator.By.res("com.google.android.permissioncontroller:id/permission_deny_button"))
                                        ?: device.findObject(androidx.test.uiautomator.By.res("com.android.permissioncontroller:id/permission_deny_button"))
                                    deny?.click()
                                    ui.onAllNodesWithText(explanation).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                                }
                                assertTrue(session!!.transfers.value.isEmpty())
                                assertTrue(FileshareRuntime.preparations.value.none { it.phase == PreparationPhase.PREPARING })
                                assertTrue(fixture.relayedMessages.isEmpty() && fixture.transactions.isEmpty())
                                ui.onNodeWithText(draft).assertExists()
                                captureNativeScreen(File(evidence, "notifications-disabled.png"), explanation)
                                File(evidence, "notifications-disabled.json").writeText("{\"result\":\"PASS\",\"actualAppOpDenied\":true,\"noTransferStarted\":true,\"draftRetained\":true}")
                                return@withFailureCapture
                            }
                            // A ready composer/session is local state, not a completed Relay handshake.
                            waitUntil { fixture.authenticatedConnectionIds.isNotEmpty() }
                            browser.hold(receipts = !incoming, chunks = incoming)
                            if (incoming) {
                                if (large) browser.sendGenerated(bytes.size, "from-browser.bin", "browser103")
                                else browser.send(bytes, "from-browser.bin", "browser103")
                                waitUntil { ui.onAllNodesWithText("Accept file").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                                assertTrue(session!!.transfers.value.single().phase == FilePhase.OFFERED)
                                assertEquals(0L, session!!.transfers.value.single().bytes)
                                ui.onNodeWithText("Accept file").performScrollTo()
                                captureNativeScreen(File(evidence, "incoming-accept.png"), "Accept file")
                                ui.onNodeWithText("Accept file").performClick()
                                waitUntil { session!!.transfers.value.any { it.phase == FilePhase.RECEIVING && it.bytes > 0 } }
                                session!!.accept(session!!.transfers.value.single().token)
                                // A late duplicate Accept must leave the already receiving transfer intact.
                            } else {
                                ui.onNodeWithContentDescription("Add media").performClick()
                                ui.onNodeWithText("Send a file").performClick()
                                if (preparationCancel) {
                                    waitUntil {
                                        context.contentResolver.call(source, "status", null, null)?.getBoolean("firstWritten") == true &&
                                            session!!.preparation.value?.phase == PreparationPhase.PREPARING
                                    }
                                    ui.onNodeWithText("Preparing file…").performScrollTo().assertIsDisplayed()
                                    captureNativeScreen(File(evidence, "preparation-active.png"), "Preparing file…")
                                    ui.onNodeWithText("Cancel").performScrollTo().performClick()
                                    waitUntil { session!!.preparation.value?.phase == PreparationPhase.CANCELLED }
                                    context.contentResolver.call(source, "release", null, null)
                                    waitUntil { !FileshareRuntime.hasLiveWork() }
                                    waitUntil {
                                        context.getSystemService(android.app.ActivityManager::class.java).getRunningServices(100)
                                            .none { it.service.className == FileTransferService::class.java.name }
                                    }
                                    assertTrue(fixture.relayedMessages.isEmpty() && fixture.transactions.isEmpty())
                                    assertTrue(session!!.transfers.value.isEmpty())
                                    ui.onNodeWithText(draft).assertExists()
                                    check(context.noBackupFilesDir.listFiles().orEmpty().none {
                                        it.name.startsWith("fileshare.") && (it.name.endsWith(".part") || it.name.endsWith(".data"))
                                    })
                                    captureNativeScreen(File(evidence, "preparation-cancelled.png"), "Transfer cancelled")
                                    File(evidence, "preparation-cancelled.json").writeText("{\"result\":\"PASS\",\"controlledPipeBlocked\":true,\"terminalCancelled\":true,\"serviceStopped\":true,\"stagingRemoved\":true,\"draftRetained\":true}")
                                    return@withFailureCapture
                                }
                                waitUntil { browser.chunks.get() > 0 && session!!.transfers.value.any { it.phase == FilePhase.SENDING } }
                            }
                            val token = session!!.transfers.value.first().token
                            if (!incoming) session!!.retry(token) // A stale conflicting Retry must not fail live work.
                            val duringTransfer = PrivateChat.create(author, wallet.publicKey, "Chat still receives during a file transfer", System.currentTimeMillis())
                            fixture.sendIncoming(duringTransfer.applicationRequest(1).drop(5).toByteArray(), 3103)
                            waitUntil { ui.onAllNodesWithText("Chat still receives during a file transfer").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            val notifications = context.getSystemService(NotificationManager::class.java)
                            waitUntil { notifications.activeNotifications.any { it.id == FileTransferService.NOTIFICATION_ID } }
                            captureNativeScreen(File(evidence, "active-${incoming}-${cancel}.png"))
                            device.pressHome(); device.sleep()
                            assertFalse(device.isScreenOn)
                            if (cancel) {
                                device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
                                device.openNotification()
                                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                                val cancelAction = androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("Cancel", java.util.regex.Pattern.CASE_INSENSITIVE))
                                while (!device.hasObject(cancelAction) && android.os.SystemClock.uptimeMillis() < deadline) {
                                    // Android can collapse this transfer inside the app's automatic
                                    // notification group. Expand its actual row/group, not a system alert.
                                    var row = device.findObject(androidx.test.uiautomator.By.text("Sending file"))
                                    var expanded = false
                                    while (row != null) {
                                        val expand = row.findObject(androidx.test.uiautomator.By.res("android:id/expand_button").desc("Expand"))
                                        if (expand != null) { expand.click(); expanded = true; break }
                                        row = row.parent
                                    }
                                    // Its title can be visible while its expanded action is below the fold.
                                    if (!expanded) device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                                        device.displayWidth / 2, device.displayHeight / 3, 12)
                                    Thread.sleep(100)
                                }
                                check(device.hasObject(cancelAction))
                                device.findObject(cancelAction).click()
                                waitUntil { session!!.transfers.value.any { it.token == token && it.phase == FilePhase.CANCELLED } }
                                assertNull(browser.received.get())
                            } else {
                                browser.release()
                                waitUntil { session!!.transfers.value.any { it.token == token && it.phase == FilePhase.COMPLETE } }
                                if (!incoming) assertArrayEquals(bytes, browser.received.get())
                                assertFalse(device.isScreenOn) // Completion happened with no visible Activity or screen.
                                device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
                            }
                            val alert = notifications.activeNotifications.single { it.id == FileTransferService.NOTIFICATION_ID }
                            alert.notification.contentIntent.send() // Actual retained destination URI and Android PendingIntent.
                            waitUntil { ui.onAllNodesWithText(draft).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            val label = if (cancel) "Transfer cancelled" else if (incoming) "File received" else "File sent"
                            waitUntil { ui.onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            if (delayedChatReturn) {
                                activity.recreate()
                                ui.waitUntil(15_000) {
                                    ui.onAllNodes(hasText("Message") and !isEnabled())
                                        .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                                }
                                alert.notification.contentIntent.send()
                                waitUntil { ui.onAllNodesWithText(draft).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                                ui.onAllNodesWithText(context.getString(nl.davidengelen.redsquare.R.string.social_push_open_failed)).assertCountEquals(0)
                            }
                            captureNativeScreen(File(evidence, "complete-${incoming}-${cancel}.png"), label)
                            if (incoming && !cancel) {
                                ui.onNodeWithText("Save as…").performScrollTo().performClick()
                                waitUntil { ui.onAllNodesWithText("File saved").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                                assertArrayEquals(bytes, context.contentResolver.openInputStream(destination)!!.use { it.readBytes() })
                                ui.onNodeWithText(draft).assertExists()
                                captureNativeScreen(File(evidence, "saved.png"), "File saved")
                                viewportWidth?.let { width ->
                                    val folder = File(evidence, "viewport-$width").apply { mkdirs() }
                                    fun returnHome() {
                                        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
                                        fun remaining() = (deadline - android.os.SystemClock.uptimeMillis()).coerceAtLeast(1)
                                        ui.onNodeWithText("Back").performClick()
                                        ui.waitUntil(remaining()) {
                                            ui.onAllNodesWithText("New chat").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                                        }
                                        ui.onNodeWithText("Home").performClick()
                                        ui.waitUntil(remaining()) { ui.onNode(hasText("Home") and isSelected()).isDisplayed() }
                                    }
                                    returnHome()
                                    ui.withPostViewports(activity, folder, "fileshare", widthDp = width) { cell, _ ->
                                        ui.onNodeWithText("Chat").performClick()
                                        waitUntil { ui.onAllNodesWithText(author.publicKey).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                                        ui.onNodeWithText(author.publicKey).performClick()
                                        waitUntil { ui.onAllNodesWithText("File saved").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                                        ui.waitUntil(15_000) {
                                            ui.onAllNodes(hasText("Save as…") and isEnabled()).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() &&
                                                ui.onAllNodesWithText(draft).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                                        }
                                        ui.onNodeWithText("Save as…").performScrollTo().assertIsDisplayed()
                                            .assertIsEnabled()
                                        // Material paints40dp inside its48dp native touch/accessibility
                                        // target. Measure the actual clickable parent, as other viewports do.
                                        var nativeSave = requireNotNull(device.findObject(androidx.test.uiautomator.By.text("Save as…")))
                                        while (!nativeSave.isClickable) nativeSave = requireNotNull(nativeSave.parent)
                                        val bounds = nativeSave.visibleBounds
                                        val density = context.resources.displayMetrics.density
                                        assertTrue(bounds.width() >= 47.5f * density && bounds.height() >= 47.5f * density)
                                        File(folder, "$cell-save-target.json").writeText(com.google.gson.JsonObject().apply {
                                            addProperty("bounds", bounds.flattenToString()); addProperty("density", density)
                                            addProperty("widthDp", bounds.width() / density); addProperty("heightDp", bounds.height() / density)
                                        }.toString())
                                        ui.onNodeWithText(draft).assertExists()
                                        captureNativeScreen(File(folder, "$cell-saved.png"), "File saved")
                                        returnHome()
                                    }
                                }
                            }
                            assertEquals(wallet.publicKey, wallets.loadExisting().publicKey)
                            assertTrue(fixture.transactions.isEmpty()) // No broadcast transaction carries a file.
                            fixture.assertHealthy()
                            File(evidence, "result-${incoming}-${cancel}-${unknown}${if (large) "-large" else ""}.json").writeText(com.google.gson.JsonObject().apply {
                                addProperty("result", "PASS"); addProperty("incoming", incoming); addProperty("cancel", cancel)
                                addProperty("sourcePin", pin); addProperty("bytes", bytes.size)
                                addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                                addProperty("actualWebRtc", true); addProperty("screenOffObserved", true)
                                addProperty("unknownSizeProvider", unknown)
                                if (large) {
                                    addProperty("browserChunksPrepared", browser.signedChunks.get())
                                    addProperty("browserLastReceipt", browser.acknowledgedChunk.get())
                                    addProperty("browserReadFinished", browser.uploadReadFinished.get())
                                }
                                addProperty("browserAdapterWallet", "Native public unsafe fixture; independent wallet vectors verified separately")
                            }.toString())
                        }
                    }
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            session?.interrupt()
            Thread.sleep(300)
            device.wakeUp(); device.executeShellCommand("wm dismiss-keyguard")
            cleanupAfterTest(primary,
                { Intents.release() },
                { restoreLegacyNotifications?.invoke() },
                { if (preparationCancel) context.contentResolver.call(source, "release", null, null) },
                { context.getSystemService(NotificationManager::class.java).cancel(FileTransferService.NOTIFICATION_ID) },
                { wallets.replace(wallets.loadOrCreate(), previous) }, { backends.save(previousBackend) },
                { files.restoreFiles() }, { files.restorePreferences() })
        }
    }
}

class FileshareSendPreviewTest : FileshareFixture() {
    @Test fun aNativeFileReachesThePinnedBrowserWhileTheActivityIsHiddenAndTheScreenIsOff() = transfer(incoming = false)
}
class FileshareReceivePreviewTest : FileshareFixture() {
    @Test fun anAcceptedBrowserFileCompletesWithScreenOffAndSavesTheExactBytes() = transfer(incoming = true)
}
class FileshareCancelPreviewTest : FileshareFixture() {
    @Test fun theActualNotificationCancelStopsTheSpecificTransferWithoutClaimingCompletion() = transfer(incoming = false, cancel = true)
}
class FileshareUnknownSizePreviewTest : FileshareFixture() {
    @Test fun anUnknownLengthDocumentIsPreparedAndDeliversTheExactBytes() = transfer(incoming = false, unknown = true)
}
class FileshareNotificationPreviewTest : FileshareFixture() {
    @Test fun disabledNotificationsExplainTheNeededControlsWithoutStartingATransferOrLosingTheDraft() =
        transfer(incoming = false, denyNotifications = true)
}
class FilesharePreparationCancelPreviewTest : FileshareFixture() {
    @Test fun cancellationDuringStreamingPreparationCannotRestartWorkAndStopsTheForegroundService() =
        transfer(incoming = false, preparationCancel = true)
}
class FileshareNarrowViewportTest : FileshareFixture() {
    @Test fun receivedFilesAndUnsentDraftsRemainUsableAcrossThemesAndLargeTextAt320dp() =
        transfer(incoming = true, viewportWidth = 320)
}
class FilesharePixelViewportTest : FileshareFixture() {
    @Test fun receivedFilesAndUnsentDraftsRemainUsableAcrossThemesAndLargeTextAt412dp() =
        transfer(incoming = true, viewportWidth = 412)
}
class FileshareLargeReceivePreviewTest : FileshareFixture() {
    @Test fun aLargerUnpacedBrowserFileCompletesWithScreenOffAndSavesEveryByte() =
        transfer(incoming = true, large = true)
}
