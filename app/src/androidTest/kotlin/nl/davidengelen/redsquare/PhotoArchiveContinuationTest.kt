package nl.davidengelen.redsquare

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import org.junit.Rule
import org.junit.Test

class PhotoArchiveContinuationTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun followingKeepsItsCachedAnchorAcrossMalformedSparseAndDuplicateTimestampRows() =
        exercisePhotoPages(ui, following = true)
}

class PhotoLargeRowPreviewTest {
    @get:Rule val ui = createEmptyComposeRule()
    @Test fun individuallyAcceptedPhotoRowsStayReachableWhenEvenTenRowsExceedTheReceiveBound() =
        exercisePhotoPages(ui, largeRows = true)
}
