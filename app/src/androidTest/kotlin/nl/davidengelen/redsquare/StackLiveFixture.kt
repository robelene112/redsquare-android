package nl.davidengelen.redsquare

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule

internal fun ComposeTestRule.switchStackModule(current: String, next: String, scrollTarget: Boolean = false) {
    revealAppNavigation()
    onNodeWithContentDescription("Switch app: $current").performClick()
    val target = onNode(hasText(next) and hasClickAction() and hasAnyAncestor(isPopup() or isDialog()))
    if (scrollTarget) target.performScrollTo()
    target.performClick()
    waitUntil(5_000) { onNodeWithContentDescription("Switch app: $next").isDisplayed() }
}

internal fun stackMotionArchive(): String {
    val reference = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        .open("native-stack-reference.json").reader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
    return com.google.gson.JsonArray().apply { add(reference.get("motion")); add(reference.get("quiet")) }.toString()
}

/** Wait for actual data and settled native card geometry before tapping, within the caller's original budget. */
internal fun awaitNativeStackArticleCard(device: androidx.test.uiautomator.UiDevice, title: String, timeoutMillis: Long, evidence: java.io.File) {
    val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMillis
    val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
    var previous: android.graphics.Rect? = null
    val loading = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.stack_loading)
    val samples = com.google.gson.JsonArray()
    try {
    while (android.os.SystemClock.elapsedRealtime() < deadline) {
        if (device.hasObject(androidx.test.uiautomator.By.text(loading))) {
            samples.add(com.google.gson.JsonObject().apply {
                addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime()); addProperty("loading", true)
            })
            previous = null
            android.os.SystemClock.sleep(50)
            continue
        }
        val bounds = try { device.findObject(androidx.test.uiautomator.By.textContains(title))?.visibleBounds }
            catch (_: androidx.test.uiautomator.StaleObjectException) { null }
        val viewport = try { nativeVerticalScrollBounds() } catch (_: NoSuchElementException) { null }
        val visible = bounds != null && bounds.height() >= 20 * density && (viewport == null || viewport.contains(bounds))
        samples.add(com.google.gson.JsonObject().apply {
            addProperty("elapsedMillis", android.os.SystemClock.elapsedRealtime())
            addProperty("bounds", bounds?.toShortString()); addProperty("viewport", viewport?.toShortString())
            addProperty("visible", visible); addProperty("settled", visible && bounds == previous)
        })
        if (visible && bounds == previous && android.os.SystemClock.elapsedRealtime() <= deadline) return
        if (!visible && viewport != null) {
            val upper = viewport.top + viewport.height() / 5
            val lower = viewport.bottom - viewport.height() / 5
            val down = bounds != null && !bounds.isEmpty && bounds.top <= viewport.top
            check(device.swipe(viewport.centerX(), if (down) upper else lower,
                viewport.centerX(), if (down) lower else upper, 20))
            previous = null
        } else {
            previous = if (visible) bounds else null
            android.os.SystemClock.sleep(50)
        }
    }
    error("The real article card must be visible and settled within ${timeoutMillis}ms")
    } finally {
        evidence.parentFile?.mkdirs()
        evidence.writeText(com.google.gson.JsonObject().apply {
            addProperty("title", title); addProperty("timeoutMillis", timeoutMillis); add("nativeSamples", samples)
        }.toString())
    }
}
