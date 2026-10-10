# Sequential implementation handoff

Updated 2026-10-10. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

Active #18, "Edit profile bio, avatar and banner", claimed after confirming
native blocked_by0. Review baseafce6610e4c78a2183bf154ac99cc35d7917b03c.
Use NATIVE_PROFILE_EDIT_CONTRACT.md and NATIVE_PROFILE_EDIT_VERIFICATION.md.
First independent signed-snapshot tracer is compileRED22s because reader absent;
minimal reader focusedGREEN42s; complete-snapshot senderGREEN40s, numeric marker
reconstructionRED25s/GREEN30s, pinned-avatarRED20s/GREEN43s. All48core/debug/test
assemblyPASS1m58s. First Android reader73f0e17 run38089539658 RED32.732s at missing bio. Header
583c0cc appears with verifiedbio/identicon, then17 fixture-healthFAIL17.755s;
explicit feed/author callback fixed. Both reviews flag unscrollable long header;
independent40linead4e5bf run38090221063 times out180s/0 completed checks/invalid
image archive, no cause claim. Header/tabs/history now share weighted LazyColumn
3ec9cb7; both bounded review axes0. Native runs38090924525/38090927117 short
bio/avatar/noTX/healthy pass; longcasefails24.859s overall10 and16.229s17:
10 waits without Compose synchronization and stays feed;17 initialscrollbefore
feedload fails. Originals inspected/retained;10 shortbio bitmap lacks separately
passing bio and is excluded from bio visual acceptance. Fixture now waits two
archive replies/no Loading posts/localwalletreadiness; exactbio Compose wait,
max10 synchronized Compose swipes, all displayed tabs/post gates intact.
Debug/testPASS1m51s; fresh dual checks pending. New core stale-read protection
compileRED22s/GREEN43s four cases; retains newest verified fullsnapshot and all
fields, bounded legacy history100/16MiB. Disk cache/editor/media/recovery remain
unimplemented. Active18 not accepted.
Only profile fields and public reference inputs;
no production publication or app/node wallet reads. #17 stays open with actual
provider acceptance unproved. Its immutable full534330e pair PASS127 cases
each; this does not prove subsequent #18 changes or real provider playback.

Ticket #15 is complete after final source/fixture/evidence reviews: both axes
zero remaining findings through2829329. Full Android10 38075318198 atd35f965
and Android17 38076602854 at23d7202 PASS123 cases/35 records/11 actual deaths
each, core39/build/release/lint, both public reads, cold recovery and signed
updates. Productioncb3d9c3 is identical across accepted candidates. Evidence
final-android10/final-android17/final-interop; details NATIVE_IMAGES_VERIFICATION.

