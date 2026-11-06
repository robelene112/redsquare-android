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
    expectedText?.let { check(device.wait(Until.hasObject(By.text(it)), 10_000)) }
    val ready = android.os.SystemClock.elapsedRealtime()
    file.parentFile?.mkdirs()
    device.dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    val hierarchy = android.os.SystemClock.elapsedRealtime()
    check(device.takeScreenshot(file))
    val finished = android.os.SystemClock.elapsedRealtime()
    // Observer cost is recorded separately from application assertions and their unchanged bounds.
    runCatching {
        File(file.parentFile, "native-capture-timings.jsonl").appendText(com.google.gson.JsonObject().apply {
            addProperty("file", file.name); addProperty("started", started)
            addProperty("readinessMillis", ready - started); addProperty("hierarchyMillis", hierarchy - ready)
            addProperty("screenshotMillis", finished - hierarchy); addProperty("totalMillis", finished - started)
        }.toString() + "\n")
    }
}

fun withFailureCapture(capture: () -> Unit, block: () -> Unit) {
    try { block() }
    catch (failure: Throwable) {
        try { capture() }
        catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
        throw failure
    }
}
