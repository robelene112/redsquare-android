# Current implementation handoff

Updated2026-10-16. Owner authorizes continuing remaining issues sequentially.
#32 is the sole implementation, review base
`dd89bc9108eb002bb260eee709c656cd45348940`, dependency#24 closed.
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

## Current bounded work

Private Chat draft preservation on a social tap remains active within #32.
Tracer a8a30ceb needed two fixture corrections: exact EditableText instead of the
Message label, then direct Chat photo visibility with a bounded decode wait.
The original failures remain in `private-chat-tracer-fixture-failure` and
`private-chat-tracer-scroll-failure`; superseded run 38966824893 is CANCELLED,
with no app assertions. All are under `docs/evidence/ticket32/`.

[Actual intended RED](../evidence/ticket32/private-chat-tap-red/android10/README.md):
run 38967008873 at bbb3db3a, nine cases, eight PASS, one missing-Conversation failure
at line 227 after actual alert/tap, 85.741 s. Minimum 84eb9704 registers the private
Chat guard and freezes edits/send/cancel/Back using existing transition state.
The shared photo callback also covers pending/restored pickers and returned URIs.
Both source/RED reviews report zero findings; local core/debug/test passed 3m 39s.

At exact source `84eb9704729182442210566bc1e341d505637eee`:
- [Android 10 social GREEN](../evidence/ticket32/private-chat-tap-green/android10/README.md),
  run 38968037239: all nine cases PASS, 72.148 s, latest draft/photo and invariants.
- [Android 17 social failure](../evidence/ticket32/private-chat-draft-failure/android17/README.md),
  run 38968041531: eight PASS, one persisted-caption mismatch after Conversation
  and Back, 111.601 s. Latest caption application before alert was not observed.
- [Android 10 photo failure](../evidence/ticket32/private-chat-photo-regression-failure/android10/README.md),
  run 38968045554: ten UI cases PASS, private/group actual recovery PASS
  (PIDs 7326 to 7607 / 7895 to 8261), then both storage cases OOM at 16 MiB.
- [Android 17 photo timeout](../evidence/ticket32/private-chat-photo-regression-failure/android17/README.md),
  run 38968049213: seven UI cases PASS, case eight interrupted at class limit 120s.
  Unique empty capture XMLs support reaching the durable-capture boundary but do
  not establish the stall cause. Later process/storage phases did not run.
- Source push 38968018508 SUCCESS includes release assembly and lint.

Storage fixture/loader/cache/manifest/build source is byte-identical to passing
full Android 10 source 33d7f942; emulator version/build is also the same. Boot
heapsize 512m does not establish normal process growth limit. No cause, production
regression, increased heap or longer assertion/class deadline is justified yet.
Root viewed six social originals, two process originals and the timeout original,
and parsed all 130 XMLs across these four result packages.

Next changes are observations only: exact latest UI caption/capture before alert,
protected draft check before Back, phase/timing in the existing private/group photo
preparation case, and effective device/runtime memory properties in storage tests.
No pre-alert forced save or disk-persistence wait is added. Production is unchanged
from 84eb9704. Local observation core/debug/test assembly passed in 1m 54s; shell
syntax passed. Both observation review axes are now zero after the b2e77822
PNG/XML wording correction. At exact 1487ed1b26ac9ee960367c114cd30ee7b9c405fb:
- [Social Android 17 helper failure](../evidence/ticket32/private-chat-reopen-helper-failure/android17/README.md),
  run 38970014218: eight PASS, one failure, 121.607 s. Pre-alert latest EditableText
  and BOTH protected-draft checks pass. After recreation, unconditional Espresso
  closeSoftKeyboard times out. Reopened photo/final invariants remain unexecuted.
- [Photo Android 17 follow-up](../evidence/ticket32/private-chat-photo-regression-followup/android17/README.md),
  run 38970017629 SUCCESS: ten UI cases 103.91 s, private/group actual recovery
  PIDs 4940 to 5011 / 5120 to 5196, and both history cases 73.503 s.
- [Storage Android 10 follow-up](../evidence/ticket32/private-chat-photo-regression-followup/android10-storage/README.md),
  run 38970020926 SUCCESS: both unchanged history cases 82.302 s. Device heapsize
  512m, heapgrowthlimit blank; effective process memory values were not retained.
- Source push 38969988576 SUCCESS includes release assembly and lint.

