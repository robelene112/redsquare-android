# Independent private Chat reference vectors

Generated 2026-10-10, extended and reproduced twice 2026-10-13 by
[private-chat-reference.cjs](../../build-support/references/private-chat-reference.cjs).
[Reference JSON](NATIVE_PRIVATE_CHAT_REFERENCE.json) SHA256:
`d30596ac8b3f10a02b9e42fd8b418a7189d02f95c2ffe176477c5534081d4778`.
Two complete executions produced byte-identical output. These are public unsafe
scalar1/2 identities, scalar3/4 exchanged-key fixtures and fixed salts: never fund them or use them in production.

## Sources and execution

The actual installed `lib/saito/crypto.ts` SHA256
`61416839726c1877875062273fd704b37002f8f3a5d299e6c0883f5d24685683`
matches public source at both
[A](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/crypto.ts#L119)
and [B](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/crypto.ts#L119).
The generator transpiles that wrapper with TypeScript5.4.5 and invokes its actual
`createDiffieHellman`, `generateSharedSecret`, `aesEncrypt`, `aesDecrypt` and hash
methods. Its minimal adapter binds core crypto to WASM functions; no application,
wallet, keychain or network initializes.

Runtime dependency versions are asserted: Node24.13.0, saito-js/wasm0.3.2,
node-cryptojs-aes0.4.0, crypto-browserify3.12.1, elliptic6.6.1, base-580.0.1,
json-bigint1.0.0. Eight installed CryptoJS files match the
[published0.4.0 package](https://registry.npmjs.org/node-cryptojs-aes/-/node-cryptojs-aes-0.4.0.tgz)
by SHA256. Their hashes and the existing signing-baseline WASM binary hash are
recorded in JSON. The dependency versions match the
[pinned declarations](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/package.json#L55).
The WASM identity is the existing signing reference baseline, not a new build
proving that every binary was compiled from A or B.

Execution used only named read-only dependency/code mounts, `--network none`,
read-only filesystem, dropped capabilities, no-new-privileges and `env -i` with
an explicit PATH. No real options/wallet/config/database was read. Reproduction:

```sh
docker run --rm --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges --pids-limit 64 --memory 512m \
  --mount type=bind,src=/opt/saito/node/node_modules,dst=/opt/saito/node/node_modules,readonly \
  --mount type=bind,src=/opt/saito-runtime/node-v24.13.0,dst=/opt/saito-runtime/node-v24.13.0,readonly \
  --mount type=bind,src=/opt/saito/node/lib/saito/crypto.ts,dst=/reference/crypto.ts,readonly \
  --mount type=bind,src=/home/david/projects/redsquare-android/build-support/references,dst=/references,readonly \
  --entrypoint /usr/bin/env mcr.microsoft.com/playwright:v1.63.0-noble \
  -i PATH=/opt/saito-runtime/node-v24.13.0/bin:/usr/bin:/bin \
  node /references/private-chat-reference.cjs
```

## What passes and what remains

Vectors include Unicode/newline text, group hash, shared-point X, KDF key/IV,
salt, formatter JSON string, doubly encoded signed message bytes, signature,
binary transaction, Archive envelope and signed-inner JSON. Actual browserify
ECDH and WASM shared-secret results agree. Both sender and recipient decrypt
each direction. Signing uses the existing pure WASM serializer/signature
reference with independent Elliptic verification; WASM binary restoration passes.

For signed-inner JSON, the generator executes the installed saito-js
`packData`/`toJson` method bodies and actual Slip wrappers against a pure adapter,
avoiding Transaction getters that require Saito's application singleton.
The original API method bodies are extracted, not replaced with a native
implementation. JSONBig serializes their BigInt values compatibly.
[Web payload assignment](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1290),
[DM construction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1437).

This proves deterministic lower-boundary compatibility. It does not prove native
Android implementation, browser UI execution, exchanged-key import selection,
Relay delivery, Archive availability, push behavior or chain inclusion. The
initial TypeScript import-interop harness error was corrected before producing
passing vectors; it was not a functional encryption failure.

The third vector uses a saved exchanged secret derived independently from public
unsafe ephemeral scalar3/4. The signer and recipient remain unsafe1/2. This
separates saved-secret selection from implicit wallet ECDH; both directions of
ephemeral ECDH agree. Existing first two message/signature/network bytes remain
unchanged. Two isolated complete executions produced byte-identical new JSON.
