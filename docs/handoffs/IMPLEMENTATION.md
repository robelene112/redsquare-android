# Implementation handoff

## Active display-name repair #100

The owner requests robust display of the same public author names shown by
Saitoverse, one issue and compatible beta delivery, then stop. Implement
[issue100](https://github.com/robelene112/redsquare-android/issues/100).
Review base `1f12812635690547656e6373f0a45a5c85542780`; public beta108 remains
unchanged until a replacement is accepted. Preserve the canonical checkout.

Fresh read-only reproduction: the owner node returns empty key/name maps and
unanswered namechecks for digirok and CryptoKen. The official node returns both
exact public-key/name mappings. CryptoKen's current registrar proof verifies;
digirok's historical cached record fails signature verification, including against
its historical signer. Do not weaken the registration/ownership verifier or add
that signer as a shortcut. Pinned official Registry source `e6c937c686495a37d8fc94ecc894189e23a453c3`
uses key/name maps for browser display independently of ownership verification.

Treat directory display labels separately from authenticated registered ownership.
Keep the exact signing public key for all identity/actions; disclose the display
name's directory/cache provenance in identity information. Preserve all existing
strict registration/availability/ownership paths and verified-name cache. Add
bounded directory cache/recovery and use the approved official Saito name directory
for missing names without changing the selected content/Chat node. Provide a
clear preference for that separate lookup and update draft data-flow information.
No fabricated names, registrations, live transactions, node/service/provider/Play
changes or owner-phone QA. Existing native UI, signed peer, cache and reversible
viewport seams remain authorized; keep original goals and deadlines.

Actual native RED runs reproduce missing names, no-navigation recovery failure,
loss on recreation and selected-node recovery failure. The first directory label
passes Android17 run42991535779; transient recovery passes Android10 run42991531381.
Bounded encrypted per-provider directory cache, provenance and the separate provider
preference are implemented. Both initial focused SDK runs pass, including the actual
reported public names and six compact viewport cells. Independent source review
corrections separate proof latency and provider caches and preserve shared fixture
state. Follow-up Spec and Standards reviews pass on `6acabb4b3`; corrected native
checks and the original16 Username cases are in progress. Source and evidence are
recorded in [ticket100](../evidence/ticket100/README.md). Complete Android10/17,
independent reviews, compatible signed update and exact beta verification remain
required before closing100. Keep #1/#36/#55/#84/#86 open.

## Delivered native Stack reading #99

Signed [beta 0.1.108](https://davidengelen.nl/redsquare/) replaces ordinary Stack
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
