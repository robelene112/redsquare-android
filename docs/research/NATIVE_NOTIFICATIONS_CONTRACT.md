# Native in-app notifications contract

Read-only research for #24, 2026-10-10. A/B mean official Saito pins
`9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
`214504ecece76d1cd20a245fe4468eccbfca2951`. These establish source contracts,
not live deployment revisions or demonstrated interoperability. No node access,
private data, initialization, builds or live writes occurred.

## Events and identity

Both versions derive events from RedSquare transaction envelopes:

| Signed request/data | Event and destination |
| --- | --- |
| `create tweet`, `parent_id`, `thread_id` | Reply; its own signature identifies the reply to open. |
| `create tweet`, explicit mention | Mention; open that post/thread. |
| `like tweet`, `signature` | Like; open the referenced original. |
| `retweet`, `signature` | Repost; open the original. |
| `retweet`, `signature`, nonempty trimmed `text` or `images` | Quote; retain commentary and original reference. |
| Dual-field `create tweet` quote from #13 | Own post containing original; open its own conversation. |

The current web classifier labels all nonreply create-tweet transactions simply
as tweets, including embedded quotes. Event identity is the transaction signature;
actor is `from[0].publicKey`; time is the transaction timestamp. Actor labels use
keychain username or key prefix, with an identicon. Native must validate signed
bytes/module/request and preserve verified actor identity beneath presentation
metadata. Reject fabricated snapshot actors and unsigned counters as new events;
do not replace invalid timestamps with the current time.
[B classifier/labels:43–134](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notification.js#L43).

Web eligibility excludes self, accepts contact-authored posts or replies to own
posts, and likes/reposts/quotes of own posts. Ownership uses cached target author,
otherwise the first output recipient different from the actor. This recipient
fallback proves addressing, not referenced-post ownership. Native should verify
the referenced original when available; use neutral activity wording when only
addressing is known. A drops notifications lacking cached referenced posts;
B retains them. Keep available signed activity when lookup fails.
[A eligibility/cache gate:107–209](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/notifications.js#L107),
[B eligibility:95–185](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notifications.js#L95).

## Archive discovery and coverage

Exact A/B streams, each `limit:10`:

| Stream | Filters | Older / newer |
| --- | --- | --- |
| Contact posts | `field1:RedSquare, field2:contactPublicKey` | `created_earlier_than` / `created_later_than`, per contact |
| Own likes | `field1:RedSquareLike, field3:walletPublicKey` | `created_earlier_than` / `updated_later_than` |
| Own reposts/quotes | `field1:RedSquareRetweet, field3:walletPublicKey` | same |

Contacts query the first module peer; interaction streams query the browser's
`localhost` archive. Native should query the selected backend's public Archive,
not a VPS filesystem. Supplement with bounded `RedSquare,field3:walletPublicKey`
reply queries, `field4:knownOwnPostSignature` queries and available feed/cache.
[A queries:857–990](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L857),
[B queries:652–785](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L652).

Reply indexing is `RedSquare`, field4 parent, field5 thread, field3 first
nonactor recipient. Likes/retweets use their respective namespaces, field3 that
recipient and field4 original. Storage defaults field2 to signer and field3 to
first output. There is one field3, not an index of all recipients.
[B indexes:1161–1287](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1161),
[storage defaults:149–164](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L149).

No distinct mention classifier/query exists in inspected notifications.
Constructor preserves `data.mentions`; composer does not populate it. Browser
mentions recognize `@PUBLIC_KEY` or `@Name@saito`. Native can classify bounded
signed mention data or exact text tokens using verified username mapping;
recipient presence alone is insufficient because replies inherit recipients.
Root-post mentions are not universally discoverable through field3. Complete
backend-wide mention delivery would require separately assessed indexing;
available-history notifications need no demonstrated backend change.
[constructor:922–925](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L922),
[composer:561–612](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L561),
[mention syntax:3116–3135](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/browser.ts#L3116).

Archive timestamp bounds are strict, maximum query limit 100. Web newer
interaction filtering uses archive update time but advances a shared cursor
using transaction time; copying this risks delayed-event loss. Native needs
independent bounded streams, timestamp-boundary overlap and signature dedup,
per-stream deadlines, partial success and explicit retry. Failure is not empty
history or exhaustion. Archive availability is not permanent chain history.
[Archive bounds:714–838](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/archive/archive.js#L714),
[web reconciliation:535–634](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L535).

## Native state and navigation policy

A uses transient unread flags; B persists a local last-viewed timestamp and
initializes it to now when absent. A groups likes by actor/target, B by target.
Native should persist observed read IDs per wallet/backend, deduplicate every
signed event before aggregation, and derive badges only from visible verified
activity. Apply the agreed wallet-scoped mute/block policy before rendering and
badges; the source eligibility above supplies no equivalent account filter.
[A state:16–60](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/notifications.js#L16),
[B state/aggregation:16–48,193–253](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notifications.js#L16),
[B persistence:334–377](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L334),
[agreed controls](NATIVE_MODERATION_CONTRACTS.md).

Current web commentary-quote notifications render/open the original, not the
quote. Native should retain the signed quote event and offer its commentary
plus original. Actor taps open the verified actor profile. Missing originals
need bounded lookup, unavailable/error state and Retry, without deleting the
notification. Preserve selected backend through outage and reject stale callbacks
after backend/wallet changes. In-app notifications work without Android system
permission; closed-app push remains #32–34.
[web referenced rendering:137–150](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notification.js#L137),
[web click routing:2041–2080](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/manager.js#L2041).

Verify spoofed envelopes, target mismatch, duplicates, late/boundary paging,
mentions/replies/quotes, personal controls, read persistence failure, offline
cache, missing-original taps and both selected backend contracts with controlled
peers. None of those runtime checks were performed in this research.
