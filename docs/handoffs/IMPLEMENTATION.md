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
- Active #10, "09: Publish text posts and replies with recoverable drafts",
  assigned@me, all native blockers complete. Review base78163d6; #9 closed
  with comment6085557419. First draft-save/reopen/no-broadcast UI case and
  focused posting runtime harness ready; actual functional red pending.
  No composer production code yet. Primary protocol researcher writes only
  docs/research/NATIVE_POSTING_CONTRACT.md, no implementation or live writes.
  See NATIVE_POSTING_VERIFICATION.md. Continue all35 sequentially.

- Authoritative release spec: https://github.com/robelene112/redsquare-android/issues/1
- Implementation tickets: #2 through #36, with native GitHub dependencies.
- Seven of35 tickets complete: #2 signature compatibility, #3 persistent wallet
  and signed updates, #4 authenticated feed/cache, #5 fixed backend selection,
  #6 authenticated wallet export and backup/recovery guidance, #7 safeguarded
  wallet import/recovery and the first live-feed review milestone; #8 threads
  and profiles with available-history paging and recovery.
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
- #7 complete, accepted source5225d06, review base9e7f40d. Final full37921133133
  passes core/assembly/lint and Android10/17 all39 normal cases each, plus both
  actual intentional process-kill stages. Seven import flows include strict
  malformed-byte rejection, explicit same-wallet restoration and MainActivity
  failed-confirmation recovery through a real isolated filesystem fault. Separate
  post-write sync rollback and pre-commit process/cold recovery pass. Both public
  backends, cache/wallet cold continuity and signed5-to-8 updates pass. Public
  logs/images inspected and retained ticket06/final-full. Official Compose captures
  mismatch the server label, with cause unestablished; separate device screenshots
  correctly show saito.io and are retained alongside them. Final source reviews
  report zero actionable findings. Version10 source5225d06 is published, and its
  public manifest/downloaded APK match the verified signing/package/SHA identity.
  Physical Pixel checks remain#35; no actual8-to-10 update or arbitrary power-cut
  claim. Current next ticket#8 threads/profiles is ready; implement sequentially.
  See research/NATIVE_WALLET_IMPORT_VERIFICATION.md for final acceptance.
  The following #7 checkpoints are historical; their pending statements describe
  the checkpoint, not current acceptance.
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
  asynchronous cleanup are implemented. Local core/test/debug/test assembly
  passed. Green37904408242/37904411630 at2047a5a passes all5 import tests on both
  SDKs, including timeout/retry/new preview retained past the60-second provider
  release. Two public images inspected and logs retained in ticket06/provider-green.
  Next test requires explicit risk acknowledgement before replacement, saved/
  active agreement across recreation and fixed-backend preservation despite
  imported peer settings, then restores the original identity through the UI.
  Compile passed. Actual functional red37905541570 at04bf0a0 reaches fresh-zero
  Skip backup and fails at missing Confirm replacement (test line274); all5
  earlier flows still pass. Log retained in ticket06/activation-red. Minimal
  acknowledgement/confirm flow, activation-time zero recheck and encrypted
  durable recovery record/full saved-options verification are implemented;
  first compile passed and first green37907126447 at5d669bf passes all6 flows on
  Android10, including actual replacement/recreation/backend preservation and
  restoration of the original wallet. Two public activated/recreated images
  inspected and log retained in ticket06/activation-first-green/api-29.
  Android17 run37907129890 fails before activation: no Compose hierarchy during
  the post-picker predicate (line272), then missing reference file at line136
  in backup-binding. Log retained in ticket06/activation-failure/api-37.0.
  Exact causes unconfirmed; no process-crash result, later tests still ran.
  Wait predicates now allow temporarily absent roots while waiting and file
  selection scrolls/reacquires before requiring a visible item, capturing the
  picker at the point of absence. Later18f87d3 runs37910062409/37910066475
  pass all6 earlier flows on both SDKs, including SDK17 activation.
  New isolated metadata-risk test first imports synthetic other-asset/name/
  pending indicators, then requires outgoing backup despite fresh SAITO zero.
  Actual red37910062409/37910066475 at18f87d3 passes all6 earlier import flows
  on Android10/17, including SDK17 activation. Metadata risk test activates the
  synthetic wallet, then selects the original backup and times out at missing
  Other assets could not be checked (line61). Logs retained in metadata-red.
  No successful risk screenshots exist at this failing point. Minimal outgoing
  cached-name/pending/unsupported-assets indicators and conservative unknown
  gate implemented625ed53. Green37912307057/37912310729 passes all6 earlier
  import flows plus the isolated risk flow on bothSDKs. Four public risk/restore
  images inspected; logs/images retained in metadata-green. Interim two-axis
  review at625ed53 identifies outgoing-export reminder leakage and cached Bound
  slip indicators plus two code-smell judgments; see ticket06/INTERIM_REVIEW.md.
  Bound-slip regression actually fails atcore SaitoWalletTest line24 before
  minimal classification, then focused core green passes. Its final device
  integration and other review fixes remain pending. Current uncommitted source
  included this source-supported numeric WalletSlip.slipType classification,
  committed4ba48ad. Reminder regression4276194 is dispatched37913759494 (scope
  import-risk, actual red pending); focused risk scopes avoid rerunning all6
  established flows, while full acceptance still includes them. Review-stage
  unused-hash removal and shared encrypted save are locally built/tested; final
  device regression and review remain pending. Reminder first run37913759494
  passes, but exports before selection and misses the reported trigger. Corrected
  test982206e actual red37914569104 fails atline68 after authenticated
  candidate-bound export/replacement and recreation; inspected point-of-failure
  image/log retained in reminder-red. Minimal correction clears outgoing export
  status before successful identity publication; local build/device green pending.
  Consolidated current evidence is research/NATIVE_WALLET_IMPORT_VERIFICATION.md.
  Reminder fix669e3da has zero actionable findings in both independent fix
  reviews, and passes Android10 run37915303490. Three public reminder/risk/
  restoration images/log inspected/retained under reminder-green/api-29.
  Android17 run37915307638 fails earlier atline53 waiting for first zero preview,
  before the new reminder trigger. Post-close screen is launcher; exactcause
  unestablished. Log/image retained under reminder-pre-preview-failure.
  New observer captures that first-preview failure and controlled request count
  at the actual failure point, before closing the screen; compile/rerun pending.
  Observerc86bffc compiles and is dispatched37916458311 (SDK17 risk-only);
  inspect result/image/request count before diagnosing the pre-preview failure.
  Verification-only WalletImportStorageRecoveryTest exercises the existing
  public storage operation with real Keystore/AtomicFile and a one-time second
  OS directory-sync failure, then checks exact outgoing options and recreated
  UI identity. No production recovery implementation/hook changed. New focused
  import-storage scopes and finalfull inclusion added; first actual result is
  pending. This adapter/failure + cold-UI evidence does not prove an import-toast
  path or arbitrary process/power interruption. Actual process-kill proof remains.
  Observer run37916458311 atc86bffc passes SDK17 risk/reminder/restore; three
  public images/log inspected and retained under reminder-green/api-37.0.
  Its retry does not diagnose37915307638's earlier first-preview failure.
  Storage-only runs37916685526/37916689763 at73bd65a pass Android10/17 actual
  injected second directory-sync failure, exact restored full options and
  recreated UI identity. Two public images/logs inspected/retained in storage-green.
  New verification-only WalletImportInterruptionTest plus host process checker
  terminates the actual process from the existing OS-sync seam after replacement
  write before record removal, then requires checkpoint/public identity, dead
  process, retained encrypted record and cold app recovery. No production hook
  added. Kotlin/test assembly and script syntax pass; actual runtime pending.
  Actual process runs37917615664/37917619364 at18016f1 pass bothSDKs: checkpoint
  reached after synced replacement write, instrumented process really killed,
  encrypted recovery record retained and new cold app recovers outgoing public
  identity. Two public recovery images/host continuity and both phase logs are
  inspected/retained in process-green. This is one controlled storage/process
  boundary, not arbitrary physical power loss or the import-toast path.
  Same-wallet preview test now also acknowledges/actually confirms restore and
  verifies the original identity, using an authenticated compatible backup.
  Local core and app/test assembly pass; its first actual runtime is pending
  in finalfull. Existing private metadata/reminders remain identity-bound;
  current feed caches are public and backend-scoped. Drafts/outbox/Chat stores
  do not yet exist and must keep these identity boundaries in their own tickets.
  Fixture/test bytes remain in private test APK
  storage; each risk run clears only the isolated .dev emulator app first.
  A failed activation uses existing-only reload, never
  creation. Main withholds the live signing wallet during persistence. Actual
  failure, process interruption, metadata/identity-isolation and same-wallet
  restoration verification still remain within#7; do not claim those complete.
  Fixture remains only in the isolated test APK; harness
  timeout increased to300 seconds for the deliberate65-second late-result check.
  Activation is implemented; failure and actual process-interruption proof remain.
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
- Signed feed build9 (f71c506) remains unpublished. Stable version10 source5225d06
  now completes the initial wallet/live-feed milestone with both fixed backends
  and safeguarded import/export. Preserve package/signing and use higher versions.
  The owner reviews the completed app; no intermediate physical review is required.
- Current next: #9 following. #8 complete. Continue all35 sequentially.

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
