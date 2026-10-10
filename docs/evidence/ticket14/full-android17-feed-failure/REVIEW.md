# Android17 full run stopped by feed fixture

Source4dd0d3d, [run38069923347](https://github.com/robelene112/redsquare-android/actions/runs/38069923347).
Source/core39/debug/test/release/lint pass. Retained logs count108 successful
individual cases,24 complete successful records,11 deliberate process deaths
and one failed feed case. This is a failed full run, not final acceptance.

All6 image composition/viewing and5 preparation/loading cases pass, as do both
cold media recoveries and all earlier media lifecycle cases in posting/queue/
author classes. All wallet export/import/storage cases reached also pass.

FeedPreviewTest.unreadableRefreshExplainsTheProblemWhileKeepingCachedPosts
fails waiting for the unreadable-response warning. The individually inspected
failure PNG shows the readable controlled post and enabled Refresh but no warning.
It does not record whether Refresh was enabled when the fixture previously
clicked it, or prove a unique failure cause.

The fixture previously waited only for initial post text. Production publishes
those rows before its second archive request/final loading reset. The correction
positively awaits both initial archive responses and an enabled Refresh, asserts
visible/enabled navigation, then requires another observed request after clicking.
The exact warning and retained-post assertions remain required. No production
source, time limit, cache or response validity rule is changed. This tests the
readiness hypothesis; a later pass alone does not prove the original cause.

The run stops before public-feed reads and final wallet/feed/signed-update checks.
Focused corrected feed verification and full17 companion acceptance remain
required before #15 closure. Keep the successful full10 ad4a8c0 evidence under
the documented production-equivalence rule.
