# Android category platform fixture failures, Android17

Run [38957004756](https://github.com/robelene112/redsquare-android/actions/runs/38957004756)
at `9de0ff39a3012f00e555986d6b58603b7db6ca23` completed FAILURE:7cases,
5PASS/2FAIL in95.491seconds. Core114/debug/test build passed; focused scope
omits release/lint/full acceptance. Raw adbexit0 is preserved; harness fails.

First dismissed-reply case fails at SocialPushPreviewTest.kt:81: immediate
activeNotifications observation returns0 instead of1 after receive returned.
The fixture did not record its return result or wait for asynchronous Android
publication. The failure capture shows launcher; later dismissal/replay/invariant
assertions in that case are unexecuted. Exact cause is not established.

The new category case now reaches the actual Android Settings package. It fails
at line259 requiring exact text Red Square. Root viewed its original PNG and
parsed all retained XMLs: Android17 exposes the visible app title as a toolbar
content description, and its categories are collapsed under Other. An expander
with resource-id com.android.settings:id/expand_icon is present. The app title
is visible in the original image. Categories, saved-work-before-return and
subsequent new-case return/tap/final invariants are unexecuted.

The other five cases pass. Guarded cleanup runs, no scope.txt after failure.
Android10 succeeds separately at the identical source. Both SDKs now demonstrate
the system page opens after the Compose-aware wait; this does not establish the
previous failures' exact cause or complete Android17 category acceptance.
Public beta24 is unchanged.