The added println diagnostics are absent from instrumentation logs. Their observation
transport is now changed to sendStatus stream messages, preserving all counts,
assertions and deadlines. The native photo-history class takes no screenshots;
its failed best-effort capture retrieval output stays raw and makes no visual claim.
The private opening helper now reuses the existing actual-Activity keyboard-hide
request plus positive displayed-navigation wait, avoiding an unconditional Espresso
callback wait. Existing reopening and exact photo assertions remain.
Combined keyboard/stream local core/debug/test compilation passed 1m 53s. Both
source/evidence reviews report zero findings at 9e2dcd18. Actual Android 17 social
run 38971338531 at that exact source now SUCCESS: nine cases, 112.585 s. Root viewed
all four private originals and parsed 37 XMLs. Latest caption, both protected-draft
checks, actual tap/exact destination, Back, recreation/reopened text/photo and all
final invariants pass. Destination has Refresh enabled plus available-history
notice, not proof of exhaustive history. Earlier failures remain failures with
unproved causes.

[Private slice acceptance](../evidence/ticket32/private-chat-reopen-green/README.md)
combines Android 10 social GREEN and actual photo UI/process recovery with passing
storage follow-ups, plus Android 17 social and photo regression GREEN. Exact Git
production-tree comparisons prove unchanged production since 84eb9704. Android 10
storage observation run 38971341246 at 9e2dcd18 passes both cases in 77.581 s and
records effective maxMemory 512 MiB, memoryClass/largeMemoryClass 512, PID 3297.
This does not establish why the earlier OOM reported 16 MiB. Diagnostic status0
messages are progress, not extra passing tests. Source push 38971334056 SUCCESS
includes release assembly and lint. Final evidence review is pending before moving
on. No full #32, actual FCM or closed-process delivery claim. Public beta24 unchanged.

Next ordinary vertical slice after final private evidence review: preserve a group
Chat draft/photo on actual social tap. Plan `.scratch/push32-group-draft-next-plan.txt`.
Keep owner key1 and a separately created/invited group, actual UI edit/system tap,
protected draft/recreation/photo and complete invariants. One intended RED then
minimum. Do not start another ticket or group implementation before that review.

## Verified #32 slices

- [First reply RED](../evidence/ticket32/reply-alert-red/android10/README.md) and
  [GREEN](../evidence/ticket32/reply-alert-green/android10/README.md).
- [Dismissed replay RED](../evidence/ticket32/dismissed-reply-red/android10/README.md)
  and [GREEN](../evidence/ticket32/dismissed-reply-green/android10/README.md).
- [Actual process recovery](../evidence/ticket32/dismissed-reply-process-green/android10/README.md):
  Android10 run38827943929/cfb7c198 SUCCESS, PIDs3447→3632, duplicate silence,
  independent fresh-reply liveness, full wallet/backend and installation restoration.
  Both evidence review axes0. This is after completed publication/dismissal;
  Android publication and disk acknowledgement are not atomic.
- [Actual reply tap RED](../evidence/ticket32/reply-tap-red/android10/README.md):
  run38829625275/0359cd91 FAILURE3cases2PASS1FAIL33.001s. Physical tap returns
  owner feed instead of Conversation. Both evidence review axes0.
- [Reply tap GREEN Android10](../evidence/ticket32/reply-tap-green/android10/README.md),
  run38878791927 SUCCESS20.256s, and
  [Android17](../evidence/ticket32/reply-tap-green/android17/README.md),
  run38878803838 SUCCESS51.896s, both at
  `027c6b90b27a1123cc80791acf58653d2b6ef98a`.
  Historical raw totals are4cases and remain unchanged. Both runs had completed
  when cancellation was attempted; Android17 could not be cancelled. Exact method
  timing relative to the owner's instruction is not established. No further run
  or separate analysis of the removed extra method is requested.

The three retained ordinary cases pass on both SDKs: actual reply alert, dismissed
replay silence and physical tap to the exact signed reply. Latest unsent text and
photo survive returning/reopening; persisted draft remains unsigned/unqueued;
full wallet/backend and zero outgoing transaction/report/Relay assertions pass.
Root viewed alert, destination and restored-draft originals on both SDKs and parsed
all14 XMLs. Android17 destination capture still says Loading replies with the
exact target already visible; do not claim a fully loaded thread at capture time.
[source-equivalence.json](../evidence/ticket32/reply-tap-green/source-equivalence.json)
proves unchanged production and retained functional assertions after scope removal.
Evidence reviews found two stale documentation claims, corrected at0364f4f8;
both reviewers confirmed final0/0. Ordinary source removal and evidence are pushed.

