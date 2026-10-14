# Older history after departure

Source eeb9903865c6893c3208ff0d3a0ab2053ebc85fc. 17 GroupChat tests passed, no skips/failures/errors.

The protocol seam retains historical text without restoring sending permission.
The GREEN also verifies an explicit signed join restores MEMBER, not former ADMIN.
This is a JVM replay/signing test, not native process-recovery or live-network proof.

Review of the committed regression and minimum fix: Standards 0 documented breaches,
0 actionable smells; Spec 0 new findings. Separate rejected-page cursor finding remains open.
