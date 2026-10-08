# Independently signed scroll and refresh reference

Generated 2026-10-08. [NATIVE_FEED_SCROLL_REFERENCE.json](NATIVE_FEED_SCROLL_REFERENCE.json)
contains twenty root Archive rows in `initialRows`, and those same rows plus
one newer root in `refreshedRows`. Both arrays are newest first. Timestamps are
`1700000000000 + index`, for indices 1–21. Text is `Reference post 01` through
`Reference post 20`, then `New reference post`. `expectedPosts` records the
authenticated fields, exact signed message/preimage/network bytes and verification
results for each distinct transaction, in ascending index order.

Used the [safe pure runner](NATIVE_SAITO_PROTOCOL.md#reproducing-reference-calculations-safely):
network-disabled Docker, read-only filesystems, empty environment and pinned
Node 24.13.0. Required installed pure WASM/Base58/Elliptic code only. No Saito
application initialization, wallet-backed signing, node state, storage or network
APIs ran. The runner's public synthetic stdout was saved in this repository.

Each transaction uses the publicly known scalar-one author
`jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR`, one zero Normal input/output,
zero block/ordinal/index values, type 0 and replacements 1. Constructed each
exact compact UTF-8 RedSquare/create-tweet JSON message and transaction with
`WasmTransaction`/`WasmSlip`. Independently built the signature preimage; asserted
its WASM BLAKE3 equals the transaction's own generated signature hash. Signed
with `wasm.sign_buffer`, verified every signature with WASM and separately
Elliptic 6.6.1, and rejected an appended-byte tamper for every transaction.
Actual WASM serialization/deserialization and detached `t`/restored `m` preserved
each signature hash. All 21 assertions passed and signatures are distinct.

Source method follows the pinned
[transaction preimage](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L813),
[signature slips](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L200),
[RedSquare creator](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1101),
[web serialization](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315)
and [root indexing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1365).
JSON provenance records installed WASM 0.3.2, source pin `9a4ca7e` and actual
WASM SHA-256 `f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`.
This identifies the binary used, not a fresh rebuild from the source revision.

Archive rows and zero-count `opt` metadata are synthesized and unsigned; the
real Archive handler was not executed. Root `field4` is empty and `field5` is
the signature. No block inclusion, real user data, production wallet, funded
validity, live submission acceptance or retention is established. The minimal
one-input/output transactions differ from the web wallet's typical two-input/
two-output constructor. This is an independent decoding fixture, with no
application behavior or implementation prescribed.
