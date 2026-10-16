# Actual reply tap reaches the feed instead of its conversation

Android10 run [38829625275](https://github.com/robelene112/redsquare-android/actions/runs/38829625275)
at `0359cd914e524941fcd02145ec42825f20ee459b` completed FAILURE: three cases,
two passes and one failure,33.001s. Core114 and debug/test assembly passed;
release/lint, full suites and Android17 are unexecuted in this focused run.
Raw instrumentation shell0 does not turn the JUnit failure into a pass.

Both existing actual-alert/dismissed-replay journeys pass, including the new
pre-cleanup full active-wallet/backend invariants. The new tap journey loads a
normalized photo draft, types the latest unsent text, locally publishes the exact
independent signed reply and visibly finds its Android shade card. Its actual
click returns; the next assertion at SocialPushTapPreviewTest.kt:105 times out
waiting15seconds for Conversation. Root viewed all five original PNGs individually
and parsed all five XMLs. The tap alert is visible; the failure frame/hierarchy
shows the selected owner feed with the signed root post, not a Conversation.
Existing source has no destination handling and creates a fresh MainActivity.
The prior endpoint-isolation correction keeps this launch on the controlled
loopback peer even though the UI names the selected approved server.

The tap journey's exact-reply display, return/reopen, exact latest text/photo,
persisted unqueued draft, identity/backend and no-wire success tails are
unexecuted after that timeout. Do not claim those passed. Independent cleanup
finishes all owned MainActivity instances before restoring scoped installation
state; no cleanup error appears in this log. Existing hot journeys do pass their
own final no-write and active-scope assertions. The fixture does not prove absence
of every possible outside network effect, real FCM, public closed-app delivery,
physical-device behavior or complete #32 acceptance.

SHA256SUMS covers retained public raw logs/metadata/core report and unmodified
PNG/XML captures, excluding this explanatory prose. No APK, private wallet file,
signing material, production configuration or token is retained. The earlier
cancelled run stays separately cancelled. Public beta24 remains current.
