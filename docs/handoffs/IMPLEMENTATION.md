# Sequential implementation handoff

Updated 2026-10-09, 00:09 UTC. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Implementation tickets: #2 through #36, with native GitHub dependencies.
- Two of 35 tickets complete: #2 signature compatibility and #3 persistent wallet
  and signed updates. #4 live feed/cache is active; its review base is a1973c2.
- Feed implementation 1bcd832 implements authenticated owner WSS, independently
  verified public feed posts, persistent public cache, cold starts, refresh,
  keyed scroll position, accurate outage/recovery and unreadable-response copy.
- Standards and Spec reviews found no remaining application/security defects
  at 1bcd832. Their stale-handoff finding is corrected by this update. Cache
  commit verification, callback races and refresh-test synchronization are fixed.
- Run **37859209011** passed fourteen core tests, assembly/lint, the full
  Android 10 runtime and all four controlled Android 17 feed cases. The later
  Android 17 fresh live test crashed during Compose layout; the full candidate
  did not pass. Retained failure evidence is under docs/evidence/ticket03/failed-final.
  Focused Android 17/public read run 37861050271 passed at 1374b1c with Compose
  BOM 2026.06.01 (UI 1.11.4) and the legacy test scheduler; results and public
  screenshots were inspected and retained in docs/evidence/ticket03/compose-focused-17.
  Reviewed candidate 8060ed2 aligns Lifecycle with resolved 2.9.4 and migrates
  test factories to the supported queued v2 scheduler. Both reviews found no
  new issues. These are compatibility changes, not an established explanation
  for the crash. Full run 37861712249 at dec035d passed Android 10 completely;
  Android 17 feed/live-read, wallet recovery and cold continuity passed, then
  the update-notice test timed out before its signed-update stage. Its one-shot
  HTTP fixture is being replaced by a multi-request server with diagnostic
  request counts and a public screenshot, keeping UI/link assertions. App code
  remains unchanged while this is investigated. Final full acceptance remains
  pending. Do not close #4 or publish build9 on partial evidence.
- Signed feed build 9 (f71c506) is prepared and signature/package verified,
  but remains unpublished. Stable preview is version 8, wallet foundation.
  Publish the first complete milestone after #7 includes server selection and
  safeguarded import/export; preserve package and signing. The owner reviews
  the completed app, so no intermediate physical review is required.
- Next after #4: #5 two fixed backends, then #6 export, #7 guarded import.
  The initial review milestone is completed by #7, not by a basic feed alone.

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

Stable review: https://davidengelen.nl/redsquare/. Versions 5, 6 and 8 published;
2, 3, 4 and 7 remain unpublished. Version 8 source is 7e24a03. Signing state
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
