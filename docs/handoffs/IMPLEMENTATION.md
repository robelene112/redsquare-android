# Implementation handoff

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
- Original large raw packets and build logs remain under
  `/mnt/storage/redsquare-android-artifacts` on the additional disk.
- Accepted complete96 manifest: `reply96-final-full-runs.json`. Accepted originals:
  `reply96-retry-full-raw/full-android10` and
  `reply96-acceptance-raw/full-android17` in the external artifact directory.
  Both sources and workflows are exactly `c1c1d1066b28fb9eff626bc27908d8c79e409382`.
  `reply96-full-runs.json` retains the historical failed first Android10 run;
  it is not the accepted pair.
- Exact published artifact: `.scratch/review-builds/105`; external notes:
  `reply96-release-notes.txt`. Do not rebuild/reserve another version for96.
- Both frozen verifiers, independent final reviews, actual signed83-to105 update
  proofs and public105 delivery checks pass. Close only96 and stop.
- Historical failures stay qualified in ticket96 evidence. Previous95 evidence
  and earlier delivery history remain unchanged. No further owner QA is needed
  for the completed development checks; physical/provider/Play gates remain36.
- Read current context, workflow, operations and tracker before a future task.
