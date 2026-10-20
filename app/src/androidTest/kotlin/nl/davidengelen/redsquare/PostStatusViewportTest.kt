package nl.davidengelen.redsquare

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.google.gson.JsonObject
import java.io.File
import nl.davidengelen.redsquare.saito.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** A genuine unavailable local action, observed through native status and retry controls. */
class PostStatusViewportTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test fun unavailableLikeDetailsAndRetryRemainReachableAcrossTextSizesAndThemes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "nl.davidengelen.redsquare.dev" &&
            android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        val author = SaitoWallet.create()
        val row = postingArchiveRow(SaitoTransaction.create(author,
            RedSquarePosts.message("Unavailable action viewport preview"), 1_700_000_000_000))
        val name = "Anon-${author.publicKey.take(6)}"
        val wallet = AndroidWalletStore(context).loadOrCreate()
        val blocked = File(context.noBackupFilesDir,
            "social.owner.${wallet.publicKey}.${row.get("sig").asString}.like.v1")
        check(!blocked.exists())
        var created = false
        var primary: Throwable? = null
        try {
            withAppFrameFixture("post-status", "post-status-evidence", feedRows = "[$row]") { activity ->
                val folder = File(context.cacheDir, "post-status-evidence").apply { mkdirs() }
                val profile = hasContentDescription("View profile for $name") and hasClickAction()
                ui.waitUntil(20_000) { ui.onNode(profile).isDisplayed() }
                ui.onNodeWithText("Later").performClick()
                check(blocked.mkdir())
                created = true
                File(blocked, "retain-public-save-fault").writeText("public test fault")
                ui.withPostViewports(activity, folder, "status") { label, _ ->
                    val details = hasContentDescription("Details: Like unavailable") and hasClickAction()
                    val retry = hasText("Retry") and hasClickAction()
                    ui.waitUntil(15_000) { ui.onAllNodes(details).fetchSemanticsNodes().size == 1 }
                    ui.onNode(hasScrollAction()).performScrollToNode(retry)
                    ui.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                        scroll(0f, 88 * context.resources.displayMetrics.density)
                    }
                    ui.waitForIdle()
                    val polite = ui.onAllNodes(SemanticsMatcher.expectValue(
                        SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).fetchSemanticsNodes()
                    val root = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow)
                    check(root.packageName?.toString() == context.packageName)
                    fun liveNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> =
                        (if (node.liveRegion == View.ACCESSIBILITY_LIVE_REGION_POLITE) listOf(node) else emptyList()) +
                            (0 until node.childCount).flatMap { index -> node.getChild(index)?.let(::liveNodes).orEmpty() }
                    val native = liveNodes(root)
                    File(folder, "$label-live-regions.json").writeText(JsonObject().apply {
                        addProperty("mergedNodes", polite.joinToString { it.config.toString() })
                        addProperty("nativeNodes", native.joinToString { "text=${it.text} description=${it.contentDescription} clickable=${it.isClickable}" })
                    }.toString())
                    val statusRegions = native.filter { it.text?.contains("Like unavailable") == true }
                    assertEquals("One concise live status must remain separate from the clickable post", 1, statusRegions.size)
                    val statusRegion = statusRegions.single()
                    assertEquals("Only the concise summary belongs in the native live announcement", "Like unavailable",
                        statusRegion.text?.toString())
                    assertFalse("The post action must not become a native live announcement", statusRegion.isClickable)
                    ui.onNodeWithContentDescription("Like post by $name").assertIsNotEnabled()
                    ui.onNodeWithText("Like unavailable", useUnmergedTree = true).assertIsDisplayed()
                    val viewport = ui.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
                    val composer = ui.onNode(hasText("New post") and hasClickAction())
                        .fetchSemanticsNode().touchBoundsInRoot
                    val minimum = 48 * context.resources.displayMetrics.density - 1
                    val bounds = listOf(details, retry).map { target ->
                        ui.onNode(target).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
                            .touchBoundsInRoot.also { rect ->
                                assertTrue("Complete reachable status target at $label", rect.width >= minimum &&
                                    rect.height >= minimum && rect.left >= viewport.left && rect.right <= viewport.right &&
                                    rect.top >= viewport.top && rect.bottom <= viewport.bottom)
                                assertFalse("Composer must not obscure status controls", rect.overlaps(composer))
                            }
                    }
                    assertFalse("Details and Retry must not overlap", bounds[0].overlaps(bounds[1]))
                    File(folder, "$label-targets.json").writeText(JsonObject().apply {
                        addProperty("statusTargets", bounds.joinToString())
                        addProperty("scrollViewport", viewport.toString())
                        addProperty("floatingComposer", composer.toString())
                    }.toString())
                    captureNativeScreen(File(folder, "$label-unavailable.png"))
                    ui.onNode(details).performClick()
                    ui.onNode(hasText("Like post by $name") and hasAnyAncestor(isDialog()))
                        .performScrollTo().assertIsDisplayed()
                    ui.onNodeWithText("Could not finish this action. Retry checks the original action before sending it again.")
                        .performScrollTo().assertIsDisplayed()
                    captureNativeScreen(File(folder, "$label-details.png"))
                    ui.onNodeWithText("Done").performClick()
                    ui.onNode(retry).assertIsEnabled().performClick()
                    ui.waitUntil(15_000) { ui.onNode(details).isDisplayed() &&
                        ui.onAllNodes(retry and isEnabled()).fetchSemanticsNodes().size == 1 }
                    ui.onNodeWithContentDescription("Like post by $name").assertIsNotEnabled()
                    captureNativeScreen(File(folder, "$label-retry-unavailable.png"))
                }
            }
        } catch (failure: Throwable) { primary = failure; throw failure }
        finally {
            cleanupAfterTest(primary, {
                if (created) {
                    val obstacle = File(blocked, "retain-public-save-fault")
                    check(!obstacle.exists() || obstacle.delete())
                    check(blocked.delete())
                }
                check(!blocked.exists())
            }, { check(AndroidWalletStore(context).loadExisting().exportJson() == wallet.exportJson()) })
        }
    }
}