First tap minimum08e1a6f2 and route-state factory correction52f6cf0d are committed.
Production source reviews0/0 after the correction. Pending public route generations
survive ordinary screen recreation without replacing original launch configuration.
Fresh selected-Archive candidate/owner-parent verification precedes guarded post
save, current scope/policy recheck and navigation commit. Legacy mounted forms
retain their screen with Retry pending their ordinary integration. No production
entry point invokes the local receiver. Full tap lifecycle behavior is not yet
verified. Corrected local core/debug/test29634 completed0 in3m21s.

GitHub had automatically closed#32 because an interim commit subject contained
a closing keyword followed by#32. Root reopened it: acceptance is incomplete,
and the owner did not request closing it. Avoid closing keywords next to issue
numbers in interim commit messages.

Ordinary no-Activity tap/recreation is verified at exact corrected source
182d38433d36131184837fbd3681cddfa2378b1c. Android10 run38931710840 SUCCESS,
4cases27.376s; Android17 run38931756109 SUCCESS,4cases57.257s. The current
four cases are alert, dismissal, warm tap and no-Activity tap, all ordinary
functionality. Local pre-correction core/debug/test34171 PASS2m24s; both corrected
source reviews0/0, and CI compiles/tests the exact correction before native runs.
[Android10 evidence](../evidence/ticket32/reply-cold-tap-green/android10/README.md)
and [Android17 evidence](../evidence/ticket32/reply-cold-tap-green/android17/README.md)
retain original logs, metadata and11PNG/XML pairs perSDK (27hashes onAndroid10,
28onAndroid17 including permission metadata). Root
viewed all five cold captures perSDK and parsed all22XMLs. Exact destination,
Activity recreation, Back/feed recreation and2s consumed-route absence pass;
saved text/photo, unsigned/unqueued work, full wallet/backend and no-TX invariants
pass. Android10 destination/recreated captures and Android17 initial destination
still show Loading replies; target is visible, but no full-thread claim there.
Both ordinary evidence reviews report0/0. No local Gradle/native run is
active. Focused checks omit release/lint/full suites. Instrumentation and fixture
peer remain alive, so this is not process-death/FCM wake-up. Public beta24 stays
current with no new version reserved.

Next ordinary mention tracer is added ataa96b120. Shared Journey enum preserves
both reply cases and adds an independently signed public-key mention with actual
mentions-channel alert, system tap to the exact post, recreation/consumed-route
and original saved text/photo invariants. Local core/debug/test38028 PASS2m10s;
both tracer source review axes0/0. The minimum implementation is now committed
at5dc0d048: fresh signed public-key/list mentions use the Mentions category and
the same classifier on tap, while owned replies retain priority. Actual
Android10 run38933228336 at8d803b4822abd3e06d5baf3a6377ca4aef64a87a is
FAILURE5cases4PASS1FAIL28.173s. Mention result is SUPPRESSED where PUBLISHED
was required atSocialPushTapPreviewTest.kt:123; later mention UI/invariant tails
did not execute. Four prior reply cases pass. [RED evidence](../evidence/ticket32/mention-alert-red/android10/README.md)
retains28hashes and12original PNG/XML pairs; root viewed failure launcher and
parsedall12XMLs. adbexit0 is retained despite harness/runtime failure, no scope.txt
on failure. Ordinary evidence/spec review0, standards found only stale handoff
status, corrected here. Minimum source review has no production finding. Local
core/debug/test9820 PASS3m33s. Both final ordinary source/evidence reviews0/0
afteree3de80c corrected the status. Actual Android10 run38934678371 SUCCESS,
5cases35.523s, and Android17 run38934687199 SUCCESS,5cases65.788s, both at exact
sourceee3de80cd0f90b126da7d7ab4fc75e453c608df1. [Android10 GREEN](../evidence/ticket32/mention-alert-green/android10/README.md)
and [Android17 GREEN](../evidence/ticket32/mention-alert-green/android17/README.md)
retain37/38hashes,16PNG/XML pairs perSDK. Root viewed all five mention PNGs
perSDK and parsedall32XMLs. Actual Mentions category/card, physical tap/exact post,
recreation, consumed navigation, saved text/photo and wallet/backend/no-write
assertions pass. Destination captures alternate Loading replies and Some
conversation entries could not be read. No fully loaded/error-free history claim
or warning cause is inferred. These are local injected public-key mentions with
instrumentation alive, not FCM delivery. Both final ordinary GREEN evidence
reviews report0/0 at53025d7c.
No local Gradle/native verification is active. Public beta24 remains current.
Username mentions, registration/choices, other forms and real transport remain.

