# Ticket91: repeated Like feedback

Review base `63a5bd8cf5e2958cdcd1f6c0a216f9491bc359fa`.
Final production/tested source `901f06b284b1228e7ea10a1415f5e96df1480b38`.

The mounted heart now owns immediate input feedback. Each tap restarts a small
pink1.16pulse with a quick spring return. Normal feedback has a240ms budget from
its input timestamp, including delayed frames; zero system motion uses a120ms
static pink acknowledgement. Feedback returns to outline and is not a persistent
liked toggle. Count typography stays still. No growing ripple, rate cap, visual
queue, archive wait or Like pending/failure dialogue is introduced.

Removing the old volatile `SocialState.tapFeedback` fields prevents a network
status reload or remount from controlling the animation. Actual signed submission,
selected-node ownership, optimistic/source-backed count reconciliation and
background diagnostics retain their existing code. Animation is not a receipt.

## Verification

|Platform|Final native run|Result|
| --- | --- | --- |
|Android10|[41924114853](https://github.com/robelene112/redsquare-android/actions/runs/41924114853)|6methods PASS|
|Android17|[41924128922](https://github.com/robelene112/redsquare-android/actions/runs/41924128922)|6methods PASS|

Each includes the new native motion journey plus the existing2repeat,2isolation
and1pending-Repost viewport methods. The new journey injects36physical taps per
OS across feed/thread/profile and real motion1/0, receiving36distinct signed Likes
at a compatible local peer. Original peer validation checks signer, target and
message shape. Counts advance, actions remain enabled,48dp targets and row
positions/heights remain, and no unlike/Like pending control appears. A truthful
count may gain a digit and widen its target.

Original movies, all decoded display frames, real system/window-scale observations,
tap timestamps and native screenshots/hierarchies are retained. Contact strips are
labelled derived crops for inspection; originals are unchanged. Both reviewers
and the implementing agent inspected the final rendered evidence. Normal feedback
repeatedly expands/contracts16to19/20pixels during the burst and ends as an
outline. Zero motion has static pink feedback and outline return. Tiny width
variation in the zero-motion feed reflects raster/centering as counts widen,
not scale animation. Native burst intervals include90to194ms, exercising
interruption/restart. Counters keep stable typography; no large ripple is visible.

Core/gateway CI checks pass (117core and16gateway cases). Final local one-worker
`:app:lintDebug` passes at901, with unchanged container/heap limits; `final-lint.txt`.
Separate Standards and Spec reviews have zero remaining findings, including the
final native evidence review. Every class retains its180second bound. Full Social
includes the new motion class additively. These focused proofs do not replace94's
consistent-source whole-application and signed in-place update gates.

## Honest failure/source history

-714350c94 /41919856546 andc95d54ae5 /41920533611 stopped at a new fixture's
  incorrect accessibility assumption. Native XML shows the label under its
  same-bounds enabled clickable parent. These are fixture diagnostics, not RED
  animation evidence. The fixture now follows that actual platform relationship.
-47295f1a0 /41921370782 is the real RED:487pink pixels remain450ms after one tap.
- Earlycb8 candidate dispatches41922177777/41922195822 were superseded before
  acceptance after spotting a new test assumption that count9to10could not widen.
  Test-only4d33 retains row/target goals while allowing truthful count width.
-4d33 /41922388377 passes all six methods on10.41922401653 fails on17 because
  the first pulse still has164pink pixels at450ms. The901input-time budget fixes
  that failure; no assertion or deadline was relaxed. Both final proofs use901.
- Fixture cache restoration includes profile metadata and retained author revisions,
  and runs independently after Activity closure. Motion settings restore separately.

`native-results.json` records exact source/runs/cases. Each platform folder has
native logs, source/workflow/runner provenance, core summary and rendered proof.
`before-and-diagnostics/` retains the failed native logs. Text whitespace is
normalized; binary originals are untouched.

These are guarded disposable dev emulators and controlled signed peers. They do
not establish physical Pixel performance or live provider/store acceptance. No
production node, owner wallet or block-production setting changed, and no public
social transaction or beta publication occurred.94 retains integrated publication.