#17 YouTube has an unresolved public-playback verification blocker; keep open.
Productionc6113d2, test/evidence candidate534330e,
fixed review base711e42e. Root-owned viewer survives rotation/recreation; small
windows use external fallback. Sticky errors persist until Retry. Text selection
now matches both original Browser/Tweet helpers, including omitted schemes and
last recognized non-numeric URL. Strict provider URI/ID validation remains.
Full44 core/debug/test buildPASS; reviewed signed fixtures/proof retain original
objects. ControlledAndroid10 four-caseGREEN38084273614 at2a99f86 passes23.724s.
Full711e42e...5454618 source reviewsboth0. Actual public playback is unproved:
10 run38084979254 one30.203s and17 run38084966811 one45.588s reach native ready
but no public Play control. Individually inspected PNGs and separate XML are
retained public-no-control. WebView74/145 respectively; cause unestablished.
Opt-in read-only DevTools diagnostics established a zero-height iframe despite
a296×448 native viewport in38086007749. A CSS-only correction8e46329 still
fails both public runs38086337225/38086335271 with the same zero-height frames.
Nativec6113d2 now explicitly sets MATCH_PARENT layout parameters before HTML
loading, following Android's WebView layout-size contract, and restores relative
CSS height. Local debug/test buildPASS1m44s; both bounded source review axes0.
Public17/10 runs38086671894/38086674099 establish corrected296×448 iframe/body
dimensions and reach trusted Play, then fail native Playing with provider
embed-disabled copy/Retry/external fallback. Exact101vs150/cause unestablished;
actual media remains unproved. Alternate existing signedbHQ probes38087138529/38087140673
also fail Playing after trusted Play with the same native refusal; no broader
cause established. Full10 run38087174575 at e8fafd9 stops before video in cached
profile recovery,7/8 browse cases pass. Failure bitmap is after scenario disposal,
so active state/cause unproved. Fixturef6c016e waits existing New post enabled
readiness before the fresh cached author tap and captures inside the scenario;
original cached-history/error assertions unchanged. Focused browse10 run38087621645
PASS8 cases15.376s atf6c016e; individually inspected cached-Likes bitmap shows the
retained liked post and outage/history warnings. Cleanup383caac preserves the
primary failure through captures/server close; both bounded reviews0. Outstanding
full10 run38087989539 at383caac is pending. Full17 companion38087176435 FAILS
at e8fafd9 before later foundation/public-read groups: video3/4 pass44.025s,
first tap case times out atline50 before scrolling/tap with0 provider requests.
Original in-scope PNG shows a cached queued post, not the selected video post.
The fixture now waits the same completed archive/loading condition as its other
cases and uses the existing scoped scroll/tap helper; positive readiness,
Referer/no-autoplay/no-transaction and close assertions remain. Runtime pending.
Android10 companion38087989539 also fails the same pre-scroll first case,
3/4 videos pass40.199s; browse8/8 pass15.281s. Its original PNG likewise shows
cached queued content. Corrected focused17 run38088217096 at534330e PASS4
cases66.007s. Original tapped-player PNG shows ready controlled-provider text,
not real media. New full17/10 runs38088542044/38088569769 at534330e PASS127 completed
cases/36 records/11 actual interruptions each, core44/build/release/lint,
both public Saito reads, cached cold launches and signed5-to8 continuity.
Selected originals individually inspected; incomplete official-feed bitmaps on
both versions and10 player header are excluded from visual acceptance, cause
unestablished. Neither prior failed full run is accepted.
Both source/fixture review axes0 through534330e. No further #17 source edits are
planned without new evidence. Continue the independently unblocked #18 next,
while those immutable regression runs finish. CDP reads only allowed public frame DOM;
page screenshots were removed at23eea43. No ticket closure until acceptance.
See NATIVE_VIDEO_VERIFICATION.md. No ticket closure or new APK.

#16 Giphy remains open: an Android-authorized key and clarification of provider
URL retention are unresolved. Blocker recorded on issue16. The prior owner
question is unanswered; do not reuse backend web credentials or repeat routinely.
Continue independently ready work sequentially.

- Ticket #8, "07: Open threads and browse user profiles", is complete at final
  source c8fe9b7 against base 44cae5d. Full run 37943159613 passes both SDKs:
  core15, release/debug/test assembly and lint, 47 completed instrument cases
  each including8 browse cases, plus two intentional process-kill stages each.
  Both public feeds, cache/wallet cold launches and signed5-to-8 updates pass.
  Public logs and representative images inspected/retained ticket07/final-full.
  Both final source-review axes have zero actionable findings. References and
  functional red/green chronology are in thread/profile verification and
  ticket07/INTERIM_REVIEW/REVIEW. No production writes or new APK publication.
- Ticket #9, "08: Follow accounts and read a Following feed", complete at
  reviewed source6d5d9da against base6dd511d. Final full37960235937 passes:
  core15, release/debug/test assembly/lint,55 completed instrument cases each
  SDK (53 top-level+2 recovered process cases),2 additional intentional
  process-kill stages each. All8 Following cases pass19.341/44.848s on29/37.
  Both source-review axes have zero actionable findings. Public logs and
  representative Following/recovery images inspected/retained ticket08/final-full.
  Wallet-scoped follow choices shared across backend sessions; selected-peer
  signed-author queries; independent wallet/backend Following cache; Retry after
  temporary choices-read failure; preserve verified partial pages before later
  author failures. No cross-device/on-chain following promise. Prior cache
  formats unchanged. Details NATIVE_FOLLOWING_VERIFICATION.md/REVIEW.md.
  Both native public reads pass selected-label semantics. Official bitmap
  captures display owner's label despite assertions; cause uninvestigated,
  excluded from visual acceptance. Controlled switching images are correct.
  Wallet/cache recovery and signed5-to-8 continuity pass. No new APK, private
  uploads, production writes or physical-device/push proof. Continue all35.
