package nl.davidengelen.redsquare

import android.content.Intent
import org.junit.Assert.*

/** Observe only the public Android handoff; expected values belong to each native journey. */
internal fun assertPublisherEmailDraft(draft: Intent, recipient: String, subject: String, body: String) {
    assertEquals(Intent.ACTION_SENDTO, draft.action)
    assertEquals("mailto", draft.data!!.scheme)
    assertEquals(recipient, draft.data!!.schemeSpecificPart)
    assertArrayEquals(arrayOf(recipient), draft.getStringArrayExtra(Intent.EXTRA_EMAIL))
    assertEquals(subject, draft.getStringExtra(Intent.EXTRA_SUBJECT))
    assertEquals(body, draft.getStringExtra(Intent.EXTRA_TEXT))
    assertEquals(setOf(Intent.EXTRA_EMAIL, Intent.EXTRA_SUBJECT, Intent.EXTRA_TEXT), draft.extras!!.keySet())
    assertNull(draft.clipData)
    assertEquals(0, draft.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
}
