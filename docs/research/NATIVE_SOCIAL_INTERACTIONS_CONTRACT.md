# Native likes and ordinary reposts

Read-only research for [ticket #12](https://github.com/robelene112/redsquare-android/issues/12), 2026-10-10. Only this note was written. No node access, private data, application initialization, build, transaction publication or service change occurred.

## Authority

Compatibility baseline: official `9a4ca7e9442eb5e872168ced361ce741d7d139d7` (A), matching the previously inspected owner's public RedSquare source. Existing `.scratch/saito-reference/redsquare-9a4ca7e.js` and freshly retrieved official A have SHA-256 `e2cf08d95ed0cebc4b4e1106134ccc13c83b2a5814f1b0e9b31579a4c14a7ca8`. Also inspected the previously identified official production-branch revision `214504ecece76d1cd20a245fe4468eccbfca2951` (B). This is a pinned comparison, not a claim about today's branch head or saito.io's deployed SHA. [Version provenance](SAITO_VERSION_PROVENANCE.md) records both inspected served bundles and their limitations.

Both pins have the contracts below. Compare [A constructors](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1148) with [B constructors](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L944), and [A receivers](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1588) with [B receivers](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/redsquare/redsquare.js#L1360). Source inspection does not prove either server accepted an Android interaction.

## Signed operation and recipients

Like: `{module:"RedSquare",request:"like tweet",data:{signature:targetId}}`.
Ordinary repost: `{module:"RedSquare",request:"retweet",data:{signature:targetId}}`.
`targetId` is the original post transaction signature. Omit commentary/images for ordinary reposts; quotes belong to #13. The web's empty repost composer produces exactly this data shape. [Composer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L577).

Use the existing compatible zero-payment signing/serialization path. Retain the self output first, then the original post's author, then its `tx.to` keys, deduplicated in encounter order and excluding the current signer when adding other recipients. Likes and reposts both use this inheritance. The first non-signer output identifies the target recipient. Self-interactions can instead fall through to storage's self-output default. [Like UI](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/manager.js#L1830), [repost recipients](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/ui/overlays/compose.js#L594), [target extraction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1330).

## Archive and reconciliation

| Field | Like | Ordinary repost |
| --- | --- | --- |
| field1 | RedSquareLike | RedSquareRetweet |
| field2 | verified transaction signer | verified transaction signer |
| field3 | first non-signer recipient, otherwise storage fallback | same |
| field4 | target original signature | target original signature |
| sig | interaction transaction signature | interaction transaction signature |

RedSquare sets field1/3/4 and `preserve:0`; storage supplies field2 from `tx.from[0]`. Archives can expire. [Like/repost saving](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1425), [storage defaults](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L135).

For exact availability, request Archive `load` with `{field1:kind,sig:originalInteractionId}` on the originating backend, then independently verify signature, signer, request and target. Presence proves that backend serves the signed interaction, not chain confirmation or permanent retention. A socket enqueue, empty callback or changed aggregate counter is insufficient.

Own actions are discoverable with `{field1:kind,field2:walletKey,field4:targetId,limit:...}`; broad own history omits field4 and uses bounded timestamp paging. This repost query is an inference from archive fields, not a dedicated observed web profile tab. Web profile Likes already queries field1/2, extracts target signatures and separately fetches `{field1:"RedSquare",sig:targetId,flagged_ne:1}`. Notifications use field3 for incoming interactions. [Profile Likes](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/manager.js#L1092), [notifications](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L883).

## Counts, repeat actions and failure

A like increments `optional.num_likes` for each new interaction signature. Another signed like from the same wallet also increments; `optional.likers` contains each wallet once. Duplicate signature delivery does not increment again. There is no observed unlike discriminator or toggle in either inspected constructor/receiver set or like handler. Preserve original signed candidates for retries; do not silently create another like.

Repost archival deduplicates signatures, but counter updates instead require interaction timestamp greater than the original's current `updated_at`. Newer same-wallet reposts can increment again while `retweeters` remains unique. Older reposts may be archived without incrementing; unrelated recent activity can suppress their increment. Do not derive global counts from unique-wallet arrays or manufacture a local increment. [Like counting](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1612), [repost counting](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1708).

The served `optional` counters/identity arrays are unsigned metadata, separate from the signed message. Display them as backend observations, bounded/validated appropriately; they are not consensus-authenticated totals. [Transaction envelope](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315).

## Controlled validation and remaining proof

Extend existing independent `build-support/references/signed-reference.cjs` and posting vectors for both requests: pinned WASM serialization/signature plus independent Elliptic verification, public unsafe synthetic keys only. Existing `profile-like-reference.cjs` already supplies signed and tampered likes. Exercise real Android actions, feed/thread/profile coherence, original-candidate retry, uncertain disconnect/exact reconciliation, backend/wallet isolation and process recovery against controlled peers. Test replay, repeated-wallet actions and timestamp-suppressed repost counts using the observed receiver rules, with server counts unchanged until a served update.

No source-level blocker found. Actual emitted native bytes, selected-backend archive availability and cross-client rendered results remain implementation acceptance work. No current official deployment revision, permanent history or live delivery is established here.
