# Full Android10 fixture interruption

Run38216135106 at8a0e785 fails during the9-case transfer runner:7PASS/2failures/
83.487s, no completed transfer runner or subsequent transfer process stage.
Earlier indexed runners recount152completed/30records/14actual death stages,
including the existing API30+ profile assumption. Compilation/core66/release/lint
passed. These incomplete counts are not full Android10 or ticket acceptance.

First failure is NativeScreenEvidence14, takeScreenshot returnedfalse at the real
system credential capture after waitForSystemPin and zero-frame assertions.
Its failure capture also returnedfalse. Neither produced an original PNG/XML;
host final screencap is0bytes and not retained as an image. No exact refusal cause
or visible PIN screen is proven. Earlier reviewed-payment PNG is fully viewed by
root, with separateXML:exact recipient/6.25/.1/Owner/memo/Authenticate and clear
footer. It precedes the failed system capture; it is not the failure screen.

Second failure is the cancellation wait's isDisplayed query throwing No compose
hierarchies found after actual system Back; no successful cancellation capture.
No exact transition timing or product cancellation failure is inferred.
Fixture correction makes system hierarchy mandatory and bitmap outcome explicit,
then preserves actual credential entry/payment/retry assertions. Cancellation
waits for semantic presence without requiring an app root during system transition,
then explicitly asserts displayed cancellation text. No timeout/product changes.
All completed/death logs retain their counts.json relative paths;48indexed hashes.
