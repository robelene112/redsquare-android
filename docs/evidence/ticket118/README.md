# Talk prejoin restoration, #118

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117).
Review base: `f7d7448ad7ae67277ddf58ac2d6761c92b3b50c5`.
Product source: `40c9b215fe22b813876831c0e1060177ea8759f3`.
Current native source: `3a5cd747a31e4821a5ec18d6f36b4aed4052b40c` (one test-only VIEW-delivery correction).

The native prejoin screen keeps microphone/camera choices across Activity recreation
and module navigation for the exact wallet, selected backend, invitation URL and
direct recipient. Unreviewed invitation input uses wallet/backend saved state under
the existing module state provider. The original Serializable TalkDraft declaration
is unchanged (SHA256 `aafeab574966c0396acf0ae5271e25e4a7a54568918637a7077ad9d5eefdf850`).
New invitation/identity scopes receive their own defaults. Deliberate Start/Join,
permission checks and runtime capture controls remain unchanged.

## Reproduction and review

Android 17 red `43732610277`, source `5ddec69e4661167d75d84ae600f802a171db0ae0`,
reproduces the exact defect: the microphone toggle is On after recreation although
both controls were switched Off. The screenshot also shows camera On. The other
four original invitation methods pass; five methods run with one failure. The raw
instrumentation output and exact failure screenshot are retained here; ZIP hash and
extraction verification are recorded in `prejoin-red17-receipt.json`.

The initial saved-draft-field variant at `6c82bd84379715b0eb9078ed6962334522c58be0`
passes all five invitation methods on Android 10/17 (`43733972019`/`43733969682`).
Those observations are diagnostic only. Standards review found that adding fields
changes the generated Java serialization identity. That variant was replaced
before acceptance: the current source leaves TalkDraft unchanged and saves the
primitive choice states under the invitation key. Both independent source axes
report zero remaining findings for the revised source. Both final native evidence
reviews also pass, with independent CLI replay and every original byte/index checked.

## Unsuccessful complete observation

Complete Talk Android10 `43735195857` and Android17 `43735192301` at source40 fail in the added identity
fixture: an ordinary VIEW intent opened another Activity, leaving the scenario's
original instance STOPPED when recreate was requested. The actual Off/input
restoration case and four original invitation methods pass. This parent supplies
no acceptance or successful-prefix assembly. The fixture now uses the existing
native active-call test's SINGLE_TOP/CLEAR_TOP delivery flags to retain the
scenario-owned Activity. Product source and every goal/deadline remain unchanged.

## Accepted current-source checks

Complete Talk Android 10 `43736794457` and Android 17 `43736791635` pass at
exact native source3a5. Each completes all 25 ordinary methods across 14 classes,
two normal process methods and one separately verified intentional process-death
start, with distinct original/recovered PIDs. Five native XML scenes verify
restored Off choices and fresh defaults. Original goals and deadlines remain.

Actual peer reception after restored Start contains silent audio RTP (249/738
bytes), decoded RMS approximately 0.00000346 and zero video width. The completed
case additionally requires no native camera track, actual audible PCM after
explicit Unmute, normal End and exact retained unsent Chat text. This does not
claim that muted WebRTC transmits zero transport bytes.

Source/debug run `43735666987` passes compiler/core/gateway/lint tasks at source40:
224 recorded core tests in 44 suites, zero failures/ignored, lint zero errors,
94 warnings and three hints. Gradle may reuse unchanged outputs; no uncached
execution claim. The sole difference between source40 and native3a5 is the test
VIEW-delivery flags; all product/core/build/script inputs are byte-identical.

`native-android10` and `native-android17` retain summaries and full raw/capture
indexes. Both reviewers freshly verify run metadata and ZIP digests, all 160/149
raw files and 215/216 capture members, and replay every assessment assertion.
See `reviews-final.json`. The externally retained assessment tool and its hash
are recorded in `assessment-tool.json`; its optional output root permits exact
review replays without modifying the packet. Selected before/after native
screenshots and XML are retained here; complete originals remain external.

Added regressions cover unchanged Off choices and unsent invitation input through
recreation/module return; fresh invitation/node/wallet defaults; and actual browser
peer reception after restored Start. The peer case requires audio RTP bytes with
near-zero decoded RMS and no video, no native camera track, then actual audible PCM
only after explicit Unmute. It retains normal End, speaker routing and the exact
unsent Chat draft. The two prior Chat media cases and all original deadlines remain.

The media test holds an independent observer lease to isolate the separately
tracked idle-account ownership race (#123). This is an explicit fixture
qualification; #118 does not claim that #123 is fixed. Real peer/media observations
use owned synthetic peers and the existing pinned browser bodies, not public calls.

## Boundaries

#118 is complete. Children #119–#130 remain separate; final integration and
beta issue #131 is blocked by all thirteen behavior slices. Public beta remains
0.1.131. This packet does not claim whole-app final qualification or a new beta.
Physical/OEM, real Relay/TURN/provider/node/schema/funding and Play gates remain
open. No production node/provider change or public financial write occurred.
Original ZIPs, archives and temporary collectors remain outside Git under
`/mnt/storage/redsquare-android-artifacts/talk-prejoin118`.
