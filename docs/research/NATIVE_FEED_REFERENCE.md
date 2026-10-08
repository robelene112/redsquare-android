# Signed feed reference

Generated 2026-10-08. [NATIVE_FEED_REFERENCE.json](NATIVE_FEED_REFERENCE.json)
contains a synthetic full Archive row under `archiveRow`, its decoded web
envelope under `webEnvelope`, and independently generated expected values.
It contains no real user's post, wallet or database record.

## Method

Followed the [pure reference runner](NATIVE_SAITO_PROTOCOL.md#reproducing-reference-calculations-safely):
Docker with no network, a read-only root filesystem and read-only host mount,
then `chroot` and `env -i` into Node 24.13.0. Required only the installed
`saito-wasm` pure package, Base58, the Elliptic pure crypto package, and Node
standard libraries. Never called Saito application initialization, wallet-backed
`sign()`, storage, propagation or networking APIs. The runner printed only this
public synthetic fixture; the host wrote the two research files.

The maintained Snake runtime pin identifies Saito commit
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7)
and saito-wasm 0.3.2. Read the installed package version and hash the installed
WASM bytes separately: SHA-256
`f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`.
This records the actual binary used, not a fresh reproducible build of that Git
revision. The independent Elliptic verifier was version 6.6.1.

Used the publicly known secp256k1 scalar 1, producing author
`jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR`. Constructed `WasmTransaction`
directly with one zero-valued Normal input and one zero-valued Normal output,
both to that author, with zero block ID/ordinal/index, timestamp
`1700000000000`, Normal transaction type 0 and replacements 1. Its exact
98-byte UTF-8 message is:

```json
{"module":"RedSquare","request":"create tweet","data":{"text":"Native Red Square reference post"}}
```

The module/request/payload shape follows the pinned
[createTweetTransaction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1101).
No parent is supplied, so this is a root post; the
[Tweet parser](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/lib/tweet.js#L180)
defaults its thread ID to the signature. Archive `field4` is the empty string
and `field5` is that signature.

Built the 200-byte signature preimage manually from the published
[transaction layout](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L813)
and [43-byte signature slips](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L200).
Asserted its WASM BLAKE3 equals `generate_hash_for_signature()` on the actual
WASM transaction. Signed the raw preimage using `wasm.sign_buffer`; verified
with `wasm.verify_signature` and separately with Elliptic's secp256k1
`verify(blake3Digest, {r,s}, compressedPublicPoint)` API. Appending one byte to
the preimage fails verification. All verification assertions passed.

Assigned that signature and called the actual WASM serializer, producing a
309-byte network transaction. Deserialization preserved its signature/message
and signature hash. To mirror the pinned
[serialize_to_web](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315),
removed transaction message bytes before generating Base64 `t`, retained the
exact detached message in Base64 `m`, and encoded unsigned JSON metadata in
Base64 `opt`. Deserialized `t`, restored `m`, and asserted the original hash and
signature still verify. Do not reserialize parsed JSON before verification.

## Unsigned data and limits

The outer row is synthesized to match the pinned
[Archive schema](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/sql/archives1.sql)
and [save defaults](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L433),
with Red Square's
[root/thread indexing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1365).
The real Archive handler was not executed. `id`, timestamps, counts, flags,
block metadata and indexing fields are backend metadata, not author-signed
claims. Derive authenticated author/text/signature from the reconstructed
transaction, not these row fields.

`unsignedOptional` records the deliberately synthetic counts and update time.
The unknown `research_extension` field lives only inside unsigned `opt`; it
must never enter the signature preimage or become verified post content.
`block_id: 0` and empty block hash establish no chain inclusion.

This is an independent read/decoding/signature fixture for the native app,
not proof of live backend availability, Archive retention, funded validity or
submission acceptance. Its minimal one-input/one-output construction is not
the web wallet's usual unfunded transaction constructor, which can add extra
zero-valued slips. Posting interoperability needs its own constructor and
delivery verification.
