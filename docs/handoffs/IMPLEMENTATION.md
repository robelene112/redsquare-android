# Implementation handoff

## Active native Saito Talk #104

The owner requested Talk after Fileshare116 and explicitly chose BOTH direct
Chat audio/video calls and group rooms with invitation links. Implement only
[issue104](https://github.com/robelene112/redsquare-android/issues/104) to complete
verification, independent reviews and signed beta. Review base is
`c02fa1e3fb795b7e252f0973235bfc852ec891b4`. Keep parents1/36/55/84/86 open.
Existing JVM protocol, native Android and controlled browser-peer seams are reused;
routine tests/emulators remain delegated, without repeated owner phone QA.

Official source pin e6c937c686495a37d8fc94ecc894189e23a453c3: Videocall, dialer,
StreamManager, Stun and Relay. Pinned Videocall.generateCallLink was independently
executed in Node24 to create the literal native-talk-reference.json fixture.
Room-link, signed/legacy invitation and room roster RED/GREEN cycles observed;
all three targeted Talk JVM tests PASS. Native TalkRoutingPreviewTest now covers
actual authenticated socket routing and negative acknowledgements. Its new
RealtimeIncoming registration is deliberately not wired to SaitoPeer yet: run
talk-routing Android10 to observe the behavior RED, then wire and rerun.
No native Talk media exists yet. Legacy unsigned Relay invitations must
remain claims until actual signed-SDP/DTLS peer authentication; they do not
implicitly authorize capture. Talk and Fileshare need shared peer ownership and
message routing because upstream Stun owns one connection per counterparty.
No Talk audio/video runtime or release acceptance is claimed yet.

The five Talk JVM checks now pass, including ownership of large encrypted Chat
and per-participant Stun routing (both meaningful failing assertions retained in
the external talk-native logs before fixes). All-core tests and Android Kotlin
compilation pass locally. Native socket-routing RED run43186947399 observed both
wrong Chat fallback and wrong success ACK before SaitoPeer's scoped dispatch was
wired. SharedRtcPeers now leases one signed-SDP/DTLS connection to Fileshare and
Talk; Fileshare's former independent factory/connection is replaced by a data-only
lease. These transport changes still require Android10/17 routing and Fileshare
regression evidence, and no call/media acceptance is claimed yet.


The current writable checkout stays
`/mnt/storage/redsquare-android-artifacts/redesign86`, codex/unified-redesign.
Temporary source and logs are external under
`/mnt/storage/redsquare-android-artifacts/talk-native`. Canonical checkout and
unrelated work remain preserved. No node/provider/Play operation, public Saito
write or community recipient is authorized. Closed/killed-app push and physical/
live-network qualifications stay separate under36. Voice continues for an active
call in background/screen-off; camera pauses when not visible. Recording,
transcription, scheduling, livestreaming and local screen broadcast are not part
of the approved direct/group-call slice.


## Delivered native Fileshare #103

[Signed beta0.1.116](https://davidengelen.nl/redsquare/) is public. Private Chat
supports native compatible live files: system picker, explicit acceptance,
progress, exact Cancel, background/screen-off notification and Save as. Both peers
must be online; no offline delivery or transparent process-death recovery claim.

Review base d46cce88b800639ffffe1ae60af5ae509e121676. Compiled product/source
0e7249cba0209c61a33e91f76245ae45fabac621; verification17 is test-only
f01ae5654e2b01ef40321ab440664c57fe40edda. Exactly PolicyCenter, SocialPushTap
and PostComposerFocus AndroidTest observations differ. Production/build/harness/
other tests are byte-identical, all original assertions/bounds retained. Original
composer transition cause remains unproved; failed/cancelled runs stay retained.

Both complete3-job SDKs and UNCHANGED frozen collector PASS:
10 run43140879123,663identities/239classes/197archives/259normal;
17 run43150151913,664identities/239classes/197archives/260normal.
Both38intentional interruptions/37continuities, with inherited qualifications.
Both independent final acceptance reviews verify1,938 complete originals and96
signed83-to116 update originals. Focused proofs cover all nine file classes,
12viewport cells,4MiB exact unpaced browser bytes, real screen-off and seeded
separate-PID recovery. Browser class bodies/native fixture signer are controlled;
not a fully initialized community webapp or live-peer process-death experiment.

Actual signed updates retain wallet, exact draft/cache/preference and debug
isolation. Candidate116 is91,442,703bytes, SHA256
`3e412f1f4564a4c42b5996d32a44017c34c54c516412bc6f6cdd0d9092077e64`.
Public latest/immutable manifest/notes/page/download match at05:09UTC November12.
Delivery receipt: docs/evidence/ticket103/delivery116/verification.json.
Both postpublication independent delta reviews PASS at05:12UTC, recorded in
delivery116/reviews.json. Issue103 implementation, acceptance and delivery are complete. Never promote
113/114/115. No new candidate build is required for docs/test-only changes.

Sole writer checkout /mnt/storage/redsquare-android-artifacts/redesign86, task
branch codex/unified-redesign. Implementation/evidence and final closeout receipts are on this branch; the
final closeout documentation commit is current HEAD. Raw artifacts stay external under
/mnt/storage/redsquare-android-artifacts/fileshare103. Canonical checkout and
unrelated work preserved. Monitoring sessions are completed, no build/test active.

FILESHARE_LIVE is retained unchecked under36 and OWNER_INPUTS; the exact parent
row is preserved in release36-qualification.json. Issue103 is CLOSED as completed
at05:14UTC November12 with the committed acceptance/delivery proof in its body.
Parents1/36/55/84/86 were verified OPEN after closure. STOP until owner
requests another ticket. No node/provider/Play operation or public Saito write.
Do not ask owner to repeat ordinary phone QA. The maintained data-flow inventory
has a dated Fileshare addendum; final operator/retention/declarations stay under36.

## Delivered native Stack links #102

Signed [beta0.1.112](https://davidengelen.nl/redsquare/) opens approved public
Stack article, author and root links inside the native module. Exact signed
article lookup does not depend on the newest discovery page. The URL source is
visible without overwriting the saved node; Back restores the originating post
and reading state. Unavailable articles retain Retry and an explicit website
Open original. Ordinary external URLs and deliberate publication/access stay
external. Loading-image Retry taps cannot activate the parent card; a loaded
image remains a normal article link.

Complete three-job Android10/17 runs43073588639/43069531194 pass with652/653
identities,229classes,195archives,248/249normal completions,37deliberate
interruptions and36continuity records. Acceptance is explicitly frozen goals
verified with the independently reviewed archive-partition catalog erratum:
the new Stack-link archive was catalogued under Social but the immutable harness
runs it in Chat. Both original verifier failures and frozen files are retained;
all194 inherited paths/goals/deadlines remain. The inherited API29 Profile
assumption, unavailable Chat archive and four failure-capture qualifications remain.

Compiled source0fd2133e7 is also the complete17 source. Complete10 source8612758a2
changes only the synthetic draft cleanup hook to use the existing store lock;
all native test bodies and production/harness are unchanged. The reviewed source
bridge preserves the actual earlier failure and its unproved precise cause.
Actual signed83-to112 updates pass on both SDKs, retaining wallet, exact unsent
draft, offline post, Light preference and distinct debug identity after cold launch.
Both independent final packet reviews pass after verifying1884 complete-run and
146 signed-update originals. Candidate111 is superseded and must never be promoted.

Public latest, immutable manifest, notes, page and actual downloaded42,683,434byte
APK match SHA256`6ba40a6b25f83e204339e570a2149931470183baed5f0f9b1ef675d007b0b4cb`
at19:21UTC November11. The [ticket102 packet](../evidence/ticket102/README.md) records source,
qualified complete results, original failures and delivery receipts. No
owner-phone/OEM, provider, Play, live Saito write or production node change is
claimed. Keep #1/#36/#55/#84/#86 open and stop after102.

## Delivered fast-scroll correction #101

Signed [beta 0.1.110](https://davidengelen.nl/redsquare/) removes a measured
main-thread wait while drawing post actions. The reactive busy-status read now
uses SnapshotStateMap directly instead of waiting on the lock held by background
durable storage. Actual operation claims, identity changes and journal writes
remain serialized. Appearance, animation and transaction formats are preserved.

Actual traces identify a34.4ms busy-read wait behind FileDescriptor.sync. The
controlled native regression fails on old source at8375ms and passes on Android10/
17 at163/434ms while the owned durable transaction remains pending. Three
post-fix scroll rounds contain zero such monitor-contention events, versus4/1/3
before. Cold emulator timing remains mixed, including a worse151.73ms sample.
This establishes removal of one measured stall, not physical smoothness or a
controlled general FPS gain. No profiling SDK is added to the ordinary APK.

Both complete three-part Android10/17 runs43040719802/43040725444 pass at
`75920db2d63eff0d3cc5b2e621609c5bd13349ac`. The unchanged pre-run101 verifier
accepts651/652identities,228classes,194archives,247/248normal completions,
37deliberate interruptions and36continuity records. The inherited API29 Profile
assumption, unavailable Chat archive and four failure-capture qualifications
remain explicit. Actual signed83-to110 updates43038821274/43038826400 preserve
wallet, exact draft, cached post, preference and separate debug identity.

The APK is42,650,666bytes, SHA256
`dd548a3f6d4abb37b94aad25d1073e2be428c30376e79a3fdb0b776201b37414`,
compiled from`64494ef9e`. Reviewed source bridges retain that exact artifact
through CI dependency provisioning and one existing JVM observer correction.
All original failed/cancelled runs retain their outcomes; none contributes partial
acceptance. Both independent final acceptance and delivery reviews pass with zero
findings. Public latest, immutable manifest, notes, page and downloaded bytes match.

The [ticket101 packet](../evidence/ticket101/README.md) records original evidence,
source bridges and limits. No owner-phone QA, live write, node/provider/Play
operation or physical/OEM acceptance is claimed. #101 is complete. Keep
#1/#36/#55/#84/#86 open and stop until another issue is requested. Preserve the
canonical checkout.

## Delivered robust display names #100

Signed [beta 0.1.109](https://davidengelen.nl/redsquare/) resolves available public
author names across feed, conversations, profiles, activity and Chat. The reported
keys resolve to digirok@saito and CryptoKen@saito through the official Saito name
directory. The owner node returned empty reverse maps for both accounts during
actual read-only comparisons.

Directory labels are separate from authenticated registered ownership. The app
retains exact public keys for identity and actions, shows name provenance in
Identity details, and preserves the strict current-registrar verification and
verified-name cache. No historical signer was added to trust. The optional
saito.io directory uses a separate read-only identity and can be disabled under
Settings, Node without changing the selected content/Chat node. Provider caches
are independent; visible names retry after transient errors and survive ordinary
navigation and offline reading. Draft data-flow documentation records the lookup.

Both complete three-part Android 10/17 runs pass on `759f46f7e`: 650/651 frozen
identities, 227 classes and 193 archives per SDK, with all inherited goals and
deadlines retained. Both independent final Spec and Standards reviews pass with
zero actionable findings. Actual signed 83-to-109 updates preserve wallet,
draft, cached content, preference and separate debug identity on both emulators.
The immutable APK is 42,650,666 bytes, SHA256
`6d3a7cb431815c6338f5bd84fca633ccd10f258bcf3653241e74ee0910a5fdb3`,
compiled from `de0ae9b60`. An exact source bridge retains that APK across two
reviewed instrumentation-only corrections. Public latest, immutable manifest,
page, notes and actual downloaded bytes are verified.

The [ticket100 evidence](../evidence/ticket100/README.md) retains original failed executions,
source bridges, complete results and delivery receipts. The inherited Android 10
Profile assumption, unavailable Chat archive and failure-capture qualifications
remain explicit. Fresh agreement checkpoints have no prior receipt; its narrow
restoration correction relies on the original failing checkpoint and source review.
Later isolation captures preserve the reading anchor; the original five-second
assertion establishes the next label, without separate native ink/timing proof.
No physical/OEM/provider/Play acceptance or production node change is claimed.
Issue #100 is complete. Keep #1/#36/#55/#84/#86 open and stop until another issue
is requested. Preserve the canonical checkout.

## Delivered native Stack reading #99

Signed [beta 0.1.108](https://davidengelen.nl/redsquare/releases/108/redsquare.apk) replaces ordinary Stack
sample stories with genuine signed public articles from the explicitly selected
node. Real authors, covers and Markdown images, followed authors, My articles,
bounded history and retained offline reading use the shared wallet and controls.
Local visual/Markdown drafts are durable; review copies Markdown and opens the
actual Stack website with its browser wallet. Native publication, restricted
access, payments and subscriptions remain first-release work under #1.

The owner node currently has no Stack archive; choose saito.io in Settings for
its available public articles. Native read-only checks exercise both approved
backends without substituting data, switching nodes silently or publishing.
Historical design fixtures are debug-only, separate from the ordinary route.

Complete Android 10 [42963703866](https://github.com/robelene112/redsquare-android/actions/runs/42963703866)
and Android 17 [42966576451](https://github.com/robelene112/redsquare-android/actions/runs/42966576451)
pass all three partitions. Frozen goals are verified with an independently
reviewed archive-partition catalog erratum: 642/643 identities, 220 classes,
186 archives, 239/240 normal completions, 37 deliberate interruptions and 36
continuity records. Only the inherited API29 Profile assumption remains.
The inherited unavailable Chat archive and failure-only captures remain qualified.
Both independent final reviews and actual signed 83-to-108 update gates pass.
Public immutable/latest manifests, page and downloaded APK match exactly.

Complete Android 10 source is `f7c92f01961c28be567d07c868625d78d1a3ee89`.
Complete Android 17 and compiled APK source are `706833f8fa332fb56e1d55e8a04839348a4ff2d0`.
The fixture-only rule retains the complete Android 10 run with exact product/core/build/
workflow/harness equality and a fresh whole Android 17 run. One test readiness wait changed
within its original budget. No partitions from different SDK runs are assembled;
the early failed Android 17 run remains failed and its underlying cause is unproved.

APK: 42,581,034 bytes, SHA256
`720b6d129afa1fa1e42a57ce1aeee2377d5c7a22d12f414d531e317f1b19772e`.
The established package/certificate and min29/target36 remain unchanged.
See [original evidence, qualifications and reviews](../evidence/ticket99/README.md).
This completes only #99; stop. Parent #1/#36/#55/#84/#86 stay open.
Physical/OEM/provider/Play acceptance remains under #36 and OWNER_INPUTS.md.
No owner-phone QA, production-node/service changes or owner-wallet operations
were performed. Source checkout remains the isolated codex/unified-redesign
worktree; preserve the canonical checkout.

## Delivered UI fixes #97 and #98

Signed [beta 0.1.107](https://davidengelen.nl/redsquare/) delivers the requested
feed and profile corrections. Refresh completion stays below the tabs at the top
of the visible posts viewport, preserves the reading pixel and lets taps reach
posts underneath. Profile history uses a neutral information icon and "History
may be limited", with accessible Details explaining local/node history and
possibly missing older posts, replies or likes. Real loading, errors and recovery
controls remain separate.

Complete Android 10 [42914306789](https://github.com/robelene112/redsquare-android/actions/runs/42914306789)
and Android 17 [42931114125](https://github.com/robelene112/redsquare-android/actions/runs/42931114125)
pass all three partitions and the frozen verifier: 634/635 identities, 215 classes,
181 archives, 37 deliberate interruptions and 36 continuity records per SDK.
Only the inherited API29 Profile assumption remains; Android 17 has none.
Both independent final reviews, actual signed 83-to-107 updates on both SDKs,
binary verification and exact public APK/page/manifest verification pass.
No partial SDK assembly is used. Earlier unsuccessful runs remain qualified.

The compiled APK and complete Android 10 use source
`8ab10cdd08909f969d485f0a4cb555e438e9bcc0`. Complete Android 17 uses fixture source
`82c01e74da76abd2117bedb3eeed73e60945a8cd`, tag `proof-ui9798-fixture-final`.
Exact production/core/toolchain objects match. The documented fixture-only rule
retains the complete Android 10 run while fresh Android 17 verifies stronger
input/window readiness inside the original budgets. Temporary probes are removed;
later Save/window readiness failure causes remain unproved. See [feed evidence](../evidence/ticket97/README.md) and [profile evidence](../evidence/ticket98/README.md).

APK: 42,388,522 bytes, SHA256
`55dbc679d116079f0e3d47c83db1073ffeeaac82633bd7fccf4e046e94ef41b3`.
The established package/certificate are preserved. This completes only #97 and
#98; stop after recording delivery. Keep #1, #36, #55, #84 and #86 open. Physical,
OEM, provider and Play acceptance remain under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md).
No production node or service changes were made.

## Delivered fix #96

Signed [beta 0.1.105](https://davidengelen.nl/redsquare/) completes
[issue96](https://github.com/robelene112/redsquare-android/issues/96).
In Conversation, the visible Reply action beneath each post now opens a reply
to that exact post, including replies to replies. The redundant header bubble
is removed; Back, history information and post-body reading navigation remain.
Per-parent drafts, exact parent/thread/recipients, recreation and successful-send
return are preserved. Long-parent replies expose the writing field at large
text sizes without forcing focus or the keyboard.

Complete Android 10 [42887785531](https://github.com/robelene112/redsquare-android/actions/runs/42887785531)
and Android 17 [42878955982](https://github.com/robelene112/redsquare-android/actions/runs/42878955982)
pass all three partitions and the pre-run frozen verifier: 634/635 identities,
215 classes, 181 archives, 37 deliberate interruptions and 36 continuity records
per SDK. The single inherited API29 Profile assumption remains; Android 17 has none.
Both independent final reviews, actual signed 83-to-105 update checks, binary gate
and post-promotion public APK/page/manifest verification pass. No partial assembly.

Compiled APK and both complete-run sources are
`c1c1d1066b28fb9eff626bc27908d8c79e409382`. The APK is 42,388,522 bytes, SHA256
`2975aa69337016bbfaa57ba368ff082135bc4dc094deb57c794e83e7c79a3d0a`, using the
established package and certificate. Later commits retain evidence/documentation.
See [ticket96 evidence](../evidence/ticket96/README.md) for originals, reviews and qualifications.
This completes only issue #96; stop. Physical/OEM/provider/Play acceptance remains open
under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md). Keep #1, #36, #55, #84 and #86 open. No node or service changes.

## Delivered fix #95

Issue [95](https://github.com/robelene112/redsquare-android/issues/95) is delivered
as signed beta **0.1.104**: [download or update](https://davidengelen.nl/redsquare/).
After successful post/reply/quote submission, the app returns to its reading
context with brief neutral "Post submitted" feedback. The signed original and
media stay durable for background observation; a delayed node cannot cause a
receipt to be resent or erase the next draft. Actual send/storage failures and
legacy102 attempts remain recoverable using the original signature.

Complete Android 10 run [42845159882](https://github.com/robelene112/redsquare-android/actions/runs/42845159882)
and Android 17 run [42823896331](https://github.com/robelene112/redsquare-android/actions/runs/42823896331)
pass all three partitions and the unchanged pre-run verifier: 634/635 identities,
215 classes, 181 archives, 37 deliberate interruptions and 36 continuity records per
SDK. Android 10 retains the one inherited Profile assumption; Android 17 has none.
Original failed/cancelled runs remain qualified, with no partial assembly.

Compiled source and immutable 104 manifest remain
`fd82ad47754d3e5137503d14331e6a6b2b4be2a1`. Android 10's final harness source is
`5336c0536c47f1d6db5ab347a91f4b802a2d097c`; an exact whole-tree bridge proves only
nine test/harness/documentation paths differ. The existing workflow permits
retaining the complete Android 17 run after a fixture-only correction. Both
independent final reviews pass. Actual signed 83-to-104 update/isolation also passes
on Android 10/17, preserving wallet, unsent draft, cached content and preferences.

APK: 42,372,138 bytes; SHA256
`a21bc86d6ce64c3cec38afdeadf9cfeb1e7b4f3dd143b98b5af324a0dc17c789`.
The ordinary public page/latest/immutable manifest and downloaded APK are verified
after exact promotion. See [ticket95 evidence](../evidence/ticket95/README.md) for originals, reviews and limits.
This delivery completes issue #95; stop after recording it. Physical/OEM, provider and
Play acceptance remain under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md). Keep issues #1, #36, #55, #84 and #86 open.

## Previous delivery #94

Issue #94's prototype-fidelity repair is delivered as signed beta **0.1.102**:
[archived APK](https://davidengelen.nl/redsquare/releases/102/redsquare.apk). The public latest pointer,
actual APK checksum and exact immutable notes are verified after promotion.
Frozen source: `1c7cb53d8c077aa1cd132deec22a1c5a72cc2d3c`, tag
`proof-prototype94-loaded-activity`. Production last changed at be37e8c42; later
changes are tests/evidence only, with an exact whole-tree bridge retained.

Both complete runs pass all three partitions: Android 10 [42452255196](https://github.com/robelene112/redsquare-android/actions/runs/42452255196)
and Android 17 [42451092093](https://github.com/robelene112/redsquare-android/actions/runs/42451092093).
The unchanged frozen Android 17 collector confirms 630 identities and no assumptions. The Android 10 collector
confirms 629 identities: 628 passed and the single inherited
API29 Profile assumption. Both catalogs have no missing or extra identities.
Both independent final packet reviews find no actionable defects. No partial or tail assembly is used.
Original 180-second class bounds, 214 classes and 629/630 expected identities remain frozen.

Actual signed 83-to-102 update/isolation passes Android 10 [42454280787](https://github.com/robelene112/redsquare-android/actions/runs/42454280787)
and Android 17 [42454286099](https://github.com/robelene112/redsquare-android/actions/runs/42454286099).
Independent actual artifact and update-native reviews find no actionable defects.
APK: 42,355,754 bytes; SHA256 `616a151011887ba2f19be9cba8fb23d69f33d4b4da15d02bebcc142d1ec14fb7`.
Signing certificate, package, minimum API29 and target API36 and vendored license are verified.
Never promote superseded candidates 93 through 101.

Prototype authority: `c95ecaa01abb71c576f052c7cc2303356416091c`,
`design-3d989e9e8406`, plus owner amendments: rounded named module selector,
real identity/data, separated profile history, compact repeatable heart, exact-post
Activity grouping and collapsing reading header. Do not copy sample identities or
artwork as live data. Stack remains explicitly a local design preview; actual
network/publication/payment/subscription work stays under issue #1.

Current originals, frozen collector results and qualified independent reviews are
in [ticket94 evidence](../evidence/ticket94/README.md). Historical failed executions
remain failed. Known baseline missing failure-only captures are recorded by the
frozen collector; they are not missing application identities or waived goals.
Physical Pixel performance, actual provider delivery, business/policy setup and
Play acceptance remain pending under #36 and [OWNER_INPUTS.md](../../OWNER_INPUTS.md).
Do not automatically close issues #1/#36/#55/#84/#86. Canonical checkout, other work,
production nodes and services remain untouched. Issue #94 is the previous accepted checkpoint; the later95 delivery is recorded above.

## Working paths and resume boundary

- Sole implementation checkout: `/mnt/storage/redsquare-android-artifacts/redesign86`,
  branch `codex/unified-redesign`. Preserve canonical `/home/david/projects/redsquare-android`.
- Public immutable candidate108 is the delivered ordinary beta. Artifact:
  `.scratch/review-builds/108`; exact notes: external `stack99-release-notes.txt`.
  Do not reserve, rebuild or promote another version for completed99.
- Compiled APK source/tag: `706833f8fa332fb56e1d55e8a04839348a4ff2d0`, `proof-stack99-feed-ready`.
  Later commits retain evidence/documentation only. Product/core/build/workflow/
  harness objects match both complete-run sources under the reviewed fixture rule.
- Accepted whole-SDK manifest: external `stack99-accepted-full-runs.json`.
  Complete Android 10: `42963703866` at `f7c92f01961c28be567d07c868625d78d1a3ee89`,
  originals `stack99-acceptance-raw/full-android10`.
  Complete Android 17: `42966576451` at `706833f8fa332fb56e1d55e8a04839348a4ff2d0`,
  originals `stack99-fixture-full17-raw/full-android17`.
  No partitions from separate dispatches are assembled.
- Frozen contract/verifier stay unchanged. Use the explicitly reviewed
  `docs/evidence/ticket99/verify-archive-partition-erratum.py` for its one-path
  expected catalog correction, and preserve the original collector failures.
  Report frozen goals verified with archive-partition catalog erratum, never an
  unchanged-verifier PASS. All identities/recovery/archives/inherited validators
  still pass. See the exact summaries and reviews in99's evidence packet.
- Actual signed 83-to-108 updates: external `stack99-update-runs.json`,
  runs 42969207984/42969210675. Originals `stack99-update-raw/candidate-update`
  and `stack99-update-raw/candidate-update-android17`. Both reviews pass.
- Exact immutable/latest manifests, public page and downloaded APK are verified.
  The original failed full17 source f7 run42963706966 remains failed; its passing
  Social/Wallet partitions are not reused. Fixture source 706 has its own complete17 run.
- This completes99 only. Stop. Parent issues #1/#36/#55/#84/#86 and physical/provider/Play gates
  remain open. Native publication/access/payment/subscriptions remain under #1.
  No further owner QA or node/service operation is needed for this reading slice.
