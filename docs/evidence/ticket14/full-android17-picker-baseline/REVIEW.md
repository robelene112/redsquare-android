# Android17 regression baseline before picker preservation correction

Run [38071834163](https://github.com/robelene112/redsquare-android/actions/runs/38071834163),
source65469a7deab9c9f6b1e402a5be49a6a61cf9ba48, full-android17 with public
reads enabled, passed. Downloaded individual completion records independently
count123 successful cases in35 passing logs and11 actual process-death stages.
Source/core/debug/test/release/lint gates pass. Both approved public feeds,
cached cold launch and ordinary signed5-to8 identity continuity pass.

This directory retains the counted logs, public continuity results and
counts.json. It makes no new bitmap or physical-device acceptance claim.

Production remains312f58c, equivalent to the earlier successful Android10
candidate. This run exercises recovery after image preparation, not the newly
identified boundary while the external picker is open before a result. The
extended tracer is atc3b2aaa and its RED run is38072955931. A production fix
requires fresh full acceptance on both SDKs before #15 can close.

No live transaction, new APK publication, node changes, Pixel7 timing, push
delivery or delivery during force-stop was verified or authorized by this run.
