# Sequential implementation handoff

Updated 2026-10-09. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Implementation tickets: #2 through #36, with native GitHub dependencies.
- Four of35 tickets complete: #2 signature compatibility, #3 persistent wallet
  and signed updates, #4 authenticated feed/cache, #5 fixed backend selection.
  Ticket #5 review base7085bcf; reviewed implementation177a647. Full37868642713
  passes Android10/17 all22 instrument cases each, core14, assembly/lint,
  cache/wallet continuity and signed5-to-8 updates. Final focused public-read
  harness passes37870028403/37870030262; four rendered feed images inspected.
  Both reviews have zero remaining findings; the post-write uncertainty gap
  fails closed with explicit recovery. The invalid fixture and mismatched
  screenshot remain historical evidence, with no asserted screenshot root cause.
  See research/NATIVE_BACKEND_SELECTION.md and evidence/ticket04/REVIEW.md.
- Active #6 authenticated export/recovery reminder, assigned with blockers closed.
  Review base2d16be4. Actual PIN/SAF export passes Android10 run37886921271,
  repeated37888087995; reminder/recovery also passes37888087995. Android17
  passes both flows in37888321321 at b068d89, with actual Compose credential
  PIN pad and saved-file readback. Public logs/screenshots are inspected and
  retained in ticket05/first-pin-green and reminder-green. Test fixture failures
  are separately retained, not counted as functional app failures.
  Wrong-PIN/cancel regression first exposed repeated-picker title ambiguity on
  both SDKs. The actual drawer row is now selected by its resource, with stale
  controls reacquired. At f095a00, full checks include export and reminder;
  both Standards findings are resolved through91d7d35. Spec review finds
  missing failure/interruption coverage and a concrete indefinite-provider-stall
  defect. New real provider pipe stays open60s and requires failure by40s;
  functional red run37889746378 at91d7d35 is observed: no failure by40s,
  while no-lock, normal save and wrong-PIN/cancel all pass. An independent30s
  deadline now invalidates first and keeps cleanup off the completion path;
  cleanup also survives activity-scope destruction. Runtime green passes both SDKs in37890457247/37890459989 at1167582;
  all four export cases and reminder/recovery pass, with public evidence retained.
  Spec re-review finds no new correctness defects in that deadline fix.
  Complete other provider faults, lifecycle/process recovery,
  both SDKs/full checks and review fixes before closing #6. See ticket05/REVIEW.md.
  At f40cf2c, focused Android10 run37891029657 passes five export cases,
  including real write/read/truncation failure and fresh authenticated retry,
  plus reminder/recovery. Process-death host/observer added2a5e507; both
  source reviews report zero remaining findings, with runtime still required.
  SDK17 run37891251005 passes original four and all fault responses but cannot
  observe the final retry PIN: helper wrongly expected a qualified resource ID.
  Actual observed Compose ID is bare cred_pin_pad; fixed7a2ab0b and compiles.
  Corrected focused SDK17 run37892237605 is active. Full37891415056 at51d11c1
  passes core/build/lint and its entire Android10 runtime step, including the
  process check. Its later SDK17 step uses the known invalid waiting helper,
  so superseded/canceled after preserving the completed Android10 step.
  Download/inspect that partial evidence, then obtain corrected SDK17 and
  final full acceptance. Do not call canceled full acceptance green.
  See research/NATIVE_WALLET_EXPORT_VERIFICATION.md.
  Then #7 safeguarded import.
  The first new phone-review milestone remains after #7, then continue all35.

- Ticket #4's reviewed implementation passed final full run **37863843807**
  at c5953de on Android 10 and 17. All fourteen core tests, assembly/lint, four
  controlled feed cases per SDK, fresh owner reads, wallet recovery/continuity,
  notice/link assertions and actual signed 5-to-8 updates passed. Downloaded
  public logs and live/cached screenshots were inspected and retained under
  docs/evidence/ticket03/final-full. Ticket #4 is closed with its acceptance
  checklist checked and linked verification evidence.
- Both independent reviews report zero remaining actionable findings after
  cache/callback/test fixes and the shared update-verification helper. Compatible
  Compose 1.11.4 and supported v2 tests are selected. Historical layout and
  update-notice failures are documented; their original causes are not asserted.
  The realistic update HTTP fixture handles multiple requests, with three
  observed on both SDKs. Final full acceptance now passes.
- Signed feed build 9 (f71c506) is prepared and signature/package verified,
  but remains unpublished. Stable preview is version 8, wallet foundation.
  Publish the first complete milestone after #7 includes server selection and
  safeguarded import/export; preserve package and signing. The owner reviews
  the completed app, so no intermediate physical review is required.
- Current: #6 authenticated export. Next: #7 guarded import.
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