- Ticket #10, "09: Publish text posts and replies with recoverable drafts", is
  complete at reviewed candidateaec16fc against base78163d6. Production source
  unchanged fromcd1ee24. Full Android10 in37994339188 and full Android17 in
  38000254350 pass70 completed cases each (including14 posting cases), plus3
  intentional process-kill stages each. Core19/release/debug/test/lint pass;
  both public reads, cache/wallet cold launches and signed5-to-8 continuity pass.
  Both final review axes have zero actionable findings. Companion production
  source equivalence verified. Encrypted wallet/backend/parent-scoped drafts,
  root/reply signing, original-candidate retry, truthful availability and checked
  navigation saves are complete. No automatic draft publication. Invalid-save
  data loss was reproduced and fixed before persistence. Public acceptance logs
  and selected inspected images: ticket09/full-android10/full-android17.
  Details NATIVE_POSTING_VERIFICATION.md/NATIVE_POSTING_CONTRACT.md/
  NATIVE_POST_TEXT_POLICY.md and ticket09/REVIEW.md. Stated bitmap limits remain.
  No new APK, production transaction, physical Pixel or push proof.
- Ticket #11, "10: Queue posts offline and resume on their original backend",
  complete at reviewed candidateb628a81, productionf03832a, base4bc4cd6.
  FullAndroid10 in38013725295 and fullAndroid17 companion38015116524 pass78
  completed cases each plus4 intentional process-kill stages. Core19,
  debug/test/release/lint,15 posting/6 queue cases, both public feeds,
  cold-cache/wallet recovery and signed5-to-8 continuity pass. Actual queue
  process death, realSAF identity replacement, backend pause/cancel/retry and
  foreground/background uncertainty reconciliation verified. Bothreviewaxes0
  actionable. Productiondiffbetweenacceptedcandidates empty. ExistingAndroid17
  thread-recovery timeout corrected with fixture readiness/navigation/capture;
  priorcauseunestablished. Publiclogs/inspectedimages ticket10/full-android10/
  full-android17; details NATIVE_POST_QUEUE_VERIFICATION.md/REVIEW.md.
  No newAPK, productionTX, physicalPixel or force-stop delivery proof.
- Ticket #12, "11: Like posts and perform ordinary reposts", complete at
  reviewed production `76197a4` against base `58aed22`; final accepted fixture
  `5ab2481` has identical production source. Full **38023863701** passes
  Android10/17: core21, debug/test/release/lint,87 completed instrument cases
  and five intentional process-kill stages each. Social8 takes15.452/15.749s;
  cold recovery2.356/3.424s. Both public feeds, cache/wallet cold recovery and
  signed5-to-8 identity continuity pass. Downloaded logs counted and selected
  images inspected/retained in ticket11/final-full. Both source and fixture
  review axes have zero actionable findings. Original signed candidates,
  exact availability retry, wallet/backend isolation and truthful served counts
  across feed/thread/profile are complete. Prior full fixture failures and
  independent WASM/Elliptic frame proofs remain in
  NATIVE_SOCIAL_INTERACTIONS_VERIFICATION.md/REVIEW.md. Android17 owner bitmap
  has stale official label despite passing assertions, excluded from visual
  acceptance; cause uninvestigated. No new APK or production write.
- Ticket #13, "12: Publish and display quote posts across supported formats",
  complete at accepted4e9fe23, fixedbase1b7f0f6. Full38030511763 passes
  Android10/17: core25, debug/test/release/lint,95 completed instrumented cases
  and six deliberate process interruptions each. Quote7 takes22.353/24.087s;
  actual cold quote reconciliation5.127/5.838s. Both public reads, cached cold
  launches, wallet recovery and signed5-to-8 continuity pass. Logs counted and
  selected images inspected/retained ticket12/final-full. Bothreviewaxes0.
  Independent WASM/Elliptic actual-frame checks and A/B actual-template proof
  retained: current web original-text omission means partial interop, no DOM or
  live-write proof. Bounded original lookups/cache, current/history/profile
  discovery, quote drafts/candidate recovery and partial social counts complete.
  Failed original signatures retain authenticated commentary. Image composition
  explicitly assigned #15, whose dependencies include #13. Final17 public owner
  bitmap lacks header fields despite semantic pass; cause unestablished, excluded
  from visual acceptance. Functional quote images correct; polish remains#35.
  No newAPK, productionwrites, Pixel/push/force-stop-delivery proof.
