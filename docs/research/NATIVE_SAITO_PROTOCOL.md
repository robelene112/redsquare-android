# Native Saito wallet and protocol reference

Research for the first native Kotlin implementation ticket, 2026-10-08.
Applied the project-local research skill and Saito routing/operations guide.
Only static sources and public primary sources were inspected. Reference
calculations used published synthetic keys in a network-disabled, read-only
Docker runner, without Saito initialization. No production wallet, saved
options, environment, database or live transaction was accessed. Only this
research file was written.

## Authority and immediate implementation findings

Use the installed `saito-js@0.3.2` / `saito-wasm@0.3.2` and official commit
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7)
as the independent reference. The maintained pin is
`/home/david/snake-stickerbook/scripts/saito-runtime.json:2-7`.
Versions were also read from the installed dependency package manifests.
See [version provenance](SAITO_VERSION_PROVENANCE.md) for deployment context.

The official `crypto.rs`, `slip.rs`, `transaction.rs`, `msg/handshake.rs`,
`msg/message.rs` and WASM `saitowasm.rs` were byte-identical at that pin, current
`prod` [`214504ecece76d1cd20a245fe4468eccbfca2951`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951)
and inspected `staging`
[`e9f3635fa56450008774f2d8d76731121268fe7a`](https://github.com/SaitoTech/saito/commit/e9f3635fa56450008774f2d8d76731121268fe7a).
This establishes agreement for these primitives, not saito.io's exact deployed
Git revision or every module protocol.

The native construction expected to match the reference is UTF-8 bytes,
BLAKE3-256 once, then deterministic secp256k1 ECDSA with RFC6979 HMAC-SHA256
nonces and low-S normalization. Return compact `r || s`, 32 bytes each,
hex encoded. Do not use a generic `SHA256withECDSA` signing operation, a signed
message prefix, DER output, or a recovery byte at this boundary. Sources and
independent vectors follow.

## Keys and message signatures

- Private keys are 32-byte secp256k1 scalars represented as 64 hex characters.
  Public addresses are raw Base58 of the compressed 33-byte point, including
  the `02`/`03` compression prefix. This is not Base58Check, WIF or a hashed
  account address. The bridge
  [derives the compressed point and Base58-encodes it](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L1021);
  the [core derivation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/crypto.rs#L64)
  uses `PublicKey::serialize()`. The wrapper's
  [Base58 helpers](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/crypto.ts#L215)
  encode/decode the raw bytes.
- Native key creation should mirror the
  [WASM generator's secure random key generation and 44-character retry](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L1360).
  Do not reject imports solely for having 45 Base58 characters: derivation from
  an existing private key does not apply that retry, and the published Rust
  fixture below has a valid 45-character public address. Validate decoded
  length, compressed point validity and the public/private pairing instead.
- [signMessage](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/crypto.ts#L34)
  passes UTF-8 bytes unchanged to `signBuffer`. The
  [WASM sign_buffer bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L793)
  calls core `sign` and returns hex. Core
  [BLAKE3 hashing and signing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/crypto.rs#L106)
  hashes the supplied bytes exactly once and serializes the ECDSA signature
  compactly. [Verification](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L805)
  performs the same hash and accepts compact signatures.
- The core pins
  [rust-secp256k1 0.30.0](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/Cargo.toml#L16).
  Its [signing implementation selects RFC6979](https://github.com/rust-bitcoin/rust-secp256k1/blob/secp256k1-0.30.0/src/ecdsa/mod.rs#L264).
  The vendored implementation uses
  [HMAC-SHA256 nonce generation and rejects high-S verification](https://github.com/rust-bitcoin/rust-secp256k1/blob/secp256k1-0.30.0/secp256k1-sys/depend/secp256k1/src/secp256k1.c#L409)
  and [normalizes S while signing](https://github.com/rust-bitcoin/rust-secp256k1/blob/secp256k1-0.30.0/secp256k1-sys/depend/secp256k1/src/ecdsa_impl.h#L293).
  The research runner independently confirmed repeated WASM signatures match,
  each fixture's S is low, replacing S with `n-S` fails WASM verification, and
  the installed independent Node libsecp256k1 signs the BLAKE3 digest to the
  identical compact signature.

For the proposed Kotlin Bouncy Castle implementation, use `Blake3Digest` with
32-byte output and the lightweight `ECDSASigner` with
`HMacDSAKCalculator(SHA256Digest())` on the resulting digest. Normalize S to
`min(s, n-s)` and encode unsigned fixed-width 32-byte R and S. This is an
implementation inference from the referenced algorithm and observed vectors;
the Kotlin implementation still needs to pass these independent vectors.
Verification should explicitly reject high-S to match libsecp256k1 semantics.

The curve order is
`fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141`.
Reject zero or a private scalar greater than or equal to that order before
attempting derivation. WASM's setters perform basic string decoding, not
public/private pair validation, and derivation invokes `SecretKey::from_slice`.
See [setters](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_wallet.rs#L420)
and [derivation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/crypto.rs#L65).

## Independent wallet/signature vectors

These values were generated and verified with the installed pinned WASM, not
the native implementation. The scalar is explicitly public synthetic material,
never a user wallet and never suitable for funds:

```json
{
  "privateKey": "0000000000000000000000000000000000000000000000000000000000000001",
  "publicKey": "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR",
  "compressedPublicKeyHex": "0279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798",
  "vectors": [
    {
      "message": "",
      "utf8Hex": "",
      "blake3Hex": "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262",
      "compactSignatureHex": "8008843d4e0261c035aea14623aba44be40389f0492f3979f8a4b5ac1742a0337cacc2c608402a297695beb98398f52f406c6ed1247f713d97fe8b19975a16e4"
    },
    {
      "message": "Red Square Android synthetic reference",
      "utf8Hex": "5265642053717561726520416e64726f69642073796e746865746963207265666572656e6365",
      "blake3Hex": "3630ed1a38acef5d07f0459cd8dc4939798e3c625605790a2e7cb746d8e964b6",
      "compactSignatureHex": "25faa62d6039f9ad39f9179d3757f6982ae148888bad2b5cefd706fbdffc060019b9c7c51cd49ef41dd5d6798f44937e04bc93055dd7e215b830193cb8e61cb6"
    },
    {
      "message": "Saito 🔴 café\n",
      "utf8Hex": "536169746f20f09f94b420636166c3a90a",
      "blake3Hex": "639d9053579e271f16248fd35202a4dba73201d61617c82f21fd87ef6b97c7c8",
      "compactSignatureHex": "ba0bb729f9579ce14f963a496af1ab2bcb6ccf19199c8d7d83ca78c4755c4a1e7190d9076c252804833862cd9fb746552e53c22fd3707f3bf660741bea798717"
    }
  ]
}
```

Each signature verified; appending byte `01` to each message caused verification
to fail. The Unicode fixture contains a literal final newline and no Unicode
normalization step.

An additional independent
[published Rust fixture](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/crypto.rs#L218)
was reproduced exactly:

```json
{
  "privateKey": "4a16ffa08e5fc440772ee962c1d730041f12c7008a6e5c704d13dfd3d1905e0d",
  "publicKey": "28Mh8nEhxymH9bFMhSKU51pnSQAnqURuPYkXTUqY2ueDM",
  "messageHex": "5a16ffa08e5fc440772ee962c1d730041f12c7008a6e5c704d13dfd3d1906e0d",
  "blake3Hex": "f8b1f22222bdbd2e0bce06707a51f5fffa0753b11483c330e3bfddaf5bacabd6",
  "compactSignatureHex": "11c0e19856726c42c8ac3ec8e469057f5f8a882f7206377525db00899835b03f6ec3010d19534a5703dd9b1004b4f0e31d19582cdd5aec794541d0d0f339db7c"
}
```

That message is decoded hex bytes, not the UTF-8 text of the hex string.

## Wallet JSON and preserving imported data

The browser backup format is an options object containing `wallet`, not a
standalone serialized WASM wallet. The
[browser bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/shared_methods.browser.ts#L353)
writes camelCase `wallet.publicKey`, `wallet.privateKey` and `wallet.balance`.
The app's
[saveWallet](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L770)
adds `preferred_crypto`, `preferred_txs`, numeric `version`, string
`default_fee` and `slips`. Its
[declared wallet version is 5.677](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L35),
distinct from dependency version `0.3.2` and the core binary wallet version.
`1 SAITO = 100,000,000 nolan` is defined in
[core constants](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/defs.rs#L25).

[exportWallet](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1244)
sets `wallet.ts` and serializes all `app.options` except `games`, preserving keys,
module preferences and other option fields. Browser pending transactions are
stored separately in LocalForage by saveWallet, so this export is not every
piece of browser storage. See [Chat/history limitations](REDSQUARE_CHAT.md).

[Full JSON import](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1386)
installs `wobj.wallet.publicKey/privateKey`, replaces options with `wobj`, updates
the version and clears inputs/outputs/spends/games. Bare-key import instead
derives identity and resets old state. Older-version migration has explicit
[legacy lowercase key handling](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L533);
this is a separate legacy path, not proof any arbitrary lowercase backup passes
the Snake import validator. Snake checks camelCase fields, version number/string
and a matching derived identity in
`/home/david/snake-stickerbook/snake.js:382-423`.

A native-created identity-only compatible envelope can include these observed
defaults. This is a proposed native envelope based on source, not an export
captured from a real browser wallet:

```json
{
  "wallet": {
    "publicKey": "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR",
    "privateKey": "0000000000000000000000000000000000000000000000000000000000000001",
    "version": 5.677,
    "balance": "0",
    "preferred_crypto": "SAITO",
    "preferred_txs": [],
    "default_fee": "0",
    "slips": []
  }
}
```

For import/export implementation, retain the complete imported JSON as protected
backup data and update only explicitly owned identity/export fields on re-export.
Preserve unknown wallet and module fields, including keychain material, instead
of reconstructing the file from a few typed fields. This is a recommendation
to preserve compatibility, not authorization to interpret all imported options.
Imported `peers`, `server`, `consensus` or similar settings must remain inert:
they must not configure the native runtime or override its two permitted backend
choices. Selected backend belongs to the app's own validated settings. Never
treat an imported numeric balance as a verified current balance.

Recommended focused cases: web JSON with unknown nested fields survives native
import/export; numeric/string version is handled deliberately; a mismatched
pair, invalid curve point, zero/out-of-range scalar, missing field or malformed
JSON fails before changing identity; imported remote-peer settings cannot change
backend selection. See [Snake safeguards](SNAKE_WALLET_SAFEGUARDS.md) for the
already agreed replacement behavior.

## Transaction bytes and archive representation

Follow the Rust serializer, not stale wrapper constants.
`node/lib/saito/transaction.ts:9-11` declares a 67-byte slip, but the actual
[Rust slip size is 59](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L14).
The installed WASM reference independently serialized two 59-byte slips below.

[Network transaction layout](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L782),
all integers big endian:

| Offset | Width | Field |
| --- | --- | --- |
| 0 | 4 | Input count, u32 |
| 4 | 4 | Output count, u32 |
| 8 | 4 | Message byte length, u32 |
| 12 | 4 | Routing hop count, u32 |
| 16 | 64 | Compact signature |
| 80 | 8 | Timestamp in milliseconds, u64 |
| 88 | 4 | `txs_replacements`, u32 |
| 92 | 1 | Transaction type, u8; Normal = 0 |
| 93 | variable | Inputs, outputs, message bytes, routing hops in that order |

Each [network slip](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L185)
is compressed point 33, amount u64 8, block ID u64 8, transaction ordinal u64 8,
index u8 1, type u8 1. Amounts are nolan. Each
[hop](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/hop.rs#L82)
is sender point 33 + recipient point 33 + compact signature 64 = 130 bytes.

The [signature preimage](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L813)
differs from network serialization: timestamp u64, all input signature slips,
all output signature slips, replacements u32, transaction type **u32**, message
bytes. Each [signature slip](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L199)
is 43 bytes and omits block ID and transaction ordinal. Counts, signature and
routing path are absent from this preimage. Signing assigns output indices
before [hashing/signing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L841).
Do not add an extra BLAKE3 hash by passing a digest to `sign_buffer`; that
function itself hashes its input.

[JS packData](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/lib/transaction.ts#L159)
uses UTF-8 of `JSON.stringify(msg)`. Preserve the exact serialized bytes for
signatures and retries; parsing and reserializing signed data can change the
preimage. An empty msg object maps to an empty data buffer. The
[zero-payment wallet creator](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1007)
supports a zero-value identity input and recipient output without funding.
This is protocol evidence, not proof of successful posting to either backend.

[Web/archive serialization](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315)
is a JSON string with `t`, `m`, `opt`: `t` is Base64 of the binary transaction
with message data removed, `m` is Base64 of the message bytes, and `opt` is
Base64 of UTF-8 JSON of unsigned `optional` metadata. Decode and restore message
bytes before verifying the signed transaction. Do not mistake `optional`
profile/counter/moderation fields for signed author data.

### Independent zero-value transaction fixture

Synthetic scalar 1, one zero-valued Normal input and one zero-valued Normal
output to its public key; every block ID, transaction ordinal and slip index is
zero; timestamp `1700000000000`; type Normal; replacements 1; no routing hops.
Exact 82-byte UTF-8 message:

```json
{"module":"RedSquare","request":"create tweet","data":{"text":"native reference"}}
```

The manually built 184-byte signature preimage's BLAKE3 equals the independent
WASM `generate_hash_for_signature` result. Its signature verifies with WASM.
WASM serialized and deserialized the 293-byte network representation, retaining
signature and exact message bytes:

```json
{
  "blake3Hex": "5c3c7dbe5992bda97be3ad2a138be79e4d688397ff02bdfa31bc81b49bede3f3",
  "compactSignatureHex": "a9ed2c49d5fe7758abf863a355f68af05c42adaf331688ffe3696596dea826d91784905535fd322da9be80241598ff3863c02f9404eaf52fbb82f4f030f37e01",
  "networkHex": "00000001000000010000005200000000a9ed2c49d5fe7758abf863a355f68af05c42adaf331688ffe3696596dea826d91784905535fd322da9be80241598ff3863c02f9404eaf52fbb82f4f030f37e010000018bcfe5680000000001000279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f8179800000000000000000000000000000000000000000000000000000279be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f8179800000000000000000000000000000000000000000000000000007b226d6f64756c65223a22526564537175617265222c2272657175657374223a22637265617465207477656574222c2264617461223a7b2274657874223a226e6174697665207265666572656e6365227d7d"
}
```

This is a serializer/signature reference only. Nothing was broadcast or saved
as a pending transaction, and backend acceptance was not exercised.

## WebSocket handshake and archive requests

The node's [WebSocket upgrade route](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L73)
is `/wsopen`. The
[browser bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/shared_methods.browser.ts#L35)
opens a binary WebSocket, queues per-peer message processing, and initiates the
handshake on open. This supports deriving `wss://<approved-host>/wsopen`; public
connection/reverse-proxy behavior still needs an authorized integration check.

[Wire messages](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/message.rs#L43)
begin with one message-type byte. The relevant types are RequestHandshake 1,
Handshake 2, Transaction 4, Ping 7, Services 9, ApplicationMessage 12,
Result 13, Error 14, KeyList 15, Pong 16, RequestServices 20.
The old commented `node/lib/saito/networkapi.ts` enum is not the active protocol.

[Handshake payloads](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/handshake.rs#L6)
are 32-byte nonce for a request, and public point 33 + compact signature 64 +
counter nonce 32 for a response. Including type bytes, sizes are 33 and 130.
The [routing state machine](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L461)
signs the other party's **raw 32-byte nonce**, creates its own counter nonce,
verifies responses only against the stored pending nonce, then answers a
nonzero counter nonce with a signature and a zero counter nonce. Therefore a
native initiator must retain its challenge, verify the peer's response, answer
the peer's counter challenge, and handle incoming challenges symmetrically.
Do not sign the UTF-8 hex representation of a nonce. An unsolicited response
without a pending challenge is rejected.

The core also [requests services on connect](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L704).
RequestServices has no payload, while
[service lists](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/service.rs#L42)
are UTF-8 `service|domain|name` records separated by semicolons. Treat service
discovery and a completed authenticated handshake separately from merely having
an open socket. The routing thread
[drops application requests from unidentified peers](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L285).

Archive reading uses application request messages, not an HTTP feed endpoint:

1. The [network sendRequest wrapper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/saito.ts#L430)
   creates a zero-value transaction addressed to the local identity, containing
   `{request: "archive", data: {request: "load", ...criteria}}` as UTF-8 JSON.
   It signs only when `signature_required` is true. The ordinary
   [loadTransactions path](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L251)
   does not request that additional transaction signature.
2. [API send](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_network_api.rs#L17)
   frames the binary transaction as type 12 + u32 big-endian request index +
   transaction bytes. The [API payload serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/api_message.rs#L9)
   applies the same u32 index layout to result/error responses.
3. The [Archive handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L353)
   takes requester identity from the connected peer, overwriting client-supplied
   REQUESTER/NOW. It invokes its query and returns rows through the callback.
   The [shared callback bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/shared_methods.browser.ts#L313)
   sends type 13 with the same request index and UTF-8 JSON response bytes.
   Result data is JSON, not another serialized transaction.
4. Rows contain `tx` as the web/archive JSON string described above. The storage
   callback reconstructs transactions and applies `updated_at` compatibility
   metadata. Future native integration must bound sizes and validate signed
   content separately from unsigned archive metadata.

The deployed [RedSquare feed query](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L656)
uses `field1: "RedSquare"`, `flagged_ne: 1`, a limit, and either
`created_earlier_than` or `updated_later_than`. Current module loaders differ,
so this is a minimal transport/query baseline, not a finished feed pagination
contract. Native posting needs the propagation type-4 path and routing semantics;
an application RPC callback alone is not chain inclusion or delivery proof.

## Reproducing reference calculations safely

This verified runner executes only pure pinned WASM crypto and reads package
code/binaries. It starts with an empty environment, has no network, mounts the
host read-only and never calls app initialization or wallet loading. It prints
only synthetic fixture data. Keep this boundary if expanding the reference:

```sh
docker run --rm -i --network none --read-only \
  --mount type=bind,src=/,dst=/host,readonly \
  --entrypoint chroot nginx:alpine /host \
  /usr/bin/env -i PATH=/usr/bin:/bin \
  /opt/saito-runtime/node-v24.13.0/bin/node - <<'JS'
const wasm = require('/opt/saito/node/node_modules/saito-wasm/pkg/node');
const base58 = require('/opt/saito/node/node_modules/base-58');
const privateKey = '0'.repeat(63) + '1'; // Public synthetic fixture only.
const publicKey = wasm.generate_public_key(privateKey);
console.log(JSON.stringify({ privateKey, publicKey,
  compressedPublicKeyHex: Buffer.from(base58.decode(publicKey)).toString('hex') }));
for (const message of ['', 'Red Square Android synthetic reference', 'Saito 🔴 café\n']) {
  const bytes = Buffer.from(message, 'utf8');
  const signature = wasm.sign_buffer(bytes, privateKey);
  console.log(JSON.stringify({ message, utf8Hex: bytes.toString('hex'),
    blake3Hex: wasm.hash(bytes), compactSignatureHex: signature,
    valid: wasm.verify_signature(bytes, signature, publicKey),
    deterministic: signature === wasm.sign_buffer(bytes, privateKey),
    tamperedValid: wasm.verify_signature(Buffer.concat([bytes, Buffer.from([1])]),
      signature, publicKey) }));
}
JS
```

For the transaction fixture, construct `new wasm.WasmTransaction()` and two
`new wasm.WasmSlip()` objects directly with the fields above. Set exact message
bytes, call `generate_hash_for_signature()` and compare the result to BLAKE3 of
the independently built preimage. Sign that preimage with `sign_buffer`, assign
the signature, then compare `serialize()` and
`wasm.WasmTransaction.deserialize(bytes)`. This was executed successfully without
initialization. Do not call its wallet-backed `sign()` in the pure fixture.

The smallest subsequent transport fixtures are a fixed 32-byte challenge and
counter challenge signed with these published keys, exact 33/130-byte frames,
an application request index such as 1, and a synthetic result containing a
web-serialized transaction. Test malformed lengths, nonce mismatch, high-S,
wrong key, response-index mismatch and inert imported backend fields. Real
WebSocket connectivity, archive coverage and backend acceptance remain future
integration verification; they were deliberately not exercised here.

## Additional transport fixtures for the feed ticket

The actual [WASM message bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L591)
forwards incoming frames to the routing state machine and returns an empty
immediate response. Handshake responses travel through the asynchronous IO
interface, not that return value. The [server creates a peer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L100)
when accepting the socket; the native client should initiate its challenge on
open rather than wait for a server greeting. The [state machine](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L536)
sends the final counter-challenge answer but no final handshake acknowledgement.
Keep output order so an application request follows that answer.

The archive application-request route checks identified peer identity, not a
completed blockchain catch-up. This permits a native archive-reading client to
query once its handshake is established. It is not evidence a complete native
wallet can ignore chain state for funded transactions. See
[application routing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L285).
Handle unsolicited Ping with Pong and service/endpoint announcements separately
from correlated API responses; discard pending callbacks on reconnect so stale
responses cannot cross connection/backend boundaries. These last lifecycle
rules are native implementation recommendations.

These synthetic handshake calculations were generated with pure pinned WASM
crypto. Frame layout comes from the cited Rust serializer; the research did not
run a connected peer handshake:

```json
{
  "clientPrivateKey": "0000000000000000000000000000000000000000000000000000000000000001",
  "clientPublicKey": "jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR",
  "serverPrivateKey": "0000000000000000000000000000000000000000000000000000000000000002",
  "serverPublicKey": "pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU",
  "challengeHex": "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
  "counterHex": "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f",
  "requestFrameHex": "01000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
  "serverResponseSignature": "86c36b0516dd6d775cccc6e976f709e3bbfe19dcaa2304313bb7ba2094edbfad3522044d0e6e1ea2e2c69ba629ea2af3aa71fce737850f98e7417a7f046486d2",
  "clientFinalSignature": "7952cb20e3e2d899524abfb531efde28dac92ad8d812e07e36a59eefe60400b61a225507307dd8d4ed2d79ae925964c869ed3a730be9408485e09ac892bf0038"
}
```

Server response is type `02` + decoded server point + server response signature +
counter bytes. Client final response is type `02` + decoded client point + client
final signature + 32 zero bytes. Each response is 130 bytes including type.

An independent unsigned archive request was serialized by pinned WASM using the
same two zero-value slips, fixed timestamp and replacements as the transaction
fixture. Its exact message is:

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","flagged_ne":1,"limit":10,"created_earlier_than":1700000000000}}
```

With all-zero compact signature and request index 1, the WASM transaction is
342 bytes; its type-12/index-prefixed application frame is 347 bytes and has
BLAKE3 digest `eefff10fa7e146bf3be79e07aa66bcd308ea952cad357486000284becddd7a14`.
An empty-row synthetic result frame is `0d000000015b5d` (type 13, index 1, `[]`).
These are byte-layout references; no archive request was sent.

The deployed RedSquare also has a [load tweets peer request](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1065)
whose response is an array of web-serialized transaction strings, unlike the
generic Archive response's row objects containing `tx`. Its advertised
[service name is redsquare](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L110).
Do not accidentally parse the two reply formats as identical. Prefer one
explicitly verified read contract per backend; both transport paths remain
source findings until ticket-level integration verification establishes actual
public availability.
