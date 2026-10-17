# Current implementation handoff

Updated2026-10-17. Owner authorizes continuing remaining issues sequentially.
#42 is the sole implementation, review base
`5a048524d8681781c1763f850cc3e0773b8e753c`. #32 depends natively on#42 and stays
open. Its earlier overall review base was dd89bc9108eb002bb260eee709c656cd45348940;
dependency#24 remains closed.
Preserve unrelated untracked `docs/research/NATIVE_GROUP_CHAT_CONTRACT.md`.

## Latest owner scope

The owner explicitly asked to stop cybersecurity requests. No Daybreak application
or cybersecurity service/account request was submitted. Stop separate research
and targeted campaigns in that area; continue ordinary app functionality and
correctness. The optional extra test and dedicated dispatch choices were removed
at50ac874d. Subsequent cases cover ordinary app functionality. Keep existing
app fixes.
Do not retry, rename or reroute blocked work. AGENTS.md records this instruction.

The ChatGPT interface displayed a content-blocked notice. Root explained that it
cannot switch that platform notice off or guarantee absence, and pointed to official
feedback guidance. A screenshot does not identify an exact rejected tool action.
Do not invent one. Repository state was verified before ordinary app work resumed.

## Current gateway implementation

#42 is assigned to the sole writer after accepted SDK preparation. Native blocker
count0; #32 is natively blocked by#42. Fixed base5a048524d8681781c1763f850cc3e0773b8e753c.
First vertical slice: public HTTP wallet-signed subscription, retained across a
server-instance restart, then retired using installation authority without the
outgoing wallet secret. Parent controlled protocol/wallet seam, not a new campaign.

Tracer6bb9175e: actual local gateway-only RED43s at firstchallenge expected201/got501,
oneFAIL. Later bind/restart/status/retirement/retry tails unexecuted. Retained at
[subscription RED](../evidence/ticket42/subscription-red/README.md),3 hashes and
four committed source byte hashes. Tracer/RED reviews report Standards0/Spec0.
Core tests were not run by that gateway-only RED task. No process-crash proof.

Minimum source now implements challenge/bind/status/revoke and durable exclusive
file state, idempotent exact retransmission and retirement tombstones. Initial
core/gateway task PASS35s. Bounded executor, canonical origin and constructor-error
cleanup changes PASS39s (exec81310 ended). Final ordinary HTTP/storage error
classification and CI gateway-test/cache wiring PASS34s (exec84118 ended).
Source review atc3abb7a6: Spec0/Standards1, numeric request conversion incorrectly
classified as503. Correction maps invalid request integers to400 while stored
record decoding stays an IO/503 failure. Corrected core/gateway PASS36s, exec48363 ended; no owned local build.
Corrected source41cf0c76 reviews Standards0/Spec0.
[First local GREEN](../evidence/ticket42/subscription-green/README.md) retains
7 hashes and exact source metadata. Final first-slice evidence reviews atc35cb6be report Standards0/Spec0.
Exact c35cb6be source CI38991601083 SUCCESS, including gateway/core, release and lint. [Current contract](../PUSH_GATEWAY.md) describes
the implemented first slice and explicit remaining deployment/runtime limits.

