# Red Square chat integration

Read-only inspection of `/opt/saito/node` on 2026-10-07 found that Red Square
can embed a chat interface, supplied by a separate Saito Chat module.

- `mods/redsquare/redsquare.js`, around line 2418, obtains an optional module
  responding to `chat-manager` and registers it with the Red Square main view.
- `mods/redsquare/lib/main.js` checks for that capability and gives the chat
  manager desktop or mobile containers. A chat view is unavailable without it.
- `mods/redsquare/lib/menu.template.js` conditionally adds a Chat navigation item.
- `config/modules.config.js` includes `chat/chat.js` in both the server and
  browser module lists on this node.

Thus messaging can be part of the web Red Square experience, while its protocol
and UI are owned by a separate module. Private and group Chat are now accepted
first-release features; remaining media, privacy and recovery scope is being
resolved in the app-wide grill.

The public Saito homepage also lists Chat separately from RedSquare:
https://saito.io/

## Media, privacy and recovery

Further read-only static-source inspection on 2026-10-08. No wallet, options,
keychain state or database contents were read and no transactions were submitted.

- Images are uploaded, resized and sent as an HTML image message in
  `mods/chat/lib/chat-manager/popup.js:850` and `:884`. Full-size viewing is at
  `:672`. Giphy URLs bypass resizing (`:868`), with provider integration in
  `lib/saito/ui/saito-input/saito-input.js:598`.
- Arbitrary file transfer is a separate FileShare integration. It contributes
  Send File actions (`mods/fileshare/fileshare.js:143` and `:193`) and requires
  a STUN-managed direct peer connection (`:151` and `:179`). It is not a persistent
  Chat attachment or archive feature.
- Ordinary two-person DMs force encryption (`mods/chat/chat.js:1519`).
  `lib/saito/keychain.ts:276` prefers a saved Diffie-Hellman secret, then falls
  back to a secret derived from wallet identity and the recipient public key
  (`:291`). Decryption has the corresponding fallback (`:187`). Older comments
  suggesting key exchange is always necessary do not fully describe this code.
- Group Chat is currently unencrypted (`chat.js:1031`). Groups with `member_ids`
  take the signing-only transaction branch (`:1467` and `:1519`).
- Delivery still depends on connectivity and peers. Chat propagates transactions
  and uses Relay (`chat.js:1540`). Explicit key exchange, when used, involves
  transaction or Relay exchanges (`mods/encrypt/encrypt.js:252`).
- LocalForage stores group history as `chat_<group id>` (`chat.js:2393`), with
  saving retaining the last 50 messages (`:2425` and `:2446`). Options retain
  group IDs, not all message history (`:2431`).
- Older history may be requested from Archive by known group ID and timestamp
  (`:2302`). Community history comes from a Chat-service peer (`:337` and `:389`),
  and group updates may carry participant-supplied history (`:1236` and `:1349`).
  Private conversations are rebuilt from saved keychain entries (`:2287`) or
  created when addressed messages arrive (`:1612`).

Wallet-key import restores identity but cannot promise every conversation or
old message. Implicit-key messages may be decrypted if retrievable; exchanged
Diffie-Hellman secrets are additional keychain state (`keychain.ts:673`, saved
in options at `:464`). LocalForage history does not travel with wallet keys.
Complete history recovery requires separately defined and verified backup or
sync behavior. These findings do not establish native cryptographic correctness.

## Blockchain submission does not guarantee complete history recovery

Follow-up static-source verification on 2026-10-08:

- Successfully constructed ordinary private, group and community Chat messages
  are submitted through `propagateTransaction` and also sent through Relay
  (`mods/chat/chat.js:1540` to `:1545`). They are not merely local or relay-only
  messages. Submission alone does not prove block inclusion.
- Relay receipt can display a message separately from chain confirmation
  (`chat.js:911` and `:963` to `:965`). Included messages are also handled by
  `onConfirmation` (`:786` to `:814`).
- Archive's blockchain indexing is conditional (`mods/archive/archive.js:313`
  to `:347`), and deletion queries remove unpreserved transactions (`:1349` and
  `:1359`). Actual availability depends on the archive retaining a message;
  no live configuration or retention setting was inspected.
- The pinned Rust core deletes older genesis blocks and transactions
  (`rust/saito-core/src/core/consensus/blockchain.rs:2323` to `:2334` and
  `:2416` to `:2419`). Chain synchronization is not a permanent-history promise.
