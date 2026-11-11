package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

internal fun ComposeTestRule.switchStackModule(current: String, next: String) {
    revealAppNavigation()
    onNodeWithContentDescription("Switch app: $current").performClick()
    onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog())).performClick()
    waitUntil(5_000) { onNodeWithContentDescription("Switch app: $next").isDisplayed() }
}

internal fun stackMotionArchive(): String {
    val reference = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-stack-reference.json").reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
    return com.google.gson.JsonArray().apply { add(reference.get("motion")); add(reference.get("quiet")) }.toString()
}