Current renewal tracer adds one ordinary HTTP journey, sourcebcf0ca97. Actual
local gateway-only RED28s:2completed/1PASS(original)/1FAIL(newrenewal), current
signed renewal expected201/got409 atline131. New setup bind passes; later stale
request/currentstatus/idempotency/post-restart renewal tails are unexecuted.
[Retained RED](../evidence/ticket42/renewal-red/README.md),3hashes/7sourcehashes.
Renewal minimum now passes39s, core task up-to-date and gateway2/0/0; exec18711
ended. Same subscription accepts fresh proof to renew FID/backend/categories/expiry
while owner/installation/revocation hash remain fixed. Per-runtime nonce ordering
rejects a delayed older request; a new runtime requires a fresh challenge. Exact
retransmission remains idempotent. Source reviews at1d72826a report Standards0/Spec0. Retained
[renewal GREEN](../evidence/ticket42/renewal-green/README.md), fourhashes and
seven source hashes. Final evidence reviews atcc332826 report Standards0/Spec0. No owned local build.
Standalone tracer83cf14dc, actual gateway-only FAIL51s: old2PASS/new1FAIL at
GatewayProcessTest:89, missing readiness because no-op main exits. New HTTP/bind/
process-death/restart tails unexecuted. [Retained](../evidence/ticket42/process-red/README.md),
fourhashes/ninesourcehashes. Minimum adds application distribution, strict local
command flags, explicit loopback port and shutdown lifecycle. Initial core/gateway/installDist PASS44s, exec33237 ended. Source review found
Standards2/Spec1 fixture issues. Correcteda71e70f5 preserves primary startup error,
forces bounded cleanup after failure and observes child alive before forced stop.
Reviews now Standards0/Spec0. Corrected build PASS33s, gateway3/0/0, coreUPTODATE114,
installDistUPTODATE; exec29335 ended. No owned local build. [Retained GREEN](../evidence/ticket42/process-green/README.md),
sixhashes/ninesourcehashes. Final evidence reviews atc71038b3 report Standards0/Spec0. No deployment.
Next tracer is a portable client against the real owned HTTP gateway: dropped
bind acknowledgement, reconstructed journal, replacement wallet and keyless
retirement/opt-out. It is not Android Keystore or actual Firebase proof.
Client tracer9c77b33a, actualcore/gateway FAIL1m26s: corefresh114/0/0, gatewayold3PASS/
new1FAIL atSubscriptionClientTest:64. Placeholder performed no HTTP, so dropped
ack/recovery tails unexecuted. [Retained RED](../evidence/ticket42/client-red/README.md),
sixhashes/eleven source hashes. Portable minimum now persists installation/retirement
authority before binding, retires old owner/origin before replacement, and checks
fresh demand around binding. First minimum d71defab PASS1m24s, corefresh114/gateway4/0/0; exec64223 ended.
Source review Standards1/Spec2: lost retirement acknowledgement needs durable
retiring intent; fresh null demand must retry instead of silently succeeding; final
opt-out assertion must target newest binding. Strengthened tracer226530ee actual
FAIL35s atSubscriptionClientTest:46 expected201/got410 on same-wallet opt-in after
lost revoke acknowledgement. [Retained](../evidence/ticket42/client-retirement-red/README.md),
fivehashes/eleven source hashes; previous tails through lost revoke ran, later
reenable/newestoptout tails unexecuted. Correction persists retiring phase before
request, completes it before opt-in and signals retry on fresh null demand with
retained authority. Corrected core/gateway PASS1m29s, gateway4/0/0/corefresh114/0/0; exec23936 ended.
Source2a1d774d reviews Standards0/Spec0. [Retained GREEN](../evidence/ticket42/client-green/README.md),
sixhashes/eleven source hashes. Final evidence reviews at724a8455 report Standards0/Spec0. Native adapter tracer8b0f03f9 is now committed, stub no-op plus real-device
PushBindingPreviewTest and first gate in existing social-push/full harness. Local compile19129 PASS3m53s, core/gateway tasks up-to-date, debug/test APKs built;
exec ended. No runtime RED or minimum yet. Tracer reviews Spec0/Standards1 fixed:
fail-fast PUSH_CONFIGURED=false now precedes fixture mutation, so consent writes
cannot schedule real SDK work. The one-line correction awaits actual CI compile.
No owned local build. Client source CI38993005106 at724a8455 SUCCESS, retained
at../evidence/ticket42/client-source-ci (2hashes). Corrected tracer60d00271 now
reports Standards0/Spec0 and is remote main. Source CI38993468005 SUCCESS, exact60d00271. Android10 intended RED38993468083
FAILURE0.205s atPushBindingPreviewTest:103, missing existing-wallet binding.
Resultphase initial-binding/binds0/revocations0/SDK29. Old11UI/worker3 and later
new wallet/backend/FID/opt-out tails unexecuted behind first gate. ADBexit0 is not
JUnitpass. [Retained](../evidence/ticket42/native-binding-red/android10/README.md),
7hashes/22sourcehashes; sourceCI separately retained. Native minimum now implements encrypted device journal, bounded configured HTTPS
adapter/debug-emulator-only local fixture transport, shared fresh push identity
and registration-worker hook. Opt-out attempts keyless gateway retirement before
SDK initialization/outstanding-task drain, then still attempts SDK unregistration;
no early unregistered-SDK success can skip remaining gateway cleanup. First source62e7fe9b reviews Standards0/Spec1: requested categories ignored Android
channel choices. Owned build18265 was stopped at exact owned container after this
concrete finding, actualexit143, not a pass or runtime failure. No other container
touched. Correction derives canonical enabled social categories from actual OS
channels, treats uncreated channels as default enabled, and shares the set across
SDK demand/freshness and gateway binding. Both disabled means no social demand.
Publication's final channel check is unchanged. Corrected source17fb7260 reviews Standards0/Spec0. Exactsource17fb72605cf6e3eb740cbaba73b429a2b5de1bfa sourceCI38994268560 SUCCESS,
release/lint/core/gateway. Native10 run38994269120 SUCCESS: binding1/1.235s,
oldUI11/84.307s, worker3/46.275s. Native17 run38994270968 SUCCESS: binding1/11.810s,
oldUI11/115.412s, worker3/47.324s. [Retained GREEN](../evidence/ticket42/native-binding-green/README.md),
14+15+2+3 hashes,23 source hashes per device. Root parsed2XML/viewed2actual opaque
alert originals; new binding HTTP/storage journey has no screenshot. Core114/0/0
reports; task cache states in actuallogs. Individual channel choices and configured
SDK worker hook are reviewed source, not executed OS toggle/liveSDK proofs.
Corrected duplicate localbuild2522 retired only after all three exact-source hosted
runs completedSUCCESS, actualexit143, never a localpass. No owned local build.
Final evidence reviews report Standards0/Spec0 after the one stale native-GREEN
denial was corrected at9c0e9168. Next slice is remaining gateway observer/events/
resolver/sender; do not close42/32 or repeat unchanged native runs. No configured SDK or Google-delivery claim.
New ordinary API research FCM_GATEWAY_SENDER_2026_10_17.md records FID payload,
ADC scope/permission, error/retry semantics and candidate auth library1.54.0;
no dependency, credentials or account operations yet. Prior native UI11/worker3 assertions unchanged behind new gate.
Earlier standalone CI
38992493829 atc71038b3 SUCCESS, separately retained; no client CI claim.

