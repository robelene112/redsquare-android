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

## Reopening RED and observed feedback visibility gap

Android10 run38169041012 at0378129:2cases/1PASS/1FAIL27.207s. Recovery case
fails at95 after Activity.recreate: unknown report state absent. Root separately
inspected3originalPNG/3XML. Lost-acknowledgement and acknowledged-feed pixels
and XML do not show status text even though Compose assertIsDisplayed passed.
Added native UiDevice text-presence capture gate, before product correction.
First GREEN does not establish visually available feedback. Recovery and native
visibility are both still incomplete; exact layout cause remains undiagnosed.

## Actual native-feedback RED and correction

Android10 run38169454650 at6160165:2FAIL24.452s, NativeScreenEvidence.kt:12
text gate after Compose assertions. Root2originals/separate2XML inspected:
required feedback absent. Earlier reopening RED remains separately valid.
Result dialog correction is now implemented; native visible message gate and
routing/receipt/retained-post assertions remain. Done dismisses result before
navigation. Recovery opens its details via explicit Retry report, still requires
retained unknown state and exact bytes/no auto send. Persistence is unchanged
and expected to remain RED until its own next implementation slice.

Bounded correction Standards review reports2findings: stale active handoff and
Node Buffer.slice aliasing in reference negative check. Handoff is updated;
Buffer.from fixes copy. Both helpers rerun against unchanged retained original
PASS; independent Python SHA matches both corrected reports (5b3f3700...eeeef6).
Original signature validation preceded tampering and was valid; original output
hash was defective, not evidence that a different specimen was sent. No native
source or routing assertion changed. See corrected first-green README/manifests.
