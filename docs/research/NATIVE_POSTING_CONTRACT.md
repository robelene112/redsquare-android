# Native text posting contract

Read-only research for ticket #10, 2026-10-09. Applied the project-local
`research` skill. Read this repository's instructions and operations, the
maintained Snake instructions/operations/runtime pin, and `saito-dev` before
accessing named public deployed source files. No application initialization,
private options, wallet state, database reads, builds, live transactions or
deployment changes occurred. Only this document was written.

## Source authority

The maintained compatibility pin is
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
with `saito-js` and `saito-wasm` 0.3.2, Node 24.13.0 in
`/home/david/snake-stickerbook/scripts/saito-runtime.json`. Installed public
package manifests still report both packages at 0.3.2 during this inspection.
The previously exported `.scratch/saito-reference/redsquare-9a4ca7e.js` and
today's named deployed `/opt/saito/node/mods/redsquare/redsquare.js` have identical
SHA-256 `e2cf08d95ed0cebc4b4e1106134ccc13c83b2a5814f1b0e9b31579a4c14a7ca8`.
This is a file comparison, not a newly verified deployed Git revision. Existing
operations record unavailable Git-object reads; none were repeated or repaired.

Named deployed files were read through Docker with network disabled, a read-only
root mount/read-only container, chroot and an empty environment. These hashes
identify additional source observations, not an official server deployment pin:

| File below `/opt/saito/node/` | SHA-256 |
| --- | --- |
| `mods/redsquare/lib/ui/overlays/compose.js` | `35819245c4a2e6fb37c69cb651d32d2343a9c76daf297f0a56059793dde3c771` |
| `mods/redsquare/lib/ui/overlays/compose.template.js` | `bf7b8819175e71a6d8b83d906be10be1de602b5f59f0b8869cb37c00e29b3400` |
| `mods/redsquare/lib/tweet.js` | `07ea0b50eac7c21ac8452b51fdb7bdda27efb5de7c62e5288a220ffaf23bf908` |
| `lib/saito/wallet.ts` | `1819d89f7bdff56127532f12fd29c4de7de902c4401b7cce9b8961f54e9560b3` |
| `lib/saito/network.ts` | `e91eeabcb9a7bb030367ab1d4616005912225a62b4cf41a44715ce48d1d51530` |
| `node_modules/saito-js/saito.js` | `295d8a1709c629687c9750f86b6a55a72d8589478e36acced06245cfa4bdf627` |
| `node_modules/saito-js/lib/transaction.js` | `c5332473326259331b0e3c9595de3bf46d9735ecb2d87bbbc9c732b55a085420` |

Existing [native protocol references](NATIVE_SAITO_PROTOCOL.md),
[first-use prerequisites](FIRST_USE.md), and
`build-support/references/signed-reference.cjs` contain independent serializer
and signature fixtures. They prove construction compatibility, not actual post
acceptance. Pinned Rust sources below were additionally inspected on their
official raw GitHub URLs.

## Public payload and reply recipients

The signed message is UTF-8 JSON with `module: "RedSquare"`,
`request: "create tweet"`, and object `data`. For a text root, `data.text` is
the original composer text; omit parent/thread fields. The tweet model derives
its root thread ID from the transaction signature when `data.thread_id` is
absent or empty. For replies, `data.parent_id` is the immediate parent's
signature and `data.thread_id` is the parent's thread ID, falling back to its
signature. A reply to a reply retains the original root thread. Sources:
deployed `compose.js:560-574`, `tweet.js:186-192`, and the pinned
[createTweetTransaction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1101).

Replies collect the parent author first, then the parent's `tx.to` public keys,
deduplicating while retaining order. The constructor adds recipients other
than the current author as zero-valued outputs after the wallet's self output.
The first other-than-author recipient matters: Red Square uses it as the reply
target for archive field3. Do not reorder that list alphabetically. Sources:
deployed `compose.js:594-612`, pinned `redsquare.js:1139-1143,1327-1332,1381-1383`.

`createTweetTransaction` normalizes an explicitly supplied `data.mentions` into
an array, but the inspected composer does not populate it or resolve textual
`@name` tokens into keys. `collectRecipientKeys` only inherits parent identity
and parent outputs. Do not claim typed usernames automatically produce mention
delivery. Root recipient selection/mention resolution needs a separately
verified Registry/user-selection contract. This research establishes the
accepted optional field shape, not a new mention UX or notification guarantee.
Sources: deployed `compose.js:560-612`; pinned `redsquare.js:1126-1129`.

## Exact zero-payment construction

