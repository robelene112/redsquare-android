# Older history after departure

Source 3495cf70. 1 test, failure: expected LEFT but was MEMBER.

The protocol seam retains historical text without restoring sending permission.
The GREEN also verifies an explicit signed join restores MEMBER, not former ADMIN.
This is a JVM replay/signing test, not native process-recovery or live-network proof.

Review of the committed regression and minimum fix: Standards 0 documented breaches,
0 actionable smells; Spec 0 new findings. Separate rejected-page cursor finding remains open.
