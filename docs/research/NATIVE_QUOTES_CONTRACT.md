# Native quote contract

Read-only primary-source inspection, 2026-10-10, for #13. H is archived
`saito-lite-rust@7200a9886f3dd02f66e8d6c146960e2a5402929f`; A/B are official
`saito@9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
`214504ecece76d1cd20a245fe4468eccbfca2951`. Public pinned files were fetched;
A RedSquare SHA-256 matches the existing source reference
`e2cf08d95ed0cebc4b4e1106134ccc13c83b2a5814f1b0e9b31579a4c14a7ca8`.
These pins do not identify either server's current deployment. No node access,
application initialization, production writes or cross-client runtime test.

## Representations and indexing

- H quote composition sends one `RedSquare` / `create tweet` with its own signed
  commentary/images, `data.signature` naming the original and
  `data.retweet_tx` containing the original's serialized web envelope. A plain
  repost instead sends `retweet`. [H post:305–340](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/post.js#L305).
  H reconstructs that original and renders both outer media and nested post;
  quotes are ordinary RedSquare archive rows.
  [H Tweet:151–160](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L151),
  [H rendering:464–469](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/lib/tweet.js#L464),
  [H save:1981–2009](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/mods/redsquare/redsquare.js#L1981).
- A/B current composer sends `retweet` with `{signature,text?,images?}` for both
  ordinary reposts and commentary quotes.
  [A data:577–591](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L577),
  [B send:657–677](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/ui/overlays/compose.js#L657).
  Archive identity is the interaction signature; `field1=RedSquareRetweet`,
  `field2=signer` through storage defaults, `field3=first non-signer recipient`,
  `field4=original signature`. The receiver archives before original lookup,
  then updates the original's served counters, without creating a quote post.
  [A save:1455–1491](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1455),
  [A storage:149–164](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L149),
  [B receive:1479–1580](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1479).
- A/B `create tweet` rendering recognizes `data.embedded`: an object with
  original signature, publicKey, created_at, text and images; it is a presentation
  snapshot, not an independently signed transaction. Rendering allows one nested
  level. [A normalize:240–268](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L240),
  [B template:120–127](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/tweet.template.js#L120).
  The official mock feed itself includes an embedded quote.
  [A fixture:3057–3071](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L3057).

## Recommended outgoing strategy, inferred from source

Send **one** signed `create tweet`, with commentary and both `retweet_tx` and
`embedded` derived from the same verified original, plus `signature=original.id`.
Leave parent/thread fields absent for a standalone quote. A/B copy arbitrary
create-tweet fields; their ordinary archive saves index `field4=''` and
`field5=outer.id`. This permits existing feed, author-profile and quote-thread
queries, while H displays `retweet_tx` and A/B display `embedded`.
[B create:897–941](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L897),
[A save:1365–1401](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1365).

Use self output followed by original author and distinct original recipients,
excluding self duplicates, consistent with current composition/constructor.
[A recipients:594–612](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L594),
[A constructor:1132–1143](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1132).
The historical web envelope is the JSON string `{t,m,opt}`; preserve original
signed transaction/message bytes. Optional metadata is not original-author
authentication. [H serialization:322–342](https://github.com/SaitoTech/saito-lite-rust/blob/7200a9886f3dd02f66e8d6c146960e2a5402929f/lib/saito/transaction.ts#L322).

Exact reconciliation queries `field1=RedSquare,sig=outer.id`; validate returned
signature, signer, request and original reference. Persist/retry the original
candidate, never send a second retweet to simulate success. A/B standalone
create-tweet receiver does not increment original repost counts; show served
counts only. [B receive:1301 onward](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1301).

## Native reading and limits

Recognize signed create-tweet `retweet_tx`, current `embedded`, and signed
retweet commentary/images. Verify nested `retweet_tx` separately and match its
signature to the declared target; reject contradictory references. An embedded
object alone is the quoting author's assertion. Prefer verified cached/original
lookup content and never attribute an unverified snapshot to the original signer.

For current commentary interactions, query RedSquareRetweet separately, filter
out ordinary reposts, merge/deduplicate by interaction ID and preserve that ID,
author and timestamp. Author profiles add `field2=author`; direct quote lookup
uses exact retweet signature. Normal feed/profile/thread queries select
RedSquare, so they cannot discover these interactions automatically.
[B feed:273–325](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/loader.js#L273),
[A profile:1092–1098](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/manager.js#L1092).
Archive supports these exact schema filters without an adapter.
[A query:822–827](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L822).

Bound pages, bytes, nested depth and original lookups. One target query
`field1=RedSquare,sig=target,flagged_ne=1` per distinct unresolved original,
with finite timeout/concurrency, suffices; distinguish unavailable from failed
lookup. Keep commentary visible with an original-unavailable placeholder and
Retry on failure; do not recurse through quote chains indefinitely.
[B target/status:2440–2474](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L2440).

No backend change is demonstrated necessary for this strategy. Existing current
web commentary-retweet display remains limited: notifications call it a quote
but draw only the original. Fixing that web display requires separately
authorized web work. Native publication/display and the dual-field outgoing
proposal still require controlled writes, signature proof and rendering tests;
this note does not establish live delivery or visual interoperability.
[B notification:62–71 and137–150](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/lib/notification.js#L62).
