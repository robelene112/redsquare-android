# Full Android10 profile-history visibility failure

Full run38109837264 on01858ad fails its first browse class: eight cases,
12.833s, six pass and two visibility failures. First failure is at46 after
the deliberate offline refresh in the cache-write-failure flow; its earlier
online post/warning/peer-health assertions pass. Second failure is at299 in
the cold cached-profile flow before its Replies/Likes verification. Remaining
full groups, profile process proof and final wallet/update/public reads are
not reached. Source core49/debug/test/release/lint pass separately in the run
and push38109832420. The full run is not acceptance.

Three original PNGs and the exact raw browse log are retained. Root individually
viewed all three. The in-scope first-failure and cold-recovery screenshots show
the profile header, failed detail/history status and backup reminder consuming
the viewport. The cached cold screenshot reaches the next post's author at the
bottom; the asserted post text is below the viewport. These observations and
the located-but-not-displayed assertion support a scrolling fixture hypothesis,
not proof that the post is reachable. The outer cached-profile-first-failure
PNG is the Android launcher after Activity closure and is excluded from profile
acceptance. These old diagnostic captures do not include XML.

The current correction scrolls each asserted post/status into view, retaining
positive display assertions and scrolling back to tabs before clicking them.
It adds separately gated native PNG/XML captures of recovered posts, likes and
history warning; primary failures preserve diagnostic errors. No production
change. Compilation/review/runtime remain pending. Parallel full Android17
run38109838600 on01858ad is cancelled because this corrected fixture requires
fresh verification; cancellation is not a passing or failing assertion claim.

Corrected fixture local test assembly PASS1m11s, exit0, retained rawlog. Cancelled
Android17 run retained browse8PASS58.84s, following8PASS25.432s and incomplete
posting output. It cannot establish later runtime or final acceptance. Fresh
full verification and committed bounded review remain pending.