Android gateway binding is accepted at the controlled HTTP/storage seam above.
Resolver, continuous observer, FCM sender, physical retention cleanup and
whole#42 acceptance remain unfinished. Existing owner
Firebase setup remains pending without repeating the question. No deployment,
node/account/IAM or live-write operations. Protected untracked group contract stays
untouched. Public signed beta24 unchanged.

## Current bounded Archive read

Tracer0cba443f has actual intended RED1m15s, exit1: gateway old4PASS/new1FAIL
atGatewayArchiveTest:87 (expected3/got0), first OWNER loop. OFFICIAL and later
assertions unexecuted. Core task UP-TO-DATE. [Retained](../evidence/ticket42/archive-red/README.md).
Minimum9967ff40 adds owned-identity authenticated read-only WebSocket requests,
bounded service/response handling, strict UTF8 and signed root/reply/mention
parsing. Its owned local build52476 finished PASS50s, gateway5/0/0 including both
selector loops (new0.888s); core UP-TO-DATE. [Retained](../evidence/ticket42/archive-green/README.md).
Independent source reviews are pending.
No continuous observer, candidate eligibility, durable event or delivery claim.

## Accepted #32 foundation

The accepted scheduled-receipt foundation uses real WorkManager,
selected-Archive retry and an actual Android alert without an Activity.
[Canonical verification](../evidence/ticket32/scheduled-receipt-green/README.md)
records both passing SDKs at exactc69a1896d01b23c6bb41b3f9e248410517017ac7:
- Android10 run38981302625: old11PASS84.608s plus worker1PASS12.005s.
- Android17 run38981305637: old11PASS96.847s plus worker1PASS11.792s.
- Source38981302496 SUCCESS includes release/lint; both native runs core114/0/0.
- Root viewed6new GREEN originals and parsed6XML. Earlier11 screenshots are not
  duplicated; their exact passing raw logs remain retained.

