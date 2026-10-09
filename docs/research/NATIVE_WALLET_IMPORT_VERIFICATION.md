# Native wallet import verification

Ticket [#7](https://github.com/robelene112/redsquare-android/issues/7), approved
slice6 of35, is complete. Review base `9e7f40d`; accepted source `5225d06`.

Final full run [37921133133](https://github.com/robelene112/redsquare-android/actions/runs/37921133133)
passes core checks, debug/test/release assembly and lint, plus 39 normal
instrumentation cases per SDK on Android10/17 and both intentional process-kill
stages. All seven import flows pass, including actual malformed-byte rejection,
same-wallet restoration and failed-confirmation recovery through MainActivity.
The latter proves pre-write filesystem failure handling; the separate second-sync
test proves post-write rollback, and the process test proves its specific
post-write/pre-commit interruption and cold recovery. None claims arbitrary
physical power-cut behavior.

Downloaded public logs and eight inspected wallet-recovery/backend images are
retained under [final-full](../evidence/ticket06/final-full). Both fresh backend
reads, cache/wallet cold continuity, update notice and actual signed5-to-8 update
checks pass. Their results do not establish an actual signed8-to-10 or Pixel
update. The official Compose feed captures show an owner label despite the
successful official-label assertion; the separately inspected device screenshots
correctly show saito.io on bothSDKs. Both captures are retained, and the
capture mismatch's cause remains unestablished.

Both final source review axes report zero remaining actionable findings at
5225d06. Signed version10 source5225d06 is published at
https://davidengelen.nl/redsquare/ with the existing application ID/certificate.
The public manifest and downloaded APK match the locally verified package,
version, source and SHA-256. See final-full/publication.json. The requested first
wallet/live-feed milestone is complete; other social/Chat features and physical
Pixel acceptance remain their later tickets. The following chronological records
preserve earlier failures and pending statements at their historical checkpoints.

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

Latest candidate review atd5a2200 has no Standards findings and two Spec
findings. Encoding regression5462793 reproduces malformed UTF-8 being offered
as a valid replacement in Android10 run37919371098; its inspected public image
and log are retained in utf8-red. Minimal correction1b9ab26 uses strict REPORT
decoding with the existing invalid-backup message. Focused import greens on
Android10/17 are pending37920272377/37920275953.

Both focused runs now pass at1b9ab26: six ordinary import flows, isolated
metadata/reminder flow, post-write directory-sync rollback and actual pre-commit
process death/cold recovery. Four inspected public images demonstrate invalid
encoding rejection and confirmed same-wallet restoration on bothSDKs. Logs/images
are retained under utf8-green. These runs predate the new failed-confirmation
test; final full37921133133 at5225d06 is running with that case, both public
backend reads and existing update/feed/wallet regressions.

Final source reviews of9e7f40d...5225d06 report zero remaining actionable
Standards or Spec findings. Signed version10 source5225d06 is prepared and
package/certificate/checksum verified, but unpublished pending final full.

Candidate full37918643735 fails the old same-wallet file lookup before restore,
with the other five import flows passing. Its zero-byte post-run screenshot
does not diagnose the failure. The lookup now uses the existing scroll/reacquire
helper, and same-wallet runtime acceptance remains pending.

The second finding requests actual failed-confirmation UI coverage. A new
verification-only case drives existing MainActivity import/Skip/ack/Confirm
through a real filesystem failure, temporarily removing write permission solely
from the isolated .dev no-backup directory. It expects the restored-wallet
message, enabled import, exact outgoing saved options and recreated public
identity. Permissions are restored in finally. It adds no production fault
hook or new recovery behavior; assembly passes, actual runtime remains pending.

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
Android10 green37915303490 passes, with three inspected public reminder/risk/
restore images and log retained in reminder-green. Android17 run37915307638
fails earlier, at the first zero-preview wait line53, before the reminder path.
Its post-close launcher image does not establish the cause. A point-of-failure
observer is added; run37916458311 atc86bffc passes SDK17 risk/reminder/restore,
with three inspected public images/log retained. This retry does not establish
the earlier failure's cause. Both independent source-fix reviews through
669e3da report zero remaining actionable findings in the fixes.

Storage-interface runs37916685526/37916689763 at73bd65a pass Android10/17 a
controlled second OS directory-sync failure after replacement write, restoration
of exact outgoing options and identity in the recreated app. Two inspected
public images/log are retained under storage-green. This tests the existing
protected storage operation with real Keystore/AtomicFile, not the import-error
toast or arbitrary process/power failure.

Process-boundary runs37917615664/37917619364 at18016f1 pass bothSDKs, with the
host requiring the reached post-write/pre-commit checkpoint, a genuinely dead
process, retained encrypted recovery record and cold app recovery of the outgoing
identity. Public logs/continuity and two inspected images are retained under
process-green. This does not establish arbitrary physical power-cut behavior or
the import-error toast path. Production code has no added test interruption hook.

Same-wallet preview now also explicitly confirms restore, with local assembly
passing; its first actual runtime is pending. Private wallet options and backup
reminders are identity-bound. Current feed caches contain public data and remain
backend-scoped; future drafts, queues and private Chat stores must implement
their own identity isolation in their authorized later tickets. Final full
Android10/17 verification, final two-axis review and a new signed review milestone
must precede ticket closure. Physical Pixel timing/biometric acceptance remains
later work; emulators do not establish those results.

Private documents remain in isolated emulator storage and are never downloaded
as evidence. Scalar1 and the disposable emulator PIN are test-only fixtures.
No production wallet, node configuration or on-chain write was involved.
