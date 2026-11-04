# Ticket #79: shared Conversation navigation and recovery

Review base: `497edac120efdccd7ce334ade1aa1def14078f31`.
Last production source: `c544c00c49e28ad2a7b86aafb77c8f13f7099080`.
Full focused native source: `6795c2562cdeac591ac7aa675269686ee092fede`.
Supplementary observer source: `15885c2a3162244e6dcc4e155d6264a4b6c5c43d`.
Production code is identical across the latter three revisions.

## Delivered behavior

Public Conversation uses the shared vector Back control and a compact aligned
heading, Reply and information action area. At narrow 200% text the heading uses
ellipsis while retaining its full accessible heading; all actions remain available.
History limits and pull guidance live in a scrollable information dialog. Loading,
failed refresh, missing/loading parent, unreadable rows and cache warnings share
one compact status surface. Details retain every active explanation. Retry is
explicit and accessible. An unavailable-parent claim is suppressed while the
thread request is pending; loading does not establish absence.

Verified posts/replies, selected reply target, 14sp public body, drafts, cache
behavior and reading position remain. Success follows applied results. Stale
instructions to use a missing Refresh button now describe pulling down.
Private/group Chat uses the same vector Back convention with identical callbacks
and disabled-state guards; message typography/protocols are unchanged.

## Native verification

| Platform | Focused run | Result |
| --- | --- | --- |
| Android 10 | [41932608760](https://github.com/robelene112/redsquare-android/actions/runs/41932608760) | All 16 methods PASS at 6795 |
| Android 17 | [41932621954](https://github.com/robelene112/redsquare-android/actions/runs/41932621954) | All 16 methods PASS at 6795 |
| Android 17 additional narrow observation | [41935415552](https://github.com/robelene112/redsquare-android/actions/runs/41935415552) | Same six-cell narrow method PASS at 15885, unchanged app |

Each broad run includes recovery (1), narrow/pixel viewport (1 each), native motion
(1), original Browse (8), typography (1), feed reading (1), private reading/Back (1)
and group reading/Back (1). Each new class retains 180 seconds. Browse's original
15-second item/author deadline remains shared across real lazy-list reveal steps.
Full Social retains the new classes additively. These focused checks do not replace
#94's consistent-source whole-app and signed update gates.

Each OS has 12 viewport cells: 320/412dp, light/dark and 100/130/200% real system
text. They cover short/long signed threads, complete 14sp body text, 48dp disjoint
native header targets, exact same-post/1px reply return, real IME, exact unsent
draft recovery and cached outage/Details/Retry in every cell. Native click targets
are measured separately from Compose's inner 40dp painted bounds. No font
reduction, app delay or original assertion relaxation was introduced.

The recovery tracer opens a genuine signed reply through Search, observes actual
missing-parent/unreadable/cache faults, reads all explanations, retries after
removing the owned fault, preserves available data through node outage, and
recovers an exact unsent reply. While the request is held, Details do not claim
parent absence before a response. Owned faults, files, identity/backend,
appearance, platform fonts/display and motion restore after Activity closure.
No public post or Chat transaction is sent by this tracer.

Four native movies per OS retain normal-motion dark and zero-motion light history
opening/closing and physical pull/loading/applied-success/return. Original PNG
references, bounds, every decoded display frame and caption matches are retained.
The Spec reviewer and implementing agent inspected matrices and phase frames.
Normal transitions are short; zero motion changes directly. Information/error
text, controls and real reply keyboard remain usable at the largest tested sizes.

## Settled display observation

The first Android 17 light 320dp/200% reply PNG did not visibly paint the final
expected words beside its lower caret, despite retained semantic text. That
original is retained and excluded from settled last-line proof. The same-app rerun
keeps all six narrow cells and adds two no-input observations. Its initial and
both later originals visibly show the complete ending and caret. Actual later
samples are 278/828ms; editor bounds (18,184,302,348) and IME height (235px) remain
identical. Native glyph comparison against an independently painted dark ending
scores 0.961 in both later light frames (old unexpected frame 0.307, known good
Android 10 control 0.960, threshold 0.85). The PNGs and observation JSON are
preserved separately. No sustained defect was reproduced, no cause was proven,
and no production fix is claimed. Independent visual review has zero findings.

## Failure and correction provenance

- Run 41926254848 at 58b709262 used a reply as a general-feed fixture, which the
  real feed filters out. It was superseded as invalid setup.
- Run 41927315549 at 20a5f3910 is the real RED: the permanent history paragraph
  appears above an authentic standalone Conversation. The corrected tracer then
  uses actual Search to reach the signed reply with its missing parent.
- Run 41928544398 at 8fac0f225 reaches parent-unavailable state but fails a guessed
  Details label. Tests now use the existing localized resource.
- Source review at 8fac found a premature parent-absence claim during loading.
  c544 gates it with `!loading`, with native negative assertions.
- c544 runs 41930313490/41930327089 fail overall. Recovery, typography, feed return
  and private/group Back pass. Viewport checks measured inner 40dp paint rather
  than native 48dp targets; XML proves the targets. Correct native observation
  retains the 48dp requirement and adds disjointness.
- Browse waited for an offscreen lazy reply before reaching it. The existing
  reveal helper now reaches the item and author within the same 15-second budget.
- Android 10's combined motion passes; Android 17's original five-second movie
  ends while Loading, before its real success reference. Separate five-second
  history and refresh movies retain phase/pixel goals and the 180-second class
  limit. No app change or timeout/assertion extension was used. Final proofs pass.

Core/gateway CI checks pass 117/16 cases. Standards and Spec source reviews and
independent native visual review have zero findings. Final lint at 15885 passes
with one worker and unchanged heap/container limits; see `final-lint.txt`.
`native-results.json` binds accepted sources and results. Text whitespace is
normalized. Binary originals are unchanged; contact sheets are derived images.

These guarded disposable emulators and controlled signed peers do not establish
physical Pixel performance, external provider/store acceptance or permanent
history. No production node, owner wallet, block-production setting or public
social transaction changed. No intermediate beta was published. #94 retains
integrated verification, update and publication; the published beta is unchanged.
