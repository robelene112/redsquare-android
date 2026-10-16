# Public-key mention alert RED, Android10

Run [38933228336](https://github.com/robelene112/redsquare-android/actions/runs/38933228336)
at `8d803b4822abd3e06d5baf3a6377ca4aef64a87a` completed FAILURE.
The ordinary focused social-push scope ran5cases in28.173seconds: the four
existing reply cases passed, and the new mention case failed at
SocialPushTapPreviewTest.kt:123, expected PUBLISHED but observed SUPPRESSED.
This is the intended missing-classification RED, not an observed display/tap
failure. Production still requires an owned reply parent and excludes this root
mention before publication.

The failing case uses the existing independently signed incomingMention reference
and begins without a MainActivity. It fails before the Android channel assertion,
shade opening, physical tap, exact destination/recreation, consumed-route and
saved-work/no-write assertions. Those tails do not execute and are not claimed
for the mention. The four passing reply cases retain their own assertions.
The outer installation-restoration cleanup still runs.

The original failure PNG shows the launcher. Root viewed that original and parsed
all12retained XMLs. There are12PNG/XML pairs: the prior passing reply captures
plus the mention failure frame. PNG and XML are not atomic. adb instrumentation
exit is0 even though the runner reports the failed assertion; the harness detects
the failure and the Android runtime step and whole Actions run correctly fail.
No scope.txt was produced after the failed step. Original metadata, logs, core
report and captures are preserved with28manifest hashes.

Core114tests and debug/application-test compilation pass. This focused run does
not perform release assembly, lint, full suites, Android17, FCM delivery or real
public backend delivery. The local controlled peer/instrumentation remain alive.
No production node/service/wallet change or public build publication occurred.
