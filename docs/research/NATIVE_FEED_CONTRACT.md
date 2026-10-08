# Native feed read contract

Read-only preparation for live-feed ticket #4, 2026-10-08, using the
project-local research skill and current app/Saito routing. Inspected static
production sources through a network-disabled, read-only Docker bind and
official public source revisions. No saved options, environment, wallet keys,
database rows, live transactions or service changes were accessed. No live peer
query was sent. Only this research file was written. The accepted full release
scope, including quotes, remains intact; this document identifies the read
contract needed for the next feed milestone.

## Recommended initial read path

After authenticating `/wsopen`, issue a correlated application request to the
selected backend's Archive service:

```json
{
  "request": "archive",
  "data": {
    "request": "load",
    "field1": "RedSquare",
    "field4": "",
    "flagged_ne": 1,
    "limit": 20,
    "created_earlier_than": 1700000000000
  }
}
```

The timestamp is illustrative; use a real initial upper boundary. The reply is
an array of **archive row objects containing `tx` strings**, not Tweet JSON
objects. Decode the web envelope and transaction, validate signed content,
then produce the native feed model. `field4: ""` restricts the archive query
to root posts at both inspected implementations. Preserve generic row metadata
separately from signed transaction data. Source and query variants follow.

This path supports a first feed without funded wallet inputs or a downloaded
blockchain. Transport/handshake identity is required. Successful reading does
not establish transaction submission, chain inclusion or a fully synchronized
wallet. Actual live endpoint/service availability still needs ticket-level
integration verification. See [native protocol reference](NATIVE_SAITO_PROTOCOL.md).

## Authority and relevant version differences