The next ordinary alert-choice tracer is committed atfad2adf0. SETTINGS_REPLY
uses no seeded consent: real Settings defaultOff -> enable -> recreationOn,
existing warm reply tap/latest post/photo, then disable and a distinct independently
signed parentIndexedReply must remain silent2s. Fresh signature/mapping is separate
from the prior receipt. Both source review axes0/0; local core/debug/test95724
PASS2m1s. Android10 run38936586595 at107439c5c991336ba1bae9a609b4134a2b37cead
is FAILURE6cases5PASS1FAIL50.936s: wait15s for the missing enabled alert switch
inside Settings (openAlertChoice line235). Later choice assertions did not run.
[Choice RED](../evidence/ticket32/alert-choice-red/android10/README.md) retains
38hashes/17PNG/XML pairs; root viewed actual Settings failure and parsed17XML.
adbexit0 is preserved; harness/runtime fail correctly. No scope.txt on failure.
The minimum UI is now added: wallet-scoped Replies and mentions switch in the
existing Settings dialog, off by default, IO read/write with unavailable/error
state and Retry. It writes existing explicit consent only; no registration,
Android permission/category shortcut or FCM transport claim. The mounted settings
modal keeps existing push-route deferral. Both ordinary source/RED evidence
reviews0/0 atdf331f8a. Local core/debug/test89082 PASS3m24s. No native GREEN
for the choice was observed before dispatch. Actual Android10 run38937947396 is
now SUCCESS6cases46.172s and Android17 run38937958934 SUCCESS6cases71.808s, both
at exact sourceaebfb3c6d276e64b343834e5612d3cd2f1a43b84.
[Choice GREEN10](../evidence/ticket32/alert-choice-green/android10/README.md) and
[GREEN17](../evidence/ticket32/alert-choice-green/android17/README.md) retain49/50
original hashes and22PNG/XML pairs perSDK. Root viewed six Settings journey PNGs
perSDK and parsed44XMLs; parent switchfalse/true/false independently checked.
DefaultOff, UIenable/recreationOn, actual replytap/exact destination/latest draft,
UIdisable and distinct fresh reply silence2s all pass, as do wallet/backend/no-write
invariants. Destination still says Loading replies; no loaded-thread claim.
System alerts enabled, runtime permission pregranted onAPI33+ only. No
OSpermission/category/IOerror recovery/FCM proof.
Both final ordinary GREEN evidence reviews report0/0 after57b36304 corrected
Android10 permission wording. No native run is active.
AppearancePreviewTest already scrolls to theme choices; no fixture change needed.
No owned local Gradle build remains active.

Current ordinary slice: Android notification category shortcut, tracer
e5f5c268 /074938aa /57fada72. It verifies actual OS Settings/app/categories,
exact persisted text/photo with null transaction/unqueued while OS Settings is
foreground, then return/recreation and existing reply navigation. The stored-work
assertion was added because Activity memory could mask an omitted pre-launch save.
Local tracer11273 PASS1m50s, corrected91029 PASS1m44s, visible-navigation98760
PASS1m41s. Corrected tracer and navigation-evidence reviews0/0.

- [Navigation failure](../evidence/ticket32/android-category-navigation-failure/android10/README.md):
  run38940489331 /d10df7f0 FAILURE7cases6PASS1FAIL58.97s. Failure PNG/XML show
  editor/IME without Settings. It is not accepted intended feature RED. Fix reused
  existing IME dismissal and added displayed-header/positive-Dialog preconditions;
  exact click timing/cause was not established.50hashes/23PNG/XML retained.
- [Intended shortcut RED](../evidence/ticket32/android-category-red/android10/README.md):
  run38941694304 /152395ae32c1f25896834f426c846824ff6e9d58 completed FAILURE,
  7cases6PASS1FAIL61.348s at shortcutwaitline254 after positive Settings display.
  PNG/XML show Settings with the alert choice and no shortcut.50hashes/23PNG/XML
  retained; root viewed failure and parsedall23XMLs. Later OS/category/persistence
  and new-case replytap/final tails are unexecuted. Six established cases pass.

