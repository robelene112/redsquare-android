# Post queue review

Base `4bc4cd6`; initial reviewed candidate `3b28fac`.

## Standards

No documented-standard violations. Two judgment findings:

- Possible Duplicated Code: composer and automatic resumer independently build
  the exact-signature Archive query and verify author/text. Share this safety
  operation while retaining each caller's UI and persistence handling.
- Possible Duplicated Code: full verification runs the wallet replacement queue
  case twice. Keep its explicit selection for focused queue runs, and run it
  once through the posting class in full verification.

`PostAvailability.kt` and the scope-dependent queue case selection address both.
Both fix-review axes approve `f03832a` with zero actionable findings. Final
runtime acceptance remains pending.

## Spec

Zero actionable source findings. Durable queue saving gates displayed acceptance
and scheduling. Cancellation preserves an unsent draft. Claims serialize with
wallet/backend mutation and retain the original signed candidate. Availability
checks reconcile uncertain delivery. Replacement identities cannot discover or
claim outgoing queued work. Existing replacement warnings explain local-record
isolation and require acknowledgement. No scope creep found.

## Verification status

Focused `38013042673` at `3b28fac` passes seven queue cases in 54.743 seconds,
plus an actual intentional process-kill stage and one completed cold-recovery
case. Wallet replacement uses real SAF import and safeguards; restoring the
original identity resumes its queued work. The cold recovery image was inspected
and retained. Replacement/restoration bitmaps show stale UI despite passing
identity assertions, so they are excluded from visual acceptance.

Cleanup local core/debug/test/release/lint checks pass in 4m25s. Final full
Android10 acceptance `38013725295` passes78 completed cases plus4 intentional
process-kill stages. Android17 stops at cached-thread recovery (7/8 browse
cases), before queue acceptance. Cause unestablished: failure image captures
the launcher after disposal. Fixture-only readiness/navigation checks and
in-activity diagnostics preserve all existing assertions; local assembly1m13s
passes. Companion full Android17 acceptance remains required. No new APK, production transaction or
physical Pixel verification is claimed.
