package nl.davidengelen.redsquare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import nl.davidengelen.redsquare.saito.AndroidBackendStore
import nl.davidengelen.redsquare.saito.SaitoBackend
import nl.davidengelen.redsquare.saito.SaitoWallet
import nl.davidengelen.redsquare.spaces.SpaceVoiceRoom
import nl.davidengelen.redsquare.talk.CallPhase
import nl.davidengelen.redsquare.talk.TalkRuntime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

class SpaceRoomJourneyTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also {
        check(it.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in setOf("ranchu", "goldfish"))
    }
    private val device get() = UiDevice.getInstance(instrumentation)
    private val evidence get() = File(context.cacheDir, "spaces-rooms-evidence").apply { mkdirs() }

    @Test fun prejoinIsQuietAndTheCallReturnsFromAnotherModule() {
        val fixture = request("room-config")
        val wallet = SaitoWallet.fromPrivateKey("0".repeat(63) + "4")
        assertEquals(fixture.get("nativeWallet").asString, wallet.publicKey)
        val store = AndroidWalletStore(context)
        store.replace(store.loadOrCreate(), wallet)
        AndroidBackendStore(context).save(SaitoBackend.OWNER)
        assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
        assertPermissionAbsent(Manifest.permission.CAMERA)
        val channel = fixture.get("channel").asString
        val space = fixture.get("space").asString
        val peer = argument("spacesPeerPort")
        ActivityScenario.launch<MainActivity>(Intent(Intent.ACTION_VIEW, Uri.parse(fixture.get("link").asString), context, MainActivity::class.java)
            .putExtra("feedPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("feedOfficialPeerUrl", "ws://127.0.0.1:$peer/wsopen")
            .putExtra("balanceApiUrl", "http://127.0.0.1:1/balance/")).use { activity ->
            withFailureCapture({ capture("room-failure") }) {
                waitText("0 people here", 25_000)
                assertTrue(observed().participants.isEmpty())
                assertTrue(TalkRuntime.call.value?.phase?.live != true)
                capture("room-prejoin")
                roomClick("Join room")
                waitText("You’re in the room", 20_000)
                val call = requireNotNull(TalkRuntime.call.value)
                assertEquals(CallPhase.ACTIVE, call.phase)
                assertEquals(channel, call.spaceRoom?.channel)
                assertEquals(space, call.spaceRoom?.space)
                assertFalse(call.microphone); assertFalse(call.camera); assertNull(call.localVideo)
                assertEquals(call.token, observed().participants.single().session)
                assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
                assertPermissionAbsent(Manifest.permission.CAMERA)
                capture("room-joined-muted")

                roomClick("Unmute")
                val deny = requireNotNull(device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 5000))
                deny.click()
                showRoomItem(hasText("Permission wasn’t granted."))
                assertEquals(call.token, TalkRuntime.call.value?.token)
                assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                assertPermissionAbsent(Manifest.permission.RECORD_AUDIO)
                capture("room-permission-denied")

                roomClick("Browse while in the room")
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)
                click("Spaces")
                click("Red Square")
                waitText("Return to Open lounge", 12_000)
                capture("room-compact-bar")
                assertEquals(call.token, observed().participants.single().session)
                click("Return to Open lounge")
                waitText("You’re in the room", 15_000)

                device.setOrientationLeft()
                waitText("You’re in the room", 15_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)
                assertFalse(requireNotNull(TalkRuntime.call.value).microphone)
                capture("room-landscape")
                device.setOrientationNatural()
                activity.recreate()
                waitText("You’re in the room", 20_000)
                assertEquals(call.token, TalkRuntime.call.value?.token)
                assertEquals(call.token, observed().participants.single().session)

                assertTrue(SpaceVoiceRoom.read(request("room-remove-native")).participants.isEmpty())
                ui.waitUntil(10_000) { TalkRuntime.call.value?.phase == CallPhase.INTERRUPTED }
                assertNull(TalkRuntime.call.value?.localVideo)
                waitText("0 people here", 12_000)
                capture("room-after-removal")
                File(evidence, "room-result.json").writeText(JsonObject().apply {
                    addProperty("pid", Process.myPid())
                    addProperty("participantRemoved", observed().participants.isEmpty())
                    addProperty("nativePermissionDenied", context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED)
                    addProperty("roomSurvives", observed().status == SpaceVoiceRoom.Status.AVAILABLE)
                }.toString())
            }
        }
    }

    private fun assertPermissionAbsent(permission: String) =
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))

    private fun observed() = SpaceVoiceRoom.read(request("room-observe"))

    private fun waitText(label: String, timeout: Long = 10_000) = ui.waitUntil(timeout) { ui.onNodeWithText(label).isDisplayed() }

    private fun click(label: String) {
        val node = ui.onNode(hasText(label) and hasClickAction() and isEnabled())
        ui.waitUntil(12_000) { node.isDisplayed() }
        node.performTouchInput { click() }
    }

    private fun showRoomItem(matcher: SemanticsMatcher) = ui.waitUntil(10_000) {
        runCatching { ui.onNodeWithTag("spaces-room-content").performScrollToNode(matcher) }.isSuccess
    }

    private fun roomClick(label: String) {
        showRoomItem(hasText(label) and hasClickAction())
        click(label)
    }

    private fun argument(name: String) = requireNotNull(InstrumentationRegistry.getArguments().getString(name)).toInt().also { require(it in 1024..65535) }

    private fun request(action: String): JsonObject {
        val port = argument("spacesControlPort")
        val connection = URL("http://127.0.0.1:$port/$action").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            require(connection.responseCode == 200)
            return connection.inputStream.use { source -> JsonParser.parseString(source.bufferedReader().readText()).asJsonObject }
        } finally { connection.disconnect() }
    }

    private fun capture(name: String) = captureNativeScreen(File(evidence, "$name.png"))
}
