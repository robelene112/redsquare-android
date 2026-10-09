# Sequential implementation handoff

Updated 2026-10-09. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

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
- Active #10, "09: Publish text posts and replies with recoverable drafts".
  Eight of35 complete,27 remaining including active#10. Review base78163d6.
  Current sourcec71221a, latest test5dbfcb4; seven-case37980600924 running.
  Local debug/test assemblyc71221a passes1m29s; corrected test compile48s.
  Six-case green37977871622 at092a5b6 passes Android10 in19.229s, including
  isolated unfinished drafts through backend navigation and recreation; public
  log/image inspected in ticket09/navigation-green. Five-case proof37973493246 at42c5774 passes
  root/nested replies, draft save/reopen, zero-payment send and verified archive
  availability in14.889s. Public logs/images inspected in ticket09 evidence.
  New actual navigation red37976960559 at8ef70f9 loses unfinished owner text
  after switching to official and back (line127). A separate feed transition
  race also failed; the harness now waits for the feed's LazyColumn. Initial
  37976114135 stopped at a placeholder assertion, not functional recovery proof.
  Minimal092a5b6 adds mounted-composer save-before-backend/identity navigation,
  verified encrypted persistence under a Mutex, signed candidate assignment
  before saving, and disabled Back/Save during Send. Existing draft formats
  retained. Navigation save failure keeps current server and editable work.
  No automatic draft publication. No local build active.
  Actual seven-case red37978708226 at1cff5c fails recovered pending copy at
  line189,51.2s; previous6 pass. Minimalc71221a shows recovered Send-requested
  uncertainty, offers same-screen Retry using the exact saved candidate, checks
  Archive before retransmission, and separates transport from storage errors.
  First attempt37979681550 fails only a disabled-editor test selector (SetText
  action unavailable),29.609s; corrected5dbfcb4 selects locked text directly and
  waits for enabled Back before leaving composers. No full pending proof yet.
  Research NATIVE_POST_TEXT_POLICY.md traces the500 UI-only restriction and
  confirms zero-payment Normal format regardless of holdings. Native complete
  JSON payload bound1MiB is safety policy, not an inferred consensus maximum.
  Both interim source reviews are ticket09/INTERIM_REVIEW.md. Remaining work:
  truthful recovered pending/error state, same-screen Retry/Check preserving
  exact ID and reconciliation before rebroadcast, native work in wallet import
  risk preview, pending cold/fault/isolation/two-backend proof, declared text
  bounds, final two-axis fixes/review/full Android10/17 acceptance. Unsaved
  activity recreation still needs its own actual verification; the new case
  recreates a draft already persisted by backend navigation. Independent root
  and ordered-reply WASM/Elliptic vectors are implemented in public core tests.
  Detailed contract/history: NATIVE_POSTING_CONTRACT.md and
  NATIVE_POSTING_VERIFICATION.md. GitHub#10 reopened after premature closure;
  do not close it until acceptance. No production writes or new review APK.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Implementation tickets: #2 through #36, with native GitHub dependencies.
- Eight of35 tickets complete: #2 crypto compatibility, #3 persistent wallet
  and signing/updates, #4 feed/cache, #5 fixed backends, #6 authenticated export,
  #7 safeguarded import, #8 threads/profiles, #9 following. #10 active,27 tickets
  still open. Preserve all accepted features; no Ralph or parallel implementation.

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

Current next: finish#10 then all remaining tickets sequentially. Owner reviews
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

Repo: /home/david/projects/redsquare-android, private GitHub robelene112/redsquare-android,
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
