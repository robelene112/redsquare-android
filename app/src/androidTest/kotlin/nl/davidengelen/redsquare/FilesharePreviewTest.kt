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
        denyNotifications: Boolean = false, preparationCancel: Boolean = false) {
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
        val bytes = ByteArray(150_007) { ((it * 13 + 7) % 251).toByte() }
        val sourceName = if (preparationCancel) "unknown-stalled-source.bin" else if (unknown) "unknown-source.bin" else "source.bin"
        val source = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/$sourceName")
        val destination = Uri.parse("content://nl.davidengelen.redsquare.dev.test.fileshare/saved.bin")
        val draft = "Keep this unsent message"
        var session: FileSession? = null
        var primary: Throwable? = null
        fun waitUntil(goal: () -> Boolean) = ui.waitUntil(30_000) {
            browserRef.get()?.errors?.firstOrNull()?.let { throw AssertionError("Browser peer: $it") }
            goal()
        }
        try {
            files.prepare(); backends.save(SaitoBackend.OWNER); wallets.replace(wallets.loadOrCreate(), wallet)
            if (!permitted && !denyNotifications) device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            context.contentResolver.call(source, "prepare", sourceName, Bundle().apply { putByteArray("bytes", bytes) })
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
                        .putExtra("feedPeerUrl", endpoint).putExtra("feedOfficialPeerUrl", endpoint)).use {
                        withFailureCapture({ captureNativeScreen(File(evidence, "failure-${incoming}-${cancel}.png")) }) {
                            waitUntil { ui.onAllNodesWithText("Chat").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNodeWithText("Chat").performClick()
                            waitUntil { ui.onAllNodesWithText(author.publicKey).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNodeWithText(author.publicKey).performClick()
                            waitUntil { ui.onAllNodesWithText("Message").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                            ui.onNodeWithText("Message").performClick().performTextInput(draft)
                            if (focusedWindowImeBottom() > 0) device.pressBack()
                            session = FileshareRuntime.session(context, wallet, SaitoBackend.OWNER, endpoint, author.publicKey)
                            waitUntil { session!!.ready.value }
                            if (denyNotifications) {
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
                            browser.hold(receipts = !incoming, chunks = incoming)
                            if (incoming) {
                                browser.send(bytes, "from-browser.bin", "browser103")
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
                                check(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.text("Cancel")), 5000))
                                device.findObject(androidx.test.uiautomator.By.text("Cancel")).click()
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
                            captureNativeScreen(File(evidence, "complete-${incoming}-${cancel}.png"), label)
                            if (incoming && !cancel) {
                                ui.onNodeWithText("Save as…").performScrollTo().performClick()
                                waitUntil { ui.onAllNodesWithText("File saved").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
                                assertArrayEquals(bytes, context.contentResolver.openInputStream(destination)!!.use { it.readBytes() })
                                ui.onNodeWithText(draft).assertExists()
                                captureNativeScreen(File(evidence, "saved.png"), "File saved")
                            }
                            assertEquals(wallet.publicKey, wallets.loadExisting().publicKey)
                            assertTrue(fixture.transactions.isEmpty()) // No broadcast transaction carries a file.
                            fixture.assertHealthy()
                            File(evidence, "result-${incoming}-${cancel}-${unknown}.json").writeText(com.google.gson.JsonObject().apply {
                                addProperty("result", "PASS"); addProperty("incoming", incoming); addProperty("cancel", cancel)
                                addProperty("sourcePin", pin); addProperty("bytes", bytes.size)
                                addProperty("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                                addProperty("actualWebRtc", true); addProperty("screenOffObserved", true)
                                addProperty("unknownSizeProvider", unknown)
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
