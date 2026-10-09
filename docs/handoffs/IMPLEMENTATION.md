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
  Eight of35 complete,27 remaining including active. Review base78163d6.
  Latest implementation candidate6cfa908. Focused14-case37990196219 passes13;
  local clearing reaches blank persisted editor but its fixture rejects exact-sig
  archive queries (missing field4). Fixture corrected; do not count this as a
  production failure. Local debug/test/lint passes2m40s. Expanded malformed-text
  cold-draft regression compiles (1m33s), functional red pending next dispatch.
  Final reviews at6cfa908: Standards0 hard violations/2 possible smells;
  Spec1 P2: lossy UTF8 draft encoding commits changed text before verification
  reports failure, discarding the last good draft. Reproduce through UI before
  strict prewrite encoding fix. Full acceptance pending.
  Reply recreation actual red37985523535 fixed6abdc11 by wallet/backend-scoped
  navigation ViewModel and retained reply mode. Twelve-case37987683585 passes
  49.873s plus cold reconciliation, including Unicode UI and longer preserved
  multilingual text through both controlled backends. Fourteen-case red
  37988651709 passes13 cases,69.856s; only Start another draft action absent.
  Real SAF wallet replacement warning/isolation and outgoing-draft restoration
  pass. Minimal6cfa908 confirms local clear, never claims server deletion, and
  verifies encrypted empty record before clearing text/candidate. Persistence
  and memory update complete together under NonCancellable across recreation.
  Original candidate remains after cancellation or failed save.
  ff55aab fixes LocalActivity lint, makes push checks source/core/debug/test/
  release/lint only, and correctly limits posting-android17 to SDK37. Actual
  push37987142391 and37987683472 pass with both device stages skipped. Full
  manual Android10/17 acceptance still required; CI parallelization unimplemented.
  Timing report ANDROID_CI_TIMING.md. Longer-run37985523535 spent minutes
  unpacking system image; cause beyond stage timing unproven. Its official
  controlled bitmap still has an owner caption despite correct semantics and
  separate fixture broadcasts; exclude that bitmap from visual backend proof.
  Seven-case37982268510 passes31.633s. Eight-case37984084989 passes32.613s
  plus actual process death and cold reconciliation without rebroadcast.
  Unsaved root-editor recreation actual red37983208565 was fixed by scoped
  in-memory ViewModel edits. No plaintext saved-state bundles. Saved signed
  candidates remain authoritative. Ten-case37984759300 passes43.649s plus
  cold reconciliation: failed save retains server/text; damaged encrypted file
  stays untouched until repaired, then Retry reloads. Public logs and selected
  images inspected/retained ticket09/{seven-green,memory-green,storage-green}.
  Shared root/reply composer uses verified encrypted draft persistence,
  serialized writes, candidate assignment before save, save-before backend and
  identity navigation, and disabled Back/Save during Send. Restored candidates
  show uncertain Send-requested status. Retry checks the exact verified archive
  signature before resending the original candidate. Storage and network
  errors differ. No automatic draft publication. Independent root/ordered-reply
  WASM/Elliptic vectors pass. Existing draft formats retained.
  Local malformed-Unicode red rejected by strict UTF8 serialization before
  signing; core green includes independent signatures and JSON escaping bound.
  Native1MiB complete-message limit is safety policy, not a consensus maximum
  or universal500-unit cap. Source trace: NATIVE_POST_TEXT_POLICY.md.
  Wallet replacement explicitly explains that local posting records remain
  with the outgoing identity and are excluded from wallet exports, and that
  uncertain publication may already have reached its server.
  Remaining: confirmed local clear green, final two-axis review/fixes and full
  Android10/17 acceptance. Review fixes may require focused rechecks.
  Both interim review axes remain ticket09/INTERIM_REVIEW.md. Detailed earlier
  contract/history: NATIVE_POSTING_CONTRACT.md/NATIVE_POSTING_VERIFICATION.md.
  #10 reopened after premature closure; keep open until acceptance. No new APK.

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
