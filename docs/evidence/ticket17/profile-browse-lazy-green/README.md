# Lazy profile history: Android17 focused PASS

Run [38110791738](https://github.com/robelene112/redsquare-android/actions/runs/38110791738)
on b0e941b: browse eight PASS in64.978s. Root and both independent reviewers
individually inspected all four original PNGs and matching separate XML/rawlog.
The correction searches the actual LazyColumn, then retains exact positive
assertIsDisplayed checks. Memory history remains reachable after public cache
write failure and offline refresh; cold Posts/Replies/Likes/history gates pass.

Memory PNG visibly shows the exact root with offline/cache warnings. Likes and
history PNGs visibly show Independent Saito reply and history warning. Fresh single original-size inspections by root and both reviewers also show
the exact root at the bottom of the cold Posts PNG, matching its separate XML
and unchanged passing runtime assertion. Initial rendered observations disagreed;
the retained file hash2735f6767269fb7d4e4a5706d275c6f6b3d2aa8bb79723bfe6230d33b0a2b012
is unchanged, and no cause for that observation discrepancy is established. Likes/history PNGs are identical bytes; their separate XML
observations are retained. PNG/XML are not atomic; no discrepancy cause claimed.
No production change, live writes, physical Pixel proof or full acceptance from
this focused run. Full Android10/17 regression still pending at this checkpoint.
