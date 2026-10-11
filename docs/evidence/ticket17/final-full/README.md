# Ticket18 final emulator acceptance

Android10 [38110790124](https://github.com/robelene112/redsquare-android/actions/runs/38110790124)
on b0e941bb3c56dfb3991a108f248c6b9546b30995 and Android17
[38110321794](https://github.com/robelene112/redsquare-android/actions/runs/38110321794)
on aea25cae62f340b5785aa9e8f712dfb4328b3f7f both PASS.
Between those commits only one Android test and documentation/evidence change.
Production/core/build configuration/scripts/reference helpers/workflows are
identical. Focused17 browse38110791738 additionally PASS8/64.978s onb0e941b.

Each full run reports145 cases in41 completion records and13 deliberate process
deaths. Android10 has144 actual passes plus one documented API30+ publishing
cache-fault assumption; Android17 all145 pass. counts.json names every counted
record and interrupted stage. The combined profile-edit-ui.txt is exact
concatenation of the four individual logs and is excluded from the case count.
Both core reports49/zero failures/zero ignored. Source-check snippets retain
actual debug/test/release assembly and lint success,1m32s/1m33s respectively.
Shared post draft/import/export regressions, both approved public feed reads,
cached-feed/wallet cold launch and ordinary signed5-to8 identity continuity PASS.
Signed updates here verify that existing preview pair, not a new profile release.

Profile class results:
- Android10 editor7/35.606s, publishing5/25.401s (four pass/one assumption),
  media2/18.221s, new-edit2/26.867s.
- Android17 editor7/39.854s, publishing5/31.712s, media2/20.841s,
  new-edit2/28.555s, all16 pass.

Actual profile draft/candidate deaths preserve wallet/backend without resetting
recovery state. Android10 PIDs16350/16590, final recovery1PASS5.109s;
Android17 PIDs9016/9123, final1PASS5.868s; capture exit0 both. Public checkpoints
and raw interrupted/recovered logs establish distinct actual deaths. The first
two runner crashes are intentional interruptions, not JUnit passes. Latest bio
and normalized red avatar/blue banner recover after the owned source images are
deleted and death occurs with the real external picker open. The exact retained
signed candidate subsequently reconciles read-only without another broadcast.
Verified clearing preserves public bio/pixels as an unchanged editable baseline.

Root and both reviewers independently inspected all twelve original process PNGs
and separate XML; root also inspected all eight retained browse history captures
and four public feeds. Exact cached root/liked reply and warnings remain visibly
reachable after cache-write/offline and cold failures. Runtime verifies Replies
as well. Media/status/control claims agree across process observations, with one
explicit qualification: Android17 external-picker-before-death.png shows the
native editor/latest bio, while its later XML shows Google's photo picker. The
runtime positive package guard and XML establish the picker foreground; that
PNG does not. No cause is established. Android10 picker PNG/XML show DocumentsUI.
Bio is above the scrolled media viewport in the other process captures, so exact
runtime assertions establish its recovery. PNG/XML are not atomic pairs.
Original files are retained unchanged. Other profile captures are available;
no claim that root inspected every bitmap or every state is visible in one frame.

Both independent review axes report0 remaining actionable findings through
b0e941b and these final runtime artifacts. Compatible actual native selected,
removed and new-edit transactions previously passed pinned WASM/Elliptic plus
unmodified A/B Profile server/browser methods with in-memory adapters; see the
canonical profile verification chronology. Public feed availability and controlled
confirmation do not prove chain inclusion or current served-web DOM behavior.
Physical Pixel7/Android17 performance and completed-app review remain #35.
No production write, store submission or new APK publication.
