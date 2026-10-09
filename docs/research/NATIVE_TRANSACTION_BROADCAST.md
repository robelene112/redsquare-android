# Native signed transaction broadcast

Source-only protocol research, 2026-10-09, using project-local `research` and
`saito-dev` after reading maintained operations. Git root `/opt/saito` remains
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7);
node source is `/opt/saito/node`. Referenced transaction, wallet, routing,
verification, consensus, mempool, bridge and RedSquare sources match that Git
object. This is checkout evidence, not a served-bundle/runtime claim. No saved
options, private wallet, database, live balances, broadcasts or node changes.

## Two distinct send paths

| Operation | Exact WebSocket binary message | What it requests |
| --- | --- | --- |
| Actual post propagation | `04 || transaction.serialize_for_net()` | Consensus transaction verification, mempool admission and forwarding. No RPC index. |
| Archive/module service request | `0c || requestIndex:u32BE || transaction.serialize_for_net()` | Application module dispatch. A transaction signature, if supplied, does not turn it into propagation. |
| RPC response | `0d || sameIndex:u32BE || UTF8(JSON(response))`, or type `0e` API error | The particular application's response, not block inclusion. An application error may itself be a type-13 JSON object. |

The browser's `redsquare-post-tweet` event creates a RedSquare message, signs
the transaction and calls `propagateTransaction`; the core eventually emits
type 4. `sendRequest(..., signature_required=true)` signs its service envelope
but still uses type 12. Ordinary Archive loads do not request that signature.
Use neither a web `{t,m,opt}` envelope nor a type-12 Archive save as a substitute
for native post propagation.
[Browser sender](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L143),
[message types/serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/message.rs#L43),
[signed service sender](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/saito.ts#L430),
[existing Archive contract](NATIVE_ARCHIVE_WIRE_CHECK.md).

## Ordinary signed zero-fee post

The compatible raw UTF-8 message shape is
`{"module":"RedSquare","request":"create tweet","data":{"text":"..."}}`.
Replies add existing `parent_id`/`thread_id` fields; mentions can add zero-valued
recipient outputs. Preserve the exact signed bytes across retries, including
field order and Unicode encoding. Do not reserialize a parsed message before
verification or silently reuse the Archive request schema.
[Post constructor](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1101).

For zero amount/fee, the pinned browser wallet's default self-recipient path
produces **two zero Normal inputs and two zero Normal outputs** to the author.
Input indices and block coordinates are zero. Signing assigns output indices
**0 and 1**. Transaction type is Normal/0, replacements default 1, timestamp is
milliseconds. No UTXO lookup is required to construct this unfunded shape.
It differs from the existing minimal one-input/one-output feed fixtures and
from the unsigned Archive vector whose outputs retain index zero.
[Default amount/fee](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L78),
[zero slips](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L849),
[constructor](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1007).

The author-signature preimage is:

`timestamp:u64BE || inputSignatureSlips || outputSignatureSlips || replacements:u32BE || transactionType:u32BE || exactMessageBytes`

Each signature slip is `compressedPublicPoint:33 || amount:u64BE || index:u8 ||
slipType:u8`. BLAKE3 hashes that preimage once; secp256k1 ECDSA produces compact
64-byte `R || S`, with low S. Counts, signature, routing hops and slip block
coordinates are absent. Wire serialization instead has a 93-byte header:
four u32 counts/lengths (inputs, outputs, message bytes, hops), signature64,
timestamp8, replacements4, transaction type **u8**; followed by 59-byte inputs,
59-byte outputs, message bytes and 130-byte hops. All integer fields are big
endian. Do not introduce DER ECDSA or hash a digest again.
[Sign/network serializers](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L750),
[slip signing bytes](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L199),
[crypto details](NATIVE_SAITO_PROTOCOL.md).

## Routing hop

Match browser propagation: complete authentication, take the connected selected
peer's verified **compressed public point**, clone the signed transaction and
append the author-to-peer hop. The hop signs:

`rawCompactTransactionSignature:64 || nextPeerCompressedPoint:33`

using the routing sender's private key with the same once-hashed BLAKE3/ECDSA
primitive. Its wire bytes are `fromPoint:33 || toPoint:33 || hopSignature:64`.
No textual Base58/hex, hostname, previous-hop signature or extra transaction
hash belongs in that 97-byte signing preimage. Increment the network hop count.
The author signature remains unchanged. Forwarding nodes append their own
signed hop for each next peer; the validated path must be continuous and
cannot contain a self hop. Native should preserve the author transaction and
rebuild only its initial routing hop if a later authenticated connection has a
different peer identity.
[Propagation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/network.rs#L86),
[hop generation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/hop.rs#L42),
[path verification](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L1695).

An empty path passes the core's per-hop validator, but is not what the ordinary
browser propagation sends. That validator checks each hop's signature,
self-hop prohibition and continuity; it does not itself require the first hop
to be the author or the last hop to be the receiving node. Native must follow
the source-defined construction rather than treating those missing checks as
its routing contract. `WasmHop.sig`'s JS getter returns **Base58**, not compact
hex, even though its serialized signature is raw 64 bytes.
[WASM hop getter](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_hop.rs#L21).

## Acceptance, rejection and confirmation

Type 4 goes to the verification thread. Invalid transactions are dropped;
valid ones enter consensus/mempool processing. **There is no correlated type-13
acknowledgment or rejection response on this path.** Successful native socket
enqueue is local transport acceptance, not evidence of server validation,
relay, indexing or block inclusion. Core propagation ignores send errors; its
computed `peer_send_count` is a count of attempted targets, not remote receipts.
[Type-4 routing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L361),
[verification/drop](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/verification_thread.rs#L36),
[send/error handling](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/shared_methods.server.ts#L21).

Relevant rejections include malformed binary framing/counts, missing input or
output, invalid author signature, incompatible input authorizers, invalid hop
signatures/continuity, outputs exceeding inputs, forbidden Bound slips in
Normal transactions, and missing/spent positive-valued UTXOs. Zero-valued input
slips explicitly pass UTXO validation. Mempool deduplicates by signature and
rejects conflicting positive-value inputs. Keep the same author signature on
retry; re-signing with a new timestamp creates another post rather than retrying
the original. Absence from Archive is not a signed rejection and can reflect
delay, moderation, retention or backend availability.
[Transaction rules](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L1218),
[zero-input rule](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L223),
[mempool](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/mempool.rs#L139).

The RedSquare type-12 peer handler processes several interactions but does
**not** process `create tweet`. Its `onConfirmation(conf=0)` handles a new post
and saves it to Archive. A service callback `{}` for an interaction establishes
only that handler's response, not consensus confirmation. A matching verified
Archive transaction establishes available indexed content; unsigned row
`block_id`/optional metadata alone are not independently verified block proof.
Stronger inclusion evidence needs compatible validated block data and chain
context. A block-production-disabled node can forward to peers, although the
core delays that path while chain sync is in progress. No node production
change is required by this wire design.
[Peer handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1011),
[confirmation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L2117),
[relay/production conditions](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus_thread.rs#L145).

Fresh unfunded wallets can construct/sign this ordinary zero-fee post and
satisfy these zero-input rules. This does not establish current delivery on
either live backend or a fee-free SLA. The browser composer blocks text above
500 UTF-16 units when its pending SAITO balance is zero; this is sender UI
policy, not the general consensus validator. See
[content bounds](REDSQUARE_CONTENT_BOUNDS.md) and [first use](FIRST_USE.md).

## Independent reference strategy and executed check

Use the existing [safe runner](NATIVE_SAITO_PROTOCOL.md#reproducing-reference-calculations-safely):
network disabled, read-only host/root, empty environment, installed pinned
`saito-wasm` 0.3.2. Construct `WasmTransaction` and four `WasmSlip` objects
directly; never initialize Saito or call the wallet-backed async `sign()` or
`propagateTransaction()`. Assign output indices explicitly before calculating
the preimage, use pure `sign_buffer`, and compare manually assembled preimage
hash with `generate_hash_for_signature()`.

The WASM wrapper exposes routing-path getters but no hop constructor/setter.
For a pure routed reference, serialize the signed unpathed transaction, set the
u32 hop count at byte offset 12 to one, append the source-defined hop bytes,
then deserialize and **reserialize with WASM**. This verifies genuine WASM wire
compatibility without calling its stateful network sender. Prefix `04` directly
from the message serializer source. Independently verify author and hop
signatures with Elliptic over their BLAKE3 digests; mutate each preimage to
check failures. A later permanent vector should retain exact raw message,
preimages, complete network/frame bytes, public test identities and provenance.
[WASM transaction API](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_transaction.rs#L21).

The inspected WASM binary SHA-256 is
`f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`;
the independent verifier is Elliptic 6.6.1.
Executed that pure check during this research with published test scalars 1
(author) and 2 (peer), timestamp `1700000000000`, replacements 1, Normal type,
and exact message:

```json
{"module":"RedSquare","request":"create tweet","data":{"text":"Native Red Square reference post"}}
```

Author is `jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR`; peer is
`pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU`. Message is 98 bytes,
author preimage 286 bytes, hop preimage 97 bytes, unpathed transaction 427
bytes, routed transaction 557 bytes and type-4 frame **558 bytes**. Frame
BLAKE3: `4c32dc488187ce36b57ae7bda610ed528c96d1649cc4d2dccbe26deff4d36028`.
WASM deserialize/reserialize reproduced the routed bytes exactly; author hash
remained unchanged, both WASM/Elliptic signatures verified and appended-byte
mutations failed. This is serializer/signature evidence, **not** execution of
core consensus validation, server acceptance, chain inclusion or real-wallet
posting. No additional fixture file, app/test change or commit was made.
