# Implementation handoff

## Active: issue69 only, then STOP

Owner authorized one more issue on2026-10-25. Selected69, accessible fullscreen
images; blocker58 is closed. Review base b7ec3c979e4187b7ab3a690a64962c9a0d7fa222.
No other issue is authorized. Protected untracked research file remains unread
and untouched. Public beta66 is unchanged; signed candidate has not been built.

Current production source0d9e7ca6f restores the attachment only after the real
parent window regains focus and permits programmatic focus during touch exploration.
Previous de64 fixed actual TalkBack image/zoom naming by using one unmerged
semantic control. All current source-level review findings are resolved; native
return focus is still pending. Uncommitted TalkBack test adds a5s native idle
observation after Next before explicitly focusing the new image, because the
Android17 service can automatically focus it during replacement. Original callers
and all completed-speech/assertion requirements are preserved.

Focused viewer runs40186130296/40186131893 at711d02c4a both succeeded,
including keyboard, native48dp viewport cells and normal/zero motion.
TalkBack runs40186377832/40186379374 at0d9 both failed and are retained:
10 restored Wallet despite actual input focus on the visible original thumbnail;
17 initial automatic Wallet focus interrupted the direct origin focus/speech.
Pinned TalkBack source explains that window restoration uses its own action history,
not arbitrary external ACTION_ACCESSIBILITY_FOCUS events. The next scoped probe
uses actual TalkBack Alt+Right navigation from completed initial Wallet speech to
the attachment, retains that navigation log, then keeps every original completed
speech and exact return-focus assertion. After Next, native idle bounds the
automatic image focus transition. No production workaround or tool change added.
Raw downloads are `/mnt/storage/redsquare-android-artifacts/viewer69-LABEL-RUN`;
require observer-result downloadExitCode0 before collection. Retain with
`/tmp/redsquare69-retain-focused.py RUN LABEL` (refuses overwriting packets).
No full execution has been dispatched; no signed version has been reserved.

Issue69 source includes compact zoom/reset/movement controls, bounded transforms,
image-position context, keyboard focus restoration, responsive inset-aware chrome
and160ms image/credit fades. Android10 targeted acceptance covers keyboard,
all6 portrait theme/font cells plus2 real landscape cells, actual48dp disjoint
native targets, pinch/menu movement and native normal/zero motion. Original
font200 dark portrait and light landscape images inspected and readable.

Retained predecessors describe actual outcomes:
- RED40183253293 proves missing Zoom out.
- 2e4 viewer40184158249/40184160059: incorrect global Compose focus uniqueness;
  fixed exact Dialog focus plus native parent-window focus exclusion.
- 2e4 TalkBack40184161840: helper focused descriptive child beneath SeekBar.
- 09a viewer40185016086/40185017930: all app assertions passed, screenshot
  predicate incorrectly sought text instead of content description; fixed.
- 09a TalkBack40185020237: actual adjustable parent lacked image-position name;
  de64 fixes semantics. Full completed speech remains mandatory.
- 9f viewer40185580828/40185582513: keyboard and native motion PASS, viewport
  measured painted40dp instead of native48dp. Corrected native geometry observer
  retains threshold and adds disjointness/display bounds. Both failures retained.
- de64 TalkBack1040185754450: completed contextual speech, then wrong return
  focus on Wallet. Current0d9 corrects touch-mode/native-window return.
- de64 TalkBack1740185756246: second image was automatically focused and speaking
  correct context while explicit focus action returned false. Bounded idle fix
  is prepared for the new observation; do not weaken speech/focus acceptance.

Four additive native methods are in the draft ticket69 catalog: expected536/537
identities,170/171 completions,131 archives, unchanged35 interruptions and34
continuity records. Collector and viewer-check preserve all accepted66 gates.
Both reviewers approved the draft, but catalogFreezeCommit remains PENDING.
After focused evidence inspection: freeze catalog/validators prospectively, then
run complete Android10/17, validate both whole executions and inspect originals.

Finish with paired source/native review, canonical signed in-place beta67 (next
version counter currently66) and public verification; close69/check child69 on55,
commit/push final handoff and STOP. External review-worktree is clean atoldcdd.
Helpers prepared `/tmp/redsquare69-prepare67.py`, `/tmp/redsquare69-public-check.py`
and `/tmp/redsquare69-retain-public.py`; no release code reserved yet. Verify actual
version/source before use. They preserve26 previous resources and withheld61.

## Issue66 delivered: STOP until a new owner request

The owner authorized exactly one issue. Issue66 is complete and beta66 is
published. Paired final review: Spec0, documented Standards0, heuristic0.
Do not start another issue. Protected untracked `docs/research/NATIVE_GROUP_CHAT_CONTRACT.md`
remains unread and untouched.

Both complete runs at `c26fd3cce8c27a1e575b7311455c3a3c70aa2d9d` succeeded:
- Android10:40027263194,531 passes plus the sole inherited API29 Profile assumption,
  532 exact identities,166 normal completions,35 deliberate interruptions,
  34 continuity records. Packet `docs/evidence/ticket66/full10-c26-40027263194`.
- Android17:40027266851,533 passes with no assumptions,533 exact identities,
  167 normal completions,35 deliberate interruptions,34 continuity records.
  Packet `docs/evidence/ticket66/full17-c26-40027266851`.

Both unchanged frozen collectors pass. All2384/2393 packet hashes verified.
These are whole successful executions, not an assembly of earlier attempts.
Original catalog/collector freeze07200af is unchanged; prospective new Activity
speech timing918a applies, with original callers/deadlines preserved. Native
large-text and actual TalkBack read-state captures inspected on both SDKs.
Global navigation wrapping at200% remains separate issue70.

