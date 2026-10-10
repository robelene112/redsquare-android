# Android17 full gate rejected at first-use system picker

Source `d35f96575be60108c8cc0144828624a6483e79a8`, full run
[38075319784](https://github.com/robelene112/redsquare-android/actions/runs/38075319784).
Source/core39/debug/test/release/lint PASS. Device run FAILS only the real system
picker's synthetic-photo lookup. It reaches83 successful cases,16 passing
completion records and nine actual process deaths. All preceding social cases
including original-port retry pass, as do signed image publication, preparation,
newest-text picker death/recovery and cold image queue delivery. The failed
six-case group contributes five successful cases but no passing record.

Individually inspected original-size system-picker-failure.png and matching
rotation1 XML show com.google.android.photopicker in landscape. The visible
Media grid is occupied by the first-use access notice and its Dismiss control;
no photo node appears. These captures do not prove the underlying synthetic
photo was missing. The fixture now positively observes/dismisses that control
only inside the captured external picker package while retaining the same
15-second media-search deadline, exactly-one-photo observation, actual picker
selection/return, landscape prepared-result/new-Activity checks and exact
portrait pixels/text/Send/no-TX checks. Unique cause awaits runtime verification.
No app source change, provider bypass or broader timeout. Complete acceptance
remains pending, as do physical Pixel/UI polish and app publication.