- Ticket #14, "13: Edit and delete the author's posts", complete at accepted
  device2f50ac6, applicationa545b94, fixedbase6c657d4. Shared public verified
  edit/deletion proofs update feed/Following/thread/profile/likes/quotes.
  Encrypted candidates/reviewed baselines survive recovery; conflicts require
  explicit choices. Delete success requires exact command and verified absence
  across both archive streams; historical-copy explanation retained.
  FullAndroid10 38057998966 and Android17 38056770920 PASS core35/build/release/
  lint,103 completed cases in31 logs each, eight actual process-death recovery
  stages each. Both public feeds, cached cold launches and signed5-to8 identity
  continuity PASS. Production/build/scripts/workflow equivalence between
  accepted2f50ac6/4513c4b verified. All source/supplementalreviewaxes0 remaining.
  Actual native create/edit/delete frames independently verify pinned WASM/
  Elliptic and unchanged A/B controlled receivers. Small conversation header
  overflow found/fixed during full regression. Earlier viewport/readiness
  fixture failures retained without claiming unproved causes.
  Counted logs/selected inspected images ticket13/full-android10/full-android17;
  deleted-profile bitmaps excluded from visual acceptance, causes unestablished.
  Public backend labels correct on10/17 after exact-file reinspection. Details
  NATIVE_AUTHOR_ACTIONS_VERIFICATION.md and ticket13/REVIEW.md.
  Known upstream current-retweet archive lookup bug recorded; native quotes use
  working create-tweet path. No node repair, live write, chain, Pixel or push claim.
  No newAPK. Next ready#15: bounded image composition/viewing with recovery.

- Ticket #15, "14: Attach and view images in posts, replies and quotes", is
  complete at productioncb3d9c3, accepted fixturesd35f965/23d7202, base3d47253.
  Bounded asynchronous galleries/viewer and off-UI normalization, image-only
  composition, authenticated dual-quote image order and encrypted media drafts
  complete. Verified saving precedes external picker launch; failed saving
  preserves work and launches no picker. Newest text/source-deleted prepared
  image and exact signed candidates survive real process death. Backend/wallet
  isolation, queue and author actions preserve media. Full10/17 each123 cases/
  35 logs/11 deaths; both review axes0. Current actual frames independently
  verify pinned WASM/Elliptic and controlled unchanged A/B templates/browser.
  Retained failures have explicit cause/viewport limits; landscape polish #35.
  No live publication, physical Pixel/push or new APK claim. Full chronology
  remains NATIVE_IMAGES_VERIFICATION.md and ticket14/SOURCE_REVIEW.md.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Implementation tickets: #2 through #36, with native GitHub dependencies.
- Fourteen of35 tickets complete: #2 crypto compatibility, #3 persistent wallet
  and signing/updates, #4 feed/cache, #5 fixed backends, #6 authenticated export,
  #7 safeguarded import, #8 threads/profiles, #9 following, #10 posting/drafts.
  #11 queues; #12 likes/reposts; #13 quotes; #14 author edits/deletion; #15 images.21 tickets still open. Preserve all accepted features; no Ralph or parallel implementation.

## Completed foundation references

#5 reviewed source177a647, final full37868642713 and focused public reads
37870028403/37870030262. Fixed backend selection, checked persistence and
uncertainty fail closed. Report NATIVE_BACKEND_SELECTION.md; ticket04/REVIEW.md.

#6 reviewed source7a2ab0b, full37892360326 passes Android10/17/core14,29 normal
cases per SDK plus actual export process-kill recovery, assembly/lint and signed
updates. Real PIN/SAF, provider failures/deadline and new authenticated retry.
Report NATIVE_WALLET_EXPORT_VERIFICATION.md; ticket05/final-full and REVIEW.md.

#7 reviewed source5225d06, closure44cae5d, full37921133133 passes both SDKs:
core15,39 completed instrument cases each plus2 actual process-kill stages,
release/debug/test assembly/lint, signed5-to-8 updates, both public feeds and
cold-cache/wallet recovery. Seven import UI flows, strict byte rejection,
same-wallet vs replacement, fresh balance-dependent outgoing backup gates,
verified old-state rollback and actual pre-commit cold recovery. Both axes
zero actionable findings. Detailed history and safeguards remain authoritative
in NATIVE_WALLET_IMPORT_VERIFICATION.md and ticket06/REVIEW.md/final-full,
and older handoff checkpoints are preserved in Git at44cae5d.

Initial wallet/live-feed milestone is published as stable signed version10
(source5225d06). No physical Pixel/update8-to-10/power-cut claim. Prior official
Compose bitmaps had inconsistent labels; use each report's stated evidence
limits. #9 also has official bitmap inconsistencies; no invented cause.

Current next: record #16 external credential blocker, then #17 and remaining ready tickets sequentially. Owner reviews
completed app; no intermediate phone-review gate is required.

## Verification already inspected

Ticket #2: hosted run 37834551583 passed Android 10/17 independent signature
and visible diagnostic checks. See [native proof](../research/NATIVE_PROOF_VERIFICATION.md).

