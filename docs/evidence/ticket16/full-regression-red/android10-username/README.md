# Second full Android10 failure, username fixture readiness

Run39014622109 at5154e89b4747cd30ff1421543485650c45b856f2 is FAILURE.
The corrected physical notification dismissal and both social process checks
pass. GIF preview3/0 and posting6/0 pass. This run reaches the existing
UsernamePreviewTest class:16cases15PASS/1FAIL102.003s. Its unavailable-name case
fails at443 because the owned Archive request is not observed within the
unchanged5second latch deadline. Registry error, later wallet checks and
subsequent process/public/update/diagnostic gates are unexecuted.

Root viewed the failure PNG and parsed its XML: selected Owner, New post
enabled, Refresh disabled, Loading posts. New post readiness establishes
wallet readiness, not cache/lifecycle/feed readiness. Source supports waiting
for enabled Refresh with the existing20second Compose-aware helper before
starting the unchanged peer latch. That correction remains fixture-only;
all original error/identity/zero-write assertions stay intact. Startup
scheduling is a hypothesis, not a demonstrated application cause.

The complete retained summary reports282 PASS, one existing Profile assumption,
one failed case,63 successful completion records and22 interrupted outputs.
The exact profile aggregate is excluded after proving byte-for-byte equality
with the four individual class outputs. The original failure stays failed;
no remaining application/public gate is waived.7 original/output files are
hashed. No beta publication or production/node change.
