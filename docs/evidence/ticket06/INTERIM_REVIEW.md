# Interim review at 625ed53

## Latest review at d5a2200

Independent final-candidate review of `9e7f40d...d5a2200` reports no Standards
findings and two actionable Spec findings:

- Raw SAF bytes currently use replacement decoding for malformed UTF-8.
  A valid keypair with an invalid byte inside opaque metadata can preview with
  silently changed metadata. Reject malformed encoding at the document boundary.
- Existing failed-save verification proves protected-store rollback and recreated
  identity, but does not drive confirmation through MainActivity's failed
  activation handler and visible recovery status. Add that application-flow check.

Both remain open. Actual same-wallet restoration, final full regression and
signed milestone publication remain acceptance work. The previous correctness
and cleanup findings are resolved; no scope creep was found.

Regression `5462793` adds a malformed-byte synthetic document selected through
real Android SAF alongside the existing invalid-file case. Actual red is pending
run `37919371098`; production decoding is unchanged at this test commit.

Actual red `37919371098` selects the malformed encoding fixture and times out
at the invalid-file status assertion (line45). The inspected point-of-failure
image shows Replace your wallet with the published scalar1 address. The earlier
invalid-JSON case passes; four other import scenarios pass. The same-wallet
case separately fails its old visible-row lookup at line91. Encoding red log
and public image are retained under utf8-red. Strict UTF-8 REPORT decoding and
the existing invalid-file status are the minimal production correction; actual
green remains pending.

The full run `37918643735` checks the earlier candidate `d5a2200`. Its results
cannot establish final acceptance after these pending review fixes.
It fails on Android10 in the same-wallet test at line86 locating the selected
file, before restore/confirmation. The other five import flows pass. Its
post-run screenshot is zero bytes and cannot diagnose the picker failure.
Public log is retained under candidate-full-picker-failure. The test now uses
the existing scrolling/reacquisition helper rather than a single visible-row
lookup; actual same-wallet restoration remains pending the new final run.

Fixed base: `9e7f40d`. This is an interim review, not ticket completion or final
review. Two independent read-only agents reviewed `9e7f40d...625ed53`.

## Standards

No hard documented-standard breaches. Two judgment findings:

- Possible Speculative Generality: Candidate fingerprint is computed but never
  used. Object identity supplies selection binding. Remove the unused hash and
  describe the nonce accurately.
- Possible Duplicated Code: encrypted initialization and `writeVerified` repeat
  save/sync/readback logic. Share that operation while retaining publication-barrier
  semantics.

## Spec

Two correctness findings:

- An outgoing export's Saved message persists across wallet replacement.
  `LaunchedEffect(address, walletExport.message)` can then hide the incoming
  wallet's backup reminder even though that wallet was never exported. This
  violates the identity-specific dismissible backup-reminder behavior.
- Cached Bound/NFT slips are not included in the `/balance` snapshot. Ignoring
  those indicators can allow backup skipping when asset status is unknown,
  violating the positive/unknown-balance backup gate.

Known incomplete work remains: failed-save/process-interruption evidence,
same-wallet actual restoration, identity-isolation acceptance, final full
verification and publication. No scope creep identified.

Total: Standards two judgment findings, zero hard breaches; Spec two correctness
findings in addition to known unfinished acceptance. All findings remain open
until their fixes and verification are recorded.

Review-stage cleanup removes the unused fingerprint/hash and describes the
candidate object nonce as holding the validated file contents. Initialization
now uses the same bounded encrypted-save/sync/readback operation as replacement,
with the existing publication barrier still preceding creation and completing
after verification. Core tests and debug/test assembly pass. Final Android
initialization/recovery regression and independent fix review remain pending.

The first reminder regression4276194 passes focused Android10 run37913759494.
It exports before selecting the import, so the existing Import action clears
that Saved message and it does not exercise the reported candidate-bound
export trigger. This is not a reproduction or a diagnosis disproving the
review finding. Its log and public reminder screenshot are retained separately
under reminder-before-selection-green. The corrected regression performs an
authenticated outgoing export after selecting the candidate and checks the
incoming reminder after recreation; actual result remains pending.

Corrected actual red: Android10 run37914569104 at982206e successfully backs up
the outgoing wallet for the selected candidate, confirms replacement and
verifies the incoming public address. After recreation it times out at
RiskPreviewTest line68 waiting for the backup reminder. The point-of-failure
public image shows the feed header with no reminder. Log/image inspected and
retained under reminder-red. Minimal fix clears the outgoing export message
before publishing a successfully imported wallet; device green remains pending.

Both independent axes reviewed fixes625ed53...669e3da and report zero remaining
actionable findings in those fixes. Standards confirms both cleanup findings
resolved; Spec confirms both correctness findings addressed. This is a source
fix review, not completed ticket acceptance. Final full verification must retain
initialization/failure/recovery and signed-update regressions because the common
save function now also serves initialization. Reminder green and known remaining
ticket work are still pending.

Reminder correction669e3da passes Android10 focused run37915303490: actual
candidate-bound export/replacement, reminder after recreation, outgoing asset
gate and original-wallet restoration. Three public images/log inspected and
retained in reminder-green/api-29. Android17 run37915307638 fails atline53
waiting for the first zero-SAITO preview, before the corrected export/reminder
path. Its post-close screen is the launcher and does not diagnose the cause;
log/image retained in reminder-pre-preview-failure. A point-of-failure preview
image and controlled balance request count observer are added; rerun pending.

## Bound-slip source and first regression

Pinned source `9a4ca7e9442eb5e872168ced361ce741d7d139d7`:
`node/lib/saito/wallet.ts:806` saves WalletSlip JSON. Its actual field is numeric
`slipType`, defined in `rust/saito-js/lib/wallet.ts:148`, not `type` or
`slip_type` from another wrapper. Bound is enum9 in
`rust/saito-core/src/core/consensus/slip.rs:27`. Snapshot selection excludes it
at `rust/saito-core/src/core/consensus/blockchain.rs:2919`.

The pure wallet/backup regression uses a published scalar1 synthetic cached
Bound indicator. It does not claim a valid live NFT or chain inclusion. Actual
red: `scripts/android-build :core:test --tests '*cached Bound*'` fails at
`SaitoWalletTest.kt:24`, one test run, one AssertionError. Minimal conservative
special-slip handling passes the same focused command (one test) after actual
red, with BUILD SUCCESSFUL. Device integration remains covered by the forthcoming
final run, not by that pure test alone.