Minimum c101dc31 now adds the Settings button using PostNavigation.navigate to
save/freeze the mounted post through IO initialization and app-specific Android
Settings launch. Replies/Mentions channel definitions are reused for first-visit
category discovery without resetting Android choices. Save/open controls disable
while busy, with explicit failure feedback. No OSpermission request, Chat category
or registration/FCM claim. Source/RED evidence Spec0; Standards found only the stale
handoff active label, corrected here. Local core/debug/test19809 PASS3m29s.
Both final ordinary source/RED evidence reviews0/0 afterd6d98bb0. Actual focused
Android10 run38943302756 FAILED7cases6PASS/1FAIL58.659s and Android17
run38943314879 FAILED7cases6PASS/1FAIL104.842s at exact source
d6d98bb03b5841d542bb22fb358d87413d75df06. Both fail at the first OS-package
observation, line256. [Retained failures](../evidence/ticket32/android-category-opening-failure/)
include50/51hashes and23PNG/XML pairs perSDK. Root viewed both failure originals
and parsed46XMLs: app Settings remains visible with an enabled shortcut and no
open-error text. Exact runtime cause is unproved. Correctionb71b1396 now uses a Compose-aware wait for the identical actual
OS-package predicate within the same10second bound. The failed fixture handed
directly to UIAutomator without advancing the Compose test clock. Exact pinned
Compose source confirms that scheduling hazard, not those failed runs' runtime
cause. Local core/debug/test22793 PASS1m48s. Both ordinary source/failure-evidence
review axes report0/0 afterdb39da49. At exact9de0ff39a3012f00e555986d6b58603b7db6ca23,
[Android10 run38957001646](../evidence/ticket32/android-category-green/android10/README.md)
SUCCESS7cases57.246s demonstrates both OS categories, exact persisted text/photo
while OS foreground, return/recreation and subsequent replytap/draft invariants.
Root viewed six new-case originals and parsed28XMLs; destination still Loading
replies, not a fully loaded thread.
[Android17 run38957004756](../evidence/ticket32/android-category-platform-failure/android17/README.md)
FAILURE7cases5PASS/2FAIL95.491s. The first case observes0alerts immediately after
receive without checking its result; exact cause is unknown. The category case
reaches actual OS Settings but fails exact text Red Square at259: XML exposes
the visible title as content-desc, and categories are collapsed under Other.
Root viewed both failure originals and parsed22XMLs. Later category/durability/
newcase tails and first-case dismissal tails are unexecuted.49hashes retained.
Current fixture-only correction accepts the same app label through text or
content-desc, expands the positively observed OS expander if Replies is absent,
and retains actual Replies/Mentions assertions. The first hot case additionally
requires PUBLISHED, then waits up to5s for Android's actual active alert before
the unchanged exact count/content/shade checks. No production changes. Corrected
local core/debug/test84915 PASS1m42s; native verification is pending.
Both ordinary source/evidence review axes report0/0 atbbdbc884. Actual corrected
[Android10](../evidence/ticket32/android-category-controls-green/android10/README.md)
run38958309170 SUCCESS7cases52.423s and
[Android17](../evidence/ticket32/android-category-controls-green/android17/README.md)
run38958312719 SUCCESS7cases96.046s at exact3ae3127536197dd4c04821abd741a6b467cc9d5e.
Both actual OS categories, before-return stored latest text/photo, return/recreation,
replytap and saved-work/wallet/backend/no-write invariants pass. Root viewed category,
Conversation and final draft originals perSDK and parsed56XMLs.61/62hashes retained;
Conversation still Loading replies, no completed-thread claim. Both final ordinary GREEN evidence review axes report0/0 at efd0d3aa.
Public beta24 unchanged.