The web wallet defaults recipient to its own identity and amount/fee to zero
(`wallet.ts:78-87`). The core constructs a Normal transaction without funded
inputs when the required amount is zero, using a zero-valued identity input
and a zero-valued recipient output. Additional social recipients require no
payment. Source:
[wallet construction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L936).

Use one input to the signer, a first output to the signer, then distinct
recipient outputs. All amounts, block IDs and transaction ordinals are zero;
input index is zero, Normal slip type is zero. Assign each output its sequential
u8 index before signing. Replacements is 1, transaction type is Normal/0,
timestamp is milliseconds. The deployed JS wrapper sets `Date.now()` when
creating the transaction (`saito.js:205-215,233-234`).

All integers use big-endian encoding. Each network slip is 59 bytes:
compressed public point33, amount8, block ID8, ordinal8, index1, type1. Signature
slips omit block ID and ordinal, so they are 43 bytes. Source:
[slip serialization](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L175).

The signature preimage is timestamp8, input signature slips, output signature
slips, replacements4, transaction type **4**, exact UTF-8 message bytes. Sign
using the existing BLAKE3/secp256k1 compact low-S contract; the signing helper
hashes the supplied preimage once. Network transaction layout is input count4,
output count4, message length4, hop count4, signature64, timestamp8,
replacements4, type **1**, network slips, message, hops. Counts and hops are
outside the transaction signature preimage. Source:
[transaction serialization/signing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L735).

Keep exact signed bytes, timestamp and signature for retry/reconciliation;
creating another timestamp/signature creates another distinct post. This is a
native idempotency recommendation supported by the signed identity contract,
not an observed universal network deduplication guarantee.

## Routing, transport and what confirmation proves

After authenticating the selected peer, append a routing hop from the author
to that peer. Its signature signs raw compact transaction signature64 followed
by the decoded peer point33. Apply the existing signing helper once, not to
hex text and not to a separately prehashed digest. Serialize the hop as
author point33 + peer point33 + hop signature64. The destination is the
authenticated connected peer, not a social recipient. Source:
[Hop::generate](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/hop.rs#L39).

The web propagation path clones a transaction for each connected identified
peer, adds that hop, and sends it. Source:
[Network::propagate_transaction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/network.rs#L78).
The native selected-backend policy should send only through the selected
session, never silently fan out to the other configured backend.

Broadcast is binary byte **4** followed by the complete network transaction,
including its message and hop. There is no RPC request index, detached Archive
envelope, type12 application wrapper, or JSON acknowledgement. Source:
[Message serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/message.rs#L39).
Incoming transactions are forwarded to the verification thread, without a
correlated type13 result:
[routing transaction handling](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L347).

A socket enqueue/send success establishes only local transport submission. It
does not establish remote receipt, indexing, block inclusion or durable history.
The inspected Red Square application-RPC handler does not handle `create tweet`
as an acknowledged write; the web path signs and propagates, then locally
invokes `receiveTweetTransaction` for optimistic presentation. Server
`onConfirmation` processes `create tweet` when `conf == 0` and saves it through
the module archive path. Sources: pinned `redsquare.js:1011-1094,1505-1512,2117-2132`;
deployed `compose.js:718-726`.

An exact-signature Archive query followed by verified signed-envelope matching
can establish that the selected backend currently serves that post. Call that
archive availability/observation, not independently proven chain inclusion.
Unsigned archive counters, `block_id` or `block_hash` are not a native chain
proof. An absent row may reflect propagation delay, retention or moderation;
retain the exact candidate and explicit uncertainty. These are implementation
recommendations using the already verified Archive read boundary.

## Practical text bounds

The deployed composer defaults to **500 UTF-16 code units**, as JavaScript
`String.length` counts them. The counter uses original text; submission checks
the trimmed text. Whitespace-only text without images is rejected, but the
original untrimmed text is used in `data.text`. There is no `maxlength` on the
textarea. Above 500 trimmed units, submission is blocked only if the web
wallet's reported pending balance is zero; nonzero balances allow longer posts.
Sources: deployed `compose.js:5,20,434-455,560-564,620-645`,
`compose.template.js:54-58`.

Therefore 500 is an unfunded web-composer threshold, not a proven consensus or
server maximum. A native hard cap of 500 for every wallet would reduce existing
funded capability. A larger finite native safety bound and funded/unknown-balance
policy must be identified explicitly as native validation, not misrepresented
as Saito's protocol limit. This inspection does not establish a maximum accepted
transaction size or official `saito.io` composer deployment. Controlled-peer
tests and independent reference construction remain necessary; no live post
acceptance, physical performance or closed-app notification delivery is claimed.