Minimum uses CONNECTED/10s exponential backoff, an execution-time7day processing
expiry and the shared receiver's fresh wallet/backend/policy/signed-Archive/ledger
checks. Clock rollback conservatively retains processing; this does not establish
WorkManager-history deletion after7days. Enqueue returns the actual persisted
unique-work UUID, including pending duplicates. A shared execution mutex is drained
after owned cancellation before fixture files/preferences/wallet/backend restore.
The native journey checks pending duplicate identity, actual recovered alert,
completed duplicate's unchanged OS postTime, no Activity, fullwallet/backend,
zero outgoing writes and scoped restoration.

[First attempt](../evidence/ticket32/scheduled-receipt-group-observation-failure/android10/README.md)
38979224052 remains FAILURE:10/11PASS82.607s, group editor observation before the
worker case. The correction explicitly compares exact full EditableText.text
rather than AnnotatedString metadata, keeping15s/finalassert and persisted
text/photo checks. Installed1.11.4 bytecode confirms those comparisons differ;
the actual old predicate-time cause remains unknown.
[Intended RED](../evidence/ticket32/scheduled-receipt-red/android10/README.md)
38980190191 at ee1bc69b: old11PASS83.64s, worker1FAIL1.88s rejecting the no-op
worker's SUCCEEDED state. Later newcase tails did not execute in that failed run.

Corrected source reviews0/0 at1a62909d; pre-review minimum local core/debug/test
PASS3m20s (exec76439 ended). The simple review fix is compiled by exact-source CI.
No owned local Gradle build remains. Bounded review base8e6498a2; minimum base
ee1bc69b. Final evidence review at2fd7079e found only stale current-handoff wording,
resolved at81624363 with final Standards0/Spec0. This bounded local slice is
accepted; actual timeout/process/external gates below remain.

The additional[unanswered-Archive observation](../evidence/ticket32/scheduled-timeout-green/README.md)
now passes on both SDKs at5463170d091ed58f3ad90686c184ca0875cea08f:
10run38982505303 old11PASS90.863s+worker2PASS35.448s;17run38982507198
old11PASS128.618s+worker2PASS35.981s. Source38982505012 SUCCESS release/lint.
Default-false withholding suppresses only actual Archive response emission after
its normal callback observed/validated the request. The withheld case checks real
≥10s elapsed from submission to retry, then full recovery/duplicate/alert and
restoration assertions; scheduling/authentication are included in that interval.
The peer timer is12s inside15s verification, not proof the outer15s specifically
fired. Same150s/180s class limits, no fake clock or shortened deadline. Production
trees c69→546 exactly equal. Root6timeout originals/12XML; malformed-mode captures
also retained with passing assertions/logs. Source reviews0/0 at0f50c2a4; final
evidence reviews at9663f3a5 report Standards0/Spec0. The bounded timeout
observation is accepted. No owned local Gradle build or SDK/config/live change.

The additional[actual-process observation](../evidence/ticket32/scheduled-process-green/README.md)
now passes on both SDKs. Original17 run38984566019 at57ffd6f8: scheduled PID4482→4741,
restorer0.835s. Corrected10 run38985316045 at47be60d8: PID3804→4054, restorer0.874s.
Both core114/0/0. Original/corrected source38984556683/38985300921 SUCCESS release/lint.
Both also retain the preceding dismissed-replay process journey/restorer. Root
viewed4 new GREEN originals and parsed12 XMLs;41 hashes per SDK plus3 root metadata.