Signed beta66 is prepared at `cddf67fc5bb43e2479dcf0d864dee3c5e2f36f82`,
production/build/harness/workflow-identical to testedc26. Do not rebuild/reuse66.
Canonical publication from external review-worktree succeeded. Delivery receipts
are in `docs/evidence/ticket66/signed66-public`; preparation in `signed66-prepared`.
Review page: https://davidengelen.nl/redsquare/ (in-place update, no uninstall).
APK SHA256: af1adb0b746f502b2ec4c06b9e507e80630ed5d7d9cae5e380951268e4e2c01f.
Historical beta65 and earlier releases remain preserved. Owner/provider/store
prerequisites remain tracked separately; they are not acceptance claims for this
native UI issue. Issue66 is closed and child66 on55 is checked. STOP.
Issues67-70 remain open. Final publication review: Spec0/Standards0/heuristic0.

## Historical issue66 checkpoints (superseded by the status above)

Latest checkpoint: complete17run40019858105 finishedFAILURE on the same new
read-event10s speech window; all preceding original checks passed. Retained
failed-full17-speech-e5b. No complete run accepted. New capture producer c5b44e1d7
adds native XML beside each control speech PNG, as required by the unchanged
frozen collector. Previous producer was PNG-only, confirmed by a deliberately
non-acceptance validator dry-run. Earlier10repeat40025793736 was cancelled before
native execution for this known artifact gap, retained cancelled-full10-hierarchy.
Focused40026242121 atc5 is wholeSUCCESS, retained speech-hierarchy-green10-c5b.
/tmp/redsquare66-check-activity-validator.py correctly rejected prior missing XML
and passes the corrected real captures. This mixed-input development check is
NOT full acceptance. Fresh whole10+17 will now run at corrected source.
Both now use the prospective15s new Activity speech bound frozen918a; old callers
remain10s. All observer sessions for old complete runs have ended. Signed66 still
held; app/build are unchanged. No earlier complete observers remain. Corrected complete run IDs will follow.


Owner on2026-10-23 authorized choosing/completing exactly one ready issue.
Selected66 activity inbox; blockers58/59/60 are closed. Review base
29253a28bc5433a460201eaf6e4997c400cbbe55. No other issue is authorized.
Protected untracked docs/research/NATIVE_GROUP_CHAT_CONTRACT.md remains unread
and untouched. Public beta65 remains unchanged and must not be rebuilt/reused.

Current implemented source fdcddb2269ca06d407dfb8d0f2f4689a40bc219d:
- ActivityRow avatar/name, event icon/caption, bounded verified preview, time,
  explicit read state; disjoint48dp author/event/menu controls and exact identity.
- Per-event Mark as read shares existing verified all-read persistence.
- Compact Activity heading/mark-all icon, history loading below the list,
  separate bounded warning summaries with Details and scrollable empty content.
- ActivityPreviews applies learned author edits/deletions on IO, observes revision
  generations and withholds mismatched previews. Retains unavailable event rows.

Focused notifications10run40007964716 at e440831d4 is whole SUCCESS: original32,
individual-read1, mixed viewport1, permission1 and actual process recovery.
Activity-only40007959383 also passes at e440. Actual100/130/200 Light/Dark
renders inspected.200% title wrap motivated later compact heading/mark-all icon.
These runs predate later warnings/state/TalkBack/revision fixes and are not final.
Original failed packets underdocs/evidence/ticket66 remain rejected, including
state40008892920 (painted40dp vs actual native48dp measurement), TalkBack40008459077
(returned-popup speech deadline) and revision RED40009487018 (learned edit absent).
State operator now measures native clickable48dp; TalkBack waits popup gone/idle,
without changing focus/speech/class bounds. Revision regression now has source fix.

