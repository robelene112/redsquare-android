package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

/** The real own-profile controls and signed bio at both widths, independent of public-profile cases. */
class ProfileOwnRedesignViewportTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun narrowOwnProfileRetainsSignedBioAndAccessibleEditing() = ProfileSectionMatrix(ui).exercise(320, own = true)
    @Test fun pixelSizedOwnProfileRetainsSignedBioAndAccessibleEditing() = ProfileSectionMatrix(ui).exercise(412, own = true)
}
