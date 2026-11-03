# Ticket76 native control regression verification

Status: ticket76 complete. Integrated app and publication acceptance remain94 work.
Final app source: `3eecb210fa2c4721837415dc83b396af6490f379`.
Review base: `d7a3bce29`.

## Changes

The native tests use current feed/Conversation pull-to-refresh and the real
accessibility Refresh equivalent, preserving each original request, deadline,
content, viewport and recovery goal. Non-top accessible refresh does not move
the reader to the start. A physical pull is also observed against an actual
connection rejection, including its manual spinner and retained top anchor.
Import-review cancellation uses its actual Cancel action rather than the older
Done, preserving unsaved biography, selected file and active wallet assertions.

The restored reading test found a real header-compensation defect: the feed's
PullToRefreshBox moved while the measured parent-relative list origin stayed0.
Measuring from root alone was insufficient because the modifier lost its
baseline when leaving for Chat. The observer now lives alongside the retained
list and is keyed by stable wallet public identity and selected backend.
The original1px reading-position tolerance remains unchanged.

The broad run also exposed superseded Like expectations. Post presentation now
checks the icon-only control's accessible name/count/target and absence of the
removed word. The unavailable-action viewport journey retains its controlled
storage fault, Retry, native live-region and48dp goals on Repost, where that
behavior remains. Likes remain enabled without pending/unavailable UI. No
production Like behavior was changed by this ticket.

## Verified checks

- Original retained-reading journey: Android10 [41731830734](https://github.com/robelene112/redsquare-android/actions/runs/41731830734)
  and Android17 [41731834793](https://github.com/robelene112/redsquare-android/actions/runs/41731834793),
  each `OK (1 test)` at74c5092a8. Chat return, recreation, outage and physical pull execute.
- Actual pinned TalkBack: Android10 [41732947338](https://github.com/robelene112/redsquare-android/actions/runs/41732947338)
  and Android17 [41732951656](https://github.com/robelene112/redsquare-android/actions/runs/41732951656),
  each six successful native classes: status, post, profile, Activity, viewer and Wallet.
  The status journey retains focus on Reference post20 and observes completed
  outage/recovery synthesis. Physical audibility is not claimed.
- Those TalkBack runs use74c5092a8. Only PostPresentationPreviewTest.kt and
  PostStatusViewportTest.kt differ at3eecb210f. Application source, TalkBack
  classes, controlled fixtures, workflow and harness are identical.
- Current combined UI replacements: Android10 [41735070362](https://github.com/robelene112/redsquare-android/actions/runs/41735070362),
  Android17 [41735074007](https://github.com/robelene112/redsquare-android/actions/runs/41735074007).
  Both are SUCCESS: each44 tests across19 normal native class invocations,
  plus the successful cold profile recovery method after two actual process
  deaths. The repeated profile-edit-ui.txt collector
  is not counted twice. Original status recovery requires exactly2 tests
  within120 seconds. Other classes retain their original180-second bounds.
- Two-axis source review is clean, including both subsequent Like-expectation corrections.

## Failure provenance

- 41729118262: old header measurement moves the anchor54px on outage.
- 41730683829/41730688654: root-relative-only attempt moves it72px after Chat return.
- 41732939194/41732943195: Appearance1, Navigation2, Reading1, Browse8 pass;
 two post-presentation assertions then expect the intentionally removed Like word.
- 41733835098: four post-presentation cases, viewport, content and contexts pass;
 unavailable-action journey still expects the intentionally removed Like error UI.
- 41733839438: cancelled by replacement dispatch, not passing acceptance.

Original failures stay failures. Focused results do not establish whole-app
acceptance. The integrated release still requires94, and final owner, physical
Pixel, provider and store gates remain independent under55/36.

## Native observations

Original captures were inspected for retained reading through outage/recreation,
import cancellation returning to an unsaved bio with the real keyboard open,
and the narrow200% landscape viewer. The existing first-use density is still
visible and remains78 work, not app-wide visual acceptance. Both native process
continuity logs pass without resetting identity/backend during recovery.

The recorded normal/zero system-motion gates retain their existing recordings
and checks. Their passing endpoints are not a new visual redesign claim.
No public beta was replaced for76; publication is deferred to94 as authorized.