Focused40010821073 atfdc is whole SUCCESS: all four actual TalkBack classes.
Retained under ticket66/talkback-green10-fdc-40010821073.40010816110 failed
viewport readiness while the safe preview still said Loading preview; retained
as rejected viewport-loading10-fdc-40010816110. Operators at8dc/14d await
resolved quote/unavailable previews within the original15s readiness bounds.
Revision40011720847 at8dc wholeSUCCESS, retained. Inbox40011716035: individual
and all six viewport cells pass; state failed because exact original lookup
returned unrelated root. Fixture atc469 filters signatures; replacement40012779269
pending. Atcdd viewport readiness also awaits enabled Refresh inside original15s.
Initial complete10run40012884531/17run40012888385 atcdd were cancelled after
focused40012779269 again failed missing target. ControlledFeedPeer routes exact
RedSquareRetweet sig reads through archiveResponse, not quoteArchiveResponse.
At82a6d39ee that fixture handler now supports exact quote lookup. No production
change. Focused state40013822813 at82a is wholeSUCCESS, retained state-green10-82a.
Complete82a runs40014676230/40014680274 are rejected/cancelled after Chat10's
original SocialPushTapPreviewTest onboarding case waited for the old Notifications
heading. Actual failure capture shows the ordinary alert prompt; retained goal is
unchanged. Exactly two stale nonclickable heading selectors (that helper and
NotificationsPreviewTest backend return) now use Activity ate5b469b9e. Other
Notifications selectors target the navigation action and remain unchanged.
Current complete10run40016684972 and17run40016688573 at exact
e5b469b9e038a70d1b545953f928bb9677b472a0 are active. Full17 Wallet failed the
unchanged PublisherViewportPreviewTest at line103, second openSupport IME10s.
Actual field is focused with no visible keyboard. Same Google image revision7
as accepted65. No cause is proven and no app/test/deadline change was made.
Focused publisher-routes-android17 run40018747907 at the samee5b is wholeSUCCESS,
retained publisher-repeat-green17-e5b. No test or production change was made.
Failed17run40016688573 cancellation requested; replacement full17run40019858105
at identicale5b is pending. Full10run40016684972 continues (WalletSUCCESS).
Observer /tmp/redsquare66-watch-full17-repeat-e5b.py tracks new17;77941 retains
old17 and current10. Never assemble partitions from rejected runs.
Other full parts continue; a failed full run cannot be accepted or assembled.
Full10run40016684972 is now wholeFAILURE: all old classes and Wallet/Social
passed; last new Activity read-event speech exceeded its new10s completion window.
Retained failed-full10-speech-e5b. Actual PID14826 utterance55 has correct full
row+Read, positiveAUDIO, START/DONE/noSTOPERROR; adjacent wall timestamps place
natural playback at10.62–10.66s. At3c406765f only new Activity event/read-event
completion windows are15s; all old callers remain10s, focus5s/class180s/pins and
assertions unchanged. Paired timing review0/0. Prospective timing clarification
refrozen918a4868f4fbb711582791f70b4ee93434758860 in activity-speech-timing.json.
Original literal catalog/collector07200af remains unchanged. Corrected focused
TalkBack10run40023104801 at3c is wholeSUCCESS, retained speech-green10-3c4.
First corrected full10run40024098277 failed the unchanged GroupInvitationRecovery
viewport case at200% dark, recover() then missing Paste invitation. Captured target
had been within viewport but the final screen remained the group list. Its exact
cause is unproven; no app/test/deadline change was made. This original class passed
in preceding full10e5b. Failed run cancelled; observer10732 retains its originals.
Fresh same-source full10run40025793736 now executes
baa1b07c351ecc96858268b6cd0157309aa49053; observer
/tmp/redsquare66-watch-full10-repeat-baa.py tracks it. No partial assembly.
The source is
after the timing clarification freeze. Observer /tmp/redsquare66-watch-full10-baa.py
downloads terminal evidence;17 observer70569 still tracks40019858105 at strictere5b.
Full17run40019858105 still runs at stricter originale5b; if it passes, preserve
that independently complete result. If it fails new speech timing, repeat full17
with corrected new-case bound. No failed partitions may be assembled.
Frozen catalog unchanged.
Observer /tmp/redsquare66-watch-full-e5b.py downloads terminal artifacts; prior
observer98062 still retains cancelled82a terminal evidence. No failed/cancelled
partition contributes to acceptance. Signed66 remains held with identical
production/build/workflow/harness; only tests/docs differ from signedcdd.
Four additive180s Social classes: ActivityInboxPreviewTest, ActivityInboxViewportTest,
ActivityInboxStateTest, ActivityInboxRevisionTest. Actual TalkBackActivityPreviewTest
is additive180s Chat. Existing identities/deadlines/tool pins retained.
Local Docker revision build log /mnt/storage/redsquare-android-artifacts/activity66-revision-build.log;
session34237 completed SUCCESS,4m51s. No local compiler remains running.

Paired source reviews throughfdc: Spec0 (prior safe-preview finding resolved),
documented Standards0/heuristic0. Reviewer agents activity66_spec_review and
activity66_standards_review available for final follow-up. No tests dispatched by them.
Complete collector/catalog frozen at07200af2bc3d35e3401c4435bfd6013267712a07
after paired prefreeze0/0 review, before full dispatch. They inherit65+5 cases:532/533 identities,
166/167normal,35interruptions,34continuity,127archives, unchanged tool pins/exclusions.
Reviewer caught whitespace in joined completion IDs; draft now strips values.

Remaining: inspect focused results/fix only observed gaps; retain final native
renders/speech; review/freeze catalog before final whole Android10+17 runs; paired
final source/evidence review; canonical signed in-place beta66 and independent
public receipt; close66/check parent55 then STOP. No Ralph/parallel implementation.
Physical/provider/store gates stay parked under36/OWNER_INPUTS.md. Never combine
failed runs' partitions into completion. External review worktree is at exactcdd; canonical signed build session83924
completed scripts/review-build successfully, reserving66 once. Log activity66-signed-build.log and
receipt activity66-signed-build-result.json under external artifacts. Prepared66 verification passed with established certificate,38291443bytes,
SHA256af1adb0b746f502b2ec4c06b9e507e80630ed5d7d9cae5e380951268e4e2c01f.
Never rebuild/reuse66; tests/docs-only follow-up leaves production identical. Initial observer18705 finished, cancelled packets retained. Replacement observer
/tmp/redsquare66-watch-full-82a.py tracks both complete runs/downloads originals.
Public65 is unchanged;66 must wait for accepted verification/public receipts.

## Prior completed delivery

## Issue65 complete; STOP until the owner resumes another issue

Owner authorization covers issue65 only. Do not start66 without a new sign.
Protected untracked docs/research/NATIVE_GROUP_CHAT_CONTRACT.md remains unread
and untouched. Earlier churn instructions below are superseded.

Both complete exact509fc99673f04ee9c5e2f99ec8e23e64c12446e6 runs are whole SUCCESS:
Android10run39852319358 and Android17run39852323349. Frozen collector/catalog
6391367bc211a63cb1e12cc3678a53ef80f17f47 unchanged. Counts527/528 identities,
161/162normal,35interruptions,34continuity,122archives each. Sole frozen API29
Profile assumption; none17. Root2189/2217 hashes and actual native Search/motion/
selected-tab speech/settings checks pass. Motion result deadline observations
10:64/1528ms,17:38/1413ms. Paired source/evidence reviews throughf0bed239d:0/0.
All failed/cancelled predecessors remain rejected. Details:docs/evidence/ticket65.

