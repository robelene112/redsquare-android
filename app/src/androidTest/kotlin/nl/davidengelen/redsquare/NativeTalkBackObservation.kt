package nl.davidengelen.redsquare

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*

/** Real service, English engine, completed speech and native focus with independent cleanup. */
internal class NativeTalkBackObservation(private val folder: File) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    lateinit var automation: UiAutomation
        private set
    lateinit var device: UiDevice
        private set
    lateinit var serviceProcess: String
        private set
    lateinit var serviceLabel: String
        private set

    fun observe(block: NativeTalkBackObservation.() -> Unit) {
        check(context.packageName == "nl.davidengelen.redsquare.dev" && Build.HARDWARE in listOf("ranchu", "goldfish"))
        val configurator = Configurator.getInstance()
        val originalFlags = configurator.getUiAutomationFlags()
        var primary: Throwable? = null
        var originalServiceFlags: Int? = null
        try {
            configurator.setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            device = UiDevice.getInstance(instrumentation)
            originalServiceFlags = automation.serviceInfo.flags
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            val service = android.content.ComponentName("com.android.talkback", "com.google.android.marvin.talkback.TalkBackService")
            val manager = instrumentation.targetContext.packageManager
            serviceLabel = manager.getServiceInfo(service, 0).loadLabel(manager).toString()
            waitFor(15_000) {
                val dump = device.executeShellCommand("dumpsys accessibility")
                val bound = dump.substringAfter("Bound services:", "").substringBefore("Enabled services:")
                (bound.contains(service.className) || bound.contains("Service[label=$serviceLabel,")) &&
                    dump.substringAfter("Enabled services:", "").contains(service.flattenToString()) &&
                    // Binding precedes TalkBack's asynchronous reader initialization. Start the
                    // app only after its touch-exploration/input flags are actually configured.
                    dump.contains("touchExplorationEnabled=true") &&
                    // The framework handles double taps before API30; this service flag does not exist there.
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || dump.contains("serviceHandlesDoubleTap=true"))
            }
            File(folder, "accessibility-before.txt").writeText(device.executeShellCommand("dumpsys accessibility"))
            serviceProcess = talkbackProcess(device)
            verifyEnglishEngine(folder)
            block()
        } catch (failure: Throwable) {
            primary = failure
            cleanupAfterTest(failure, {
                File(folder, "failure-speech.txt").writeText(speechLog(device))
            }, {
                File(folder, "failure-accessibility.txt").writeText(device.executeShellCommand("dumpsys accessibility"))
            }, {
                capture(automation, File(folder, "failure.png"))
            })
            throw failure
        } finally {
            cleanupAfterTest(primary, {
                originalServiceFlags?.let { original ->
                    automation.serviceInfo = automation.serviceInfo.apply { flags = original }
                }
            }, { configurator.setUiAutomationFlags(originalFlags) })
        }
    }

    fun verifyEnglishEngine(folder: File) {
        val context = InstrumentationRegistry.getInstrumentation().context
        val ready = CountDownLatch(1)
        var initialization = Int.MIN_VALUE
        val tts = TextToSpeech(context) { initialization = it; ready.countDown() }
        try {
            assertTrue("English TTS initialization must complete", ready.await(15, TimeUnit.SECONDS))
            val observation = JsonObject().apply {
                addProperty("initialization", initialization)
                addProperty("defaultEngine", tts.defaultEngine)
                addProperty("installedEngines", tts.engines.joinToString { it.name })
                addProperty("englishAvailability", tts.isLanguageAvailable(Locale.US))
            }
            File(folder, "engine.json").writeText(observation.toString())
            assertEquals(TextToSpeech.SUCCESS, initialization)
            assertEquals("The controlled offline engine must be selected", "com.reecedunn.espeak", tts.defaultEngine)
            assertTrue("A real English voice must be installed", tts.setLanguage(Locale.US) >= TextToSpeech.LANG_AVAILABLE)
            val completed = CountDownLatch(1)
            var synthesisError = false
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit
                override fun onDone(id: String?) { completed.countDown() }
                @Deprecated("Legacy TTS callback") override fun onError(id: String?) { synthesisError = true; completed.countDown() }
            })
            val audio = File(folder, "english-engine.wav")
            assertEquals(TextToSpeech.SUCCESS, tts.synthesizeToFile("Red Square accessibility ready", null, audio, "status-readiness"))
            assertTrue("Actual engine synthesis must finish", completed.await(15, TimeUnit.SECONDS))
            assertFalse("Engine synthesis must succeed", synthesisError)
            assertTrue("A real audio file must be produced", audio.length() > 1000)
            assertEquals("RIFF", audio.inputStream().use { input ->
                val header = ByteArray(4); check(input.read(header) == 4); String(header, Charsets.US_ASCII)
            })
        } finally { tts.shutdown() }
    }

    fun fragments(device: UiDevice, summary: String): List<String> =
        speechLog(device).lineSequence()
            .filter { it.contains("Speaking fragment text=") && it.contains(summary) }.toList()

    fun talkbackProcess(device: UiDevice): String =
        device.executeShellCommand("pidof com.android.talkback").trim().also { check(it.matches(Regex("[1-9][0-9]*"))) }

    fun speechLog(device: UiDevice): String =
        device.executeShellCommand("logcat -d -v brief --pid=${talkbackProcess(device)}")

    /** These markers originate only in real engine callbacks, never fallback completion. */
    fun completedFragments(device: UiDevice, summary: String): List<String> {
        val log = speechLog(device)
        return fragments(device, summary).mapNotNull { line ->
            Regex("utteranceId=([^,\\s]+)").find(line)?.groupValues?.get(1)
        }.filter { id ->
            fun callback(event: String) = Regex("REDSQUARE_ENGINE_$event utteranceId=${Regex.escape(id)}(?:\\s|$)")
                .containsMatchIn(log)
            callback("START") && callback("AUDIO") && callback("DONE") && !callback("ERROR") && !callback("STOP") &&
                Regex("REDSQUARE_ENGINE_AUDIO utteranceId=${Regex.escape(id)} bytes=[1-9][0-9]*").containsMatchIn(log)
        }
    }

    fun appRoots(automation: UiAutomation): List<AccessibilityNodeInfo> = automation.windows.mapNotNull { it.root }
        .filter { it.packageName?.toString() == InstrumentationRegistry.getInstrumentation().targetContext.packageName }

    fun node(automation: UiAutomation, text: String): AccessibilityNodeInfo {
        var found: AccessibilityNodeInfo? = null
        withFailureCapture({
            File(folder, "node-lookup-failure.txt").writeText("requested=$text serviceFlags=${automation.serviceInfo.flags}\n" +
                automation.windows.joinToString("\n") { window ->
                    "id=${window.id} title=${window.title} active=${window.isActive} focused=${window.isFocused} rootPackage=${window.root?.packageName}"
                })
        }) {
            waitFor(5_000) {
                found = appRoots(automation).firstNotNullOfOrNull { root -> visibleTextNode(root, text) }
                found != null
            }
        }
        return requireNotNull(found)
    }

    fun visibleTextNode(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (root.text?.toString() == text && root.isVisibleToUser) return root
        return (0 until root.childCount).firstNotNullOfOrNull { index ->
            root.getChild(index)?.let { child -> visibleTextNode(child, text) }
        }
    }

    fun click(automation: UiAutomation, text: String) {
        var target: AccessibilityNodeInfo? = node(automation, text)
        while (target != null && !target.isClickable) target = target.parent
        check(requireNotNull(target).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    fun focusedText(automation: UiAutomation): String? =
        appRoots(automation).firstNotNullOfOrNull { it.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)?.text?.toString() }

    fun waitFor(timeout: Long, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100)
        check(condition()) { "Accessibility condition did not complete" }
    }

    fun capture(automation: UiAutomation, file: File) {
        val bitmap = requireNotNull(automation.takeScreenshot())
        try { file.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
        finally { bitmap.recycle() }
    }
}