Next ordinary spec gap confirmed by review: NotificationPermissionPrompt's Enable
alerts requests OS permission/records global offered only, never enables wallet
SocialPushSettings. Pregranted Android permission or visiting Chat first also skips
the global prompt. Settings consent works, but the first-use action is disconnected.
Tracer309124e7, corrected33f89f53, adds ONBOARDING_REPLY at the existing approved
Android/controlled-peer seam. It leaves social consent unseeded, verifies the
global prompt already offered and Android permission pregranted onAPI33+, then
requires an explicit offer in Notifications, enable, actual OS page onAPI29,
recreation with no repeated offer for2s, Settings consentOn and the existing warm
replytap/latest draft/photo/full invariants. Positive displayed navigation target
and distinct non-clickable Notifications heading precede missing-offer assertions
and identify the screen after return/recreation. Both corrected source reviews0/0.
Pre-correction local81769 PASS1m47s; corrected local19596 PASS1m38s. Actual
[consent RED](../evidence/ticket32/social-consent-red/android10/README.md)
run38960006823 atf5e824dcc06ecdf02a53387bb4d784f6f98613b8 FAILED8cases7PASS/1FAIL
68.437s, waiting15s for the missing offer atline139 after positive Notifications
screen display. Root viewed failure PNG and parsed29XMLs; actual Notifications
has no offer.62hashes retained. Later new-case consent/OS/recreation/tap/final
tails are unexecuted. Seven prior cases pass. The minimum correction is now
written: a feature-local wallet-scoped offer independent of global Chat/Android
offer history, explicit encrypted consent on Enable, owner offer flag on Skip,
and Android permission requested only when needed. Generic Chat usage remains
no-argument. The shared prompt serializes choice persistence before OS interaction
with busy/error feedback. An absent encrypted choice stays distinct from an
explicit disabled choice without changing stored format.
Fixtures preserve the new owner offer preference and permission-test social files;
OS waits advance Compose while awaiting the new IO path. The notification history
process fixture explicitly marks offers handled because its seam is read recovery,
not first use. No social consent is enabled by that setup. Correctionf9ac43f9
keeps reading available with a passive message if Skip persistence fails; Enable
failure retains retry controls. Separate messages distinguish persistence and
subsequent OS-controls launch failure. Both final ordinary minimum/source/RED
evidence reviews report0/0. Local core/debug/test1582 PASS3m53s. No owned local build is active. At source495fb72cd066aef4fd0f154bcb52779d79a906c9 the actual
focused dispatches are social Android10 run38962211627, social Android17
run38962215407, existing notifications Android10 run38962218678 and existing
notifications Android17 run38962222453. All four now succeed. [Social consent GREEN](../evidence/ticket32/social-consent-green/)
records Android10 eight cases68.043s and Android17 eight cases87.529s. Root viewed
six/five new originals and parsed67XMLs, checked the exact social-switch parentsOn.
73/72hashes retained. Explicit offer despite prior global history/pregranted API33+
permission, enable/recreation/no-repeat2s and actual replytap/latest draft/photo/
full invariants pass. Android10 destination is still Loading replies; Android17
capture has Refresh enabled, no exhaustive-history claim.
[Compatibility regression](../evidence/ticket32/social-consent-regression/README.md)
records32 read cases perSDK (157.814s/137.688s), one/two actual OS permission
cases and history process recovery PIDs6386→6473 /5338→5411. Root viewed eight
permission/cold originals and parsed96XMLs.106/111hashes retained. Denial keeps
in-app reading available; no receiver-after-denial proof from those cases. Read
recovery shows the intended server-read error while retaining NoUnread/actor.
Separate source push38962198525 succeeds at the same495source, including release/
lint. Focused scopes omit those and full acceptance. Both final ordinary evidence
review axes report0/0 at e236bd4a, including all363 hashes and163 XMLs.
Public beta24 is unchanged and #32 remains open.
Concrete notes: .scratch/push32-consent-integration-plan.txt.

Remaining#32 includes saving/retry and other forms,
mentions/registration proof, optional notification settings, real transport and
full Android acceptance. Firebase project/IAM and gateway setup remain external;
the existing owner question stays pending. Do not repeat it. Local injection
cannot close#32. Gateway deployment and production node changes require a concrete
bounded separate authorization. Existing transport context:
[local seam](../research/SOCIAL_PUSH_LOCAL_SEAM_2026_10_15.md),
[closed-app requirements](../research/NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md),
[observer](../research/SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md),
[Archive discovery](../research/SOCIAL_PUSH_ARCHIVE_DISCOVERY_2026_10_15.md),
[future Chat transport](../research/CHAT_PUSH_EXISTING_TRANSPORT_2026_10_15.md).

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

For earlier ticket chronology, failed attempts, detailed source provenance or old
session IDs, consult [the historical checkpoints](IMPLEMENTATION_HISTORY_2026_10_16.md).
Those old active labels are historical; canonical ticket evidence and current code
remain authoritative. This archive preserves the previous handoff body unchanged.
