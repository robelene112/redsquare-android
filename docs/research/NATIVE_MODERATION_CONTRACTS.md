# Native personal moderation and reports

Source-only inspection, 2026-10-09. Owner source is `/opt/saito/node`, Git root
`/opt/saito`, revision `9a4ca7e9442eb5e872168ced361ce741d7d139d7`.
No saved options, wallets, moderation lists, databases or live requests were
accessed. Source presence does not establish the deployed browser bundle or
runtime behavior. See [version provenance](SAITO_VERSION_PROVENANCE.md).

## Personal settings and effects

The implemented module is **Modtools**, service **modtools**. Red Square Block
emits `saito-blacklist`; Modtools changes its local list before attempting a
signed peer request. A peer failure therefore does not undo personal blocking.
Entries contain `publicKey`, `moderator`, `duration`, `created_at`, and `hop`.
`options.modtools` persists blacklist, whitelist and permissions. Browser Block
defaults to `200000000` milliseconds, approximately 2.315 days; `duration=-1`
means permanent. These legacy expiry semantics should not silently weaken the
accepted native persistent account-block behavior. [Block menu](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/tweet-menu.js#L172),
[local event](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L89),
[persistence](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L1015).

Shared moderation compares the transaction's first input public key, with
whitelist precedence over blacklist. Red Square reapplies it to loaded posts;
the browser can show a revealable mask. Chat rejects incoming transactions and
filters history through shared moderation. A native personal block should take
precedence over backend whitelist assertions. [Core filter](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L717),
[feed updates](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L306),
[Chat receipt](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1581),
[Chat history](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1811).

No implemented Red Square account-mute contract was found. Its optional
`hideTweet` menu action falls back to an unimplemented message. Chat conversation
mute is different: `group.muted` suppresses notifications/chimes while retaining
messages. That flag lives in LocalForage, outside standard options JSON export.
Chat also has `options.chat.black_list` and a keychain `mute` marker suppressing
deleted DM reconstruction; neither establishes feed account muting.
[Menu and mute](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/lib/overlays/chat-user-menu.js#L61),
[conversation storage](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2425),
[keychain marker](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2491).

Native policy should explicitly implement the agreed distinction: account mute
hides posts and notifications while retaining Chat; account block also hides
incoming Chat; conversation mute only silences that conversation. Apply current
policy before displaying cached items or issuing notifications. Inspected Red
Square notification rules do not establish an account-mute filter. Persist
versioned personal settings with the wallet across backend changes, separately
from backend-specific moderation snapshots; preserve compatible imported opaque
options. Imported Modtools entries mix personal and foreign-moderator provenance,
so do not reinterpret every legacy entry as an intentional permanent personal
block. [Notification rules](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/notifications.js#L151).

## Service and report contracts

Use the established binary application RPC, type 12 with correlated type 13
response, not transaction broadcast type 4. Exact signed serialization is
documented in [broadcast research](NATIVE_TRANSACTION_BROADCAST.md).

| Operation | Raw transaction message | Response and meaning |
| --- | --- | --- |
| Load backend lists | `{"request":"modtools","data":{"request":"load"}}` | `{whitelist:[...],blacklist:[...]}`; backend assertion, no signed entries or snapshot version. |
| Modtools blacklist | `{"module":"Modtools","request":"blacklist","data":<entry>}` | Signed transaction required; `{ok:1}` means handler completed, not necessarily list mutation. |
| Modtools unblock | `{"module":"Modtools","request":"unblacklist","publicKey":"<key>"}` | Signed; original moderator/trusted sender may remove an entry. `{ok:1}` is not removal proof. |
| Browser report | `{"request":"flag tweet","data":{"signature":"<post signature>"}}` | Browser uses an unsigned service transaction, without response callback. |
| Compatible signed native report | `{"module":"RedSquare","request":"flag tweet","data":{"signature":"<post signature>"}}` | Handler accepts the module-qualified shape; correlated `{}` means handler acknowledgment. |

Modtools verifies the compact signature against the first input's public key.
Server blacklist mutation requires a whitelisted moderator, yet the blacklist
handler returns `{ok:1}` even when authorization causes no mutation. Personal
blocking does not require becoming a server moderator. Load responses are usable
for selected-backend policy; individual `moderator` fields are not independently
signed attestations. Browser merge logic only adds/refreshes entries, so an empty
load must not be mistaken for synchronization of a personal unblock.
[Load/handlers](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L408),
[signature check](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L495),
[authorization](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L587),
[merging](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/modtools/modtools.js#L836).

Red Square's report constructor returns an unsigned transaction; its menu sends
only `tx.msg.data` through `sendRequestAsTransaction('flag tweet', ...)`. The
handler accepts either message shape above and marks the target archived post
`flagged=1`, updating `updated_at`. It returns `{}` even when no target exists.
No explicit reporter signature/authorization check appears in this handler;
generic module dispatch does not supply a universal signature check. Signing
native reports authenticates the client's intended bytes but does not change
that server behavior. [Browser report](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/tweet-menu.js#L182),
[constructor](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1206),
[acknowledgment](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1036),
[flag mutation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1813),
[module dispatch](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/modules.ts#L134).

Bind confirmation and transmission to the selected backend's authenticated peer,
not the post author's address or all connected peers. Socket enqueue establishes
transmission progress; a correlated response establishes handler acknowledgment.
Explicit transport/RPC failure is failure; timeout after transmission is unknown
delivery. None establishes moderator review, acceptance, a retained report or
chain inclusion. Keep optimistic local hiding separate from observed backend
flags. **Compatible submission and truthful status need no new durable receipt or
backend authentication contract.** Stronger receipts/auth checks are optional
server hardening, not prerequisites for ticket #22.

## Learning moderation changes in cached feeds

`flagged=1` is under review/filtered; `flagged=2` is approved and curated, not a
boolean removal flag. Review deletion deletes the Archive row without retaining
a moderation tombstone. Author deletion can save a separate deletion action,
but it does not provide a universal moderator-deletion stream. The inspected
review body validates target/decision without an explicit signer authorization
check; moderator UI access is not independent proof of server authorization.
[Review](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L2043),
[author deletion](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1932).

Normal browser feed updates specify `flagged_ne:1`, so newly flagged rows can
vanish from updates while an old cached copy remains. Native status reads can
omit that filter. Two supported application messages are:

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","sig":"<cached post signature>","limit":10}}
```

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","updated_later_than":1700000000000,"ascending":true,"limit":100}}
```

Archive returns row arrays; `sig` is an exact supported predicate, timestamp
comparison is strictly `>`, limits are bounded to 10–100, and presence of
`ascending` selects ascending order even if its value is false. The handler
overwrites requester/time fields with peer/server values.
[Feed filter](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L655),
[Archive handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L353),
[query bounds](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L750),
[exact predicates](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L822).

Validate the original post signature and match its identity before applying
metadata. `flagged`, `updated_at` and optional moderation fields remain unsigned
assertions scoped to the responding backend. Refilter cached posts on refreshed
backend lists; retain personal policy independently. Targeted cached-signature
checks can resolve flags without modifying the server. Timestamp deltas lack a
secondary pagination cursor: over 100 updates sharing one timestamp can be
missed. Overlap/dedup helps but cannot guarantee complete synchronization;
bounded targeted rechecks are necessary for affected cached items.

An absent page row is never deletion evidence. Even an empty successful exact
lookup proves only that this backend currently returns no matching row; review
deletion, retention and pruning can all cause absence. Pruning explicitly deletes
flagged rows without a tombstone. Treat backend availability separately from
verified author deletion, retain suitable cached history under the agreed
offline policy, and never infer removal from an outage or parse failure.
[Flagged pruning](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L1337).

## Version and verification limits

The historical official-source pin
`214504ecece76d1cd20a245fe4468eccbfca2951` has the same inspected browser report
menu shape, with no callback, and exposes the same Modtools signature-check and
Archive timestamp/filter contracts. This is a bounded source comparison, not a
claim about today's served official bundle or complete module equivalence.
[Official report menu](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/tweet-menu.js#L182),
[official Modtools](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/modtools/modtools.js#L496),
[official Archive](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/archive/archive.js#L751).

Native policy/persistence, selected-peer RPC correlation, and targeted Archive
status refresh are client adapter work. Isolated staging should verify report
acknowledgment and flag transitions against each backend before claiming runtime
interoperability. A durable tombstone/status stream or reliable delta cursor
would enable stronger synchronization guarantees; current source does not
provide them. Their absence does not prevent personal controls, compatible
reports, or conservative cache handling.