- A full JSON wallet export copies options excluding games
  (`lib/saito/wallet.ts:1244` to `:1251`), retaining saved keychain and Chat group
  IDs. Full import loads that object (`:1392` to `:1404`). This is broader than
  importing a private key alone (`:1417`).
- Chat's local group objects and recent messages are in separate LocalForage
  storage (`mods/chat/chat.js:2402` and `:2453`), not the wallet options export.

Consequently, full wallet backup import can aid conversation discovery and
decryption, but cannot guarantee every old message will return. Recovering
available network history and migrating a complete local history are distinct
product requirements. The user accepted available network history plus local
cache as the first-release scope, with no expectation of permanent availability.
See PROJECT_CONTEXT.md for the resulting recovery and migration boundaries.

Official background sources explain Saito's
[on-chain and off-chain messaging](https://archive.saito.io/org.saito.tech/tutorial-2-chat-notification-application/)
and [chain pruning design](https://archive.saito.io/saito.tech/index.html%3Fp%3D700.html).
These are historical explanations; the observations above come from inspected
source rather than an assumption that the historical tutorials match all current
runtime behavior.


## Telegram Desktop performance reference, 2026-11-11

Owner-requested short reference after beta112. Official source inspected at
[`1924801c147371267b4cb8696767bc0d4d8f39c7`](https://github.com/telegramdesktop/tdesktop/commit/1924801c147371267b4cb8696767bc0d4d8f39c7).
These are observed mechanisms and prospective Android lessons, not measured
explanations of Telegram's overall speed or a scalability result for our app.

- Limit painting to visible/damaged rows: the message list finds its paint
  interval with binary searches. Our LazyColumn already supplies lazy rows;
  data preparation during scrolling must also stay small.
  [Viewport painting](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/history/view/history_view_list_widget.cpp#L3487-L3492).
- Request history slices around the reading position, with before/after limits
  adjusted from the viewport. Preload near a boundary and guard duplicate requests.
  Preserve the reading anchor; the desktop preload distances are not mobile defaults.
  [Slices](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/history/view/history_view_list_widget.cpp#L818-L843),
  [sizing](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/history/view/history_view_list_widget.cpp#L2537-L2584),
  [ordinary history request guards](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/history/history_widget.cpp#L5138-L5186).
- Maintain message IDs/ranges incrementally and reuse existing item views.
  Stable Compose keys help, but do not make rebuilding an entire data model cheap.
  [ID updates](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/data/data_messages.cpp#L94-L135),
  [view reuse](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/history/view/history_view_list_widget.cpp#L1636-L1652).
- Repaint the affected item/rectangle and reuse measured heights until a resize
  or width change requires recalculation. Consider narrow message updates and
  reusable derived row state when unrelated counters or receipts change.
  [Repaint](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/history/view/history_view_list_widget.cpp#L6113-L6138),
  [measurement](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/history/view/history_view_list_widget.cpp#L3256-L3268).
- Reuse prepared image variants. One cached-file path decodes asynchronously and
  returns through a guarded main-thread callback. Prepare display-sized media,
  reuse transformations and discard obsolete results; this is not evidence that
  every Telegram operation or decoding path runs off the UI thread.
  [Image variants](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/ui/image/image.cpp#L91-L112),
  [cached-file decoding](https://github.com/telegramdesktop/tdesktop/blob/1924801c147371267b4cb8696767bc0d4d8f39c7/Telegram/SourceFiles/storage/file_download.cpp#L277-L315).

Local inspection at `b2e46f9950` identifies candidates to measure, not confirmed bottlenecks:

- `GroupChatStore.entries()` enumerates group files and calls `load()` for each;
  `load()` replays group originals. `GroupChatScreen.publish()` already does this
  work on Dispatchers.IO, but multiple updates reload the collection. Investigate
  an incrementally maintained inbox summary and loading detailed history only
  for the conversation/window that needs it.
- `ChatHistoryStore.contacts()` recovers counterparties from retained envelopes.
  Explore a verified discovery index rather than repeated full envelope recovery.
- `ConversationMessageList` already uses LazyColumn, stable row IDs and reading
  anchors. Its derived history sets/filtering and list membership checks during
  scroll are further measurement candidates before changing Compose structure.

Any derived index must preserve verified originals, encrypted private storage,
wallet/node isolation, membership changes and durable send/acknowledgement rules.
Do not remove transactional locks or skip signature/replay checks on the strength
of this comparison. Measure cold/warm opening, scrolling, media and one incoming
message at realistic chat/history sizes, separating IO/replay/lock time from UI
work. No application change, new performance target or benchmark is established here.
