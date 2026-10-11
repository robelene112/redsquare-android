# Pending Registry import warning RED

Android 10 run38118322693 onf9421aa reports six cases, five passes and one failure in31.191s. The new case signs one OWNER request, switches to OFFICIAL, verifies an empty enabled name field/no Retry/no rerouted sends, selects the different-wallet document, and fails at the missing pending-work warning. Later cancellation, return-to-owner reconciliation and exact Retry were not reached. Root individually viewed the failure PNG and inspected XML: scalar1 outgoing/scalar2 selected, with no specific pending-work warning. The unavailable-case error scroll correction passes in this run; its original PNG was individually viewed and XML inspected. Shared username picker is exercised; profile callers still await affected regression verification.

Review identified two test-sensitivity gaps. Follow-upf01211f checks for no unrelated post/profile work for this public fixture, verifies persisted backend choices and an actual official Archive read, and requires an increased matching owner namecheck count before Retry. Those later guards were not part of this run. They are not claimed verified here. The observed absence of the pending warning is the intended RED, not a passing false-positive case.

Only controlled loopback writes, published registrar recipient and unsafe public fixtures. No live namespace ownership, wallet replacement or process-death claim. Original evidence SHA256:

- `username-ui.txt`: `5a7878d76b176d0684ae3cf2109ad01fa54ab0cbba88bcc28001c0742352608b`
- `username-pending-import-failure.png`: `a21dedc3ee1b81c484dd0f7c5d087da4cf8471f36ed83ac78a3d2787ade7db84`
- `username-pending-import-failure.xml`: `68546e33f2e1941163e3e687f53969bf71506ebfaf0af825ece137b59d9e9743`
- `username-unavailable.png`: `768a39e2b8471637a815252ff30117ca465c9f731eeb242768097323e29231b0`
- `username-unavailable.xml`: `e046ad73a3a1139410829663a89c8901bc757b5604e9c4a57b72c34e955010be`
