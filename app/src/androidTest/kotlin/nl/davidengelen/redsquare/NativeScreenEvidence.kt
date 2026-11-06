package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File

/** Separate screen observations, not an atomic screenshot/hierarchy pair. */
fun captureNativeScreen(file: File, expectedText: String? = null) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val started = android.os.SystemClock.elapsedRealtime()
    file.parentFile?.mkdirs()
    val hierarchyFile = File(file.parentFile, file.nameWithoutExtension + ".xml")
    val reuseHierarchy = expectedText != null && nativeXmlPreserves(expectedText)
    val ready: Long
    val hierarchy: Long
    if (reuseHierarchy) {
        val deadline = started + 10_000
        while (true) {
            check(android.os.SystemClock.elapsedRealtime() <= deadline) { "Native exact-text observation timed out" }
            device.dumpWindowHierarchy(hierarchyFile)
            val visible = nativeXmlHasVisibleText(hierarchyFile, requireNotNull(expectedText))
            val observed = android.os.SystemClock.elapsedRealtime()
            check(observed <= deadline) { "Native exact-text observation exceeded its ten-second deadline" }
            if (visible) break
            android.os.SystemClock.sleep(50)
        }
        ready = android.os.SystemClock.elapsedRealtime()
        hierarchy = ready // The retained hierarchy itself supplied the exact native readiness check.
    } else {
        // UiAutomator replaces XML-forbidden characters. Preserve exact original lookup for these strings.
        expectedText?.let { check(device.wait(Until.hasObject(By.text(it)), 10_000)) }
        ready = android.os.SystemClock.elapsedRealtime()
        device.dumpWindowHierarchy(hierarchyFile)
        hierarchy = android.os.SystemClock.elapsedRealtime()
    }
    check(device.takeScreenshot(file))
    val finished = android.os.SystemClock.elapsedRealtime()
    // Observer cost is recorded separately from application assertions and their unchanged bounds.
    runCatching {
        File(file.parentFile, "native-capture-timings.jsonl").appendText(com.google.gson.JsonObject().apply {
            addProperty("file", file.name); addProperty("started", started)
            addProperty("readinessUsesHierarchy", reuseHierarchy)
            addProperty("readinessMillis", ready - started); addProperty("hierarchyMillis", hierarchy - ready)
            addProperty("screenshotMillis", finished - hierarchy); addProperty("totalMillis", finished - started)
        }.toString() + "\n")
    }
}

/** A dot can be an exported control character, so these strings retain raw native exact lookup. */
private fun nativeXmlPreserves(value: String): Boolean = value.isNotEmpty() && '.' !in value && value.codePoints().noneMatch { point ->
    (point < 0x20 && point != 9 && point != 10 && point != 13) ||
        (point in 0x7f..0x9f && point != 0x85) || point in 0xd800..0xdfff ||
        point in 0xfdd0..0xfdef || (point and 0xffff) in 0xfffe..0xffff
}

/** UiAutomator's dump covers all native windows; match only text under visible ancestors. */
private fun nativeXmlHasVisibleText(file: File, expected: String): Boolean = file.inputStream().use { input ->
    val parser = android.util.Xml.newPullParser().apply { setInput(input, "UTF-8") }
    val visible = mutableListOf<Boolean>()
    while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "node") {
            val current = (visible.lastOrNull() ?: true) && parser.getAttributeValue(null, "visible-to-user") == "true"
            visible.add(current)
            if (current && parser.getAttributeValue(null, "text") == expected) return@use true
        } else if (parser.eventType == org.xmlpull.v1.XmlPullParser.END_TAG && parser.name == "node") {
            visible.removeAt(visible.lastIndex)
        }
        parser.next()
    }
    false
}

fun withFailureCapture(capture: () -> Unit, block: () -> Unit) {
    try { block() }
    catch (failure: Throwable) {
        try { capture() }
        catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
        throw failure
    }
}
