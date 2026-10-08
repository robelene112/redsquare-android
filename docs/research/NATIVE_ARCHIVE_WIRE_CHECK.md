# Archive wire and signed-post checks

Static recheck for ticket #4, 2026-10-08. Read only source through the existing
network-disabled Docker runner with read-only host/root mounts and empty
environment. On current netcup host `v2202609429510531338`, the Git repository
root is `/opt/saito`, not `/opt/saito/node`. Running Git from the node directory
reports top-level `/opt/saito` and prefix `node/`; it has no separate `.git`.
Both paths resolve checkout HEAD to
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7).
Compared the inspected core slip/transaction/wallet, JS bridge, network API,
storage, module dispatcher, Archive and web-transaction files to that Git object;
all compared files are byte-identical. No wallet/options/database, live request,
application initialization or service mutation was involved.

This agrees with [the earlier version provenance](SAITO_VERSION_PROVENANCE.md),
which identifies `9a4ca7e` as the owner's deployed source checkout and
`214504ecece76d1cd20a245fe4468eccbfca2951` as the inspected **official upstream
`prod` branch**, not the owner's checkout. The `prod` branch label must not be
read as proof of a particular server's deployment. This is a fresh source
observation, not evidence that a served JavaScript bundle was rebuilt or that
all currently loaded runtime code matches HEAD. This check fetched no public
bundle and inspected no live process; retain the historical bundle hashes and
their qualifications in that provenance note.

## Exact unsigned Archive request