The byte-identical shared test-only peer runs in a bounded host JVM, survives app
death and is reached through an owned reverse mapping. Both prove pending retry,
actual death, distinct PID, post-death available reads and exact visible Android
alert without target instrumentation/Activity or forced job during observation.
Cleanup follows observation, stops the target, cancels/drains owned work and
independently restores device-only originals/full wallet/backend. Source and17
evidence reviews report0/0; combined final reviews atcae9abe5 report Standards0/Spec0.
This bounded autonomous-scheduling observation is accepted.

[Initial10 failure](../evidence/ticket32/scheduled-process-stop-failure/android10/README.md)
38984563727 at57 remains FAILURE at external run-as kill exit1, exact cause unknown
(stderr not retained). New setup reaches pending PID3819, recovery tails unexecuted,
restoration passes. Root viewed1 new failure original/5XML;32 hashes. The marker
correction1738159f/794d145c requests only the waiting fixture's own termination,
matching established tests;10 validates acknowledgement and actual gone PID.17
keeps its actual original external-stop pass, not a claimed later-source run.
Eight production source/build objects57→47 are identical. No additional unchanged
native rerun is needed; final full#32 acceptance remains required.

Local initial build5m32s/corrected2m27s/core114 and isolated host readiness/stop smoke
pass. Later marker source is compiled by exact CI. No owned local build remains.
## Current opaque wake processing

Same-ticket transport step, review base68e420ee. [Intended RED](../evidence/ticket32/opaque-wake-red/android10/README.md)
38986422596 at4f56e70e: existing11PASS87.424s; worker3 reports2PASS/1FAIL37.422s,
new opaque case rejects no-op SUCCEEDED. Existing Archive journeys pass. New
request/auth/recovery/alert/dedup/preservation tails are unexecuted in this RED;
recipient-proof assertions452f4595 were added afterward, not proven by that run.
The21 hashes/7XML are retained; root viewed only the new failure original. Its
PNG IHDR is320x640 and XML records Launcher; preview showed only a thin statusbar,
so no rendering cause is inferred. Source38986388089 SUCCESS release/lint/core114.

Minimum861e795c now resolves the opaque wake over HTTP, using a local recipient
signature over literal domain/full resolver URL/wake. The future gateway must
match signer to wake recipient before returning the strict public reference.
Body remains onlyversion/wake. It retries outages, bounds response1024/HTTP12s,
checks current consent/identity/backend, then reuses the existing signed selected-
Archive verifier and shared publication mutex. Processing expiry7days, fresh
policy/cancellation checks, HTTPS/no redirects, debug+emulator127 fixtures only;
archive overrides are fixture-only. No new user-configurable backend.

Source reviews at861e795c find0 source defects; the handoff correction5aac5d04
now reports Standards0/Spec0. Local minimum core/debug/test build passes3m10s
(log .scratch/push32-opaque-minimum-build.log, exec74616 completed). No owned local
build remains. Exact2685f5fc Android10 run38987459024 SUCCESS: old11PASS86.777s,
worker3PASS46.679s. Android17 run38987461150 SUCCESS: old11PASS124.78s,
worker3PASS47.545s. Both core114/0/0; source38987439730 SUCCESS release/lint.
[Combined evidence](../evidence/ticket32/opaque-wake-green/README.md) retains51
per-device hashes/18XML plus source metadata. Root viewed6 new opaque originals;
12 earlier-mode originals retained without claiming new visual inspection. Full
native assertions and scoped restoration pass. Final combined evidence reviews at22d65f7b report Standards0/Spec0.
The bounded local opaque-wake slice is accepted; next is SDK/client preparation. Tracer local compile failed2m26s at
nullable request body, corrected1m47s pass. Tracer/correction reviews at553df5b0,
dc48e398/452f4595 report0/0. Existing150s worker class retains both Archive journeys.

