# Android category shortcut RED, Android10

Run [38941694304](https://github.com/robelene112/redsquare-android/actions/runs/38941694304)
at `152395ae32c1f25896834f426c846824ff6e9d58` completed FAILURE:7ordinary cases,
6PASS1FAIL in61.348seconds. The new case times out15s at the absent shortcut
(visitAndroidCategories line254), after the displayed header and Settings title
inside Dialog have been positively observed. This establishes the intended
missing-shortcut RED. The earlier fixture navigation failure remains separate.

Root viewed the original failure PNG and parsed all23XMLs. Failure PNG/XML agree
on the Settings dialog with Replies and mentions enabled and no Android
notification settings button.50original-file hashes preserve23PNG/XML pairs,
raw runner output, metadata and core report. adbexit0 remains recorded while the
harness correctly fails the runtime step and overall run. No scope.txt followed
the failed step.

The six established cases pass with their own assertions. The new case never
opens the OS settings screen, so OS package/app/category, persisted-work callback,
return/recreation and later replytap/final invariants remain unexecuted. Guarded
outer cleanup still runs. No corrected runtime or shortcut behavior is inferred.

Core114/debug/application-test compilation pass. Focused scope omits release/lint,
full suites and Android17. No FCM/public delivery or physical Pixel proof.
Public beta24 stays current. No production service/node/wallet changes.
