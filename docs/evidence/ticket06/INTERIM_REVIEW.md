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
