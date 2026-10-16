# Android notification settings opening failure, Android17

Run [38943314879](https://github.com/robelene112/redsquare-android/actions/runs/38943314879)
at `d6d98bb03b5841d542bb22fb358d87413d75df06` completed FAILURE:7ordinary
cases,6PASS/1FAIL in104.842seconds. The new case fails at
SocialPushTapPreviewTest.kt:256, waiting10seconds for the Android Settings
package after the shortcut action. The six established cases pass.

Original failure XML shows the app Settings dialog with an enabled Android
notification settings button, no system page and no open-error text. This
observation alone does not establish whether the coroutine ran or the launch
failed. Root viewed the original failure PNG on both SDKs and parsed all46 XMLs.
Both images agree with their hierarchies: the app dialog remains visible.
The fixture switches directly from a Compose action to an external UIAutomator
wait although the callback launches Compose-scheduled asynchronous work.
Missing Compose clock synchronization is a source-supported hypothesis,
not an established runtime cause. A bounded Compose-aware wait can observe
the same actual OS-package condition without extending the deadline.

OS-title/category, stored-work-before-return, return/recreation and later
new-case reply/tap/final invariants are unexecuted. Existing guarded cleanup
runs. Raw runner output, actual failed metadata, core report and original
PNG/XML pairs are retained. adbexit0 is preserved; the harness correctly fails.
No scope.txt was produced after failure. Core114/debug/test build passed;
focused scope omits release/lint/full suites. This is no FCM delivery proof
or acceptance of the category feature. Public beta24 remains unchanged.
