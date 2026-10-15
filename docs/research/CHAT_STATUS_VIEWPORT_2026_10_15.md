# Chat status viewport, 2026-10-15

Read-only next-step research for
[#35](https://github.com/robelene112/redsquare-android/issues/35), while #40
remains the sole implementation issue. Only this document was written.
No prototype, tests, build, CI, production/node/wallet/service or credential
operation occurred.

## Evidence and ownership

Inspected the original
[queued-group screenshot](../evidence/ticket40/group-diagnostic-green-android17/group-archived-queued.png)
and its [qualified record](../evidence/ticket40/group-diagnostic-green-android17/README.md).
At 320x640 with the keyboard closed, it shows global backup/header content,
privacy/history guidance, several error rows and group actions; Queued appears
near the bottom, with lower controls outside the capture. This is constrained
viewport evidence, not keyboard-open acceptance, a queue-state defect, lost
work, or proof of the previous CI failure's cause.

[MainActivity](../../app/src/main/kotlin/nl/davidengelen/redsquare/MainActivity.kt)
owns root safeDrawing insets, global header/server/update/reminder and the
weighted feature pane. Secondary chrome returns when the keyboard closes.
[FeedScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/FeedScreen.kt)
routes directly into Chat before rendering its own feed banner; do not assume
that banner is causing this Chat stack.

[ChatScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatScreen.kt)
owns private privacy/history guidance, contact/load/cache/group-receive/reaction
errors, loading/older-history controls and weighted message history. Reaction
status/retry sits after history, before the composer.
[GroupChatScreen](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/GroupChatScreen.kt)
adds 8dp spacing, invitation/member actions, queue cancellation, membership/action
status/retry and separate history/live/storage errors around its weighted list.
[PersonalControlsStatus](../../app/src/main/kotlin/nl/davidengelen/redsquare/moderation/PersonalControls.kt)
can add loading/error/Retry rows in both. Weighted history alone cannot constrain
all these unweighted siblings.

## Smallest layout strategy to evaluate

Keep existing navigation/privacy indicators and one finite conversation pane.
Give the existing Send or queued-cancellation action row priority. Put secondary
guidance/warnings/management content in a bounded, vertically scrollable status
region; keep its full text and actions reachable. Determine its maximum from
the actual remaining viewport, not an assumed keyboard state or fixed screenshot
pixels. This preserves the existing message list and its quote/queued-scroll
indices, avoiding a list-index migration.

Bound the composer region too: long reply text, attachment preview, queued
preview and preparation/error explanations must scroll within finite space
rather than pushing its action row offscreen. Preserve the editable text field
and reachable Send. A photo preview can occupy less space, while the full image
viewer remains available. Do not truncate or rewrite the caption/quoted message
stored in drafts or signed payloads.

Concrete paths:

- [ChatComposer](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatComposer.kt):
  queued bubble/status/Cancel, work-read/automatic-send/general errors,
  reply/remove-reply, attachment and four-line editor/Send. Keep its mounted
  state, mutex, saveCurrent registration and exact cancel/retry handlers.
- Group selected-conversation branch: separate queue controls from secondary
  status content; retain existing membership/readiness/busy/read-failure guards.
  Its group-action FORWARDED state must not become a delivered-message claim.
- [ChatPhotoAttachment](../../app/src/main/kotlin/nl/davidengelen/redsquare/chat/ChatPhotoAttachment.kt):
  existing 72/100dp preview, removal, preparation/failure and Add photo. Preserve
  beforePicker persistence, session binding and preparation/send guards.

Retain the single root inset owner. Do not wrap an unconstrained LazyColumn in
verticalScroll, or move the stateful composer into disposable lazy items.
Check global reminder consumption if local bounds remain insufficient; temporary
compaction must not dismiss the reminder, change backend selection or remove
its recovery actions. Keep distinct privacy, history, live-connection, persistence
and submission explanations accessible.

## Verification seams

Extend existing
[queue cases](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/ChatQueuePreviewTest.kt),
[photo cases](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/ChatImagesPreviewTest.kt)
and [keyboard cases](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/ChatKeyboardPreviewTest.kt):
320x640 normal/offline warning stacks, queued text/photo cancellation and exact
restored captions, multiline/reply/photo composition, open/closed IME, rotation
and larger fonts. Verify visible bounds and actual taps/scrolls, persisted
identity/backend/draft, unchanged signed original, zero accidental sends and
normal explicit-send behavior. Cover both SDKs; #35's physical Pixel and full
integrated acceptance remain separate. This document implements or accepts none.
