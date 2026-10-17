# Cold admission overlap established

[Run39042667158](https://github.com/robelene112/redsquare-android/actions/runs/39042667158)
at exactf4f20e0f8011e509e5ec7da687b6932dde4823c6 completed FAILURE4cases/1failure/85.59s.
All three established warm/group/preview cases pass. All cold pre-tap saved-message,
wallet/backend/policy checks pass, but first target wait fails after actual shade tap.
The original failure remains a failure even though diagnostic UI Retry opens the
exact cached target. Recreation, prior-draft return and final cold tails remain
unexecuted. Two status2 records are diagnostics, not additional passing tests.

Debug admission records show the cold attempt entering idle with no saver/guard,
then another entry while navigation is busy, still no saver/guard. That entry is
refused. Later diagnostic Retry enters idle with no saver/guard and opens. The
source returns immediately on a busy transition; its existing finally clears the
transition after the previous attempt unwinds. Together these establish overlapping
attempts and busy admission refusal. The exact effect-key change that restarted
it is not established. No record/policy/protocol change is supported by this result.
The correction waits for existing navigation to become idle before attempting the
current delivery, retaining cancellation, current-generation and form guards.

Core114/0/0 and debug/test assembly pass. Forty-one original records are hashed;
all18XMLs parse and PNG signatures validate. Root viewed original cold failure
and diagnostic-Retry target PNGs. Cached content and Loading messages are visible;
archive completion is not claimed. Sixteen other PNGs are retained without new
visual claims. No Google transport, actual process death or physical Pixel proof.
Whole #44 remains open until corrected and final native acceptance.
