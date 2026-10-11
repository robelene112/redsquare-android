# Unanswered report deadline RED

Android10 run38170920890 atc10de10:3cases/2PASS/1FAIL33.365s. The new
controlled peer retains an open connection and withholds a correlated reply
after receiving the report. The test fails waiting20seconds for truthful
unknown status (ReportPreviewTest.kt128). Reopening and ordinary acknowledgement
regressions pass. Root individually viewed the live failure original and
separately parsed XML: Sending report to saito.davidengelen.nl... is still
visible and Done is enabled. This reproduces stale sending feedback.

Source inspection identifies TimeoutCancellationException being rethrown by
the general cancellation catch. The next minimal correction catches request
deadlines as failure/unknown while preserving cancellation of the parent scope.
Unchanged device assertions still require explicit retry of exact signed bytes,
no automatic report or type4 broadcast. No production write or acceptance claim.
