# Android category fixture navigation failure, Android10

Run [38940489331](https://github.com/robelene112/redsquare-android/actions/runs/38940489331)
at `d10df7f052ce22bbdd3e4d54f28321826a9e42db` completed FAILURE:7ordinary cases,
6PASS/1FAIL in58.97seconds. The new case times out15s waiting for the shortcut
at visitAndroidCategories line252. This is not accepted as the intended missing
production-shortcut RED: no positive Settings-dialog observation preceded it.

Root viewed the original tap-android-failure PNG and parsed all23XMLs. PNG and
hierarchy agree on the latest post editor with the keyboard open, without a
Settings dialog. The attempted header click did not establish the intended
screen. Existing PostPreviewTest explicitly dismisses IME and waits for Settings
to be displayed before clicking. The precise transition/event timing is not
proved; the new fixture lacked that visible-parent precondition.

The six established cases still pass with their own assertions. This new case
never reaches OS-package/title/category, saved-work-before-return, recreation,
replytap or final invariants. Those tails are unexecuted. Original guarded
cleanup still runs.50hashes retain23PNG/XML pairs, raw runner output, metadata
and core report. adbexit0 is preserved while the harness correctly fails.
No scope.txt was produced after failure.

Core114/debug/application-test build pass. Focused scope omits release/lint,
full suites and Android17. No app/transport defect or FCM delivery is inferred.
The fixture correction uses existing IME dismissal plus visible header and
positive Settings-dialog observations. It keeps the actual shortcut/category/
durability assertions and their deadlines. Public beta24 is unchanged.
