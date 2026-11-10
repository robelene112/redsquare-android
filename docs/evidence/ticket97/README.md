# Feed refresh feedback placement, issue97

The owner reported Feed updated covering an older post near the bottom of the
visible feed. The OS volume panel in the screenshot is unrelated.
The correction anchors the existing brief status beneath the feed tabs, at the
top of the actual posts viewport. Its overlay does not reserve space or move
the reading anchor. Refresh state, applied-result timing, outage behavior,
2.5-second lifetime, shared motion and live announcement remain unchanged.

Review base: `ee8683f6d31680a911f105ab52ed971aaa392d86`.
Initial placement source: `98c881a50b62784feae171b33f46fe2da094d6e0`.
Corrected pass-through source: `c4ca9575ebf9928ba1585672ba162d9e9edf07d3`.
Final combined97/98 verification and candidate source remain to be frozen.

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

The initial full Android10 run42903926048 failed Avatar navigation after the
new status Surface swallowed a covered post tap. The original failing Social
packet remains in [first-full10-failure](first-full10-failure/qualification.json).
The independent new physical-tap regression [fails before correction](tap-red/qualification.json).
Android17 run42903935061 separately failed Settings readiness under TalkBack;
its [original Wallet packet](first-full17-failure/qualification.json) remains
failed, and the cause is unproven. The unchanged focused policy17 readiness
run42908326045 passes. Both first full runs were cancelled after their failed
partitions; no fragments accept either SDK.

The corrective source replaces only the read-only refresh overlay's blocking
Surface with a painted Box. Ordinary statuses retain their Surface and interactive
details/actions. [Corrective focused originals](corrective/) preserve all6+2 feed
and12 avatar methods per SDK, including actual post tapping while the notice is
visible. Source and native follow-up reviews are recorded separately.

Signed106 [binary verification](artifact106/review.json), staging and update
checks remain historical only. Candidate106 is superseded and must never be
promoted. Public latest remains105. Final complete primary Android10/17,
compatible signed candidate/update checks, final packet reviews and exact public
delivery remain pending. Issue98 is implemented next, sequentially, and may share
those final gates. No physical/OEM/provider or Play acceptance is claimed.
Parents1/36/55/84/86 remain open.
