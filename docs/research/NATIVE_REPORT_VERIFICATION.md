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

## Feedback GREEN, recovery RED, next durable correction

Android10 run38169816452 at75cd94b:2cases/1PASS/1FAIL27.857s. Native ack and
unknown text gates now pass. Reopening still fails at96 waiting for Retry report.
Root3originals/separate3XML prove both complete named feedback dialogs and the
reopened ordinary Report action. Durable feedback-green-recovery-red retains
qualified raw evidence. Bounded UI/evidence review axes clear through887ba8c
after2Standards fixes. This is not whole ticket acceptance.

Next minimal correction saves exact signed candidate before possible send,
records handler acknowledgement, reloads validated wallet/backend/post-scoped
state and requires explicit Retry after reopening. Unreadable saved details
fail closed with reload, without silently creating another signed intent.
Timeout/pre-transmission distinctions, interrupted saving, identity/backend
isolation, actual process death and paired full acceptance remain unfinished.

## Actual reopening GREEN and next unanswered-reply RED

Android10 run38170519348 at006594e passes2cases9.619s. Activity recreation
restores unknown delivery without sending; explicit Retry emits the identical
signed report and gets acknowledgement. Root individually viewed6originals
and separately parsed6XMLs; named dialogs are fully visible. Conversation
shows the retained original with Loading replies at capture, not finished
thread loading. Public frame/envelope and raw results are retained in
ticket21/recovery-green. This is not actual process death or final acceptance.

Bounded review: Spec0; Standards1 source-supported row-loading cancellation
race. ReportState now owns/deduplicates loading in its existing feature scope;
a disposed row cannot erase a replacement view's placeholder. Local debug/test
assembly passes2m20s. The next single test withholds the correlated report
reply, requiring deadline to show unknown delivery and explicit identical retry.
The production timeout classification is intentionally unchanged for this RED.

## Actual unanswered deadline RED and correction

Android10 run38170920890 atc10de10:3cases/2PASS/1FAIL33.365s. Only deadline
case fails at128 waiting20seconds for unknown feedback; reopening and ordinary
ack regressions pass. Root viewed live original/separateXML: Sending report
persists with Done enabled. The pending source correction treats only
TimeoutCancellationException as failure; other scope cancellations rethrow.
Exact retry/no auto send/no broadcast assertions remain unchanged. Durable
ticket21/deadline-red retains4hashes. Native correction check is next.

## Deadline GREEN, next failed-saving RED

Android10 run38171267643 at02c5a47 passes3cases28.596s. Root2affected
originals/separate2XMLs show truthful unknown after deadline, then explicit
exact signed retry acknowledgement. Earlier cases pass. Both bounded source
axes0findings. Durable ticket21/deadline-green retains6hashes.

Next single test obstructs the OS file-commit seam after ready state, confirms
no report or broadcast, requires truthful save-failure wording, repairs only
the isolated public obstacle, then explicitly retries with original identity
and target. Production save-failure feedback remains unchanged for RED.

## Save-failure RED and minimal correction

Android10 run38171654146 at1e874de:4cases/3PASS/1FAIL47.575s. New case
passes no-report/no-broadcast guards before failing at173 waiting for truthful
save error. Root original/separateXML show unknown delivery despite prevented
transmission. Durable ticket21/save-red4hashes. Current correction distinguishes
save failure, states nothing new was sent and preserves explicit repaired retry.
Correction runtime is next. Post-acknowledgement save warning, other pre-send
errors, authority/isolation/process/final paired checks remain unfinished.

## Initial save-failure GREEN, next receipt-save RED

Android10 run38172025405 atb867022 passes4cases32.465s. Root2affected
originals/separate2XMLs show save error and repaired explicit acknowledgement;
no send before repair, original identity/self-recipient/target retained. Both
bounded source axes0findings. Durable ticket21/save-green6hashes.

Next single case uses isolated emulator OS permission failure after original
report receipt, preserving the public candidate and restoring exact modes in
finally. It requires acknowledgement plus persistence warning, then recovered
unknown and explicit identical retry after repair. Legacy/new AtomicFile paths
are both blocked. Production warning remains unchanged for RED.

## Receipt-save warning RED and minimal correction

Android10 run38172502009 at1055657:5cases/4PASS/1FAIL53.629s, new case
fails239 for warning after acknowledgement and one-report guards pass. Root
live original/separateXML show named acknowledgement without required warning.
Exact OS modes are restored in finally; no production permissions change.
Durable ticket21/receipt-save-red4hashes. Current source adds secondary phone
save warning while retaining acknowledgement, and clears old save marker on
new explicit retry. Recovery/retry segment remains unproved until correction
runtime. Other pre-send, authority/isolation/process/final checks stay pending.

## Receipt-save warning GREEN and fixture review correction

