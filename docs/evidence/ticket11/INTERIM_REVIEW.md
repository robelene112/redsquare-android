# Interim review of ticket #12

Review base `58aed22`, candidate `26ee962`. Independent read-only agents reviewed
both axes. These were static findings when reported.

## Standards

Two actionable findings: full acceptance omitted `verify_social`, and the shared
post snapshot permanently masked later served counts. No actionable smell
baseline findings; no secret exposure found in controlled fixtures or evidence.
The harness now includes social cases and actual process-death recovery.

## Specification

Two actionable findings: later refreshed counts were masked by the action's
snapshot, and failure refreshing counters after verified availability displayed
an impossible action retry instruction. Preserve verified action success while
reporting counter refresh failure separately. Process interruption and identity
replacement are verification gates, not proven source failures.

Count-refresh reproduction candidate `323c17f` is running in `38019559322`.
No production fix or runtime reproduction is claimed yet. Final review must
include the fixes and completed acceptance evidence.

## Follow-up at 529a768

Standards: both earlier findings addressed, zero new findings. Specification:
stale counts addressed in source; impossible Retry remains pending its actual
red. One new harness finding: WorkManager may restart a new process after the
deliberate kill. The recovery checkpoint now records the original PID and the
host rejects only that surviving original PID, matching the queue harness.
Test assembly passes 54s; runtime verification remains pending.
