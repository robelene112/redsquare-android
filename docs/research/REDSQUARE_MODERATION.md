# Existing personal moderation and reporting

Read-only static-source inspection under `/opt/saito/node` on 2026-10-08.
No saved options, keys, database contents or live operations were inspected.

- Red Square's Block action emits `saito-blacklist`
  (`mods/redsquare/lib/ui/overlays/tweet-menu.js:172`). ModTools stores local
  blacklist records in options (`mods/modtools/modtools.js:892` and `:1015`).
  Signed moderation requests can also reach a connected ModTools peer (`:118`
  and `:471`), which applies them only for trusted senders (`:587`).
- No separate account-mute feature was found in inspected Red Square sources.
  Hide this post calls an optional `hideTweet` or displays an unimplemented
  message (`tweet-menu.js:163`); no implemented `hideTweet` was found.
- ModTools rejects blacklisted authors (`modtools.js:717`). Chat checks that
  shared moderation on receipt (`mods/chat/chat.js:1581`) and history rendering
  (`:1813`). Red Square filters tweets (`mods/redsquare/redsquare.js:323`), with
  already-loaded posts potentially shown under a revealable moderation mask
  (`lib/tweet.template.js:142`).
- Chat conversation mute retains messages while suppressing notifications and
  sounds (`mods/chat/lib/overlays/chat-user-menu.js:61`, `chat.js:2027` and
  `:2566`). Its group flag is in separate LocalForage storage (`:2453`).
- Chat's separate block list is stored in options (`chat-user-menu.js:79`,
  `chat.js:2389`). Deleting a DM also saves a keychain mute marker to suppress
  default reconstruction (`:2491` and `:2294`). A matching direct block-list
  rejection was not found in the receive path; shared ModTools blocking is
  distinct. Define consistent native semantics rather than copying ambiguity.
- Standard wallet JSON copies options-backed ModTools, Chat and keychain lists
  (`lib/saito/wallet.ts:1244`), but not LocalForage conversation mute flags.
  Export inclusion does not imply automatic synchronization between servers.
- Report sends a `flag tweet` peer request with the post signature
  (`tweet-menu.js:182`). The UI marks the post and local archive immediately,
  without a response callback (`:191` to `:197`).
- The backend invokes its flag handler and can return an empty response
  (`redsquare.js:1036`). It marks the target local archived post `flagged=1`
  (`:1813` to `:1865`). This does not establish a separate durable report record,
  reason text, reporter receipt or guaranteed moderation.
- Feed queries exclude flagged posts (`:658`); trusted moderators load them for
  review (`:253`). Review can delete the archived post or approve it as curated
  (`:2080` to `:2102`). The under-review wording (`lib/tweet.template.js:145`) is
  an optimistic state, not verified moderator acknowledgement.

Native account muting and reliable report status require explicit behavior and
integration verification. Existing browser hooks alone do not prove delivery.