Deployed reference: official
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
from the maintained Snake runtime pin. Also inspected official `prod`
[`214504ecece76d1cd20a245fe4468eccbfca2951`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951)
and `staging`
[`e9f3635fa56450008774f2d8d76731121268fe7a`](https://github.com/SaitoTech/saito/commit/e9f3635fa56450008774f2d8d76731121268fe7a).
These do not establish saito.io's exact deployed Git SHA; see
[version provenance](SAITO_VERSION_PROVENANCE.md).

| Contract | Owner pin | Inspected prod/staging |
| --- | --- | --- |
| `load tweets` RPC | `field1: RedSquare`, `flagged_ne: 1`, fixed limit 10, strict creation boundary | Same, additionally `field4: ""` |
| RPC reply | Web transaction string array | Same |
| Generic archive load | Row array, creation/update boundaries, schema equality filters | Same query behavior; Archive diff only adds module status |
| Web transaction envelope | `{t,m,opt}` | Byte-identical `node/lib/saito/transaction.ts` at all three revisions |
| Feed loader implementation | Inside `redsquare.js` | Extracted into `lib/loader.js`; prod/staging loader byte-identical |
| Tweet parsing | Includes `optional.curated` | Curated removed; staging additionally supports `optional.repliers` |
| Timeline ordering | Root creation time descending | Can promote a root using a newly received critical reply after last-viewed time |

References: pinned
[RPC](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1065),
current
[RPC](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L860),
[loader](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/loader.js#L273),
[current ordering](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweets.js#L259),
[staging Tweet parser](https://github.com/SaitoTech/saito/blob/e9f3635fa56450008774f2d8d76731121268fe7a/node/mods/redsquare/lib/tweet.js#L187).

## Requests and exact response variants

The node advertises service
[`redsquare`](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L110).
Use service discovery to distinguish authenticated connectivity from available
application services. The native transport uses type 12 + big-endian u32 request
index + the binary application-request transaction; results are type 13 + same
index + UTF-8 JSON. Application-request transactions are not blockchain
propagation messages. Full layout and synthetic fixtures are in
[the protocol reference](NATIVE_SAITO_PROTOCOL.md#websocket-handshake-and-archive-requests).

**Red Square-specific read:**

```json
{"request":"load tweets","data":{"created_earlier_than":1700000000000}}
```

Pinned `mods/redsquare/redsquare.js:1065–1091` invokes local Archive load and
serializes each returned transaction into the callback. Current prod
`:860–887` does the same with the additional root filter. The request contains
no `module: RedSquare` discriminator because the top-level request is the
application RPC. Supplying an extra limit does not change the handler's fixed
10-item limit. The current loader includes `field4: ""` in request data, but
the current handler itself hardcodes that filter. Reply shape:

```json
["{\"t\":\"BASE64_TX\",\"m\":\"BASE64_MESSAGE\",\"opt\":\"BASE64_OPTIONAL_JSON\"}"]
```

**Generic Archive read:** the recommended request above, or exact lookup:

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","sig":"POST_SIGNATURE","flagged_ne":1,"limit":10}}
```

The [Archive handler, lines 353–395](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L353)
returns raw rows from `SELECT *`. A response has this general shape, with
additional schema fields:

```json
[
  {
    "sig": "POST_SIGNATURE",
    "created_at": 1700000000000,
    "updated_at": 1700000000001,
    "flagged": 0,
    "field1": "RedSquare",
    "field2": "AUTHOR_PUBLIC_KEY",
    "field4": "",
    "tx": "{\"t\":\"BASE64_TX\",\"m\":\"BASE64_MESSAGE\",\"opt\":\"BASE64_OPTIONAL_JSON\"}"
  }
]
```

These are structural examples, not actual rows or valid signature fixtures.
The browser
[storage adapter, lines 259–282](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L259)
parses each `res[i].tx` and fills `optional.updated_at` from the row only when
the optional value is absent. The native implementation should likewise
distinguish these two response variants and retain archive update cursors.

## Indexing, filtering and pagination

The pinned
[saveTweetToArchive, lines 1365–1401](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1365)
sets `field1: RedSquare`, `field4: parent_id || ""`, `field5: thread_id` and
update timestamp. Storage defaults
[field2 to sender and field3 to first recipient](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L149).
Replies override field3 with the interaction target when available.
Do not mistake field3 for a universal mention index.

[Archive load, lines 712–835](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L712)
defines these semantics:

- `created_earlier_than` uses strict `<` and orders by `created_at DESC`.
  `created_later_than` uses strict `>` and also defaults to descending order.
- `updated_later_than`/`updated_earlier_than` use strict update boundaries and
  order by `updated_at`. `ascending` switches sort to ascending **when the key
  is present**, even if its value is false. Omit it to get descending order.
- Limit defaults to 10 and is clamped to the range 10–100. Asking for 1 does
  not establish a single-row contract.
- `flagged_ne: 1` removes archive rows flagged exactly 1. Reviewed/approved
  `flagged: 2` rows are allowed. Schema fields, including `field4`, use equality.
- There is no offset, arbitrary SQL, composite timestamp/signature cursor or
  general less-than-ID parameter in this inspected read API. Do not invent
  unsupported query keys; they will not create the desired filter.

For initial tip and older pages, use creation boundaries. Polling for changes
uses update boundaries because likes, replies, edits and reposts can modify
existing posts. Current
[loader lines 313–324](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/loader.js#L313)
uses precisely this split. Keep the displayed order separate from the
archive-fetch order. A polled old post with new counts must not become a new
post merely because its update timestamp changed.

Advance older cursors from **all fetched rows**, including locally filtered
replies, moderated authors and malformed/unsupported feed items with usable
archive cursor metadata. Do not declare exhaustion merely because one batch
produces no visible cards. Empty batches and timeouts/errors are different.
Merge by transaction signature and keep paging state per selected backend.
These are native implementation recommendations; the pinned browser loader
[lines 563–597](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L563)
also advances timestamps for ignored Tweet results.

Strict timestamp paging can omit records sharing the page-boundary millisecond,
because there is no tie-break cursor. An overlapping boundary and deduplication
can handle ordinary small ties, but cannot guarantee complete retrieval when
a tie exceeds the 100-row limit. Similarly, simply taking the maximum update
timestamp from a full descending update page can skip intermediate updates.
Resolve bounded drain/overlap behavior in pagination work; do not claim the API
already guarantees lossless traversal under arbitrary ties or heavy activity.

The browser
[Tweet indexer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweets.js#L3)
checks module moderation and accepts create-tweet messages. Its
[timeline insertion](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweets.js#L241)
excludes nonempty `parent_id` and ephemeral cards. The native decoder should
require the expected signed `module: RedSquare` and `request: create tweet`,
instead of treating every arbitrary Archive row as a post. Personal mute/block
controls are additional to the selected backend's flagged filter. Replies may
still be used to build thread chains; the feed root filter does not remove
thread scope from the application.

## Tweet fields and signature boundaries

Pinned
[Tweet.parseFromTransaction, lines 180–238](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L180)
defines the following model:

| Native field | Source | Trust/meaning |
| --- | --- | --- |
| Post ID | Transaction signature | Original signed transaction identity; not `data.signature` |
| Author | `tx.from[0].publicKey` | Verify original signature against this key |
| Creation time | `tx.timestamp` | Signed millisecond timestamp; validate range locally |
| Text | `msg.data.text` | Original signed body, optionally superseded by a separately signed edit |
| Parent/thread | `msg.data.parent_id`, `thread_id` | Nonempty parent is reply; empty thread falls back to post signature |
| Images | `msg.data.images` | Array, with scalar-to-array compatibility in browser parser |
| Embedded card | `msg.data.embedded` | Signed outer snapshot; referenced original identity is not separately proven by the snapshot alone |
| Link | `msg.data.link` | Signed link; browser also analyses text/YouTube links |
| Counts/state | `optional.num_likes`, `num_replies`, `num_retweets`, `likers`, `retweeters`; staging `repliers` | Backend-derived, unsigned metadata |
| Update/edit time | `optional.updated_at`, `edit_ts`; archive row fallback | Unsigned update cursor metadata |
| Moderation | `optional.flagged`; archive flagged filter | Backend policy, not author-signed content |
| Preview data | `optional.link_properties` | Unsigned backend enrichment |

Web envelopes
[serialize/deserialize at transaction.ts:315–345](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315)
split binary transaction `t`, exact message bytes `m` and unsigned JSON `opt`,
each Base64 encoded. Reconstruct original message bytes before verification;
do not parse and reserialize them to compute the signature. The original
signature preimage is specified by
[Rust transaction.rs:813–851](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L813)
and the [protocol reference](NATIVE_SAITO_PROTOCOL.md#transaction-bytes-and-archive-representation).
Signature authenticity does not prove chain inclusion or consensus-valid funded
inputs; archive `block_id` and `block_hash` alone are metadata, not a proof.

Edits are delivered through `optional.update_tx`, itself another serialized web
transaction. The browser
[edit renderer, lines 468–485](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L468)
uses its `data.text`. The receiver
[lines 1871–1904](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1871)
requires the editor to match the original author and stores the signed edit
in optional metadata. Native rendering should independently verify the edit's
signature, module/request, target `data.tweet_id` and author before applying it;
do not mutate the original signed message or take arbitrary optional text as
an authenticated replacement.

## Reposts and quotes remain represented separately

An ordinary repost uses `request: retweet`, `data.signature` referring to the
original post, and is archived under `RedSquareRetweet`. Commentary and images
remain in that signed retweet payload in the inspected redesign. Its receiver
updates original-post metadata rather than creating a new create-tweet feed
item. Thus a `field1: RedSquare` feed does not by itself retrieve every signed
quote/commentary event. `data.embedded` create-tweet snapshots are a separate
representation; do not conflate them with `data.signature` on a retweet or
silently discard preserved quote fields.

See the
[complete quote trace](REDSQUARE_QUOTES.md), current
[retweet creation](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L973)
and [receipt](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1479).
The first feed milestone should keep source fields/transaction envelopes and
an extensible item model; complete quote/repost presentation belongs to its
accepted implementation work, not an inferred deferral of feature parity.

## Minimum author/profile hydration

Initial cards can render from signed author key immediately. The pinned
[author helper, Tweet lines 14–29](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L14)
uses keychain username and a public-key identicon; the handle is the raw public
key. [Keychain lines 590–601](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/keychain.ts#L590)
fall back to `Anon-` plus the first six public-key characters. A registered
identifier is separate from an independently editable display name.

Batch unique authors using Registry's read request:

```json
{"request":"registry query","data":{"request":"registry query","keys":["AUTHOR_PUBLIC_KEY"]}}
```

[Registry queryKeys, lines 396–417](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L396)
and [handler, lines 530–543](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L530)
return an object mapping public keys to identifier strings. Missing keys may be
absent. The server can query its Registry peer, or return partial results when
no parent is available, per
[lines 736–833](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L736).
These strings are a trusted Registry-service response, not a registration proof
carried in the post's signature. Missing Registry service must not block feed
rendering.

Public Profile hydration uses generic Archive
`{request: load, field1: Profile, field2: AUTHOR_PUBLIC_KEY, limit: 100}`.
[Profile lines 280–350](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L280)
sort transactions newest-first, select the newest `snapshot: 1/true` message,
and otherwise merge legacy updates oldest-to-newest. Verify each profile
transaction belongs to the requested author and uses `Profile/update profile`.
Profile data may contain `image`, `banner`, `description`, `archive` and other
fields; avatar content is `image`, not a mandatory `avatar` JSON field. The
schema and signed snapshot are at
[lines 360–376](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/profile/profile.js#L360).
Hydrate asynchronously and cache per backend/public key. Banner/bio loading
need not delay a feed card. Avoid depending on unknown fields or complete
legacy profile history beyond returned Archive coverage.

## Reading without chain sync and protected archives

The core
[application-message route](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L285)
requires an identified peer, not completed blockchain synchronization. Ordinary
Archive requests use the unsigned application wrapper described in the
protocol reference. Authenticate using the locally held wallet and maintain
ordered final-handshake/request frames; no Saito private key goes to a backend.
No type-4 propagation message or balance/funding RPC is required to load this
public feed.

Archive
[lines 377–384](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L377)
overwrite `REQUESTER` and `NOW` from peer/server state. Supplying a different
REQUESTER in JSON does not grant that user's access. New ordinary Red Square
rows default to no owner restriction through
[Archive save lines 433–455](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L433).
Protected rows are filtered when ownership enforcement applies, using
[access scripts/witnesses and evaluation, lines 913–1040](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L913).
Do not add vault access fields to a public feed read or claim generic archive
access retrieves private wallet/history data. A restricted row can disappear
from an otherwise nonempty SQL batch; this can complicate exhaustion inference.

Before closing the live-feed milestone, verify the chosen read route on both
allowed endpoints, authenticated request correlation, reply shapes, signature
verification with detached message bytes, malformed-row isolation and genuine
empty/error distinctions. Later pagination, hydration, thread, moderation,
quote and interaction tickets retain their accepted scope. This research
establishes static compatibility, not successful live reads.
