# Ticket #80: honest Activity availability

Review base: `919d5eaf251153646347ff561d3d9185cc6a149d`.
Behavioral source: `12aaf39f2`. Last production change: `30be8f4b9`, copy only.
Full native source: `81207a746`. Focused availability source: `27636b46b`.
Production app code is identical across the latter three revisions.

## Delivered behavior

Failed first loads show Activity unavailable with Details and an accessible Retry,
without zero-unread or welcoming-empty claims. Partial reads have their own warning;
complete failures use shared error severity. Status summaries use the existing
polite announcement convention. Healthy verified events and saved read IDs remain
available through outages, recreation and an actual process death.

Counts are qualified as applying to available history while results are incomplete,
cached, loading or have remaining eligible streams/cursors. Ordinary welcoming
emptiness requires a successful read and no known incomplete-history condition.
Unchecked history is described without promising that additional events exist.
Retry retains the failed streams; ordinary Refresh still requests newest history.

## Native results

| Platform | Full notifications run | Result |
| --- | --- | --- |
| Android 10 | [42049913067](https://github.com/robelene112/redsquare-android/actions/runs/42049913067) | All 41 UI methods, permission method and actual process-death continuity PASS |
| Android 17 | [42049918203](https://github.com/robelene112/redsquare-android/actions/runs/42049918203) | All 41 UI methods, two permission methods and actual process-death continuity PASS |

The 41 methods retain all original 32 notification journeys, Like-history contract,
individual inbox, mixed viewport, state and revision checks, plus four new availability
methods. Their original assertions and bounds remain. The new classes each retain
180 seconds; original notification batch remains 240 seconds. The runner aggregates
outcomes, and required fixture/permission setup now explicitly propagates failures.
The latter runner-only correction at `47b1ee29f` is source-reviewed and shell syntax
checked; it changes no app/test behavior or successful setup path. This source
mapping does not claim #94's final consistent-source whole-app gate.

Focused final [Android 10](https://github.com/robelene112/redsquare-android/actions/runs/42049374065)
and [Android 17](https://github.com/robelene112/redsquare-android/actions/runs/42049378304)
repeat the four availability methods at 27636. Both widths (320/412dp), both themes
and 100/130/200% system text cover genuine failed first load, selective Archive
failure, successful empty results and actual Retry. Retained read/outage/recreation/
applied recovery uses 100/200%. Each OS retains 52 original PNGs with XML and
configuration observations. Contact sheets are derivatives. The recovery predicate
requires both error summaries absent within its original 15-second bound, so an
already-present read count cannot alone satisfy recovery. The available-history
qualifier may remain after applied recovery; no cause is inferred from earlier frames.

Both source review axes and independent native visual review have zero remaining
findings. Core and gateway CI checks pass; final local single-worker lint has a
clean exit. Static evidence does not establish every scroll geometry or motion;
shared status motion is retained, and #94 keeps final whole-app transition gates.

## Failure provenance

- Test-only 04f356a12, run 42041591403, is the real RED: a rejected connection still
  shows No unread activity and welcoming empty copy. The in-scope native capture
  is retained, separately from the outer capture after Activity closure.
- 724f dispatches 42042788428/42042793054 were cancelled after source reviews found
  remaining-history and 412dp gaps. No passing acceptance is claimed.
- 12aaf runs 42043321175/42043325618 stop before Activity on a stale inline feed
  Refresh selector. Native captures show the healthy loaded feed. Readiness now uses
  the existing real refresh helper within each original 15/20-second deadline.
- 34b runs 42045397557/42045402587 execute 30/32 passing methods. Remaining tests
  expect #79's former permanent parent paragraph or misoperate the new profile sheet.
  Parent recovery now reads actual Details and retains original content/retry goals.
- 1957662 run 42046866509 executes 31/32 passing methods. The next sheet capture
  proves its nonclickable title was selected instead of the actual Mute button.
  A dialog-marker hypothesis was not established. Tests now open the action first,
  then confirm in the actual root containing Cancel. Intermediate full 865bc runs
  42048247275/42048253094 were cancelled; focused 42048258171 passes four methods.
- a8e runs 42048329387/42048334221 execute 31/32 passing methods. The remaining
  case carries the first control's owned server fault into the second cycle. Each
  Mute/Block cycle now starts with its own healthy read, then tests its own outage.
  Healthy zero and failure-without-false-zero assertions are both preserved.
- One local compile crossed a resource edit and failed on the new R identifier.
  A later successful Gradle build's wrapper exited 143 after completion; its cause
  is unestablished. Neither is counted as a clean check. `final-lint.txt` records
  the subsequent clean single-worker PASS.

`native-results.json` records exact sources and counts. Original failure logs,
relevant captures and final native evidence remain here. These are controlled
signed peers and guarded disposable dev emulators, not physical Pixel, real
provider/store or final signed-update acceptance. No owner wallet, production node,
block setting or public transaction changed. No beta was published; #94 retains
integrated verification/update/publication. Parent issues retain their own criteria.
