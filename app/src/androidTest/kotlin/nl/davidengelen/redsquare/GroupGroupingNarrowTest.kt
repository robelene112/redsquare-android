package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

class GroupGroupingNarrowTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun longNamesTextPhotoRepliesAndImeRemainReadable() =
        ui.inspectGroupedChatViewports(group = true, width = 320)
}
