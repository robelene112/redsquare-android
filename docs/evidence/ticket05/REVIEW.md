# Ticket #6 review and recovery verification

Review base `2d16be4`, first reviewed HEAD `bd3902b`. Both independent axes
inspect the actual committed diff. Historical interim findings are retained below. Final acceptance is recorded
at the end of this document.

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

At `f40cf2c`, Android10 run `37891029657` passes all five export cases and
reminder/recovery. The new case exercises write-open rejection, read-open
failure and truncated readback, followed by a fresh actual PIN prompt and a
successfully reimported file with the unchanged identity. Public retry evidence
is inspected and retained under provider-faults-green/api-29.

Both independent source reviews through `2a5e507` report zero remaining
actionable findings. The interruption harness kills the real test process
before credentials, checks the created file has zero bytes and verifies cold
identity continuity plus a new authenticated retry. Its runtime result and
the later full run remain pending. This proves only its specified interruption
stage, not arbitrary process loss midway through saving.

Android17 run `37891251005` at `2a5e507` passes the original four export
cases and all three provider-failure responses, but its final retry test cannot
observe the PIN prompt: the new waiting helper expected a qualified XML resource
name, while the already observed Compose PIN pad exposes bare `cred_pin_pad`.
This is a fixture failure before retry credentials, not a functional export red.
The wait now recognizes that exact resource only in Settings/System UI. Original
logs, public screenshot and control metadata are retained under
pin-control-fixture-failure; process interruption was not reached in that run.

At `51d11c1`, full run `37891415056` passed core/build/lint and its complete
Android10 stage, including 29 normal instrumentation cases, available public
feeds on both backends, cold cache/identity and signed-update continuity.
The separate export-interruption prepare phase deliberately terminates its own
process; the host then verifies actual death, an empty test document and a
passing cold observer. Its public logs and before/after wallet images are
inspected under process-green/api-29. The full workflow was canceled during
its later Android17 stage because that commit retained the now diagnosed
invalid PIN-wait helper. This is partial-stage evidence, not overall acceptance.

Independent Standards and Spec reviews through `7a2ab0b` confirm zero remaining
source findings in the exact resource correction. Corrected focused Android17
run `37892237605` and final full `37892360326` remain pending. No production
export code was changed for the fixture repair.

Corrected Android17 focused run `37892237605` at `7a2ab0b` passes all five
export cases, reminder/recovery and the actual process-death/recovery phases.
Its public retry and cold-recovery images/logs are inspected and retained in
final-focused/api-37.0. Both SDKs now have actual provider-failure and fresh-PIN
retry evidence, plus a terminated-before-authentication process check. Only
final full `37892360326` remains pending; no extra source change is proposed.

Normal real-PIN export and reminder/recovery pass both Android 10 and 17.
Wrong-PIN/cancel checks pass both SDKs after correcting repeated-picker
fixture ambiguity by selecting the actual drawer row. No wallet JSON is in evidence.
Physical Pixel biometric/performance acceptance remains #35.

## Final acceptance

Full run `37892360326` at `7a2ab0b` passes both Android10 and17: core14,
29 normal instrumentation cases per SDK plus each controlled process-kill
prepare stage, build/lint, both fresh public feeds, cached cold launches and
actual signed5-to-8 wallet continuity. Downloaded public logs/images are
inspected and retained in final-full. Both source review axes have zero
remaining actionable findings through that tested source. Lint has nine
warnings and no errors; no claim of warning-free release polish is made.

Export failure coverage is precisely write-open rejection, read-open failure,
truncated readback and stalled read, with real authentication and a subsequent
successful retry. Process death occurs before credentials. No arbitrary
mid-write crash, physical-device biometric or Pixel performance pass is claimed;
physical acceptance remains #35. No wallet JSON/private key is in evidence.
