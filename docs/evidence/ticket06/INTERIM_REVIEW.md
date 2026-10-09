# Interim review at 625ed53

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
