# Separate protocol tracer reviews

Fixed base bbdd121c, initial source through73facc22. This is a bounded creation
protocol slice, not #26 completion. Invite/join/leave/UI/recovery remain pending.

## Standards

One receiving-boundary validation breach: readCreation built roles from all signed
output bytes without checking each was a valid Saito public key. Signature
verification authenticates bytes, not recipient curve validity. Saito skill field
validation and repository compatibility rules apply. No actionable Fowler smells.

## Spec

One member-view validation gap against compatible membership rules: a malformed
recipient could enter the role map despite a valid creator signature. Signer/admin
equality and authentic discovery for an unjoined reader otherwise matched the
bounded behavior. Reference limits and future-work qualifications are accurate.

## Correction

Generated an actual WASM/Elliptic signed creation containing one deliberately
invalid 33-byte zero point. Native envelope authentication succeeds. RED4cases,
3PASS plus new rejection assertion failure because nothing was thrown; build35s.
Receiver now validates every public key before publishing roles. GREEN4PASS;
build57s. Prior signature/admin and creation/discovery assertions remain intact.
Bounded correction reviews73facc22...62106e18: Standards0 and Spec0.
Both independently confirm the prior finding is resolved, original assertions
remain intact, hashes and RED/GREEN results match. No residual bounded findings. No app UI or complete-ticket acceptance.

## Invitation slice,058460d3...b75e4592

Standards: zero documented breaches/actionable heuristics. Input/decoded bounds,
URI structure, strict UTF8/JSON fields, public key validation and raw '+' behavior
are appropriate. Returned claims are unsigned; no wallet/backend/membership state
changes. Static cause-free failures protect pasted contents.13updatedmanifest
hashes and RED/GREEN results agree, including documented unchanged RED repeat.

Spec: zero actionable findings. Actual pinned link/codec vectors support stated
compatibility, while link names and roles are not represented as authenticated.
Malformed/duplicate input is recoverable; old signed creation vectors stay separate.
Joining, authority, persistence, groupUI and whole-ticket acceptance remain pending.

## Join and retained controls,86734261...66d9df30

Join: Standards0 and Spec0. Leave/control/restore: Spec0; Standards0 documented
breaches and one possible Duplicated Code heuristic in the independent join/leave
generators. Extracted their shared source verification/VM/wallet/signing setup to
control-reference.cjs. Both public corpora regenerated offline with byte-for-byte
comparisons passing. Native source and acceptance assertions are unchanged.
This bounded refactor is being independently reviewed, not full #26 acceptance.

Correction review66d9df30...05dc3be1: Standards0, previous duplication heuristic
resolved; Spec0. All seven manifest hashes agree. Both preserve fixture-specific
wire proof limits and distinguish unfinished Android/live/whole-ticket acceptance.

Confirmed-invitation origin reviewa2990aeb...c28cad6e: Standards0 and Spec0.
Unsigned provenance remains explicit, receipt/signer validation and replay survive,
no sender membership invented. Active-wallet binding and deliberate confirmation
remain controller responsibilities; no network admission or full UI claim.

Message reviewc28cad6e...b0b9876e: Standards0; Spec1. Native reader used the body
timestamp, while pinned addTransactionToGroup starts from the signed transaction
header timestamp. Original equal-time vectors could not expose this. An actual
constructor reference with different header/body times is being added before
correcting the reader. No claim that the initial review was clean.

Snapshot reviewb0b9876e...f6a6da41: Standards0 hard, possible active-role
duplication; shared role predicate now centralizes three actual uses. Spec1: the
255-entry role-map ceiling was incorrectly borrowed from transaction slip limits.
A group can gain more members via joins, and update outputs do not list all members.
A larger valid-map regression and correction are next. Timestamp correction
f6a6da41...91c65a96 Standards0; Spec review pending.

Timestamp correction reviewsf6a6da41...91c65a96: Standards0 and Spec0, previous
timestamp finding resolved. Source time remains authenticated header; web local
arrival-order adjustment excluded explicitly. Snapshot-map size finding corrected
with256-entry RED/GREEN; active-role duplication extracted separately.

