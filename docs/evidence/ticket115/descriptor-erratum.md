# New 115 continuity descriptor erratum

This is a collector defect, not a failing application requirement or permission
to accept a partial SDK. The unchanged frozen 115 collector remains retained as
`verify-full.py`; do not edit it or describe it as passing after this qualification.

While complete runs 43621894162/43621897570 were active, a static comparison found
that the new five-process loop expected every human-readable success descriptor
to begin `PASS: actual authenticated native`. Four do. The unchanged
`store-crypto-process-check` writes `PASS: actual native reviewed/PIN-authorized
provider reservation before process death, ...`. Its native case does require
real PIN authorization, actual process termination, different recovery PID,
original provider invoice recovery and restoration. The descriptor wording does
not alter those requirements. The mismatch is reproducible without changing any
application, test, harness, goal, contract, count or deadline.

`verify-qualified.py` first executes the exact unchanged frozen collector against
each complete successful primary run. It retains stdout/stderr and requires the
expected descriptor assertion failure; unrelated failures are not waived. It then
executes an explicitly qualified copy with exactly one replacement: StoreCrypto
must equal the full continuity descriptor extracted from its immutable executed
script. The other four prefix assertions remain. All original process/PID,
identity/count/deadline, artifact, visual, speech, media and inherited 94/103/104
validators execute unchanged. Each SDK still requires its own complete successful
three-job primary run. No failed partition supplies acceptance.

The final result must be reported as complete-source verification with a reviewed
new 115 descriptor erratum. Retain the original failure, both collector hashes, the
executed script hash, exact expected text and independent Standards/Spec review.
The qualified wrapper does not establish physical/OEM, live-provider or Play
acceptance. Source-only Vault restoration remains undeployed.
