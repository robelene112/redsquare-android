# Android17 focused image PASS after bounded picker correction

Source23d7202, run [38076217862](https://github.com/robelene112/redsquare-android/actions/runs/38076217862).
PASS20 completed cases in five records plus three actual process-crash stages.
All unchanged actual-picker selection/rotation, newest-text/media cold recovery,
candidate reconciliation, source-deleted queue and storage refusal assertions pass.

Individually inspected original-size system-picker.png shows landscape system
picker with the sole green photo; matching XML identifies the media-module picker,
a different implementation from failed full17's com.google.android.photopicker.
This focused run therefore does not establish that the previously observed
first-use Dismiss control was exercised or uniquely caused the original failure.
The fixture handles only positively observed controls in the launched package,
retains the15s deadline and never bypasses actual selection/result/pixel checks.

Full17 companion38076602854 at23d7202 is pending with public reads; corrected
production/core/build/scripts/workflow remain identical to accepted full10d35.
No complete #15 acceptance or new app publication yet.