Correction reviews91c65a96...dfe60e90: Standards0, active-role duplication
resolved; Spec0, role-map limit finding resolved. Full101core precedes sizefix;
14focused group cases verify it. No runtime/whole-ticket claim.

Native tracer reviewdfe60e90...455d69ba: Standards0; Spec2 strengthened before
GREEN: compare exact normalized broadcast/Relay originals and all four invitation
claims. Recreate's retained Chat navigation corrected separately, no assertion
relaxed. First native creation implementation review/compile/runtime pending.

Creation source review455d69ba...e30f7d53: Standards1 source-supported stale
status publication race; Spec2, same race plus group work missing from wallet-
replacement disclosure. Neither is claimed as an observed emulator failure.
Ordered durable-operation/UI publication now uses the existing private-reaction
pattern; separate restore/send errors prevent reload from erasing send failures.
Pending-work tracer is being compiled before its minimum hasWork implementation.
Whole-ticket group/key/retry/join/leave/history acceptance remains pending.

Ordered publication correctione30f7d53...db2292c5: Standards0 and Spec0, source
race resolved and no reverse-lock order identified. Pending-work disclosure
remains separate; originale30native flow1PASS33.884s does not prove newer fix.

Pending tracerdb2292c5...ce808a84: Standards0; Spec1, backend aggregation used
only the selected backend. Corrected to change selection before aggregation and
restore it during cleanup. ActualRED fails earlier false-stub check; later stronger
backend/fault assertions are not claimed until GREEN. Minimum detector and wallet
import integration now implemented; compile/device/correction reviews pending.

Pending-work correctionce808a84...3a174169: Standards0 and Spec0, previous
disclosure and actual-backend-switch findings resolved in source. Local debug/
testAPK compilation PASS2m50s. Corrected native work/backend/fault GREEN and actual
wallet-import warning UI are still pending. No complete26 claim.

Key checkpoint/evidence5409d7cd...b54a7c87: Standards0 and Spec0; exact private
key comparisons have static diagnostics and own-fixture cleanup. Prior two-case
GREEN sets scoped correctly. Nullable key remains unpopulated pending actualRED;
no process-death, app interruption/retry or complete26 claim.

Key implementationb54a7c87...fb2ffefb: Standards0 and Spec0. Key/group binding,
protected preparation/read-back, no private key in public transactions and legacy
optional-key handling are sound. Local debug/testAPK PASS2m53s. Corrected native
GREEN pending; app retry/PID/full26 remain unproved.

Invitation tracer a6a9a772...fbd353ea: Standards0; Spec1, displayed inviter and
group ID lacked assertions. Added both against independent claims before Join.
Actual nativeRED4cases/28.489s has3PASS plus missingJoin control; later asserts
unexecuted. Minimum preview/confirmed-origin store/UI now implemented; compile
and nativeGREEN/correction review pending.

First join sourcefbd353ea...9690f6d6: Standards0 hard plus possible duplicated
metadata/submission/status/error lifecycle; Spec1 non-scrollable preview could
hide claims with long names. Bounded correction makes preview scrollable and
extracts actual shared send/attempt lifecycle, preserving preparation/Relay choices.
Claim display assertions now scroll to each claim before asserting visible, retaining
the explicit-consent/backend/wire checks. Initial compilePASS3m35; refined compile
pending, no native joinGREEN claimed.

Refined join source9690f6d6...ee17e184: Standards0 and Spec0, previous duplicated
submission lifecycle and unreachable long-claim preview findings resolved. Shared
prepared-send handling retains durable preparation, explicit confirmation and
creation's distinct Relay choice. Local debug/testAPK compile PASS3m43s. Native
four-case GREEN on Android10/17 remains pending; no complete26 acceptance claim.

Invitation four-case GREEN44e931af:10run38521385291 PASS16.510s;
17run38521387910 PASS46.936s. Root joined PNGs and original XML states inspected;
reopened captures show group list, not selected detail provenance. All strict
consent/backend/exact-wire checks pass. Leave tracer939ba4cf...8f46a173:
Standards0, Spec2: exact departure recipient sequence and retained signed departure
were unasserted. Correction830ed6c2 adds both; separate correction reviews0/0.
Refined testAPK compile PASS1m33s. Actual leave RED17run38538979079 active;
missing Leave control expected but not claimed before its actual report.

