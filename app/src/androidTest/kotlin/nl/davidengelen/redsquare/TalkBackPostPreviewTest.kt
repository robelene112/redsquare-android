package nl.davidengelen.redsquare

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Test

/** Actual contextual control speech and native accessibility focus, with no publishing. */
class TalkBackPostPreviewTest {
    @Test fun postControlsAndOwnerMenuSpeakTheirActionAndAccountContext() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val folder = File(context.cacheDir, "post-talkback-evidence").apply { mkdirs() }
        NativeTalkBackObservation(folder).observe {
            val row = instrumentation.context.assets.open("native-feed-reference.json").reader().use {
                JsonParser.parseReader(it).asJsonObject.getAsJsonObject("archiveRow")
            }
            val author = "Anon-jesTu2"
            withAppFrameFixture("talkback-post", "post-talkback-evidence", "[$row]") {
                check(device.wait(Until.hasObject(By.text("Native Red Square reference post")), 15_000))
                click(automation, "Later")
                check(device.wait(Until.gone(By.text("Back up your wallet")), 5_000))
                device.waitForIdle(5_000)
                focusAndHear("profile", "View profile for $author", folder)
                focusAndHear("thread", "Open thread by $author", folder, clickLabel = true)
                for (action in listOf("Like", "Repost", "Quote"))
                    focusAndHear(action.lowercase(), "$action post by $author", folder)
                focusAndHear("more", "Post actions for $author", folder)
                check(actionNode("Post actions for $author").performAction(AccessibilityNodeInfo.ACTION_CLICK))
                focusAndHear("report", "Report post by $author", folder)
                device.pressBack()
            }
            val owner = AndroidWalletStore(context).loadExisting()
            val ownerName = "Anon-${owner.publicKey.take(6)}"
            val owned = postingArchiveRow(SaitoTransaction.create(owner,
                RedSquarePosts.message("Own TalkBack post"), 1_700_000_000_000))
            withAppFrameFixture("talkback-owner", "post-talkback-evidence", "[$owned]") {
                check(device.wait(Until.hasObject(By.text("Own TalkBack post")), 15_000))
                click(automation, "Later")
                check(device.wait(Until.gone(By.text("Back up your wallet")), 5_000))
                device.waitForIdle(5_000)
                focusAndHear("owner-more", "Post actions for $ownerName", folder)
                check(actionNode("Post actions for $ownerName").performAction(AccessibilityNodeInfo.ACTION_CLICK))
                focusAndHear("edit", "Edit post by $ownerName", folder)
                focusAndHear("delete", "Delete post by $ownerName", folder)
                device.pressBack()
            }
            assertEquals(serviceProcess, talkbackProcess(device))
            File(folder, "accessibility-after.txt").writeText(device.executeShellCommand("dumpsys accessibility"))
        }
    }

    private fun NativeTalkBackObservation.focusAndHear(label: String, description: String,
        folder: File, clickLabel: Boolean = false) {
        val target = actionNode(description, clickLabel)
        target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS)
        device.executeShellCommand("logcat -c")
        assertTrue("Actual focus action for $description",
            target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
        waitFor(5_000) {
            appRoots(automation).any { it.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) == target }
        }
        waitFor(10_000) { completedFragments(device, description).isNotEmpty() }
        assertEquals("The same real service must produce contextual speech", serviceProcess, talkbackProcess(device))
        File(folder, "$label-speech.txt").writeText(speechLog(device))
        File(folder, "$label-result.json").writeText(JsonObject().apply {
            addProperty("expectedContext", description)
            addProperty("actualAccessibilityFocus", true)
            addProperty("serviceProcess", serviceProcess)
            addProperty("engineCompletions", completedFragments(device, description).joinToString())
            addProperty("physicalAudibility", false)
        }.toString())
        capture(automation, File(folder, "$label.png"))
    }

    private fun NativeTalkBackObservation.actionNode(description: String,
        clickLabel: Boolean = false): AccessibilityNodeInfo {
        fun find(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (root.isVisibleToUser && if (clickLabel) root.actionList.any {
                    it.id == AccessibilityNodeInfo.ACTION_CLICK && it.label?.toString() == description
                } else root.contentDescription?.toString() == description) return root
            return (0 until root.childCount).firstNotNullOfOrNull { index -> root.getChild(index)?.let(::find) }
        }
        var found: AccessibilityNodeInfo? = null
        waitFor(5_000) { found = appRoots(automation).firstNotNullOfOrNull(::find); found != null }
        var target = requireNotNull(found)
        while (!target.isClickable) target = requireNotNull(target.parent)
        check(target.isVisibleToUser && target.isEnabled)
        return target
    }
}
