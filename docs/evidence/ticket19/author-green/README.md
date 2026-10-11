# Search author revisions against stale history

Android17 run38144936110 at34c4c6763023a0cd202158391545903395658455:
nine PASS70.297s. The independent signed original is found, a signed edit
changes search membership/text, and that edit survives a fresh Activity against
stale-original-only Archive after post caches are removed. A signed deletion
and successful exact-target absence reads on both streams remove the result.
After another post-cache reset/fresh Activity, query `ed` matches both versions
and neither is resurrected by the stale original. Same wallet/all authenticated
client keys, zero TX and peer-health gates PASS. Source/core/debug/test PASS.

Root individually viewed both new original PNGs and separately parsed XMLs:
OWNER selected, retained edited text first, then successful qualified empty
results for `ed`. Raw log/pairs retained unchanged; other captures not newly
viewed. This tests existing author behavior, without a manufactured RED. No
actual process death, public live read, physical-device or final #20 claim.
