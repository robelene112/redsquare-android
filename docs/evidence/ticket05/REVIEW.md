# Ticket #6 review and recovery verification

Review base `2d16be4`, first reviewed HEAD `bd3902b`. Both independent axes
inspect the actual committed diff. The ticket is still active; this is not
completed acceptance.

## Standards

Two findings: full verification omitted export/reminder tests, and the success
test duplicated its existing picker helper. Both are corrected in `f095a00`:
full now calls the flows, and the shared helper selects the drawer row by its
Android title resource instead of the identically named toolbar title. Runtime
validation remains pending. Independent Standards re-review through `91d7d35`
confirms both fixes and zero remaining source findings; this does not establish
runtime acceptance.

## Specification

Two findings: required provider-failure/interruption coverage is absent, and a
stalled provider can keep Export disabled indefinitely. Blocking best-effort
deletion also delays failure completion. A controlled Android provider read
pipe now stays open for 60 seconds, with a test requiring a readable failure
within 40 seconds, unchanged identity and a retryable Export action. Its actual
functional red is observed in Android10 run `37889746378` at `91d7d35`:
no readable terminal failure by40s. The no-lock, normal PIN/save and wrong-PIN/
cancel tests all pass in that same run. The implementation now schedules an
independent30s deadline, invalidates the operation before publishing failure,
and keeps provider cancellation/closure/deletion outside its completion path.
Runtime green passes37890457247 (Android10) and37890459989 (Android17)
at1167582: all four export cases plus reminder/recovery. Spec re-review reports
zero new correctness findings in the deadline fix. Evidence is retained in
provider-deadline-green. Other failure/interruption checks remain pending.
Additional write/readback and lifecycle/process recovery checks remain pending.

Normal real-PIN export and reminder/recovery pass both Android 10 and 17.
Wrong-PIN/cancel checks pass both SDKs after correcting repeated-picker
fixture ambiguity by selecting the actual drawer row. No wallet JSON is in evidence.
Physical Pixel biometric/performance acceptance remains #35.