Leave minimum830ed6c2...c21c6fb5: Standards0; Spec1 evidence-only stale REDpending
sentence. Corrected to completed five-case RED with later assertions unexecuted;
Spec correction0. No source-level findings. Local debug/testAPK compile PASS3m41s.
Corrected native leave GREEN pending; retry/message/history/PID/full26 still open.

Leave five-case GREENcdbeef72: Android10 38540341373 PASS21.397s;
Android17 38540343832 PASS57.190s. Root original LEFT/reopened captures inspected.
Retry tracer cdbeef72...a8867c3e Standards0, Spec1 circular metadata oracle.
Independent saved-backup schema/private-scalar checks added1d258b1c; correction
Standards0/Spec0. Actual six-case RED17 38541609952 has5PASS/missingRetry;
later metadata/disconnect/retry assertions unexecuted. Minimum5a19ef41 Spec0;
Standards0 hard plus repeated verified decode/UI-thread heuristic. Correction
4743355c performs one decode on IO, reviewed0/0. Minimum debug/testAPK compile
PASS3m15s; refined Kotlin compile PASS1m31s. Corrected native GREEN active both
38543182621/38543185574, not yet claimed. No whole26 or actual PID-death proof.

Retry six-case GREEN4743355c:10run38543182621 PASS26.252s;
17run38543185574 PASS65.008s; root forwarded originals inspected/retained.
Known rejoin tracer a51d9202 reviewed0/0; RED17run38544239476 at362f97c2:
7cases/41.162s,6PASS + duplicate-group rejection. Later preservation assertions
unexecuted. Minimum81af8bb5/cf8e6897 Standards0, Spec1: retained local authority
did not determine outgoing admin targets. Initial compilePASS3m15s.

Known-routing correction/tracercf8e6897...95207cf0: Standards0; core Spec0,
native tracer Spec2: unscoped name could match underlying list and enabled Join
readiness lacked wait. Core helper RED1 UnsupportedOperationException then
GREEN3 invitation tests, builds1m3/1m4. Native testAPK compilePASS3m2.
New eight-case RED17run38546506531 at95207cf0 active. Scoped-dialog display and
bounded enabled assertions are prepared before GREEN, alongside native preview/
store authority routing correction; neither refined assertions nor corrected UI
acceptance claimed yet. No whole26/PID claim.

Known-routing native RED38546506531/95207cf0:8cases/67.047s,7PASS plus missing
retained admin atfixture90. RootoriginalPNG/XMLshowconflictingname/admin. Former
unscopedname assertion is notnameproof; later wire/recovery tail unexecuted.
Correction95207cf0...ef3647ae (source8d8e9004): Standards0/Spec0, routing and
both tracer findings resolved. NativePreview resolves retained authority off Main
before consent; preparation independently validatescanonicalname/admin/exact
recips, preservingorigin/key/controls. Modalclaims/provenance andenabled readiness
are asserted. Localdebug/testAPK compile PASS3m28s. CorrectednativeGREEN pending.

## Incoming storage publication and reconnect refinements, October 14

Production through839208f8: both axes report zero remaining findings for typed
local-storage failures, ordered publication and retaining error signals across
passive socket reconnects. A positive ACK follows durable authenticated-original
storage; local record failures yield negative ACK and visible generic storage
error. Invalid/unanchored traffic does not falsely report local storage failure.

17-case fixture refinement7cec21d5...4f437d6f: Spec initially required per-operation
ACK correlation and a positive replacement authenticated connection. Both were
added. Spec0 and Standards0 at4f437d6f; physical transport teardown is unnecessary
because old sockets are excluded from delivery and old RPC ACKs cannot match a
new operation ID. Local test APK compiles; runtime recovery initially pending.
The preceding native16 runs at955 both failed final peer health after the new
UI assertions, due to a missing Group Archive callback; retained evidence qualifies
the actual executed assertions and does not label the entire run successful.

## Historical fetched join correction

