# Consume Talk launch deliveries once, #127

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117), SP1.
Review base: `1934ec2c6a8c319a7154d2956e481ae2e115e6c0`.
Product/source/Android17: `319946d69e7c5f68b228fc06490f646a3a4069cb`.
Android10 fixture: `e2060c0161749244f06c2be73a6070bc43a0ba3d`.
Final diagnostic cleanup: `2d7fd72b594943af9b86a3492275ca48bb82f219`.

Talk launch references now use a saved-state ViewModel with separate initialization,
delivery generation and pending reference. Consuming a delivery clears only that
current identity, retaining initialization/generation across Activity recreation.
An original handled invitation or return-to-call Intent cannot replay over the
user's restored module. An unhandled delivery remains pending until wallet/node
and navigation are ready. A later intent receives a new identity, including a URL
that the user explicitly opens again.

Navigation verifies the exact delivery and owner/node before committing, and
consumes it inside that commit. Failed navigation retains the pending identity.
Busy wallet/backend operations defer handling. Valid new routes clear old expired
feedback. The original Activity Intent and debug endpoint configuration are never
replaced or stripped. The shared trusted model provider preserves empty untrusted
creation defaults and the exact legacy NotificationRoutes model key; existing
notification admission and handling are unchanged.

## Complete relevant native qualification

| Controlled native scope | Complete parent | Source | Result |
| --- | --- | --- | --- |
| Android10 | [43835719858](https://github.com/robelene112/redsquare-android/actions/runs/43835719858) | `e2060c016` | 13 normal completions, five groups |
| Android17 | [43829235005](https://github.com/robelene112/redsquare-android/actions/runs/43829235005) | `319946d69` | 13 normal completions, five groups |

Each complete scope has13 normal methods across five groups, retaining original
180-second class bounds and actual permission grant/restoration helpers:

- Two new methods cover handled cold invitations, cancellation/module changes,
  repeated recreation, pending new deliveries while backend selection is
  unavailable, recovery through the real Settings choice, a later same-URL
  delivery, and exact original launch/endpoint preservation.
- The second method starts an owned muted voice room, keeps its ownership while
  the first Activity closes, and cold-launches a second Activity using the exact
  live-call return URI with the original owned endpoint configuration. It then
  navigates to Stack and sends the actual Android foreground notification's
  PendingIntent. The exact call appears, real End call removes foreground state,
  and subsequent Stack recreation has no expired-route feedback or renewed capture.
- All six original invitation methods preserve prejoin choices, unreviewed text,
  wallet/node scope and normal external-link handling. Two routing and two
  ownership methods retain incoming delivery/acknowledgement and stale-control,
  wallet/node/blocking isolation assertions.
- The existing module-push method uses the real social notification journey from
  Stack, preserving the unsent post and opening the exact signed conversation.

The original cold return URI is modeled from public live-call fields with owned
launch configuration. The subsequent return is an actual notification
PendingIntent delivery. This is not proof that an original cold notification tap
itself supplied the controlled endpoint extras, nor physical/OEM shade behavior.
Common native fixtures verify complete wallet/options preservation, selected node,
zero unapproved transaction/report publication, and independent state restoration.

## Exact fixture bridge and source checks

The319-to-2d7 bridge changes exactly two AndroidTest files. The existing restored-
input readiness wait now requires the Talk header and one merged editable node
within its unchanged5,000ms budget. The original scroll operation, exact unfinished
text assertion, media assertions, capture and identity checks remain. Product,
core, resources, build configuration, workflow and harness are byte-identical.
SocialPushTapPreviewTest also waits for a natively hidden IME and an enabled visible
module header within its original15s preparation budget. It performs one header
click and one Stack click, observing the popup choice in between. A single5s
transition deadline now spans both clicks and destination readiness, checked again
at completion. The original draft/photo/signature/identity and downstream routing
assertions remain. Android10 also retains at most128 header observations around
that single click. The successful run records the same full152x48px header and
hidden IME before/after the click, then observes the picker. The final2d7 cleanup
uses existing withFailureCapture so diagnostic failure cannot replace the native
failure. Its success path has unchanged observations, queries, clicks and deadline.
Both source review axes approve these explicit bridges; each accepted SDK has its
own complete parent and exact source receipt. The earlier intermittent picker
failure cause remains unproved.

Final [source-debug43836186053](https://github.com/robelene112/redsquare-android/actions/runs/43836186053)
compiles the final2d7 AndroidTest source and assembles debug/test APKs. Core test
reports contain224 tests/44 classes with zero failures/ignored; core and gateway
test tasks are FROM-CACHE. Actual lintDebug succeeds with zero errors,95 warnings
and three hints. Original319 source-debug43829238885 is separately retained.
These are source/debug checks, not release signing or whole-app verification.

## Retained original observations

All originals, API metadata, full logs, server-digest-verified archives and exact
ZIP/TAR extraction checks remain externally at
`/mnt/storage/redsquare-android-artifacts/talk-launch127`:

- 43826332008@4a7e5a729 is the actual cold-invitation replay: after cancellation
  and successful Stack reading, Activity recreation returns to the old prejoin
  screen and fails the original5s Stack-header goal. Pending later phases are
  not claimed from that failed method.
- 43827246350@727c743ce is an intermediate complete one-method success covering
  the cold/pending/new-delivery journey. It is not final whole-ticket acceptance.
- 43829231193@319 completes both new methods, then an original invitation case
  fails its merged text-field lookup after only the header had become ready.
  The native error reports one unmerged editable node; a later screenshot shows
  the retained unfinished text. The exact underlying timing cause remains
  unproved. This parent stays failed and contributes no accepted prefix.

- 43830728231@aef completes both new methods and all ten original Talk methods,
  then the selected social method fails at its missing Stack popup target after
  an immediate header click following asynchronous keyboard dismissal. The exact
  earlier touch failure cause remains unproved. Its four successful Talk groups
  do not supply partial acceptance. The next fixture change observes actual IME/
  header/popup readiness, preserving one click per action and original time limits.


- 43832600830@7bc also completes all12 Talk methods, then the module-push
  method times out after4,977ms waiting for the Stack sheet following one header
  tap. Actual IME-hidden and enabled/visible-header preconditions passed. A later
  capture retains the unsent post/photo with no picker or visible keyboard.
  The readiness change was insufficient; the exact cause remains unproved.
  The whole failed parent is excluded, without prefix reuse.

- 43834820181@7bc independently passes the unchanged single module-push method
  (81 ZIP entries/nine TAR members). It minimizes the intermittent observation
  but supplies no partial acceptance and does not explain the earlier failures.
- Complete43835719858@e206 passes all13 methods together, with bounded header
  observations. Its actual source is retained rather than relabeled as2d7.

## Evidence and limits

Both independent Spec and Standards axes report zero remaining source findings
and independently accept each complete native parent, original digest/bytes,
source-qualified bridge and modeled-cold-return qualification. Final source/debug
compilation succeeds. Copied packet review is pending.

Controlled emulators establish the agreed Activity/Intent/notification/storage
behavior. Physical/OEM behavior, default third-party domain association, live
community services and killed-app incoming calls remain qualified separately.
These targeted runs do not establish whole-app release/performance acceptance.
No production node/provider operation or public transaction occurred. Public beta
remains0.1.131 until combined integration and signed delivery in#131. Continue
128–130 sequentially, then131, without another approval.
