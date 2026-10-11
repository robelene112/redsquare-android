# Native reports verification

Ticket #22, review base9ecb97b. Parent #1 accepted seams1–4: actual Android UI
against an authenticated controlled binary peer; independent pinned transaction
validation; actual recovery/failure; cross-client source compatibility.
No additional testing boundary or production report is authorized or required.

First vertical slice: name the selected reporting destination before consent,
submit signed type12 application RPC only to that backend, accept correlated
handler acknowledgement without claiming moderator review or removing the post.
ReportPreviewTest is introduced before product changes. Local :app:assembleDebugAndroidTest passes; shell syntax/diff checks pass.
Device RED pending. An initial helper parameter-order compile error was fixed
before runtime testing and is not counted as a behavioral RED.
Source contracts: NATIVE_DISCOVERY_MODERATION_CONTRACT.md,
NATIVE_MODERATION_CONTRACTS.md, NATIVE_TRANSACTION_BROADCAST.md.


## First actual RED

Android10 run38167998216 atb4be908 fails the only report case after22.489s,
at ReportPreviewTest.kt:41 waiting for the missing Report control. Refresh was
enabled and the original signed post was loaded. Root viewed the original live
confirmation-failure.png and separately parsed its XML. No product report code
was present. Local helper compile failure is separate and already corrected.
First minimal GREEN implementation is in progress; persistence/failure acceptance
is not complete. No production reports, moderator review or removal are claimed.

Local first implementation build: core55/0/0, debug APK and instrumentation
assembly pass. An accidental unused duplicate ReportState declaration in an
existing local function was removed after this build; hosted compilation and
runtime will verify the corrected source. Independent frame/receiver helpers
pass isolated Node syntax checks; actual native specimen validation is pending.

## First GREEN

Android10 run38168632919 at567a8a9:1PASS2.999s. Root2originalPNG/separate2XML
inspected. Durable ticket21/first-green retains native emitted frame/envelope,
raw runtime and independent frame/receiver results. Both first-slice source axes
0findings. This proves the first success slice only, not durable failure/recovery.
Next RED: lose acknowledgement after controlled peer receives the signed report,
reopen, retain uncertainty with no auto-send and explicitly retry exact bytes.
