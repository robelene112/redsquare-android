package nl.davidengelen.redsquare

import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue

/** Resolve the actual card at gesture time, retaining each caller's existing lookup deadline. */
internal fun visibleNotificationCard(device: UiDevice, text: String, timeoutMillis: Long): UiObject2 {
    val card = requireNotNull(device.wait(Until.findObject(
        By.res("android", "status_bar_latest_event_content").hasDescendant(By.text(text))), timeoutMillis))
    val bounds = card.visibleBounds
    assertTrue("The actual notification card must be fully visible before swiping",
        bounds.left >= 0 && bounds.right <= device.displayWidth && bounds.top >= 0 &&
            bounds.bottom <= device.displayHeight && bounds.width() > device.displayWidth / 2 && bounds.height() > 16)
    card.setGestureMargin(8)
    return card
}