Signed beta65 has been published by canonical review-publish and independently
verified over HTTPS. Sourceca678e521f63ed04d54cb0da4197443b8cc7c7e1, production/build
identical to verified509. SHA256b877f406c6c7bce1edc4f2b07cd54279b889753a51a377848fb06ecbb1ddadfc,
38250427bytes, established certificate, version0.1.65. All22 historical resources
unchanged, withheld61 remains404. Packets signed65-prepared and signed65-public;
raw /mnt/storage/redsquare-android-artifacts/search65-publication-65. Public
https://davidengelen.nl/redsquare/ . Never rebuild or reuse65.

Final public delivery review throughd93ec88ab reports Spec0/documented Standards0/
heuristic0. Issue65 is CLOSED and its child entry on55 is checked. Completion:
https://github.com/robelene112/redsquare-android/issues/65#issuecomment-6352032887 .
STOP. No further implementation, test runs or builds are authorized until a new
owner instruction. Physical/provider/store work remains parked under36/OWNER_INPUTS.md;
owner-wide visual acceptance55 remains open. No other issue has been started.

## Historical checkpoints


Updated2026-10-22 at16:15UTC. Public beta64 is published and independently
verified. Issue64 implementation, complete Android10/17 and prepared signed64
have paired Spec0/documented Standards0/heuristic0 reviews. Final public delivery
through e7c7b3f51a664c24340a3f6a014b222dab02fd53 has paired0/0. Issue64 is
CLOSED and55 updated. Issue65 is assigned and in progress. That earlier churn authorization is superseded by the current one-issue limit.

## Resume here