The [storage loader](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L251)
uses request `archive` and data `{request: "load", ...criteria}`, then
`sendRequestAsTransaction` without a signature-required argument. The
[JS network bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/saito.ts#L430)
creates a zero-amount, zero-fee transaction addressed to its own public key,
packs UTF-8 JSON and signs only if `signature_required` is truthy. Example
message, with an illustrative initial timestamp boundary:

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","field4":"","flagged_ne":1,"limit":20,"created_earlier_than":1700000000000}}
```

The [callback sender](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/saito.ts#L405)
serializes the complete binary transaction, not a web `{t,m,opt}` envelope.
The [WASM API](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_network_api.rs#L17),
[API serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/api_message.rs#L9)
and [message serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/message.rs#L43)
establish this single binary WebSocket message:

`0c | request-index:u32 big endian | network-transaction bytes`

There is no extra transaction length, JSON length, Base64 or inner type-4
propagation prefix. The transaction's own header contains message byte length.
It is 93 bytes: counts u32 x4, signature 64, timestamp u64, replacements u32,
type u8; then input slips, output slips, raw message, routing hops. Slips are
59 bytes and hops 130. See the actual
[network serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L750).
The unsigned signature is 64 zero bytes, replacements default 1 and type is
Normal/0. Timestamp is milliseconds, assigned by the JS wallet wrapper.

**Exact web constructor count:**
[generate_slips(0)](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L777)
returns one zero input and one zero change output. Then
[create_transaction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1040)
adds its own initial zero input before those selected inputs and adds the
recipient output before change. Thus this request has **two inputs and two
outputs**, all zero-valued Normal slips to the local key. All indices remain
zero because this request is not signed. A minimal one-input/one-output
request can be structurally deserialized, but is not byte-identical to that web
constructor. Its live acceptance would be separate evidence.

The independent [unsigned Archive vector](NATIVE_ARCHIVE_REFERENCE.json) uses
that exact example JSON (143 UTF-8 bytes), the scalar-one public key and fixed
timestamp `1700000000000`. A pure WASM constructor received two explicit zero
inputs and two zero outputs with every index/type/block coordinate zero,
replacements 1, Normal type 0, zero signature and no hops. Actual WASM
serialization produced **472 bytes**; deserializing and reserializing reproduced
them exactly. Assertions independently checked header counts/lengths and every
zero-valued slip field.

The source-verified prefix `0c00000001` was prepended in the research runner,
producing **477 framed RPC bytes**, BLAKE3
`3b8814629e78cb87cec7d1297b02ca3d179eaceadd5be84134ec85c4a9fdc41d`.
The JSON records full `networkHex` and `framedRpcHex`. The transaction bytes
come from WASM, not native Kotlin. The five-byte API prefix is direct
source-verified construction; the stateful WASM network sender was never
invoked. This fixture is unsigned deliberately and proves byte layout, not
authenticated peer delivery or live Archive acceptance.

The [routing gate](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L285)
requires an identified peer; the
[module dispatcher](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/modules.ts#L111)
deserializes and dispatches this request without consensus transaction
validation. Archive
[overwrites REQUESTER/NOW](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L353)
from peer/server identity. Module moderation can still affect availability.

Responses are `0d | same-index:u32 | raw UTF-8 JSON` (success), or analogous
`0e` for protocol API errors. The
[server callback](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/shared_methods.server.ts#L227)
JSON-encodes returned objects. Application errors can also arrive as a type-13
object such as `{ "err": "no response" }` from the module dispatcher. These
are errors, not an empty feed. A successful empty row array is `[]`; nonempty
results are row objects whose `tx` is the web-envelope JSON string.

## Signature slips: preserve values, do not normalize on verification

The pinned [input/output signature serializers](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L199)
are identical: public key 33 + amount u64 8 + slip index u8 + slip type u8.
They omit block ID and transaction ordinal. **There is no special Normal,
ATR, Bound, zero-amount or input-versus-output normalization in these methods.**
Do not filter zero slips, reset input indices, replace nonzero slip types with
Normal, sort slips or add block coordinates to the signature preimage.

The [transaction preimage](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L813)
is timestamp u64 + all input signature slips in order + all output signature
slips in order + replacements u32 + transaction type **u32** + exact message
bytes. Counts, routing hops and signature itself are absent. Signed amounts
can be nonzero; fee-bearing inputs can retain MinerOutput, RouterOutput,
BlockStake or ATR slip types. Their bytes still belong in that same preimage.

The only relevant index assignment occurs during
[signing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L841):
outputs become indices 0,1,... before hashing. Inputs keep their existing UTXO
indices. Verification reconstructs already-signed stored values; it does not
call signing or reassign indices. Ordinary signed two-output posts therefore
have indices 0 and 1, unlike the unsigned request described above.

The [web serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315)
puts message-free binary transaction in `t`, original detached message bytes
in `m` and unsigned optional metadata in `opt`. Restore `m` for verification;
verify raw bytes before JSON interpretation. Block IDs/ordinals on outputs can
change during confirmation without entering the signature preimage. Routing
hops likewise do not change original author signing.

## Other real-post compatibility boundaries

- The normal Red Square creator uses the wallet's Normal transaction constructor.
  Do not require every **slip** to be Normal just because transaction type is 0.
  The Rust deserializer bounds input/output counts to 255 each; a header's u32
  storage does not mean the core accepts arbitrary counts.
- The pinned [Tweet parser](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L180)
  treats missing or explicit-null `text`/`parent_id` as empty and null/empty
  `thread_id` as the signature. The
  [creator](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1101)
  preserves null values and stringifies non-null supplied text/IDs. Rejecting
  explicit null in the native decoder can discard otherwise compatible posts.
  Such field interpretation must not rewrite bytes used for verification.
- Web display author comes from `from[0]`; consensus authorization is more
  general and can skip Bound slips or use P2SH scripts. The ordinary wallet
  constructor prepends its own Normal sender slip, so first-input public-key
  verification fits ordinary posts. Do not claim this implements all P2SH/NFT
  consensus authorization or verifies funded validity.
- The JS unpacker accepts Normal and Bound transaction message types. Normal
  creation is the ordinary Red Square path; source alone does not establish
  that every historical/custom module-produced Archive post is Normal.

No special slip normalization explains a signature failure in an ordinary
Normal post under this pinned protocol. Investigate exact detached bytes,
all slip indices/types/counts, header replacements/type width and runtime
version before relaxing signature verification. This static check did not
inspect real posts or establish either backend's live read availability.

## Independent null-field regression fixture

Generated [NATIVE_FEED_NULL_REFERENCE.json](NATIVE_FEED_NULL_REFERENCE.json)
using the same safe pure-WASM runner and publicly known scalar 1 as
[the original fixture](NATIVE_FEED_REFERENCE.md), without initializing a wallet
or using native Kotlin signing. The fixed timestamp, one-input/one-output
Normal slips and unsigned synthetic row/optional metadata remain the same;
the exact signed 102-byte UTF-8 message is now:

```json
{"module":"RedSquare","request":"create tweet","data":{"text":null,"parent_id":null,"thread_id":null}}
```

The manually constructed 204-byte signature preimage matches the pinned WASM
transaction hash. `wasm.sign_buffer` produced the fixture signature; both WASM
verification and the independent Elliptic 6.6.1 secp256k1 implementation verify
it against BLAKE3
`409791782023180aadfffd7d05bc54c1ed5b3670ac4050e6e6a7197a53691b72`.
An appended-byte mutation fails. Actual WASM network serialization is 313
bytes; deserialization and detached-message web reconstruction preserve the
verified hash. All assertions passed, using WASM binary SHA-256
`f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`.

Expected display interpretation is empty text, empty parent ID and thread ID
equal to the signature, per the cited Tweet parser. These display defaults
never replace the nulls in signed bytes. As with the original reference, this
fixture demonstrates parsing/signature compatibility, not transaction
acceptance, chain inclusion or complete consensus validation. Its minimal
signed-post slip construction is separate from the exact two-input/two-output
unsigned Archive-request constructor established above.
