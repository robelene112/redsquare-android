# Native report feedback RED

Android10 run38169454650 at6160165:2FAIL24.452s. Both fail the new native
UiDevice presence gate at NativeScreenEvidence.kt:12: ReportPreviewTest.kt:93
for unknown delivery, :50 for acknowledgement. These are after existing Compose
status assertions. Root individually viewed both live failure originals and
separately parsed XML: neither required feedback text is present. Root observed
a visibility defect; exact layout cause remains undiagnosed. Recovery-stage code
was not reached in this run. Earlier run38169041012 separately proves recreation
retention failure. No moderator/deployed/physical-device claim.

Next correction displays results in a named dialog rather than relying on text
below a tall feed item. Tests retain all routing/receipt/no-removal assertions,
require native text before capture, dismiss completed result with Done before
navigation, and reopen pending details through the explicit Retry report action.
Durable recovery remains intentionally not implemented at this checkpoint.
