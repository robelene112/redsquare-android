# Gateway chain wire, 2026-10-17

Bounded functional protocol research for#42, extending the
[observer note](SOCIAL_PUSH_GATEWAY_OBSERVER_2026_10_15.md) and reading the
[retained October15 probe](../evidence/ticket32/public-observer-oct15/probe.cjs).
Only this note was written. No peer connections, node/private-file access,
implementation, tests, Git operations or deployment occurred.

Official source pins A=`9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
B=`214504ecece76d1cd20a245fe4468eccbfca2951` were read directly. The five inspected
files `network/msg/{blockchain,block,message}.rs`, `network/sync.rs` and
`routing_thread.rs` match byte for byte between A/B. No current-main comparison
or new deployment-revision attestation is claimed. Links below select A.

## Exact framing

One binary message starts with a one-byte type:21 request,22 response. Remaining
offsets below are **body-relative**, excluding that byte; frame offsets add1.
There is no request/callback ID for these types.
[Message serializer/parser](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/message.rs#L42-L81).

All integers are unsigned, big-endian. Request body:

| Offset | Bytes | Field |
| --- | --- | --- |
|0|8|known height|
|8|32|known hash|
|40|32|fork ID|
|72|1|sync mode: Full0, SPV1, legacy Ghost2|
|73|33|compressed public key|
|106|4|key count|
|110|33×count|additional public keys|

Keyless request: body110/frame111bytes. Last4bytes are zero key count, not
padding. Bootstrap uses zero height/hash/fork, SPV1 and the gateway's public key.
Continuation uses its durably processed cursor/hash, returned fork ID and Full0.
The parser requires supported mode and exact declared length.
[Request schema](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/blockchain.rs#L108-L178).

Response body:

| Offset | Bytes | Field |
| --- | --- | --- |
|0|8|tip height|
|8|32|tip hash|
|40|32|fork ID|
|72|8|ancestor height|
|80|32|ancestor hash|
|112|8|earliest height|
|120|32|earliest hash|
|152|8|latest height|
|160|32|latest hash|
|192|4|reference count|
|196|53×count|references|

Require count≤128 and exact body length`196+53×count`; maximum body6980/frame6981.
The source comment's192-byte header excludes the count.
[Response schema](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/blockchain.rs#L180-L260).

Each reference: height at0/u64, hash at8/32bytes, timestamp at40/u64,
transactions at48/u32, golden-ticket flag at52/u8. Serializer emits0/1;
Rust parser treats **any nonzero flag as true**. The retained probe's≤1 check is
a stricter observer acceptance rule, not an existing Rust parser guarantee.
[Reference schema](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/block.rs#L54-L95).

## Producer semantics observed in the pin

Known nonzero height/hash on the longest chain becomes the ancestor; otherwise
fork-ID lookup supplies one. Zero-cursor Full starts at retained genesis;
zero-cursor SPV at`max(genesis,tip-9)`. Known ancestry starts at
`max(genesis,ancestor+1)`. Missing ancestry falls back to that recent window.
Empty keylists fall back to the requested public key.

The loop gathers at most128 references, **skipping heights without a longest-chain
hash or available block object**. Full sets transactions0; SPV sets0/1 for key
relevance. Neither is the full transaction count or permission to skip content.
Nonempty bounds equal first/last references. Empty bounds **both equal tip/hash**,
even when missing retained block objects caused emptiness.
[Producer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/sync.rs#L418-L576).

Fork-ID ancestry compares selected two-byte hash fragments at weighted historical
heights, returning0 when no match is found. It is a synchronization hint, not
a full-hash ancestry proof. Check the response's full ancestor hash against a
retained local journal before rewinding.
[Ancestor lookup](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/blockchain.rs#L1156-L1313).

Routing computes `has_more = payload_latest_height < tip_height`; it does not
establish complete history. Its sync-possible signal requires nonzero ancestor
height/hash. Type6 announcements trigger work; type23's request handler is empty.
[Routing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L160-L219).

## Concrete gateway constraints and limits

These are proposed observer rules, not independent consensus validation:

- Bound WebSocket message size before allocation, use one in-flight chain request
  per authenticated connection and reject truncated/trailing/count-invalid bodies.
  Kotlin must represent unsigned fields explicitly or reject values beyond its
  supported nonnegative range before arithmetic; do not wrap a u64 into a height.
- Require nonempty bounds to match first/last references, strictly consecutive
  heights, references≤tip, and matching hash when last height equals tip. A source
  gap is a coverage gap, even if the wire parser would accept it. Do not advance
  through it. Timestamp and relevance fields do not establish chain membership.
- Bootstrap deliberately omits older history. Record its coverage start. For
  continuation require the known ancestor, then its exact successor; a retained
  genesis floor can jump ahead, which is retention loss requiring an explicit gap.
  A different known journal ancestor allows bounded rewind and orphan-candidate
  withdrawal. Unknown ancestry requires reconciliation/gap handling, not silently
  treating the recent fallback as uninterrupted catch-up.
- An empty response is expected when already at an unchanged tip, and an empty
  chain can have zero tip/hash/bounds. Otherwise empty-with-newer/different-tip is
  unavailable coverage, not successful catch-up. Keep the last processed cursor.
- Fetch **every** accepted reference through the approved backend's full-block
  HTTP route. Verify fetched height/hash, creator/Merkle content and previous-hash
  continuity before durable processing. Ref lists contain no previous hash.
  Process candidates and their durable outcomes before committing the cursor.
  New tip polling/reconnect reconciliation remains necessary after announcements.

This proves only inspected wire/source behavior. The old probe observed one
recent SPV response/full-block read per backend. It did not establish Full
continuation, forks, retained gaps or complete history. Header/Merkle and signed
social verification do not independently prove Saito consensus or irreversible
finality. Selected-backend Archive visibility remains a separate event gate.
