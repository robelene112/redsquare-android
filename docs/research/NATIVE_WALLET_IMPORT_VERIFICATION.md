# Native wallet import verification

Ticket [#7](https://github.com/robelene112/redsquare-android/issues/7), approved
slice6 of35, remains open. Review base `9e7f40d`. This document records inspected
partial evidence, not completed acceptance.

## Verified application behavior

At `625ed53`, focused runs `37912307057` (Android10) and `37912310729`
(Android17) pass all six ordinary import flows and the isolated metadata-risk
flow. Logs and four inspected public risk/restore screenshots are retained in
[metadata evidence](../evidence/ticket06/metadata-green).

The ordinary flows demonstrate actual Android file selection, invalid-file
rejection without identity changes, same/different preview, selection-bound
authenticated outgoing backup, explicit fresh-zero skipping, unavailable/stale/
malformed snapshot handling, provider timeout with a successful fresh selection
remaining intact after the late provider returns, explicit confirmation and
durable replacement/restoration across recreation. Imported peer options do
not change either fixed backend. Backend switching keeps the imported identity.

The isolated risk flow imports the test-only scalar1 identity containing other
crypto, saved-name and pending metadata. Selecting the original backup then
requires a freshly authenticated outgoing export despite a controlled zero
SAITO response. It shows the outgoing cached name/pending indicators, blocks
confirmation without backup and finally restores the original identity.

The snapshot is a trusted selected-server observation with documented freshness
and size bounds, not independent proof of asset absence or immediately spendable
funds. Missing browser history and unsupported crypto remain uncertain.
See [policy and primary source](NATIVE_WALLET_IMPORT_ASSESSMENT.md).

## Review fixes and remaining verification

Two independent interim reviews at625ed53 found two correctness issues and two
judgment cleanup findings. See [the review record](../evidence/ticket06/INTERIM_REVIEW.md).
Cached Bound/special WalletSlip indicators now retain unknown asset risk at
`4ba48ad`; a source-supported pure backup regression failed before the fix and
passes afterward. The unused hash is removed and encrypted initialization uses
the same verified-save operation at `9f9887f`; core and Android assembly pass.
These later changes still need final device regression and independent review.

The first reminder test at4276194 passes Android10 run37913759494, but exports
before selecting the import and does not exercise the reported problem.
The corrected test at982206e exports after candidate selection and checks the
incoming identity's reminder after recreation. Run37914569104 reproduces the
missing reminder at line68, after actual authenticated backup/replacement and
address verification. The inspected point-of-failure image/log are retained in
[reminder red](../evidence/ticket06/reminder-red). Minimal correction clears the
outgoing export message before publishing a successfully imported wallet;
device green remains pending.

Actual failed-save recovery, process termination during replacement, same-wallet
restoration and identity-isolation acceptance remain unfinished. Final full
Android10/17 verification, final two-axis review and a new signed review milestone
must precede ticket closure. Physical Pixel timing/biometric acceptance remains
later work; emulators do not establish those results.

Private documents remain in isolated emulator storage and are never downloaded
as evidence. Scalar1 and the disposable emulator PIN are test-only fixtures.
No production wallet, node configuration or on-chain write was involved.
