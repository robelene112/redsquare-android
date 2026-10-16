# Alert choice Settings RED, Android10

Run [38936586595](https://github.com/robelene112/redsquare-android/actions/runs/38936586595)
at `107439c5c991336ba1bae9a609b4134a2b37cead` completed FAILURE.
Six ordinary cases ran in50.936seconds: the five existing reply/mention cases
passed; the new user-choice case timed out after15s waiting for the enabled
Replies and mentions toggle inside Settings (openAlertChoice line235).
The initial Settings header/button step succeeded. This is the intended missing
production UI RED, not a demonstrated persistence or notification-control defect.

Root viewed the original tap-settings-failure PNG: the normal Settings dialog
contains personal controls, username, server and appearance, without the alert
choice. All17XMLs parse.17original PNG/XML pairs include the prior five passing
journeys plus this failure frame, retained with38original-file hashes. The
choice case never reaches enable, recreation, alert/tap, latest draft recovery,
disable or fresh-reply silence assertions. Those later assertions are not claimed
for this case. Guarded outer cleanup still executes.

The raw adb instrumentation exit is0; the runner reports6tests/1failure and the
harness correctly fails the Android runtime step and overall run. No scope.txt is
produced after that failed step. Core114tests and debug/application-test build
pass. Focused scope omits release assembly, lint, full suites and Android17.
Injected local transport is not FCM or actual public delivery. Public beta24 is
unchanged. No production node/service/wallet or signing material was modified.
