# Social push: Archive discovery, 2026-10-15

Source-only research for #32, complementing the
[block observer](SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md).
Existing public source copies at pins A=`9a4ca7e9442eb5e872168ced361ce741d7d139d7`
and B=`214504ecece76d1cd20a245fe4468eccbfca2951` were inspected.
No app changes, network/probes, live node/data access, builds or CI.

**Do not assume ordinary create-tweet posts have an unconfirmed Relay indexing
path.** At A, [browser composition](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L709-L726)
signs and propagates, then calls `receiveTweetTransaction` locally for optimistic
browser state. [Native PostComposerState.send](../../app/src/main/kotlin/nl/davidengelen/redsquare/feed/PostComposerState.kt)
uses `peer.submit`; [type4 routing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L361-L365)
enters verification, not the application RPC handler.

Both [A's peer handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1011-L1095)
and [B's](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L806-L891)
handle several interactions but omit `create tweet`. Wrapping one in Relay
dispatch does not supply that missing case. A's
[onConfirmation(conf=0)](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L2117-L2149)
calls `receiveTweetTransaction(tx,blk)`;
[receive/save](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1338-L1402)
indexes `field1=RedSquare`, parent/thread metadata and optional block metadata.
No direct peer create-tweet indexing path was demonstrated. This finding does
not prove current deployment timing or eventual inclusion of zero-fee posts.

**Archive can nevertheless contain unconfirmed/off-chain records.** Its separate
[peer archive/save handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L353-L419)
deserializes and saves a supplied transaction. [saveTransaction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L425-L455)
defaults block ID/hash to zero/empty and timestamps to supplied metadata or
transaction timestamp. This does not establish ordinary compose/native posting
uses remote archive/save, nor that a stored row is block-confirmed. Never add
an Archive-save write as a discovery mechanism.

A minimal read-only complement is a gateway-owned authenticated peer request:

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","flagged_ne":1,"limit":100}}
```

Omit recipient and root-only `field4` restrictions, so replies and content-only
mentions remain candidates. The [load implementation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L712-L838)
defaults to insertion ID descending; limits are clamped to10..100. This head
read can discover recently inserted backdated rows that timestamp-only polling
misses. Verify original signatures/content and parent author/mentions, deduplicate
per backend/signature, then retain the existing exact selected-backend visibility
and phone eligibility checks. Row metadata is not authority.

Supported older/newer bounds are strict `created_earlier_than`,
`created_later_than`, `updated_earlier_than`, `updated_later_than`; timestamp bounds
switch ordering to that timestamp. `ascending` is supported by property presence.
Schema fields such as `id` support equality, not an insertion-ID range cursor;
no offset/composite timestamp-signature cursor is implemented here. A bounded
older-page scan with overlap can improve coverage but cannot guarantee it:
timestamp ties at the page boundary, backdating, metadata updates, more than100
new head rows between polls, outages, moderation and pruning can cause gaps.
Never advance a supposedly exhaustive cursor across such ambiguity.

Proposed, not implemented: periodic bounded Archive head reconciliation beside
block observation, with explicit row/page/time budgets and recorded coverage
limits. It covers selected-Archive-visible candidates independently of their
block discovery, not every possible network post or offline notification.
No new public endpoint or production hook is required by this source proposal;
actual useful recent-page discovery and closed-app delivery remain unverified.