Android10 run38172869855 atc6f7148:5PASS36.278s. Root3unchanged
originals/separate3XMLs show ack plus warning, reopened unknown and repaired
explicit ack. Exact signed retry/no automatic send assertions pass. Durable
ticket21/receipt-save-green8hashes. This is Activity recreation, not process death.
Both review axes identified the same fixture cleanup defect: saved modes were
consumed before chmod success, one restore could skip the other, and finally
could mask the primary failure. Correction retains each mode until success,
tries both independently and suppresses cleanup failures onto a primary. No
restoration failure was injected; new fixture source verification is pending.
Other pre-send/authority/isolation/process/final acceptance remains incomplete.

Next single native case closes the controlled peer before consented sending.
It requires known not-sent wording, the same state after recreation, no report
or type4 transaction, and only an explicit retry after server repair. Production
classification remains unchanged for RED. This also compiles/runs the corrected
receipt fixture; it does not inject a chmod restoration failure.

## Pre-transmission failure RED and minimal durable classification

Android10 run38173313832 at6957f47:6cases/5PASS/1FAIL58.246s.
New case fails294 waiting for not-sent wording; root original/separateXML
show unknown. Reopening/repair remain unreached. Other five pass, including
corrected cleanup on the actual successful restore path. Both cleanup review
axes clear. Durable ticket21/not-sent-red4hashes.
Current correction persists attempted=false before connecting, then true before
possible signed enqueue. Older two-key records remain conservatively unknown.
Retries retain previous uncertainty. The signed request executes the durable
marker and original wallet/backend check under the selection/identity lock
through actual socket enqueue; cancellation is checked before and after saving.
Unsigned requests retain their existing flow. Cleanup uses set(null) after
successful chmod, avoiding boxed-Int reference equality. No restoration-failure
injection or atomic switch/isolation/process acceptance is claimed. Runtime and
whole-ticket acceptance remain pending.

Bounded correction review: Spec0; Standards1 fixture rebinding concern. First
listener used port0, repeating the earlier Android17 EADDRINUSE pattern fixed
in SocialPreviewTest. Reserve an explicit numeric port before both listener
lifetimes so MockWebServer enables reuse. Consent, endpoint and acceptance
assertions unchanged. No report rebinding failure is claimed observed. Current
38173684709 predates this fixture-only correction; runtime still pending.

## Pre-transmission classification GREEN

Android10 run38173684709 at4e53ddd:6PASS42.139s. Root3unchanged
originals/separate3XMLs show not-sent, recreated not-sent and explicit repaired
ack. Other5regressions pass. Durable ticket21/not-sent-green8hashes. Both
bounded source and explicit-port fix reviews clear; this runtime predates
fixture-only3609e3b. Next single native case exercises independent reports
to both approved destinations, official receipt recreation and owner unknown
recovery/exact retry, with no automatic reroute or resend. Production unchanged
for this additional acceptance case; no deliberate RED defect is presumed.

Bounded isolation Spec review adds a fixture sensitivity check: both destination
reports must have the same signer, self-only recipients and original target;
final displayed and saved wallet must retain that signer. Earlier bytes-only
distinction could miss an identity-change regression. No production identity
defect is claimed. Current runtime38174100403 predates these added assertions;
final paired runs must execute them.

## Backend isolation GREEN and next actual process boundary

Android10 run38174100403 at068af0b:7PASS49.455s. Root5originals/
separate5XMLs show owner unknown, official consent/ack, restored owner unknown
and exact owner retry ack. All earlier6pass, including explicit-port repair.
Durable ticket21/backend-green12hashes. Both identity-sensitivity fix reviews
clear; runtime predates4a3ebee stronger signer assertions, later runs must
execute them. Next single boundary check deliberately kills the Android process
after the peer receives the actual report but before acknowledgement. Host
checks pending phase published after native guards/capture, original PID absent,
retained record metadata, then a separate cold process preserving wallet/backend
and requiring unknown/no auto send/exact explicit retry. Checkpoint is public
transaction/identity only. Recovery never clears fixtures or rewrites selection.
Product source unchanged; no presumed defect or process success claimed yet.

Process bounded review: Spec0 confirmed findings, Standards1 source-supported
fixture synchronization risk. Replace blocking latch await after asynchronous
Send with bounded Compose ui.waitUntil observing the same real receipt latch,
keeping the5s deadline. No receipt timeout is claimed observed. Current
38174505849 at0d59cd3 predates this preventive fixture correction.

## Actual process fixture first run stopped before kill

Run38174505849 at0d59cd3: ordinary7PASS50.598s, now including
stronger4a3ebee signer/active-wallet assertions. Process first phase1FAIL8.22s
at59 blocking5s receipt wait. Root original/separateXML show confirmation
still present; receipt checkpoint/pending kill/cold recovery not reached.
Durable ticket21/process-fixture-red5hashes. This supports the reviewed timing
hypothesis but does not establish its cause or a production defect. Both
7ab3387 bounded synchronization-fix review axes0; rerun corrected fixture
with same5s receipt deadline. Product unchanged.
