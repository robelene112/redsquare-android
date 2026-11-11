# Feed refresh feedback placement, issue #97

Delivered with #98 as signed [beta 107](https://davidengelen.nl/redsquare/).
See [publication](publication107.json) and [independent final reviews](final-reviews.json).
Feedback stays below the tabs at the top of the actual visible posts viewport,
including collapsed reading navigation. It preserves the reading anchor and lets
physical taps reach the post underneath. Applied-result timing, outages/recovery,
brief lifetime, motion and accessibility remain enforced.

Review base: `ee8683f6d31680a911f105ab52ed971aaa392d86`.
Compiled APK and complete Android 10 source: `8ab10cdd08909f969d485f0a4cb555e438e9bcc0`.
Complete Android 17 fixture source: `82c01e74da76abd2117bedb3eeed73e60945a8cd`.
[Exact production equivalence](fixture-source-equivalence.json) and
[fixture source reviews](fixture-source-reviews.json) preserve the documented
fixture-only rule. No temporary main-source probes remain.

## Original failures and corrective observations

[Position baseline](baseline/qualification.json) fails both strengthened original
refresh methods while the six original feed cases pass. Native geometry and PNGs
show the old bottom overlay. Initial source `98c881a5` relocates the banner but its
Material3 Surface intercepts a covered post tap. Initial full 10 run 42903926048
fails original Avatar navigation; full 17 run 42903935061 independently fails
Settings readiness under TalkBack, whose cause remains unproved. Originals remain
in [first full 10](first-full10-failure/qualification.json) and
[first full 17](first-full17-failure/qualification.json). Neither unsuccessful run accepts an SDK.
The [physical tap baseline](tap-red/qualification.json) reproduces interception.

Corrective `c4ca9575` gives only the read-only refresh overlay a painted pass-through
Box; ordinary statuses keep their Surface/details/actions. All original six feed
plus two strengthened refresh methods and all twelve avatar cases pass both SDKs
in [corrective originals](corrective/) with [independent reviews](corrective-reviews.json).
Candidate 106 is superseded, was never promoted, and must never be promoted.
Its binary/staging/update results remain historical only.

The first final-source 17 attempt 42914310908 fails Social's Save draft return and
Chat's one-shot native viewport observation; remaining Wallet is cancelled when
the replacement starts. The next whole 17 attempt 42919304537 repeats the Save
return failure. [First Social failure](first-final17-social-failure/qualification.json),
[first Chat failure](first-final17-chat-failure/qualification.json), and
[repeated Social failure](retry-final17-social-failure/qualification.json) remain unsuccessful.
No partial success from either run accepts Android 17.

Unchanged [reading readiness](reading-readiness17/qualification.json) and
[Node readiness](node-readiness17/qualification.json) pass original goals. The
[save-prefix diagnostic](save-prefix-diagnostic/qualification.json) observes a 235px
Save movement during IME entry followed by successful persistence/close. These
observations do not prove historical failure causes. Timing probes can perturb the run.

Final fixtures require positive usable targets: enabled/visible Save above the
actual IME with stable bounds before physical input; readiness, input and visible
feed return share the original 15-second budget. The original pixel/draft assertions
remain. Dialog dismissal and positive feed content/unique viewport share the
original 3-second budget; original collapse before 4s and exact anchors remain.
Class bounds, catalog, contract and verifier are unchanged.

## Complete verification and delivery

[Android 10](full10-8ab1-42914306789/summary.json) and
[Android 17](full17-82c0-42931114125/summary.json) pass all three partitions.
The frozen [contract](full-contract.json) and [verifier](verify-full.py) enforce
634/635 identities, 215 classes, 181 archives, 234/235 normal completions,
37 deliberate interruptions and 36 continuity records. The inherited API29 Profile
assumption, unavailable Chat archive and four failure-only captures remain qualified;
no new goal is waived. Both independent final packet reviews pass.

Actual [107 binary](artifact107/review.json), [staging](artifact107/staged.json),
[Android 10 update](update107-android10/qualification.json),
[Android 17 update](update107-android17/qualification.json) and
[update reviews](update107-reviews.json) pass. Exact promotion and actual public
page/latest/immutable manifest/downloaded APK checks pass in publication107.json.
APK: 42,388,522 bytes; SHA256
`55dbc679d116079f0e3d47c83db1073ffeeaac82633bd7fccf4e046e94ef41b3`.
No physical/OEM/provider/Play acceptance is claimed. Keep #1/#36/#55/#84/#86 open;
stop after #97 and #98 delivery.
