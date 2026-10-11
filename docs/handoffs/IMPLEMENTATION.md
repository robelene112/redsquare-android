# Sequential implementation handoff

Updated 2026-10-11. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

Active #18, "Edit profile bio, avatar and banner", assigned/unblocked.
Fourteen of35 complete (#2–#15). #16 Giphy awaits Android-authorized credentials;
#17 YouTube actual provider playback remains unproved. Do not close either from
controlled source/layout successes. Next independent ticket after #18 is #19.

Full #18 review base afce6610e4c78a2183bf154ac99cc35d7917b03c. Canonical
source contract, exact test/failure chronology and evidence limits are in
NATIVE_PROFILE_EDIT_CONTRACT.md and NATIVE_PROFILE_EDIT_VERIFICATION.md.
Production includes verified complete signed snapshots, native generated avatar,
scrollable public profile, image/banner display, selection/crop/removal and bio
editing. Encrypted identity/backend-owned drafts and original signed candidates
survive interruption; shared EncryptedLocalFile preserves existing post formats.
Publication merges newest verified untouched fields; failed saves stay truthful.
Private supersession barriers and historical availability prevent stale retries.
Explicit Start new edit confirms retirement with a warning, keeps newest verified
baseline privately and never erases a received update elsewhere. Exact signed
candidate preview preserves media omissions; editable baseline merges run on IO.
Late-commit retries use exact per-store own-attempt witnesses without treating
readable unsynced bytes as successful writes. Wallet import waits for mounted
persistence and acknowledges pending/unknown work on both supported backends.

Full cumulative source review afce661...ed645ab: Spec0 and Standards0 remaining
hard issues; narrow test-capture duplication extraction is committed01858ad.
Bounded ed645ab...01858ad review with independent process evidence inspection is
running. The extraction leaves production/assertions/readiness gates unchanged;
local test assembly PASS1m13s (rawlog in ignored scratch).

Actual ed645ab dual focused process proof PASSED: Android10 38109328015 and
Android17 38109329450. Four preceding classes report16 cases each (Android10
fifteen pass plus one API30+ assumption; Android17 all16). Two real app PID deaths
per SDK; newest bio and prepared images recover after owned source deletion and
death in the real external picker. Uncertain exact signed candidate survives
second death; cold exact availability is read-only with zero new broadcasts.
Verified clearing retains public bio/images. Recovery never resets wallet/backend.
Final recovery JUnit1PASS4.21s/5.913s. All12 original PNGs individually viewed by
root, XML/logs/public checkpoints retained ticket17/profile-process-green.
Bio is above the scrolled media viewport and verified by exact runtime text,
not a visible-bio bitmap claim. First two runner crashes are deliberate
interruptions verified by host phase/PID checks, not JUnit passes.

First full regression on01858ad: Android10 38109837264 fails two existing
browse visibility assertions after offline/cold headers consume the viewport;
six other browse cases pass. Original in-scope failure PNGs and raw log retained
profile-browse-full-red. Android17 38109838600 passed browse8 and following8
before cancellation during posting, not a full acceptance run. Source push
38109832420 passes core49/debug/test/release/lint. The same old-flow fixture
now scrolls posts/statuses/tabs while preserving visible assertions, and captures
native XML/PNG separately. Local test assembly PASS1m11s. Committed bounded
review01858ad...aea25ca passed both axes0. Next10 full38110320329 passed
cold Posts/Replies/Likes/history but fails the other offline case's
performScrollTo because the target is not composed. Same fixture now searches
LazyColumn via existing performScrollToNode, preserving every exact display
assertion; local test assembly PASS1m8s. Original4PNG/XML/rawlogs retained
profile-browse-scroll-audit. Android17 full38110321794 onaea25ca continues with
identical production/core/harness. Next is fresh10full and focused17browse for
the test-only correction; do not repeat unaffected17full if its run passes.
Do not close #18 until downloaded full evidence/runtime checks pass,
including shared post/wallet regressions, actual recovery and signed5-to-8
updates. Emulator checks do not establish physical Pixel7/Android17 acceptance.
No APK published or production node/wallet/on-chain/store state changed.

Original PNG/XML are separate observations. Historical capture discrepancies and
invalid early success gates remain qualified in the canonical verification doc;
never invent their cause. Current process observations agree in essential state.
Actual native selected/removed/new-edit frames passed independent pinned
WASM/Elliptic and unmodified A/B Profile server/browser methods with in-memory
adapters. This is not live chain inclusion, deployed DOM or physical proof.

Protected untracked future research must stay out of #18 commits: username,
discovery/moderation, notifications, transfers, private/group Chat contracts and
private-chat reference helper/JSON/Markdown. Sequential implementation only;
review agents may independently inspect but never claim another ticket.

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
