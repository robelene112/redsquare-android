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
