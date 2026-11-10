# Feed refresh feedback placement, issue97

The owner reported Feed updated covering an older post near the bottom of the
visible feed. The OS volume panel in the screenshot is unrelated.
The correction anchors the existing brief status beneath the feed tabs, at the
top of the actual posts viewport. Its overlay does not reserve space or move
the reading anchor. Refresh state, applied-result timing, outage behavior,
2.5-second lifetime, shared motion and live announcement remain unchanged.

Review base: `ee8683f6d31680a911f105ab52ed971aaa392d86`.
Frozen correction, full-run and candidate source:
`98c881a50b62784feae171b33f46fe2da094d6e0`.

[Baseline42901485731](baseline/qualification.json) fails both strengthened
existing refresh methods on unchanged beta105 production. The six original feed
cases pass. Actual geometry places notice text at447–461px in a viewport
starting184px; original PNGs show the reported bottom overlay. This run remains
failed and is not corrected-source acceptance.

Both corrected focused runs pass the six original and two strengthened cases:
[Android10](focused/feed-refresh-regression/qualification.json) and
[Android17](focused/feed-refresh-regression-android17/qualification.json).
Four original geometry observations per SDK include expanded and physically
collapsed app navigation, applied results and outage recovery. Exact reading
pixels and all original deadlines remain enforced. Original PNGs show the banner
beneath the tabs. [Independent focused reviews](focused-reviews.json) find no
outstanding defects; the full scope remains a separate gate.

[Source reviews](source-reviews.json) pass both axes. The [contract](full-contract.json)
and [verifier](verify-full.py) were committed before complete execution. They
preserve96's full634/635 identities,215 classes,181 archives and every recovery,
assumption, exception and inherited-validator goal. Only baseline metadata and
two packet paths change; the inherited temporary prefix is cosmetic.

Signed106 [binary verification](artifact106/review.json) and actual
[staged download](artifact106/staged.json) pass. Public latest remained105 at
staging. Complete primary Android10/17, actual signed updates, final packet
reviews and exact public106 delivery are still pending. No physical/OEM/provider
or Play acceptance is claimed. Parents1/36/55/84/86 remain open.
