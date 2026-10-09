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
  Eight of35 complete;27 remaining including active. Review base78163d6;
  current candidatecd1ee24. Focused Android10 37992419586 passes14 cases
  (65.692s) plus actual process-death reconciliation. Full37993042535 running.
  Local core/debug/test/lint passes2m38. Both final source-review axes atcd1ee24
  have zero actionable findings. Runtime acceptance and final full still pending.
  Latest actual red37991336062 at67e883d passes13/14 and actual process-death
  reconciliation. Only invalid draft saving overwrites the last good record;
  screenshot confirms rewritten text. Strict UTF8 prewrite validation fixes it
  before any key/file mutation. Local clear and wallet replacement isolation
  pass that red run. Composer state and shared fixtures address review smells.
  Existing signed draft formats remain readable. Retry checks verified exact
  availability before resending the original candidate. No automatic draft Send.
  Save-before wallet/backend navigation, serialized encrypted persistence,
  activity memory and retained reply navigation protect composed work.
  Source trace and all significant red/green references:
  NATIVE_POSTING_VERIFICATION.md/NATIVE_POSTING_CONTRACT.md/
  NATIVE_POST_TEXT_POLICY.md. Public evidence ticket09; final REVIEW.md keeps
  Standards and Spec separate. Full Android10/17 required before closing#10.
  Push checks are source/core/debug/test/release/lint only, not device acceptance.
  Official controlled bitmaps with inconsistent backend captions are excluded
  from visual backend proof despite correct semantics/routing. Cause unproved.
  No new APK publication, production writes or physical Pixel/push proof.
  Next: finish final full, close#10 with evidence, then#11 offline queues.

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