Ticket #3: run 37849279965 at 35dff32 passed both SDKs: eleven instrumentation
tests per SDK, cold-process identity preservation and actual signed 5-to-8
updates. Final two-axis reviews passed through 1b50e2f; a1973c2 is its final
status/evidence commit. Recovery fails closed for missing/corrupt published
wallets. Controlled interrupted-initialization fixtures model crash states;
they do not prove arbitrary physical power-cut behavior. Directory/file sync,
reopening verification and a nonsecret publication marker prevent Retry from
silently replacing an exposed identity. Detailed failures and fixes are in
[wallet verification](../research/WALLET_PREVIEW_VERIFICATION.md), with durable
public evidence under docs/evidence/ticket02.

Ticket #4: full run 37857285246 at 4fd87bd passed both SDKs, including separate
fresh native owner reads, cached cold launches, wallet recovery and signed
updates. Downloaded outputs/screenshots were inspected; public feed evidence
is retained under docs/evidence/ticket03/first-full. This predates later fixes.
Focused run 37858491264 at c17e0b5 passed reconnection and corrected refresh/
recreation position checks. It exposed a missing cached unreadable warning
(now fixed) and a storage fixture error: `.new` is not Android 10's write path.
The corrected fixture obstructs the actual public cache base and restores its
preserved contents in finally, without changing wallet storage. All four final
controlled feed cases passed on both SDKs in 37859209011, before its later live
Android 17 failure. See [feed verification](../research/NATIVE_FEED_VERIFICATION.md).

## Build and review operations

Repo: /home/david/projects/redsquare-android, public GitHub robelene112/redsquare-android,
main branch. Kotlin/JVM core plus Compose Android app, Android 10+; Docker
supplies Java 17, Gradle and SDK. Run scripts/android-build for local checks.
Hosted KVM provides actual Android 10/17 runtime verification. Intermediate
focused checks do not replace final full verification. Capture failures and
separate functional failures from fixture/build/emulator setup errors.

Stable review: https://davidengelen.nl/redsquare/. Versions5,6,8,10 published;
2,3,4,7,9 remain unpublished. Version10 source is5225d06. Signing state
is private under the operator's local share directory, outside Git/public
hosting; never print or copy its contents. Public certificate fingerprint and
publication/update checks are documented in [review operations](../REVIEW_BUILDS.md).
Review publication touches only the app's existing static directory.

Use project-local implement/tdd/two-axis code-review and applicable Saito
skills. Node access follows [operations](../OPERATIONS.md) and maintained Snake
routing. Production configuration, block production, on-chain actions and store
submission require their own explicit authorization. No production writer was
changed for the Android app. Read-only public integration is separate from
ordinary controlled tests; no production transaction was broadcast.

## Outstanding external acceptance

Owner requested physical app review at completion. Pixel 7 Android 17 checks
are tracked in #35. Emulator results do not establish physical timing, updates
or push behavior. Use [the timing protocol](../research/FEED_PERFORMANCE_PROTOCOL.md).

Closed-app notifications require Firebase access and a gateway. A prior optional
one-question prompt asked dedicated versus existing Firebase project; no answer
has arrived. Continue independent work and never invent credentials or upload
wallet private keys. Source research is documented, not implemented delivery.

Giphy Android credentials are also unresolved. An optional single question on
2026-10-10 asked whether an existing Android-authorized key is available and
where configured; no response yet. Do not ask again routinely, invent a key or
assume a backend's browser key is authorized for Android. Source contract is in
NATIVE_GIF_VIDEO_CONTRACT.md. Continue independent tickets and controlled
implementation while external configuration remains pending.

Read-only research notes for later tickets were prepared during hosted checks:
NATIVE_QUOTES_CONTRACT, NATIVE_EDIT_DELETE_CONTRACT, NATIVE_IMAGES_CONTRACT,
NATIVE_GIF_VIDEO_CONTRACT, NATIVE_PROFILE_EDIT_CONTRACT, NATIVE_USERNAME_CONTRACT,
NATIVE_DISCOVERY_MODERATION_CONTRACT, NATIVE_NOTIFICATIONS_CONTRACT,
NATIVE_SAITO_TRANSFER_CONTRACT, NATIVE_PRIVATE_CHAT_CONTRACT and
NATIVE_GROUP_CHAT_CONTRACT (all .md under docs/research). They are uncommitted
and must be included with their own relevant tickets, not the #12 closure.
They establish source contracts, not runtime or feature completion.
