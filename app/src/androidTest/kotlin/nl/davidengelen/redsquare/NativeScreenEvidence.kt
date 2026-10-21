package nl.davidengelen.redsquare

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File

/** Separate screen observations, not an atomic screenshot/hierarchy pair. */
fun captureNativeScreen(file: File, expectedText: String? = null) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    traceConversationOperator("capture-ready-before:${file.name}")
    expectedText?.let { check(device.wait(Until.hasObject(By.text(it)), 10_000)) }
    traceConversationOperator("capture-ready-after:${file.name}")
    file.parentFile?.mkdirs()
    traceConversationOperator("capture-hierarchy-before:${file.name}")
    device.dumpWindowHierarchy(File(file.parentFile, file.nameWithoutExtension + ".xml"))
    traceConversationOperator("capture-screenshot-before:${file.name}")
    check(device.takeScreenshot(file))
    traceConversationOperator("capture-screenshot-after:${file.name}")
}

fun withFailureCapture(capture: () -> Unit, block: () -> Unit) {
    try { block() }
    catch (failure: Throwable) {
        try { capture() }
        catch (diagnostic: Throwable) { failure.addSuppressed(diagnostic) }
        throw failure
    }
}
