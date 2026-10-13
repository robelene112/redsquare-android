# Native private Saito Chat contract

Read-only public-source trace, 2026-10-10. Pins A
`9a4ca7e9442eb5e872168ced361ce741d7d139d7` and B
`214504ecece76d1cd20a245fe4468eccbfca2951`, repository SaitoTech/saito.
No node, private state, runtime initialization or network submission was used.
Earlier [Chat observations](REDSQUARE_CHAT.md) describe deployed inspection;
these pins establish source behavior, not current backend availability.

## Message and conversation

Chat constructs a zero-payment transaction to self and adds group members.
For two members without `member_ids`, its plaintext is
`{module:"Chat",request:"chat message",group_id,message,timestamp,mentioned}`
with optional `group_name`, then force-encrypted before signing. Ordinary
delivery propagates that signed transaction and sends a `chat relay` wrapper.
Conversation ID is BLAKE3 of UTF-8 sorted member public keys joined with `_`.
Received addressed messages can create conversations; message signatures
deduplicate entries. Archive history uses
`{field3:group_id,limit:50,created_earlier_than:oldestTimestamp}`. LocalForage
stores `chat_<id>` with the latest 50 processed messages; options store IDs.
[A Chat construction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L1437),
[identity/history/cache](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/chat/chat.js#L2133).

B additionally saves the DM counterparty as a key and discovers contacts with
`added || aes_publicKey`; A discovers `aes_publicKey` contacts. B keychain
initialization retains `added` entries beyond A's watched/recent-entry rules.
These are concrete discovery differences, not encryption changes. Crypto,
transaction and Rust ECDH files are identical at A/B; Encrypt and Relay differ
only by module metadata.
[B contact/discovery changes](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/chat/chat.js#L1521),
[B key recovery](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/keychain.ts#L68).

## Encryption bytes

Keychain prefers counterparty `aes_secret`; otherwise it derives an implicit
wallet-identity ECDH secret. Decryption tries the saved secret, then implicit.
Failure can return the original plaintext object during encryption, so native
private sending must fail closed instead of copying that fallback.
[Keychain](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/keychain.ts#L187).

Implicit ECDH is secp256k1 multiplication of wallet scalar and decoded compressed
counterparty key; the secret is the 32-byte shared-point X coordinate. It is
**not** libsecp256k1's default hashed SharedSecret result.
[Rust generate_shared_secret](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/crypto.rs#L175).

The hexadecimal secret is passed as a **string passphrase** to CryptoJS AES,
encrypting UTF-8 `JSON.stringify(plaintext)`. The formatter returns a string
containing `{ct:Base64(ciphertext),iv:hex,s:hex}`. Wallet assigns that string to
`tx.msg`, then signs UTF-8 `JSON.stringify(tx.msg)`: the wire message is a JSON
string containing the formatter JSON, not a direct cipher object.
[Crypto wrapper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/crypto.ts#L119),
[wallet encryption/signing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1290).

The pinned `node-cryptojs-aes:0.4.0` sources establish AES-256-CBC, PKCS7,
8-byte salt and OpenSSL-style EVP_BytesToKey with MD5 and one iteration.
For ASCII hexadecimal passphrase P and salt S, concatenate
`D1=MD5(P||S)`, `D2=MD5(D1||P||S)`, `D3=MD5(D2||P||S)`;
first 32 bytes are key, next 16 IV. Decrypt derives IV from salt, replacing the
formatter IV. Native generation should emit the matching derived IV and use
secure random salt. CBC has no MAC; signed transaction verification supplies
payload authentication. This does not establish forward secrecy.
[Pinned dependency declaration](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/package.json#L72),
[published package source](https://registry.npmjs.org/node-cryptojs-aes/-/node-cryptojs-aes-0.4.0.tgz)
(SHA256 `b514fa78b0d056973a260586a45b77a83a4720b2ea45323f5bea3d22691e9b01`;
`lib/aes.js`, `cipher-core.js`, `evpkdf.js`, `jsonformatter.js`).

## Initial contact and exchanged keys

Implicit encryption permits immediate first contact without a DH handshake.
Explicit exchange uses ephemeral secp256k1 keys: signed Encrypt
`key exchange request` with `alice_publicKey` compressed hex, then
`key exchange confirm` with `bob` compressed hex and `tx_id`. Off-chain wrappers
are `diffie hellman key exchange` and `diffie hellman key response`.
Both persist counterparty `aes_publicKey`, `aes_privatekey`, `aes_secret`.
Imported exchanged-key messages need those secrets; wallet scalar alone cannot
reconstruct them. Do not overwrite an existing secret automatically.
[Encrypt exchange](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/encrypt/encrypt.js#L252).

## Relay, Archive and trustworthy status

Relay's inner unsigned envelope has inputs identifying sender, recipient outputs,
and `{module:"Relay",request:"chat relay",data:signedChatTransaction.toJson()}`.
Its server request is `relay peer message`, carrying that envelope's `toJson()`.
The relay forwarding callback indicates forwarding, not recipient read or chain
inclusion. Always authenticate the original signed Chat transaction independently.
[Relay nesting/forwarding](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/relay/relay.js#L77).

Archive requests use `archive`, `{request:"load",...criteria}` or
`{request:"save",serial_transaction,...fields}`. Defaults are module in field1,
sender in field2 and first output in field3; Chat overrides field3 with group ID
when its sender saves after confirmation. Returned rows contain `tx`, a web
envelope `{t,m,opt}` with detached signed message bytes. Its serializer copies
encrypted `data`, not decrypted `dmsg`.
[Storage schema](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/storage.ts#L135),
[web envelope](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/transaction.ts#L315).

## Native prerequisites and verification

Implementation requirements inferred from this contract:

- Extend current object-only signing/envelope boundaries to authenticated JSON
  string payloads. Verify signature before decryption; require the active owner
  and exactly the expected DM participants, authenticated author, canonical group
  ID and strict decrypted Chat schema. Reject wrong recipients, tampering,
  invalid padding/UTF-8 and malformed ciphertext without displaying plaintext.
- Expose validated imported keychain secrets/contacts from preserved backup
  options through a narrow private API. Cache messages by owner/backend;
  restoration combines available Archive history with that cache. IDs alone
  and private-key-only import cannot promise complete conversation discovery.
- Authenticate the selected peer; verify its advertised relay/archive support
  and implement unsolicited transaction routing alongside request responses.
  Use original signature for retries/dedup. Report Submitted or available-copy
  status without inventing delivery/read receipts. Archive retention/indexing
  and live unsolicited delivery remain integration evidence to obtain.
- Generate lower-boundary vectors using public unsafe scalar1/2, fixed timestamp,
  exact plaintext and fixed8-byte test salt against the published CryptoJS
  package and pinned Saito signing runtime, with no app initialization/network.
  Record group ID, shared X, KDF key/IV, formatter string, escaped signed bytes
  and signature. Verify both directions, Unicode, imported-DH preference and
  implicit fallback. Fixed salt belongs only in fixtures. These vectors are a
  proposed verification step, not generated or passed evidence in this note.