One public core tracer produced actual RED: expected LEFT, got MEMBER. Fix
0b6121dc...a699b893 rejects a newly fetched join that would undo retained departure,
marks history incomplete, and preserves strict deliberate rejoin to MEMBER.
Both axes report zero findings; full core109 passed after correction. This proves
core replay behavior, not global chronological authority, native UI or full26.

## Whole-review refinements and mixed reception, October 14

Whole checkpoint bbdd121c...e427: Spec1 foreground mode split; Standards2
malformed optional hints parsed in Main composition and queued storage-failure
signal lost by metadata-only reconnect. Hint parsing9da and stable receipt signals
f9 reviewed0/0 through8617. Native malformed-hints RED is retained and corrections
await fresh runtime. Mixed fixture has authentic encrypted/private reaction and
plaintext group originals, correlated ACKs, positive old-connection departure,
separate native content views and no outgoing submissions. Cleanup ownership
chatwork companion correction875cccd reviewed0/0.

Actual mixed RED38609275290 retained. Routing minimum e26ed211 Standards0; Spec1
remaining: private Chat group storage failures receive negative ACK but no visible
recovery notice. Authentication, routing and durable acknowledgement otherwise
resolve the earlier mode split in source. New fault tracer4d394d68 injects only
its owned group8 unfinished companion in private Chat, expects correlated negative
ACK and visible notice before exact-byte/no-replacement/positive recovery tails.
Actual warning RED38610859211 dispatched, correction not applied yet. No whole26
acceptance or group PID runtime claim.

Private-screen warning correction4d394d68...5d9ba21e: Standards0/Spec0. Stable
public-key/backend signal collector and shared wrapper preserve classified
storage errors, negativeACK and explicit recovery guidance across metadata and
private contact changes. Native corrected10/17 and whole re-review active at90e,
not acceptance yet.

Whole re-review bbdd121c...90e979e8: Spec0 actionable source findings. Standards0
hard breaches, one possible repeated native instrumentation/capture/gate heuristic.
Prior hint composition, queued receipt signals, foreground routing and private-screen
classified storage warnings are resolved in source. Still no runtime acceptance.
Correction90e...f25965c1 shares the three actual native classes across six focused/
full invocations, preserving classes, order, 180/60s deadlines, report/capture names
and exit0 plus nonempty JUnit OK. Every invocation now checks isolated emulator.
Both bounded correction reviews0; no production or acceptance predicate changes.
Local warning-minimum debug/test APK build34718 PASS2m57. Native10/17 at90e active.

Focused evidence reviewf259...7d695abf: both0,108hashes/44XMLverified. Actual10
class/PID/restorationrecordsagree. 17classOKwithhost124correctlyfailed. Prepared15
evidence7d...e488both0,5hashes/cert/appID/min29/exactAPK/productionequivalence.
StandardsrequestedoriginalSDKfailureproof, a872failureexcerpt/jobsteps added;
correction0/0 and build/device skipped independentlyobservable.
Full17 importedguidance fixturee95 correctionboth0, same15s actualdisplaywait
andunchangedassertion; no productionchange or claimedfailurecause. Finalfull17
38614860580 at03aacb31 active,10attempt2c519active, bothpublicreads.

## Final full evidence and publication

Android10 final evidence through408925f1: Standards0/Spec0,650hashes231XMLverified,
302reported301PASS+oneexistingAPI30assumption,66completionrecords20deathstages,
core110/allrelease/lint/native/publicread/privategrouprecoverygatesconfirmed.
Android17 final408925f1...3a740d0a: Standards0/Spec0,655hashes233XMLverified,
303PASS67records20deathsnoassumptions,core110/allgatesconfirmed. Rootandbothreviewers
viewedfinaloriginals. Exactgroup/privatecheckpointsandrestorationagree; production
andbuildconfigurationequalprepared15. Noremainingactionablefindingoneitheraxis.

Signed15published2026-10-14 afterbothfulls/finalreviews. Rootpublicpage/manifest/APK
HTTP200, exactdownloadhash, directsignature/package/version/minSDK checksPASS.
Immutable14retainedunchanged. Publicrecords signed-preview15-published; no node/
service/ingress/storechanges. PhysicalPixel/update/performance remain35. Owner
pauseafter26 appliesbeforeanotherissueorUIpolish.