The[entry-point research](../research/NATIVE_PUSH_TRANSPORT_ENTRY_POINTS_2026_10_17.md)
and[manual setup research](../research/FCM_MANUAL_INITIALIZATION_2026_10_17.md)
prepared the SDK/client work below. Opaque acceptance itself did not add the SDK.
Local scheduling/resolution is not proof of incoming FCM/locked/Doze/Pixel/full#32.

## Current Firebase client preparation

Bounded source base4f7000c29463a044a2060887bfdcd4d28a21cf25. Actual Messaging26.0.0
now added with per-variant public build options, manual default-app initialization,
removed FirebaseInitProvider, explicit FID mode, automatic registration/analytics/
default collection/delegation disabled. All options absent leaves it unconfigured;
partial options or declared package/appId/sender mismatch fail the build.
[Configuration and lifecycle](../PUSH_CLIENT_CONFIGURATION.md) lists the options
and explicitly distinguishes syntax validation from real project association.

The Application supports cold service initialization without wallet creation.
The messaging service accepts only data version/wake, using the configured
resolver and awaiting durable existing wake scheduling. The registration worker
serializes explicit SDK tasks, encrypted installation phase/FID/time, pending
phase before each request, fresh wallet/backend/consent/OS-permission demand,
post-task demand reread, daily refresh and callback-triggered corrective work.
Callbacks do not blindly overwrite the stored phase. Hooks cover committed social
choice, permission result, startup/resume, wallet import/recovery and backend state.
No signing wallet material is retained in installation registration storage.

Initial local build FAIL23s at Gradle java.net name shadowing, corrected explicit
URI import. Corrected build compiled debug/test code, then release lint failed on old
transitive Fragment1.1.0 at existing wallet ActivityResult calls. The remaining
owned build was stopped (exit143), not claimed PASS; log
.scratch/push32-sdk-client-build-corrected.log. Fragment constrained1.8.9 for
compatibility with current Activity/Lifecycle, no lint suppression. Merged debug manifest confirms
service/metadata and removal of FirebaseInitProvider; no runtime result claimed.
Initial source reviews at258ec4f7 found Standards1/Spec2: latest demand blocked
by APPEND retry backoff and waiting timeout releasing an unfinished SDK Task.
Correction uses REPLACE for current demand, matching-callback suppression and a
retained outstanding Task drained under the execution lock before another SDK
operation. Durable phases survive process loss; configured remote behavior remains
unverified. Corrected source reviews at2310e0df report Standards0/Spec0. Local
core/debug/test/release/lint PASS10m15s (exec93880 completed). No owned local build
remains. Exact2310e0df source38989625277 SUCCESS release/lint; Android10
38989625467 SUCCESS11/87.193s+3/46.541s; Android17 38989627211 SUCCESS11/129.468s+
3/47.423s. Both core114/0/0. [Retained source-preparation checkpoint](../evidence/ticket32/firebase-client-preparation/README.md)
contains24 hashes/2XML, two actual alert originals root viewed. Other assertions
pass in retained logs; remaining captures stay raw, not newly claimed viewed.
Merged debug/release manifest and default config checks pass, SDK AAR matches
prior independent binary. Final committed-evidence reviews atbf75f7e1 report Standards0/Spec0;
this bounded SDK source-preparation checkpoint is accepted. This is source
preparation, not a new TDD RED/GREEN or configured Google delivery proof. No new
mock SDK/internal tests were added in place of actual external transport.

Owner Firebase setup question stays pending, do not repeat it. No real project
options/account/IAM/sender credentials or deployed gateway have been supplied.
Signed gateway binding/revocation, observer/events/reconciliation and actual Google
delivery remain unimplemented. Task timeout/rotation/callback/process behavior
still requires configured runtime verification, both backends/fullSDKs/Pixel too.
No node/deployment/account mutation or new beta; signed beta24 unchanged.

