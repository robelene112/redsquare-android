# Sequential implementation handoff

Updated 2026-10-09. The owner authorized implementing all 35 proposed
tickets sequentially, committing after each, and reviewing the app at the end.
Continue without routine permission requests or a grill per ticket. Ask only
one question at a time when a material choice really needs the owner.

## Current work

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Implementation tickets: #2 through #36, with native GitHub dependencies.
- Five of35 tickets complete: #2 signature compatibility, #3 persistent wallet
  and signed updates, #4 authenticated feed/cache, #5 fixed backend selection,
  #6 authenticated wallet export and backup/recovery guidance.
  Ticket #5 review base7085bcf; reviewed implementation177a647. Full37868642713
  passes Android10/17 all22 instrument cases each, core14, assembly/lint,
  cache/wallet continuity and signed5-to-8 updates. Final focused public-read
  harness passes37870028403/37870030262; four rendered feed images inspected.
  Both reviews have zero remaining findings; the post-write uncertainty gap
  fails closed with explicit recovery. The invalid fixture and mismatched
  screenshot remain historical evidence, with no asserted screenshot root cause.
  See research/NATIVE_BACKEND_SELECTION.md and evidence/ticket04/REVIEW.md.
- Ticket #6 reviewed source7a2ab0b passes final full37892360326 on Android10/17:
  core14, 29 normal instrumentation cases per SDK plus each intentional
  process-kill stage, assembly/lint, both public feeds, cold cache/wallet and
  signed5-to-8 update continuity. Actual PIN/SAF verified backup, no-lock,
  wrong PIN/cancel, provider deadline/write/read/truncation failure and fresh
  authenticated retry pass. Reminder/recovery and real process-death recovery
  pass. Both reviews through7a2ab0b have zero remaining findings. Downloaded
  logs and public images are inspected/retained under evidence/ticket05/final-full.
  Earlier fixture/functional failures and canceled partial full remain honest
  historical evidence in ticket05/REVIEW.md. No arbitrary mid-write crash or
  physical Pixel biometric/performance claim. Pixel acceptance remains #35.
  Lint completes with nine warnings, no errors; release icon/config polish remains
  later work. See research/NATIVE_WALLET_EXPORT_VERIFICATION.md.
- Active #7 safeguarded import, assigned with blockers #5/#6 closed.
  Review base9e7f40d. First vertical slice verifies an invalid backup selected
  through actual Android SAF leaves the public wallet unchanged across recreation.
  Test and focused import harness added; actual functional red37894544199
  at2e3f491 reaches the Wallet screen and cannot find Import wallet (before
  picker selection). Log retained in ticket06/invalid-file-red. Minimal file
  selection/bounded validation implemented2a934aa. Actual Android10 green
  37895348266 passes invalid file and recreated identity; public image inspected
  and retained in ticket06/invalid-file-green/api-29. Android17 run37895351707
  cannot find invalid-import.json after choosing the root (line34), before
  validation. Cause not established: only log was retained, no picker image.
  Failure observer now captures system screen and synthetic file size; obtain
  actual evidence before guessing/fixing the filename/root selection.
  Observer run37896128323 at5ab92ce reproduces failure; synthetic file size19
  is correct, and post-failure picker image shows Downloads, not the test root.
  This does not prove the exact cause. Selection now rechecks the visible test
  folder header and reacquires/retries root controls instead of assuming a
  closed drawer means the requested folder was selected. Actual green
  37897188055/37897191325 at1bfa635 now passes this flow on both SDKs.
  Public images/logs inspected/retained in ticket06/invalid-file-final-green.
  A valid-backup preview test now selects an authenticated same-wallet backup
  and independent published scalar1 fixture; it requires Restore/Replace
  classification and unchanged active identity. Actual functional red37898039926
  at e692da3 selects the authenticated same-wallet backup and times out waiting
  for Restore this wallet (test line81); invalid-file/recreation still passes.
  Public log retained in ticket06/valid-preview-red. Minimal preview now keeps
  the validated candidate private, displays only its address/classification and
  does not publish a replacement. Focused green37898881744/37898885225 at
  f2d8107 passes both tests on Android10/17. Four public same/different preview
  images inspected and logs retained in ticket06/valid-preview-green. Next
  test requires a freshly authenticated candidate-bound outgoing backup and
  invalidates proof upon reselection. Actual functional red37899714481 at
  997217b passes both earlier flows and fails at the missing required-backup
  copy (line119). Log retained in ticket06/fresh-backup-red. Minimal backup gate
  now accepts only the verified authenticated export callback bound to the
  outgoing wallet and exact selected candidate/nonce. Green37900484896/
  37900488420 at4168ac4 passes all3 import tests on both SDKs, including actual
  SAF/PIN save and reselection invalidation. Four public gate images inspected
  and logs retained in ticket06/fresh-backup-green. Read-only published scalar1
  HTTP balance format observed on both public backends; see assessment doc.
  Next test drives positive/fresh zero/malformed/stale HTTP responses through
  a controlled loopback server and allows an explicit recommended-backup skip
  only for fresh complete outgoing zero. Existing tests now specify unavailable
  loopback balance endpoints so ordinary checks cannot read public balances.
  Actual functional red37901341544 at fcd1f8f passes the3 existing flows and
  times out on the missing positive-balance status (test line192). Log retained
  in ticket06/balance-red. Minimal fixed-host HTTP lookup/strict integer snapshot
  assessment and explicit zero-only Skip backup are implemented. Local core/test
  and debug/test assembly pass. Green37902481094/37902485519 at d4b0954 passes
  all4 import tests on both SDKs; eight public balance images inspected and logs
  retained in ticket06/balance-green. Policy is10-second/2MiB bound,15-minute tip age/1-minute future skew,
  no cache reuse/redirect/fallback. See the research doc for its trust limits.
  A new stalled-provider test requires control to return within40 seconds and
  a fresh preview to remain intact past the controlled pipe's60-second release.
  Compile passed. Actual functional red37903379772 at8f01d88 passes the4 earlier
  flows and times out waiting for terminal failure at40 seconds (test line224).
  Log retained in ticket06/provider-red. Minimal independent30-second main
  deadline, unique read-attempt binding, cancellable descriptor and independent
  asynchronous cleanup are implemented; compile/green pending. Fixture remains only in the isolated test APK; harness
  timeout increased to300 seconds for the deliberate65-second late-result check.
  No activation is implemented yet.
  Testing uses approved parent application/failure boundaries, with fixture data
  in the isolated test APK only. Do not claim valid import preview/activation
  from this slice; those remain upcoming within#7.
  Read NATIVE_WALLET_IMPORT_ASSESSMENT.md and SNAKE_WALLET_SAFEGUARDS.md.
  Fresh backup proof must bind outgoing wallet and selected candidate/nonce;
  a dismissed reminder or a previous saved-status message is not that proof.
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
- Current: #7 guarded import. #6 complete.
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
