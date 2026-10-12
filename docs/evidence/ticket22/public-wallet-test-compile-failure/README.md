# Public wallet test compilation failure

Same-source fullAndroid10run38214596540 and fullAndroid17run38214598816 ataf7ed3b
stop in compileDebugAndroidTestKotlin: OwnerLiveFeedTest.kt:23 imports onNode,
which is a Compose rule member rather than a top-level extension. Android runtime
steps do not run. This is test compilation failure, not public endpoint/native
RED or app behavior. Two hashes retain original failed-step logs.

Minimum correction removes only the unnecessary import. All native selectors,
assertions, metadata and public read/write boundaries are unchanged. Hosted
compiler/core/debug/test/release/lint and restarted same-source full10/full17 with
public reads must pass before acceptance. No local APK build is claimed.
