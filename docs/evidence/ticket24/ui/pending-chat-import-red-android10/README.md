Native pending Chat work import tracer RED

Run 38395668530 at fd6a382, Android 10: 19 tests, 18 passes and one failure in walletReplacementExplainsUncertainChatWorkBeforeConfirmation after 97.738 seconds. Core source checks pass 86 tests. The actual SAF provider supplies an explicitly public unsafe scalar-1 fixture; the active public unsafe scalar-2 wallet has an independently generated original saved as UNKNOWN. No replacement is confirmed and no message is transmitted.

The import preview shows the expected different identity and unavailable balance. It includes the existing generic names/pending-work acknowledgement, but omits the specific “Pending work belongs to your current wallet” disclosure. The test times out waiting for that specific text at line 314. Its later disabled-confirmation and cancellation assertions have not executed in this RED run. Root inspected the original failure PNG and parsed every retained XML. The screenshot does not establish anything about controls below the viewport. Raw instrumentation exit 0 is preserved, while the mandatory OK gate rejects the failing suite.

This is an incremental failure record, not whole-ticket acceptance or actual process-death proof. The minimal Chat work inspector and the review-requested durable read after cancellation are subsequent changes.
