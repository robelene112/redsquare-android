# Exported launch state reproduces a startup cast failure

Android10 [38831067405](https://github.com/robelene112/redsquare-android/actions/runs/38831067405)
at `8a93aec8228b26879461fe4a6359b03c1e6eb110` completed FAILURE in the focused
social-push-input scope. Core114 and debug/test assembly pass; release/lint,
normal four-case social-push, process checks, full suites and Android17 do not run.

The actual exported Activity launch carries a valid selected-wallet/backend public
URI and malformed social.generation/social.pending extras, initialized=false.
Both backend transport overrides are controlled loopback. Startup fails before UI
with String cannot be cast to Long in SocialPushRoutes.enqueue line22, called by
initialize line17/MainActivity.onCreate line111. The retained independent
AndroidRuntime stack confirms FATAL EXCEPTION main/PID3351 and the same cause.
Instrumentation records one failed method and Process crashed, no passing result.
Raw shell0 is not JUnit success; the harness correctly fails without an OK marker.

The source's default Activity ViewModel creation arguments seed SavedStateHandle
from launch extras. A String generation reaches typed arithmetic during enqueue.
This demonstrates the specific exported-default-input problem found in review;
it does not establish every possible malformed-key failure.

No screen capture is reached: live feed, retained-route feedback, unchanged-wallet/
backend/alerts and no-wire success assertions, plus in-process fixture Activity
cleanup, are unexecuted after the crash. No actual runtime invariant or restoration
pass is claimed. The fixture loads its existing original wallet without replacing
it and performs no fixture file/preferences/backend changes; this is source
behavior, not a completed post-crash observation. No production node or wallet is
accessed. Public raw logs/metadata/core report only, hashes exclude this prose.
No private wallet JSON, token, signing material or APK is retained.

The dedicated ViewModel default arguments require isolation from exported extras,
while retaining normal saved-state registry/owner restoration. That correction and
actual GREEN are subsequent work. Public beta24 remains current; #32 stays open.
