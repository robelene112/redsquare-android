# Native private Chat encryption and recovery

Source research and pure synthetic checks, 2026-10-09. Git root `/opt/saito`
is [`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
node source `/opt/saito/node`. Chat, Encrypt, keychain, wallet, transaction,
crypto and core/WASM ECDH files match that Git object. Checkout evidence does
not establish served-bundle identity or live Android/browser interoperability.
No saved options, real wallets, database, environment or live requests were
accessed. Only this note is added; no app/test/state changes or commit.

## Compatible secret selection

Private two-person Chat uses **forced encryption**. Named groups have
`member_ids`, address their group public key and follow the plaintext signing
branch. Keep that distinction even when a named group has two members.
Ordinary DM group IDs are BLAKE3 of UTF-8 sorted Base58 member addresses joined
with `_`; sorting matches JavaScript's lexical `.sort()`.
[Chat sender](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1437),
[group ID](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2133).

For a counterparty, encryption first uses `options.keys[].aes_secret` when
present. Otherwise it derives an **implicit** secret from the wallet private
key and counterparty wallet public key. Thus a fresh wallet need not complete
DH negotiation before constructing an encrypted DM. This is a construction
fact, not evidence of delivery on either selected backend.
[Selection](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/keychain.ts#L276).

Implicit ECDH is secp256k1 multiplication, taking the **raw 32-byte X
coordinate**, returned as lowercase 64-character hex. Public addresses decode
to compressed 33-byte points. Do not use a library's hashed ECDH default or
hash this X coordinate again. The exchanged-DH path uses Node/browser
`createECDH('secp256k1').computeSecret`, producing the same raw-X encoding.
[Core primitive](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/crypto.rs#L175),
[WASM encoding](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L1031),
[DH constructor](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/crypto.ts#L159).

The browser catches encryption errors and returns the original plaintext
object. Native private sends must **fail closed** when encryption cannot
produce a valid ciphertext string; reproducing that fallback would violate
the accepted private-chat scope. Decryption tries stored DH first, then implicit
ECDH if JSON parsing fails. However, the first AES call is outside its `try`:
an exception can prevent fallback. Native should catch each candidate's
decryption/UTF-8/JSON failure separately, then show unavailable content if none
succeeds. Never accept ciphertext itself as displayed chat text.
[Encryption/decryption behavior](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/keychain.ts#L187).

## Exact AES and signed payload

Saito imports installed `node-cryptojs-aes` **0.4.0**. Its `cipher-core.js`,
`evpkdf.js`, `jsonformatter.js` and `aes.js` bytes were independently matched
by SHA-256 to dependency Git revision
[`7fef7148f7f7d6771c97acb3375c5e4167fde25c`](https://github.com/chengxianga2008/node-cryptojs-aes/tree/7fef7148f7f7d6771c97acb3375c5e4167fde25c).
This pins dependency behavior rather than assuming current CryptoJS defaults.

| Layer | Exact behavior |
| --- | --- |
| Plaintext | UTF-8 `JSON.stringify(ChatMessage)` |
| Passphrase | ASCII/UTF-8 lowercase shared-secret hex, **not decoded 32-byte AES key** |
| Derivation | EVP_BytesToKey: `D_i = MD5(D_(i-1) || passphrase || salt)`, one iteration; concatenate until 48 bytes |
| AES | First 32 derived bytes are AES-256 key; remaining 16 IV; CBC with PKCS7 |
| Salt | 8 bytes, serialized as 16 hex characters; use fresh secure randomness in production |
| Cipher formatter | JSON string with `ct` Base64 ciphertext, `iv` hex, `s` salt hex |
| Signed data | UTF-8 `JSON.stringify(cipherFormatterString)`, including outer quotes/escaping |

Saito's `secret.toString('hex')` leaves a secret that is already a JS hex
string unchanged. The dependency's passphrase decryptor derives IV again from
the salt; its transmitted `iv` is redundant on this path. Do not treat this
format as modern AES-GCM or add an incompatible KDF/MAC. CBC itself has no
authentication tag: independently verify the signed transaction over the
**original ciphertext bytes** before decryption, and strictly validate resulting
UTF-8, JSON, module/request, participants and group bindings.
[Saito AES wrapper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/crypto.ts#L119),
[derivation and password cipher](https://github.com/chengxianga2008/node-cryptojs-aes/blob/7fef7148f7f7d6771c97acb3375c5e4167fde25c/lib/cipher-core.js#L761),
[MD5/one iteration](https://github.com/chengxianga2008/node-cryptojs-aes/blob/7fef7148f7f7d6771c97acb3375c5e4167fde25c/lib/evpkdf.js#L22),
[formatter](https://github.com/chengxianga2008/node-cryptojs-aes/blob/7fef7148f7f7d6771c97acb3375c5e4167fde25c/lib/jsonformatter.js),
[AES key size](https://github.com/chengxianga2008/node-cryptojs-aes/blob/7fef7148f7f7d6771c97acb3375c5e4167fde25c/lib/aes.js#L203).

Plain message fields, in sender insertion order:

```json
{"module":"Chat","request":"chat message","group_id":"...","message":"...","timestamp":1700000000000,"mentioned":[]}
```

The sender may append `group_name`. DM construction starts with the ordinary
zero-fee self transaction, adds the other member once, encrypts then signs.
For a simple DM this is two zero author inputs, two zero author outputs and
one recipient output; output indices after signing are 0, 1, 2. `addTo`
deduplicates addresses, so adding the author again does not add a fourth
output. Mentions can add further addresses. Transaction decryption selects
the first distinct address other than the active wallet; private crypto keys
must therefore bind to the counterparty, not the authenticated backend.
Decrypted `dmsg` is separate from signed `data`.
[Wallet encoding](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1290),
[address/decryption](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L126),
[deduplication](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L279),
[binary signature/routing contract](NATIVE_TRANSACTION_BROADCAST.md).

The signed inner chat is propagated via type 4 and also relayed:
`{module:'Relay',request:'chat relay',data:innerTransactionJson}` addressed to
members. That Relay wrapper itself is **not signed** by `createRelayTransaction`.
The service send adds another request envelope:
`{request:'relay peer message',data:relayTransactionJson}` addressed to the
server. These are nested transaction JSON objects, distinct from Archive
`{t,m,opt}` web envelopes. Native must verify the **inner author signature**;
outer socket authentication or wrapper recipients cannot replace it. Routing
hops sign the authenticated next peer as described in the broadcast note.
Relay callbacks report handling/forwarding, not recipient decryption or chain
inclusion. Backend nodes can observe participant addresses and timing.
[Relay construction/send](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/relay/relay.js#L77),
[Chat relay handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L911).

## Negotiation and recoverable history

`Encrypt` uses separate random DH keys, not wallet identity keys:

1. Request: `{module:'Encrypt',request:'key exchange request',alice_publicKey:<compressed point hex>}`; save own `aes_publicKey`/`aes_privatekey` under the counterparty, secret initially empty.
2. Confirm: `{module:'Encrypt',request:'key exchange confirm',tx_id:<request id>,bob:<compressed point hex>}`; Bob saves raw-X hex, Alice reconstructs her saved DH pair and computes it from `bob`.
3. Both save counterparty `aes_publicKey`, `aes_privatekey`, `aes_secret` and mark it watched. Here `aes_publicKey` means **our local DH public key**, not the counterparty's identity address.

These are signed plaintext inner transactions, propagated on chain or wrapped
in Relay requests `diffie hellman key exchange` / `diffie hellman key response`.
Native must validate the inner signer, intended recipient, point format and
pending exchange before accepting a confirmation. Reset clears the saved secret
and local DH private key. No ciphertext key ID, historical key ring or ratchet
appears in these paths.
[Initiate](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/encrypt/encrypt.js#L252),
[accept/confirm](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/encrypt/encrypt.js#L328),
[reset handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/encrypt/encrypt.js#L122).

Full browser wallet export clones options, including `keys` and Chat group IDs,
but does not export the separate LocalForage records `chat_<group_id>`.
Those records contain rendered message content and keep the latest 50 messages.
Preserve imported crypto/group fields in the encrypted native backup; do not
silently prune them using the browser's seven-day key-loading rule. Wallet-key
only import can decrypt **available implicit-key ciphertext**, but cannot derive
an unrelated exchanged DH secret. A saved DH private key is useful only with
the corresponding peer DH point; `aes_secret` directly carries the needed
shared secret. Missing/overwritten secrets can make older messages inaccessible
even if the identity key and ciphertext survive.
[Backup](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1244),
[key persistence](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/keychain.ts#L673),
[Chat cache](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2393).

Older history is an Archive query for a **known** `field3:group_id`, limit 50,
before the oldest timestamp. Sender browser confirmation explicitly saves this
group label; general Archive chain indexing is conditional and a backend cannot
extract encrypted group IDs by itself. Chat's relay handler does not itself
promise durable archival storage. Options/chat discovery and keys help find
known groups; identity import alone does not enumerate all previous contacts.
Retained, properly indexed, decryptable history plus protected local cache is
the defensible recovery promise. Neither complete permanent history nor forward
secrecy follows from this static-key scheme.
[History query](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2302),
[sender indexing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1586),
[conditional Archive indexing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js#L313).

## Executed independent synthetic check

Used the [safe pure runner](NATIVE_SAITO_PROTOCOL.md#reproducing-reference-calculations-safely):
network disabled, read-only host, empty environment, Node 24.13.0, installed
`saito-wasm` 0.3.2 and Elliptic 6.6.1. WASM binary SHA-256:
`f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`.
No Saito initialization, wallet-backed signing or network method was invoked.

Published synthetic scalars 1/2 provide author
`jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR` and recipient
`pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU`. WASM, Node ECDH and
independent Elliptic point multiplication all produced shared X
`c6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee5`.
Synthetic DH scalars 3/4 also agreed, producing
`d01115d548e7561b15c38f004d734633687cf4419620095bc5b0f47070afe85a`.
These are public test secrets, unsuitable for real messages.

Exact plaintext uses group ID
`fbafe62761f194eb174cf4a072d8cd8601b11fe03f5a1ed32bf5be20dade0b19`,
the ordered fields above, message `Native private Chat reference café 🔴`,
timestamp `1700000000000`, and `mentioned:[]`. Fixed salt `0001020304050607`
was supplied through a pure custom CryptoJS KDF calling its actual OpenSSL KDF.
For implicit secret, independently derived AES key is
`c8b0aeadbc56e1d124b49a409dee4a6b3d927c1b1c82df0ee59016d360b63583`,
IV `19476f0762c63a22196de4772afa3229`. Exact formatter string:

```json
{"ct":"dQE95k3MrwiH9JCNU8wD9HQhQFxgx9os0dbpUlYM5gfwcLIHA7L5T5hDTRCfUF4DfhAWTlWR56pWoBy4g1Uf4ciO2Yl1hxgJYfbhORyumckkPodqUTjjzIfBWx0FwsNxpg7QdDxixN5VffteWcsmoFgCMFYlEzcyyN2ONJ4zpy9vN2YH0XVDGoIfMTFzLXQo73nDbTQAlb8sY60Myqye9qJZZDZluoFrgCwkseq9+/XdsjSGJ/58eOiSPjOyNCMzRlzapKg4fu7Io4Z7d1Ck8dTkU9FLnL6AJVjghVncZ5I=","iv":"19476f0762c63a22196de4772afa3229","s":"0001020304050607"}
```

CryptoJS encryption matched independently assembled MD5 derivation and Node
AES-256-CBC bytes; both implementations decrypted exactly, for implicit and
exchanged secrets. Pure WASM constructors serialized the described 2-input,
3-output signed transaction, and deserialize/reserialize preserved it exactly.
Signed data is 386 bytes, author preimage 617, unpathed network transaction 774.
Preimage BLAKE3:
`04a2ec70808a5cb33ab67eb01fdf6d72de54f2d3a9cb02f4fe6f514990dbbc5e`;
network BLAKE3:
`78b13d4383864b30f6d5ac171f0d7cf9ffd76d774b625560bcf943aa3aa7585c`.
WASM and Elliptic both verified the signature; an appended preimage byte failed
verification. The recipient decrypted the restored signed bytes to the exact
Unicode plaintext. This verifies primitives, nesting, signature and serializer,
not a live key exchange, archive recovery, core admission or chat delivery.

For implementation regression fixtures, retain these literal bytes and add
separately signed exchanged-secret messages, wrong/missing secrets, malformed
padding/UTF-8, recipient/group mismatch, duplicate signature and named-group
plaintext cases. Run independent CryptoJS/WASM generation before comparing
native code; never create expected ciphertext by the native implementation
being tested. Wallet import/reset must isolate cached chats and secrets by
wallet identity, and backend availability must not imply permanent recovery.