1. [Profile hierarchy/editing64](https://github.com/robelene112/redsquare-android/issues/64)
   is CLOSED after final delivery reviews0/0. Completion comment:
   https://github.com/robelene112/redsquare-android/issues/64#issuecomment-6345553015 . Review basea010a79fd4669892d3828eab3e93a2397f033a03.
   Latest source/evidence reviews through9856534608186358e143f055c87ff8a5150780df
   are paired0/0. Production unchanged since4dabe3124. Shared profile/header,
   compact exact-key details, selected accessible history tabs, secondary actions,
   coherent editor preview/fixed controls, saved-draft recovery and tonal media
   retry contrast preserve identity/signing/cache/publishing behavior.

   Complete Android10run39804393637 exact89cb35a7a2d2633cab53497aced64bbcaff83381:
   whole/all3 SUCCESS, frozen collector0,519 exact identities,156 normal,
   35 interruptions,34 continuity,117 archives,518PASS plus sole frozen API29
   Profile assumption. Root2014 hashes checked, native130Light/Dark retry,
   200Dark recovered editor, keyboard and TalkBack Likes focus viewed. Selected
   speech PID16447 utterances41/44/47 has START/positiveAUDIO/DONE/noSTOPERROR;
   original/restored settings byte-identical. Packet
   `docs/evidence/ticket64/full10-runner-89c-39804393637` has paired0/0.

   Complete Android17run39792064782 exact05680ceac13a1fb39fa5344b076ce25ea61da62f:
   whole/all3 SUCCESS, frozen collector0,520 IDs/157normal/35interruptions/
   34continuity/117archives/no assumptions. Root2058 hashes/native originals,
   selectedspeech PID16511 utterances30/33/36 and settings restoration checked.
   Packet `full17-056-39792064782` has paired0/0. Production/build/harness/workflow
   match89c; only reviewed physical Back injection/API29 TalkBack prerequisite
   fixtures and evidence/docs differ. Workflow explicitly permits this pair.

   Both complete proofs include public reads. Frozen catalog/collector commit
   1c5e024d773b3169c2e63eda6402e97c181ec5e3 and collector SHA
   ce04dc5a8ff4884466ed9eadec91cbed5914eb88aa0f804ae79a28e71417f156 remain fixed.
   Five optional diagnostic exceptions match the accepted baseline. Original
   3c/a87/056 failures stay rejected and retained; corrected focused proofs are
   diagnostic only. Original89c run39799164441 was CANCELLED after50minutes
   silent Wallet SDK setup before app build. Its available Social/Chat results
   are never combined with accepted runs. All observers ended normally.

   Canonical signed64 at exact89c was reserved once, prepared and published.
   APK38209467bytes SHA256
   4cc9965af797037155ed76a8a8913f7b045958291e9fc9dbeb9289ad8552cd0e;
   established certificate6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2.
   Ordinary nl.davidengelen.redsquare0.1.64, min29/target36, nondebug/nonprofile/
   noBackup; actual DEX REVIEW true/PUSH+bothGIF false. Public APK matches held
   bytes and latest equals immutable manifest. Independent AFTER confirms20
   historical resources unchanged,61 remains404, performance43 unchanged.
   Packets `signed64-prepared` and `signed64-public` both paired0/0.
   Public https://davidengelen.nl/redsquare/ . Never rebuild/reuse64.

   External review worktree is clean at89c. Build log/receipt:
   `/mnt/storage/redsquare-android-artifacts/profile64-signed-build.log` and
   `profile64-signed-build-result.json`; raw publication `profile64-publication-64`.
   Complete raws `profile64-full10-runner-89c-39804393637` and
   `profile64-full17-056-39792064782`. Verification refs remain at056 and89c:
   `verify/profile64-counts-05680ceac`, `verify/profile64-runner-89cb35a7a`.
   No active compiler, signing process or native verification run.
2. Search65 is assigned, with review base
   `6a386c372a1b3627cdd59e6ce9a6e8a9eaf57ed4`. All blockers are closed.
   The first native tracer SearchEntryPreviewTest covers actual IME visibility,
   explicit search, clearing without reads and recreation. Existing search/full
   Social scopes run it after the unchanged original class, each180seconds.
   Initial02edf4157 run39813595141 failed test compilation before native execution:
   performTextInput returns Unit, so the subsequent IME action needs a separate
   node interaction. Corrected that call. Paired review found no standards breach
   and one Spec coverage gap: count both Archive streams, with an explicit zero
   before submission and no username checks for this text query. Tracer updated;
   corrected tracer has Spec0. Replacement run39814099427 at
   ee4708716505468571a88920ffc93f4964c5ed48 passed all9 original Search methods
   and failed the new tracer at actual IME submission: ImeAction was Default.
   Native log/artifacts downloaded to external search65-red10-ee4-39814099427;
   whole-run finalization remains pending. This is RED, not acceptance.
   Added Search IME submission and a48dp vector Clear search control. Clearing
   invalidates the pending request, resets visible search state and preserves
   verified local cache. First green verification is next; hierarchy/position
   and the remaining ticket requirements are still outstanding.
   Paired reviews found a cancellation gap before effect disposal. Clear now
   synchronously cancels the active lookup Job, with identity-checked cleanup.
   An additive delayed-response journey clears in flight, releases the response,
   recreates and checks empty state with no continued quote read. Preliminary
   Spec review accepts the correction; committed paired review/native proof
   remain pending. Actual input/result captures now wait for their visible text.
   First-green run39815531436 atcc0ca38e4 FAILED: original9PASS, new tracer
   timed out on the quote counter despite the actual result visible. The fixture
   routes quotes separately, bypassing the ordinary callback where it was counted.
   Added an optional quoteArchiveResponse to withAppFrameFixture (null default
   preserves all existing uses) and attached both counters to their actual routes.
   Original assertions/deadlines retained. Raw/retained packetfailed10-cc0-39815531436.
   Local compile of cancellation code/tests PASSED in4m25; quote-routing correction
   is subsequent and awaits compilation/native execution. Logsearch65-cancel-compile.log.
   GitHub's active incidentwq8305lppdc2 includes degraded Actions orchestration:
   https://www.githubstatus.com/incidents/wq8305lppdc2 . At16:43UTC red run39814099427
   reported in_progress despite every job step completed, then finalized FAILURE.
   Red packetred10-ee4-39814099427 is retained. Never accept an incomplete run.
   Read-only planning found Search query/results are retained in wallet/backend
   FeedNavigationMemory, but its local rememberLazyListState loses position on
   profile/thread navigation. Preserve that position in the same retained scope.
   Clear must reset visible query/results and cancel pending work without starting
   network reads or clearing verified cache. Preserve exact name qualifications,
   completed-stream retry bounds and personal-control/node scope. Existing9 Search
   tests plus PostPresentationPreviewTest, PostContentContextsTest,
   PersonalControlsPreviewTest and StatusMotionPreviewTest exercise its old UI.
   Adapt actual visible controls and scoped scrolling, preserving assertions and
   original bounds; do not add invisible aliases.
3. Continue66through69 then70, one ticket at a time. Keep55 open for owner final
   visual acceptance and1/36 for actual release requirements. OWNER_INPUTS.md
   identifies parked publisher/provider/physical/store work; placeholders never
   satisfy operational acceptance. No owner input blocks native UI work.

Read [context](../PROJECT_CONTEXT.md), [workflow](../WORKFLOW.md),
[operations](../OPERATIONS.md) and [owner prerequisites](../../OWNER_INPUTS.md).
Preserve unrelated untracked `docs/research/NATIVE_GROUP_CHAT_CONTRACT.md`
**UNREAD and UNTOUCHED**. Stage explicit paths. Ordinary app development only:
no restricted cybersecurity work, production node/provider/store changes or
old-host writers. Authored copy has no em dashes; communicate concisely in Dutch.

## Completed63 and exact accepted executions

Review base: `baede8527d38dea6cf0afd280d29eb88d1b1cb95`.
Product source at441 and subsequent fixture/evidence changes have final
Spec0, documented Standards0 and heuristic0 reviews through `db50f703e`.
At63 acceptance, both complete gates and the signed artifact used
`44601552d50de619def7c5b90de27af985a9cb66`; the pre-closure local commits after446
contained evidence/docs only. Current64 source/run bindings are above. The protected untracked research is not task work.

| Gate | Run | Exact source | State |
| --- | --- | --- | --- |
| Complete Android10 |39725660757|44601552d50de619def7c5b90de27af985a9cb66|Whole/all3 SUCCESS, collector0|
| Complete Android17 |39723682236|44601552d50de619def7c5b90de27af985a9cb66|Whole/all3 SUCCESS, collector0|
| Source/core/release/lint |39723214278|44601552d50de619def7c5b90de27af985a9cb66|SUCCESS,9tasks|
| Original frame viewport10 |39723214114|44601552d50de619def7c5b90de27af985a9cb66|SUCCESS,27.885s/180|

Both complete dispatches include Chat/Social/Wallet and explicit public reads.
Current10/17 source/build stages passed. New10 repeats the full gates after the
preceding same446 Wallet/public-read failure described below. Android17 has complete native/evidence acceptance with paired0/0 reviews; Android10 final evidence review is0/0. Publication/delivery reviews are also0/0.
Latest tracker update:
https://github.com/robelene112/redsquare-android/issues/63#issuecomment-6332881529

Completed17 detached observer: PID3149293 (ended normally),
`/tmp/redsquare63-watch-full-446.py`; log:
`/mnt/storage/redsquare-android-artifacts/group63-full-446-observer.log`.
It checks metadata every45s, downloads all named artifacts only after the whole
run is terminal, and invokes the frozen collector only on whole SUCCESS.
Current raw targets: `group63-full10-446-39725660757` and
`group63-full17-446-39723682236` under the external artifact root.
Packet targets: `docs/evidence/ticket63/full10-446-39725660757` and
`full17-446-39723682236`. Inspect actual outcomes and collector output; exit0
is evidence validation, not automatic UI acceptance or publication. The observer
never retries native jobs or combines failed partitions. No local compiler,
signing job is active. New10 observer PID3163066 (ended normally),
`/tmp/redsquare63-watch-full10-repeat-446.py`, logs
`/mnt/storage/redsquare-android-artifacts/group63-full10-repeat-446-observer.log`.
The original combined observer retained rejected10 and complete17, then ended.

### Latest complete17 proof

Run39723682236 at exact446 is whole/all3SUCCESS. Frozen collector exits0:
516PASS/no assumptions,153 normal completions,35 deliberate interruptions,
34 continuity records,113 archives/3686 original members,1856 file hashes.
Packet `full17-446-39723682236`; raw external `group63-full17-446-39723682236`.
Root verified all1856 hashes and12 ready Preview geometries and viewed nine
current originals across the four new journeys, real IME and200% both themes.
Original GroupChat22 methods172.245s/180, Admin12 methods54.621s/180 all pass.
SystemUI clipboard preview still temporarily occludes some Share/Done/node/key
areas; no all-glyph/post-Copy-Done physical claim. Existing nav wrapping remains70.
Full17 final evidence reviews report Spec0/documented Standards0/heuristic0. Current10 complete success and final0/0 review are retained;
Public63 is verified with final delivery reviews0/0. No supplemental or failed-run assembly.

### Latest complete10 proof

Run39725660757 at exact446 is whole/all3SUCCESS, frozen collector0:515identities,
514PASS plus the sole approved API29 Profile assumption,152normal completions,
35interruptions,34continuity,113archives/3682members,1850filehashes. Root verified
all hashes and12 ready Preview geometries, and inspected nine current originals
across all four new journeys, including real IME and200% Light/Dark. Original
GroupChat22 pass168.946s/180 and Admin12 pass53.063s/180. Packet
`full10-446-39725660757`; same-named external raw directory with `group63-` prefix.
No failed-piece assembly or source-equivalence exception. Final paired evidence
reviews report Spec0/documented Standards0/heuristic0. Signed63 is now published
with public-after PASS and final publication reviews0/0.

### Frozen complete acceptance

[full-identity-reference.json](../evidence/ticket63/full-identity-reference.json)
and [full-collector.py](../evidence/ticket63/full-collector.py) were independently
frozen at `068972a427e2b3b412f67002963dd9bf30f98791` before full execution.
Only mutable actual source/run bindings have changed since that freeze.

Required Android10/17 totals:515/516 literal identities,152/153 normal completions,
35 deliberate interruptions,34 continuity records and113 archives. The only
allowed assumption is the exact pre-existing API29 Profile case. All prior
classes, assertions, request/class deadlines, exclusions, collector validators
and TalkBack/engine pins remain mandatory. All three partitions must succeed
in their own complete run. No failed/superseded subsets are accepted. Retain
raw artifacts outside Git and inspect current default/error/large-text/IME
originals alongside the exact protocol/identity/restoration goals.

### Product and focused proof

Implemented: contact-first private Chat with validated key fallback and full-key
Details; named groups with individual known/pasted selections and removal;
explicit exact-link Copy and Android Share; imported-ID Use invitation into an
ID-bound trust/current-node/privacy preview with Cancel and deliberate Join.
No global directory/contact upload, silent join/node switch or broadened payload.
Membership, promotion/removal confirmation, mute and leave remain unchanged and
must pass their original complete goals. There is no general MainActivity OS
app-link handler; scope is explicit paste/open Join, not automatic OS routing.

| Slice | Android10 | Android17 | Retained focused packet labels |
| --- | --- | --- | --- |
| Private entry |39705562540,56.821s|39705566867,67.096s|`greenSDK-initial-anchor-4d5-RUN`|
| Group setup |39711898801,88.366s|39711906716,106.694s|`greenSDK-group-typography-1ee-RUN`|
| Copy/Share |39713641960,34.961s|39713677034,65.859s|`greenSDK-invitation-share-568-RUN`|
| Invitation recovery |39720565982,54.671s|39719615280,81.952s|`green10-invitation-recovery-visible-ime-e3c-RUN` / `green17-invitation-recovery-geometry-a76-RUN`|

All four journeys pass all six320dp default/130%/200% Light/Dark cells with real
IME/native48dp actions and original identity/node/cache/controlled-peer/cleanup
goals. They are bounded slice proofs at their recorded sources, not final full
acceptance at446. Every packet is under `docs/evidence/ticket63` and reviewed0/0.
Current product is unchanged since441; later changes are fixture/evidence only.
Root inspected representative default/200% entry, selected-member, error/IME,
preview and full-key originals. Android17's temporary SystemUI clipboard preview
occludes some identity area; do not claim every character is unobscured during
that OS overlay. Share's physical-target proof precedes Copy's OS overlay.

Recovery's physical Preview helper uses focused-window IME insets plus the
accessibility keyboard rectangle clipped to the physical display, native/Compose
bounds agreement and two stable observations before exactly one tap, within the
original10s readiness bound. Nominal API29 IME bounds can extend below display.
All invalid/mismatch/trust/Cancel/exact signed Join/raw+Relay/roles goals remain.
The original shared InlineStatus production feedback is retained; a direct-text
experiment did not solve the historical test failure and was reverted.

## Rejected attempts and latest fixture correction

Latest446 full10run39723679811 is rejected: Wallet job119642660437 fails official
OwnerLiveFeedTest in16.252s with Peer disconnected and WebSocket upgrade HTTP502.
Root cancelled remaining Chat/Social; whole CANCELLED, Wallet FAILURE, others
CANCELLED. Original official feed was already visibly correct; separate direct
RPC connection failed before wallet balance/funding UI. Missing-file diagnostics
named PNG/XML/JSON are not actual images/responses or a blank-window defect.
Bounded failed packet `failed10-full-public-websocket-446-39723679811` retains32
hashes and inventory of425 external raw files, all original artifacts external.
No service/CDN/route root cause or product-fix claim; no source/test change.
Native compatibility/update/final tail after this failure is not accepted.
Manual fresh complete10run39725660757 at SAME446 includes every original gate;
no failed pieces are reused. Android17 and current Android10 whole/all3 SUCCESS with collector0. Prepared63 source stays unchanged; publication is recorded below. Latest source/frame/prepared/docs reviews through0861 are0/0; this
new failed packet and actual-run rebinding have final Spec0/documented
Standards0/heuristic0 review at a0d100d76. Latest tracker update:
https://github.com/robelene112/redsquare-android/issues/63#issuecomment-6333160425


Original full10run39721355158 at78bd is rejected. Chat job119635319551 failed
AppFramePreviewTest's original larger-text method with an
InstrumentationActivityInvoker DESTROYED-stage exception after six completed
cells. Root then cancelled remaining Social/Wallet: actual whole CANCELLED,
Chat FAILURE, Social/Wallet CANCELLED. The other three frame methods passed;
no subset is full acceptance. Packet
[failed10-full-frame-recreation-78b-39721355158](../evidence/ticket63/failed10-full-frame-recreation-78b-39721355158)
retains159 hashes,2archives/141 original members. Raw three-partition artifacts:
`/mnt/storage/redsquare-android-artifacts/group63-full10-78b-39721355158`.
Pre-cancel and terminal metadata also remain in the external
`group63-full10-78b-completed-chat-failure-39721355158` directory.
The crash-only log is empty; the final failure PNG is launcher after cleanup.
Neither an application crash nor exact historical callback ordering is proved.

The fixture now uses shared `recreateHomeViewport` in PostViewportFixture, asking
for recreation on the current main-thread Activity and positively observing a
focused replacement with width/font/Home inside the original15s bound. Original
frame8cells/targets/assertions/180s and existing post goals remain intact. Both
source review axes report0 after extracting the duplicated operator.
Initial16a compile explicit0/2m4s wall; shared446 compile explicit0/2m0.085s wall,
Gradle1m59. Receipts/logs are external `group63-frame-current-instance-compile`
and `group63-shared-home-recreation-compile` with `.json`/`.log` suffixes.

Corrected original frame10 proof:
[green10-frame-current-446-39723214114](../evidence/ticket63/green10-frame-current-446-39723214114),
138 originals/149 hashes, all eight cells/96 native targets/backup guidance and
independent restoration,27.885s/180. Root viewed320/font130/Light and412/font200/
Dark plus full backup guidance. The source446 packet has2hashes/264 original
stage lines. Both packets' final review is0/0 atdb50. Known200% Following/navigation
word wrapping remains70; wallet polish remains67. Functional reachability is
not app-wide visual completion.

Old full17run39721357702 at78bd was superseded by root while Wallet SUCCESS and
Chat/Social active, with no known goal failure, to execute final446 on BOTH SDKs.
Actual whole CANCELLED, Wallet SUCCESS, Chat/Social CANCELLED. Raw artifacts are
external `group63-full17-78b-39721357702`; pre-cancel/terminal metadata and reason
are in `group63-full17-78b-superseded-39721357702`. Old observer3135239 ended
normally after terminal retention. No old subset contributes to current gates.
Earlier focused failures remain separate immutable packets under ticket63.
A focused PASS or an accent field border does not prove a historical tap,
validation, animation or lifecycle cause. Preserve those README qualifications.

## Held beta63 and publication

Built ONCE by canonical review-build at exact446, reserved code63. Explicit0,
4m27.582s wall/Gradle4m20. Do not rebuild or reuse63.

- Immutable APK: `/mnt/storage/redsquare-android-artifacts/review-worktree/.scratch/review-builds/63/redsquare.apk`
- Bytes38139779; SHA256 `0b992e57431c6c8f26d017205612b43a53a54a9ef4486ead79d8a5a19e398b1d`.
- Established certificate `6ca1209bcbba661943f7b683d48b2f58493f13851c760c8d80d1521ac0ee79a2`.
- Actual package `nl.davidengelen.redsquare`,0.1.63; nondebuggable/nonprofileable,
  noBackup,min29/target36; actual DEX REVIEWtrue,PUSH and both GIF optionsfalse.
- [signed63-prepared](../evidence/ticket63/signed63-prepared):6 hashes, final0/0 review atdb50.
- Original command receipt/log: external `group63-signed63-preparation.{json,log}`.

Candidate was published exactly once after both complete native/evidence reviews
reported0/0. Canonical publisher and independent public-after checker exit0.
Actual public63 matches the exact held artifact;18 historical resources remain
byte-identical and both withheld61 URLs404. Final delivery reviews report0/0.
See `docs/evidence/ticket63/signed63-public` and external original receipts. Independent public
BEFORE check already passes: publiclatest62,18 historical resources protected,
both permanently withheld61 URLs404. Original downloads remain external
`/mnt/storage/redsquare-android-artifacts/chat-entry63-publication-63`.

Completed tools: canonical `scripts/review-publish`, independent
`/tmp/redsquare-chat-entry63-publication63-check.py` ordinary63 before/after,
and `/tmp/redsquare63-retain-public.py`. The public packet retains34hashes,
actual command receipt/log, before/after observations, public manifest/package/
signature and the checker source. Original downloads remain external. Both
complete packets are required by the retainer. No APK is added to Git.
Old releases and separate performance43 are unchanged. Static APK publication
is authorized; ingress, node, signing or provider changes are not implied.

Historical beta62 remains accepted and immutable:
38,008,595bytes, SHA256
`054bcf3fec36c45172cb740298cfca7670912444c63467557adcffa61f597000`,
source `b997f301c2c3830ce3767866ce296d2abf95c36f`, same established certificate.
Its exact held artifact is beside63 in review-builds/62. Version61 is permanently
withheld404. Never rebuild/reuse either version. Public62 packet final0/0 atc346;
closure atbaede. Its complete10run39692824874 has511 identities/510PASS+soleProfile
assumption/148normal/35interrupt/34continuity/109archives; complete17run39681571919
has512PASS/149normal/35/34/109, with the documented fixture-only source-equivalence
exception. See ticket62 evidence for original accepted sources and details.

## Remaining work and parked owner gates

Next64 profiles/editing,65 search,66 activity inbox,67 wallet flows,68 Settings,
69 accessible image viewer, then70 adaptive very-large-text navigation. Read each
current GitHub issue before claiming it. No35 separate grill sessions are needed.
Initial native2–35, supplements37–54 and current UI56–62/functional57 are closed.
Final owner app-wide acceptance remains55, not implied by those scoped closures.

Owner-directed parking under36 preserves13 unchecked gates: GIPHY_POSTS,
GIPHY_CHAT,VIDEO,REGISTRY,SOCIAL_PUSH,PRIVATE_PUSH,GROUP_PUSH,PIXEL_CONTINUITY,
PIXEL_CACHED,PIXEL_FRESH,PUBLISHER,POLICY_OPERATIONS,FINAL_SUBMISSION. Angels’
Foundry is chosen; KVK is unfinished. Track actual needs in OWNER_INPUTS.md,
use clearly marked placeholders and continue independent native work. Keep real
provider lifecycle, locked/Doze, physical/ARM64 and Play requirements pending.
Pixel protocol still requires five cached launches each<=2000ms and five isolated
fresh launches each<=5000ms on stable Wi-Fi, normal animations, no debugger and
matching candidate/configuration, preserving the ordinary wallet. See
[the protocol](../research/FEED_PERFORMANCE_PROTOCOL.md).

Netcup152.53.197.181, hostname v2202609429510531338, is production. Verified
migration state keeps old Hetzner writers disabled. VPS has no KVM; use hosted
emulators and bounded Docker builders. Read STORAGE.md and check actual mounts
before sizeable writes. External artifacts/caches use
`/mnt/storage/redsquare-android-artifacts`;04:30 observation: /dev/vdb1 ext4 at
/mnt/storage with103GB free, root51GB free. Recheck before new large downloads.

Superseded execution narrative remains in Git at `db50f703e` and prior commits;
canonical source/receipts/original captures remain in `docs/evidence/ticketN` and
tracker comments. Those records preserve historical failures and causal limits.
They do not replace the current run bindings, scope or next actions above.

## Active64 focused regression executions

Exacta6c6d40b1f80189bfac6031e32c0d87ba6ab6b16:
keyboard10run39735804256, actual pinned TalkBack10run39735806952,
original browse10run39735809739, profiles10run39735812210,
usernames10run39735815251, controls10run39735817877,
publisher-routes10run39735820655. All were dispatched with public readsfalse
and remain pending. Observer3220136 `/tmp/redsquare64-watch-regressions-a6.py`,
external `profile64-regressions-a6-observer.log`, terminal-only raw targets
`profile64-SCOPE-29-a6-RUN`. It does not retry or accept results automatically.
Editor681 observer3219671 ended normally after both failures were retained.

Original fixture adaptationsac363/c1d preserve exact-key/name-sibling assertions,
full-list absence, displayed Unmute, reporting context and original deadlines.
Navigation uses real secondary sheet/identity dialog with positive dismissal;
review fixes final Spec0/documentedStandards0/heuristic0. Additive keyboard
and actual selected-tab speech tracers at a6 each retain180s. Source Spec0;
Standards found substring speech predicates could accept unselected/embedded
label, corrected to bounded words ata018; final paired0/0.
Editor scroll/display must complete inside the absolute original15s at505,
final paired0/0.
All original identities/assertions/pins remain required. Local compile3218757
`profile64-accessibility-compile.{json,log}` exits0/4m31s Gradle. Its working
snapshot includes later fixture edits, so final executing-source proof is remote. Public63 unchanged.

Corrected focused editor sourcea0181eb175ecd132f45b9dc91e9e6c8653d1c0dd:
Android10run39736046494,17run39736049716, both pending. Observer3222803,
`/tmp/redsquare64-watch-editor-a018.py`, external
`profile64-editor-a018-observer.log`, terminal-only raw targets
`profile64-editor-SDK-a018-RUN`. The later505 deadline qualification must also
pass complete final-source verification. Do not assemble failed cells.

### Frozen complete64 acceptance

`docs/evidence/ticket64/full-identity-reference.json` and `full-collector.py`
are independently frozen at1c5e024d773b3169c2e63eda6402e97c181ec5e3 before
complete execution; both prepared references/collector have final paired0/0.
They preserve every literal accepted63 identity and all previous validators,
tool pins, assertions/deadlines and the sole exact API29 Profile assumption.
Four additive methods require519/520 identities,156/157 normal completions,
35 deliberate interruptions,34 continuity records and117 archives. Full
execution/source/run bindings remain pending. All three partitions in each
complete run must succeed. No focused/failure assembly is authorized.
