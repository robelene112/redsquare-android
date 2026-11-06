# Chat grouping verification (#83)

Source under verification: `9f00665e3da30a15b50ed6e81d0d0f0ade500828`.
Review base: `27ebdff8362da11438f9602da969d9a405a79dd8`.
Product implementation ends at `77ec61af2d2528719e6daefb5ec7e835f274d415`;
subsequent commits change test observation only.

## Delivered behavior

Consecutive admitted messages from the same exact verified sender share an
avatar/name header when their adjacent timestamps are nondecreasing and at most
five minutes apart. A sender change, reversed time or longer gap starts another
group. Records stay individual and in their original order. Each retains its
own time, menu, reply, copy, reaction and media actions. Queued previews remain
separate. Body typography and system text scale are unchanged.

Sender identity and message menus retain actual48dp controls. Separate header
rows use physical lazy-list keys internally; accessibility row counts and
scroll ordinals still refer to actual messages. This preserves original
quote-navigation, filtering and older/newest reading assertions. A strict
noninteractive clip boundary preserves native header bounds while restoring the
inherited touch configuration for every interactive child.

## Required evidence

The literal catalog is frozen in `literal-identities.json`:22 methods across
12 classes per Android version, in three complete scopes. `retain-native.py`
requires matching source/workflow provenance, successful complete runs, exact
method identities and zero host exits. It validates original PNG/XML data
before retaining it. All six complete scopes passed. Final native review is recorded below.

The new native matrices cover private/group Chat at320/412dp,100/130/200%
text, light/dark, verified long names, multiline text, photo replies, identity
and individual menus, with the real IME. Original reading and private action
classes retain their assertions and180-second bounds.

## Complete focused results

| Android | Grouping | Original reading | Original actions |
| --- | --- | --- | --- |
| 10 | [7 methods,6 classes](https://github.com/robelene112/redsquare-android/actions/runs/42159567235) | [5 methods,5 classes](https://github.com/robelene112/redsquare-android/actions/runs/42159573098) | [10 methods,1 class](https://github.com/robelene112/redsquare-android/actions/runs/42159564315),80.421s |
| 17 | [7 methods,6 classes](https://github.com/robelene112/redsquare-android/actions/runs/42159570266) | [5 methods,5 classes](https://github.com/robelene112/redsquare-android/actions/runs/42159575754) | [10 methods,1 class](https://github.com/robelene112/redsquare-android/actions/runs/42159561731),105.610s |

Every scope tested exact source9f, with original assertions, deadlines and
180-second per-class bounds. Local instrumentation compilation also passed.
These focused results do not replace #94's complete application gates.

## Native review

Both independent source axes report zero unresolved findings. The standards reviewer inspected286 selected PNGs across both SDKs:115
grouping/reading and28 actions captures per version. All48 grouping header
receipts match their XML, including144 targets at least48x48px. Coverage lists
and crosschecks are retained in `reviews/`. The spec reviewer inspected154 selected PNGs:57 grouping/reading per SDK
and all40 Android17 actions captures. Both final native axes report zero
unresolved actionable findings. This is representative coverage, not a claim
that every retained member was visually inspected. The Android17 actions reviewer inspected all40
PNGs plus eight selected XMLs; the standards reviewer inspected28 action PNG/XML
pairs per Android version, including168 native fixed-header targets at least
48x48px. Neither found an actionable defect. The complete grouping/reading review also found no actionable defects.

Native XML and PNG capture is non-atomic. In the Android17 zero-motion
quote-before-tap pair, XML already lists the original in view while the PNG
still shows the spacer and sent reply. The original Compose offscreen assertion
and before/after PNG sequence support navigation; that XML alone does not prove
the pre-tap position. Native bounds describe observed geometry, not physical
hit testing or continuous-frame attribution.

## Diagnostic history and limits

The `failures` directory retains earlier functional, logical-row, IME and native
bounds failures. Plain clipping did not fix the native bound. Successful older
packets do not replace the current source's verification.

The Android17 original action class exceeded180seconds twice at source77,
and again at7199. No partial result is accepted or assembled into a full pass.
A diagnostic-only source completed ten tests in174.059seconds; its40 captures
spent14.985seconds checking text readiness and13.881seconds dumping XML.
The current observer uses the mandatory visible native XML for safe exact-text
readiness, retaining its ten-second deadline and raw matching for ambiguous
exported text. The39 captures in the failed7199 execution used17.290seconds
in total, versus29.837seconds in the earlier40-capture probe. This establishes
observer cost reduction, not a cause for distributed runtime variation.

The final observer change injects an actual system Back key directly for a
positively observed message popup, retaining the original five-second actual
disappearance assertion. It avoids UiAutomator2.4's separate idle/event wait.
Both source reviews reported zero findings. Both complete ten-method classes now pass. This does not independently
establish the cause of the earlier distributed-run timeouts.

Controlled signed-history/emulator evidence does not establish physical Pixel
performance, gesture feel, continuous-frame rendering or spoken TalkBack quality.
No owner wallet, public transaction or node configuration is changed.
