# Social push: native tap routing, 2026-10-15

Read-only proposal for #32 at source `ede09ff105619b45e5d7390e9440179298c3b208`.
No implementation, tests/builds/CI, network or runtime user-data access occurred.

## Existing boundaries and concrete traps

- [MainActivity](../../app/src/main/kotlin/nl/davidengelen/redsquare/MainActivity.kt)
  has startup only (`onCreate`, L99); no `onNewIntent`. Wallet/backend load
  asynchronously (L114-139,214-230). [Manifest](../../app/src/main/AndroidManifest.xml)
  gives MainActivity no special launch mode. Creating a second Activity can bypass
  the mounted first Activity's draft-save callback.
- [ChatNotifications](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatNotifications.kt)
  L53-54 currently creates one generic immutable PendingIntent with requestCode0,
  no destination or wallet/backend binding. New social routes need distinct
  PendingIntent identity, not merely changed extras, so alerts cannot overwrite
  one another's target.
- [PostNavigation.save](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostNavigation.kt)
  L6-8 invokes the mounted form's suspendable callback. Registration exists in
  [PostComposerScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostComposerScreen.kt)
  L103-106, author/profile editors, [ChatComposer](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatComposer.kt)
  L58-72 and [GroupChatScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/GroupChatScreen.kt)
  L101-122. Private/group drafts debounce300ms; disposal alone does not flush the
  latest text. Sending/photo preparation can refuse navigation. Await save and
  leave the current screen intact on false; never synthesize Send or cancel work.
- [FeedScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/FeedScreen.kt)
  L198-214 prioritizes Chat, queue, author-work and composer flags before its
  browse stack. Appending a Reference alone cannot surface it in those states.
  Browse memory is wallet/backend-scoped (L82-84), but private/group selection is
  ordinary `remember`, not durable navigation state:
  [ChatScreen L59](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatScreen.kt),
  [GroupChatScreen L49](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/GroupChatScreen.kt).
  Saving preserves work; it does not prove exact conversation/scroll restoration.
- [PersonalControlsScope](../../app/src/main/kotlin/nl/davidengelen/redsquare/moderation/PersonalControls.kt)
  L119-125 loads asynchronously per owner. Require `ready`, no in-flight policy
  save, and current eligibility. `hidesPosts` fails closed (L111).
- [NotificationReferenceScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/NotificationReferenceScreen.kt)
  L24 requests `refresh=true`, but [QuoteState.load](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/QuoteState.kt)
  L37-45 returns cached content/deletion before network even then. Reusing that
  screen is not evidence of fresh selected-Archive visibility on tap.

## Smallest proposed route

Use one explicit MainActivity intent with immutable public route metadata:
version, wallet public key, approved backend ID, original event signature and
category. Distinguish its PendingIntent identity per event/binding. Validate
bounds/types and treat this exported Activity input as untrusted.

Handle cold `onCreate` and warm `onNewIntent` through one pending-route state.
Use deliberate task flags that deliver to the existing Activity, verified by an
actual warm-tap test. Keep route parsing separate from the Activity's existing
launch intent: replacing that intent wholesale can discard controlled endpoint
configuration and other launch state.

Wait for normal wallet/backend/personal-controls readiness. Reject a stale
wallet/backend binding without changing either selection. Perform the bounded
fresh exact Archive lookup and signed event/parent validation first. Navigation
uses that verified result, never notification text. Then guard mounted-form input
across final `PostNavigation.save()`, scope/policy rechecks and the navigation
commit, or use equivalent proven revision-aware serialization. The guard must be
active before saving starts: ChatComposer.persist suspends during work.saveDraft
without freezing editing (L58-66). A save followed by network work, or moving the
lookup before an unguarded save, can still discard newer text at disposal.
Pending save failures retain the route and latest work for explicit retry.
Consume the route once after successful navigation; recreation must not replay it.

Apply the destination transition before FeedScreen's early-return branches,
with an explicit return strategy. Reopening saved post/private/group work is the
smallest existing mechanism; exact Chat selection restoration would need an
additional route-state decision. Do not introduce an overlay containing another
composer without resolving PostNavigation's single callback ownership.

## Smallest app-level tracer

Extend the existing controlled-peer Android boundary, not an internal navigator
mock: type an unsent post with image, immediately tap the actual Android system
notification, observe the exact independently signed reply/thread, return and
reopen the exact saved draft. Assert unchanged wallet/backend and zero outbound
post/Chat transactions. Then repeat for private/group unsent work and cold launch;
add stale binding, unreadable/saving controls, save-refusal and unavailable-target
cases. Actual system notification/tap behavior is required; directly invoking an
intent alone does not establish it. This demonstrates local delivery/navigation,
not FCM, closed-app wakeup or full #32 completion.