Required separate[gateway ticket#42](https://github.com/robelene112/redsquare-android/issues/42)
is now published and linked as a #32 sub-issue. It is ready for the next sole implementation after this accepted SDK checkpoint.
No parallel ticket writer. Source/local verification only;
production deployment keeps its separately authorized boundary. #42 is now the
sole implementation after this checkpoint; the earlier ready status is historical.

The full-harness omission is corrected at1037dc95: full now invokes existing
social UI/work checks then both process helpers. Focused default behavior/app/core
are unchanged. bash -n and source reviews Standards0/Spec0 pass. The new full
invocation is unexecuted; exact2310 native evidence does not claim that wiring.

## Earlier accepted username slice

[Verified username acceptance](../evidence/ticket32/username-mention-green/README.md)
retains intended RED38975655606, first minimum failure38976627865 (username passed,
group observation failed), corrected10 run38977710046/78e4172d11PASS81.609s and17
38976630590/7824504111PASS124.127s. Both notification compatibility runs
38976632886/38976636125 pass32read cases, applicable permission choices and actual
read-history recovery PIDs6113→6203/5326→5398. Production equivalence782→78 and
source release/lint38976622437/38977709969 are retained. Final evidence reviews0/0
at3f3c5bb4. Root10username originals+groupdraft+5permission/recovery originals,
allXML parsed. Loading/failed history and earlier failed-run tails remain qualified.

## Accepted bounded #32 work

These are controlled-transport slices. The scheduled receipt includes actual
process death/autonomous restart, while incoming FCM remains unimplemented.
Every prior failure retains its actual conclusion and qualified limits. Full raw
chronology is in the historical handoff; linked evidence is authoritative.

| Behavior | Canonical evidence |
| --- | --- |
| First reply alert | [Reply GREEN](../evidence/ticket32/reply-alert-green/android10/README.md) |
| Dismissal replay and actual process recovery | [Replay](../evidence/ticket32/dismissed-reply-green/android10/README.md), [process](../evidence/ticket32/dismissed-reply-process-green/android10/README.md) |
| Warm reply tap and post draft/photo | [Tap GREEN](../evidence/ticket32/reply-tap-green/) |
| No-Activity tap/recreation/consumed route | [Cold tap GREEN](../evidence/ticket32/reply-cold-tap-green/) |
| Public-key mentions | [Mention GREEN](../evidence/ticket32/mention-alert-green/) |
| Wallet-scoped alert choice | [Choice GREEN](../evidence/ticket32/alert-choice-green/) |
| Actual Android category shortcut | [Categories GREEN](../evidence/ticket32/android-category-controls-green/) |
| First-use social consent | [Consent GREEN](../evidence/ticket32/social-consent-green/), [compatibility](../evidence/ticket32/social-consent-regression/) |
| Private Chat draft/photo through social tap | [Private acceptance](../evidence/ticket32/private-chat-reopen-green/README.md) |
| Group Chat draft/photo through social tap | [Group acceptance](../evidence/ticket32/group-chat-tap-green/README.md) |

Latest group minimum0b5dc394, exact runtime source4419fd1a: social10 run38973726401
10PASS78.837s, social17 38973729273 10PASS103.434s. Group compatibility
38973732283/38973734828 each26 final completions (actions1/mixed1/group22/recovery1/
restoration1), actual PIDs13930→14322 /5236→5318. Separate source38973706184
SUCCESS release/lint; localminimum3m45s. Root14originals/167XML, both final evidence
reviews0/0 at041de29f, finalizedec025f6b. Loading destinations, delivery-unconfirmed
forwarding and intentionally unavailable-history recovery are qualified. No
unchanged rerun needed. Management dialogs keep their current work deferred.

Private photo failures and follow-ups remain qualified in canonical acceptance.
The earlier Android10 16MiB OOM cause remains unknown; later actual storage process
maxMemory is512MiB. A pass does not establish an earlier failure's cause. Additional
instrumentation status0 diagnostic messages are progress, not extra passing cases.

## Remaining #32

Native-listed mention proof, receipt permission/category controls and
recovery, other mounted forms, registration, real transport and full Android10/17
acceptance remain. Legacy unguarded forms retain work with Retry. Local injection
cannot close#32; the new configured SDK entry has no real cloud runtime evidence.
Firebase project/IAM and gateway setup remain external; the existing owner
question stays pending. Do not repeat it. Production gateway deployment/node
changes require concrete bounded separate authorization, after reviewable work.

Transport references: [local seam](../research/SOCIAL_PUSH_LOCAL_SEAM_2026_10_15.md),
[closed-app requirements](../research/NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md),
[observer](../research/SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md),
[Archive discovery](../research/SOCIAL_PUSH_ARCHIVE_DISCOVERY_2026_10_15.md),
[future Chat transport](../research/CHAT_PUSH_EXISTING_TRANSPORT_2026_10_15.md).
No public/private wallet data or live-node changes are authorized by fixture work.
Later source-only[FCM API observation](../research/FCM_ANDROID_API_2026_10_16.md)
confirms new FID APIs in current Messaging26.0.0 and server fid target, superseding
only the dated token API observation. That research added no SDK/config/service;
current client preparation is described above.

## Current public build

#41 is CLOSED. Canonical [final acceptance](../evidence/ticket41/FINAL_ACCEPTANCE.md)
records full Android10/17 gates and final review results.
Signed beta24/0.1.24 is published and verified at https://davidengelen.nl/redsquare/.
APK SHA256 `7d6e8e746f3d1cb68969e302245d6868bb4edcdd6cb1560ed6fe5344d97f8c16`.
No version25 is reserved. Keep signing material outside Git. Use
[review-build operations](../REVIEW_BUILDS.md) for the next authorized publication.

## Other remaining issues

| Issue | Current condition / next useful work |
| --- | --- |
| #16, #31 | Giphy Android key pending owner setup; question already asked. |
| #17 | Real native YouTube Play fails error150. Preserve failed run38791958201. Prepared bounded provider-reason observation is `.scratch/video17-next-native-observation.md`; unchanged rerun adds no evidence. |
| #19 | Owner Registry name checks time out despite authenticated peer/key mapping. Frozen pending concrete cause/change. Canonical `docs/evidence/ticket19/FINAL_REGRESSION_CHECKPOINT.md`. |
| #33, #34 | Depend on#32. Chat transport source audit above is preparation only. |
| #35 | Integrated acceptance and actual Pixel7 startup/update checks remain. Emulator results do not prove physical targets. |
| #36 | Real publisher/legal/contact/retention/declarations/submission facts remain. Read the Play research documents before release work. |
| #38 | Branding decision, not a release blocker. |

Parent#1 remains open. Original35 tickets:26closed,9open (#16,#17,#19,#31–#36).
Extra#37,#39,#40,#41 are closed. Fetch actual tracker state before changing claims.

## Evidence and environment traps

Large artifacts go under `/mnt/storage/redsquare-android-artifacts`; verify the
mount/space before large writes. Build instructions are in the operations docs.
No local KVM: follow
[local feasibility](../research/LOCAL_ANDROID_TEST_FEASIBILITY.md), use hosted KVM.
Preserve current pins. Source edits wait until any owned local Gradle build finishes.

Some earlier image previews contradicted the actual PNG bytes. When that happens,
use original hashes, XML and independent read-only pixel decoding. The corrected
[dismissal RED package](../evidence/ticket32/dismissed-reply-red/android10/README.md)
retains decoded observations and the reproducer. No source image was edited.
Do not infer a black UI or an Android rendering cause from a contradictory preview.

For earlier ticket chronology, source provenance, failed attempts or previous
session IDs, read [historical checkpoints](IMPLEMENTATION_HISTORY_2026_10_16.md).
The snapshot before the username minimum preserves the previous handoff verbatim.
Historical active/pending labels are not current authority.

