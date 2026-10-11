# Offline search cache RED, Android 17

Focused run [38135929831](https://github.com/robelene112/redsquare-android/actions/runs/38135929831), source `019091926b9f471e87ccfe2791047b13094f4d73`. Three cases in72.918s: existing reply and partial/paging tests PASS; the new cache journey fails only atSearchPreviewTest.kt:73, the missing reply after closing and relaunching the Activity against the stopped owner peer. Earlier successful online search and settled-action gates pass. Later server-switch/identity gates have not run.

Hosted focused source/core/debug/test assembly pass. Separate push run38135930222 also passes source/core/debug/test/release/lint. Neither substitutes for the pending complete native suite or physical-device acceptance.

Original raw log and in-scope failure PNG/XML retained unchanged. Root individually viewed the original failure image and inspected separate XML: owner selected, Independent query, Retry search, qualified available-history failure, no reply. No reproduced later OFFICIAL fixture failure, process-death, cache or ticket completion claim.
