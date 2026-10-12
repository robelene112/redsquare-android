# Notification review checkpoints

Whole-ticket base: d1e65f532e5639bfc8f6c20b5b3fe9794f5e8117.
This is an active #24 delivery slice, not a completed-ticket review.
Canonical source/runtime chronology: [verification](../../research/NATIVE_NOTIFICATION_VERIFICATION.md).

## Standards

Bounded dcac031...bb4443a: zero residual hard violations or actionable heuristics.
Verified signed actor replaces the invalid empty-owner sentinel. The private
fixture file helper has two actual consumers, correct owning locks, restoration
ownership before deletion and independent exact-byte/fileset cleanup.

Bounded bb4443a...8db4979: zero hard violations or actionable heuristics.
Bounded corrective 8db4979...a3f65e7: zero residual findings. ArchiveResponses names
the external test control used by the two real recreation cases; owner-specific
response counts exclude generic feed/profile requests.

## Spec

Bounded 8817cc4...dcac031: zero actionable findings for the warm-history minimum.
Optional-null repost persistence and fresh-success history retention remain later
cases, not accepted whole-ticket behavior.

Bounded dcac031...bb4443a: zero actionable findings. Independently verified all22
GREEN hashes, ten passing journeys/69.858s/one runner/zero deaths, ten XMLs and
recreated original image. Corrective compilation subsequently passes2m46s/core72.
Docker wait confirms actual container exit0 after the attached client exits143;
no explanation for the attached status is established.

Bounded bb4443a...8db4979: one synchronization finding. Initial-enabled Refresh
and transient cached activity could let the test navigate before a successful
empty refresh finished. Run38300238513 was canceled and is not RED evidence.
Bounded corrective 8db4979...a3f65e7: zero residual findings. The new test waits
for the changed exact-owner notification response and completed refresh before
retention assertions. Corrected runtime38300436228 records a genuine missing-retained-row RED;
minimum merge correction is awaiting compilation/GREEN.

## Acceptance limits

Ten-journey GREEN38299013457 proves Activity recreation with invalid selected
server responses, not physical offline or process death. Read markers/badges,
partial streams/paging, cache faults and wallet/backend/personal-control isolation,
other missing targets, real permission choices and final SDK pair remain work.
Closed-app push and Pixel acceptance are separate tickets. No new APK or live
transaction was published.
