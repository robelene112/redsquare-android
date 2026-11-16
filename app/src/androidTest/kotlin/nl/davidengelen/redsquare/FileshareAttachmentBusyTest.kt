package nl.davidengelen.redsquare

import org.junit.Test

/** Original owned browser, streaming provider and held Relay scenarios with actual attachment-menu checks. */
class FileshareAttachmentBusyTest : FileshareFixture() {
    @Test fun streamingPreparationExplainsTheFileRestrictionWhileOtherMediaStayEnabled() =
        transfer(incoming = false, preparationCancel = true, attachmentAvailability = true)

    @Test fun anAcceptedTransferExplainsTheFileRestrictionWithoutBlockingTheWriter() =
        transfer(incoming = true, pendingAccept = true, attachmentAvailability = true)
}
