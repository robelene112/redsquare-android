# Native wallet import safeguards

Source-only assessment for ticket #7, observed 2026-10-08. Read maintained Snake
routing/operations, this app's operations and `saito-dev` first. Inspected only
source through a network-disabled, read-only Docker mount with an empty
environment. `/opt/saito` HEAD is
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
with node source under `/opt/saito/node`. Referenced source excerpts match that
Git object; direct public raw downloads additionally matched the local Git
bytes for the snapshot serializer, message enum and server source. This says
nothing about served bundles or live chain readiness. No saved options,
database, environment, private keys, balance requests or broadcasts were read
or used. Snake checkout HEAD was `12e0194954d1d74cdd450f5738c9f53de22bf87f`.

## Native balance path and its trust boundary

The browser wallet import already fetches **`GET /balance/<publicKey>`** and
updates its slips from the result. The pinned HTTP handler accepts a single
Base58 key or semicolon-separated keys, obtains a core snapshot and returns
its text. Native Android can use the same route on the selected approved HTTPS
backend, independently of the existing type-12 Archive RPC. Never request an
empty key list: that asks for the entire UTXO set. This is a public endpoint;
it requires neither a wallet signature nor the WebSocket handshake.
[HTTP handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L522),
[browser import fetch](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1334).

The first line is `<timestamp>-<blockHeight>-<64-hex-blockHash>.snap`.
Each subsequent row has six space-separated fields:

`publicKey blockId transactionOrdinal slipIndex amount slipType`

The amount is an integer in Nolan, with **100,000,000 Nolan per SAITO**.
Parse integer amounts exactly, validate numeric ranges and slip types, and
require every row to belong to the requested outgoing identity. The hash is
the chain tip hash, not a checksum over the rows. There is no snapshot
signature, request nonce, UTXO inclusion proof or proof of absence.
[Serializer/parser](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/balance_snapshot.rs#L48),
[units](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/defs.rs#L25).

The server selects currently valid UTXOs for the key, excludes Bound slips and
slips outside the genesis period, and uses its last accepted block timestamp.
It does not filter staking slips by unlock status. Thus the sum is a useful
conservative asset indicator, not a claim about immediately spendable funds.
Updating from a snapshot explicitly leaves pending transactions unchanged.
[Snapshot selection](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/blockchain.rs#L2893),
[block timestamp](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/blockchain.rs#L2245),
[pending preservation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L2195).

**Recommendation, not an existing protocol guarantee:** obtain a fresh bounded
HTTP response through the selected hostname's verified TLS connection, disable
cache reuse and cross-host redirects, and bind the result to that backend,
outgoing public key and assessment generation. A positive valid sum can require
backup immediately. A zero sum is only **backend-reported zero** after complete
successful parsing and an explicit readiness/freshness policy. Missing, empty,
truncated, oversized, malformed, timed-out or unready results are `unknown`.
Reject an uninitialized zero-height/zero-hash tip as zero evidence. Fetch time
alone cannot prove freshness: its timestamp describes a block, so any age
threshold must remain an explicit product policy rather than a protocol fact.
No inspected source supplies a universal freshness deadline or independently
proves that an empty snapshot covers a fully synchronized ledger.

Snake's existing [native check](/home/david/snake-stickerbook/snake.js:525)
requires successful bounded fetch, a syntactically valid header, requested-key
rows and an exact integer sum. It **does not** check tip age/readiness or compare
the tip with another peer. Preserve its `positive / zero / unknown` semantics
without describing that check as cryptographic zero proof.

## Light-peer messages

The pinned message enum contains no dedicated balance/UTXO request. Type **15**
registers a watched key list (concatenated 33-byte keys); **21 / 22** exchange
blockchain requests/chunks, and **6** carries a block reference. A type-22
response includes advertised latest height/hash and up to 128 references;
SPV sync type is 1. References lead to filtered HTTPS block downloads at
`/lite-block/<hash>/<publicKey>`. An empty reference chunk is not proof of a
zero wallet balance. The type-23 `RequestBlockReference` routing branch is
currently a no-op, so it is not a usable shortcut to request a tip.
[Wire enum](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/message.rs#L164),
[chain wire](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/blockchain.rs#L6),
[routing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L215),
[fetch URL](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/peer.rs#L244).

A selected authenticated peer's advertised tip could corroborate snapshot
readiness, with race/reorganization handling. That requires new native message
parsing/state beyond today's handshake and type-12 RPC support; it still trusts
the selected peer. Full independent light-wallet verification additionally
needs block/transaction validation, fork handling and UTXO updates. The HTTP
snapshot is the source-supported minimal import guard and needs no backend
configuration change. Public static official `prod` pin
`214504ecece76d1cd20a245fe4468eccbfca2951` has the same HTTP route and six-column
serializer; this is source compatibility, not a live endpoint test.
[Official route](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/core/server.ts#L522).

## Metadata and pending-work indicators

Browser `exportWallet()` serializes the full options object except `games`.
Import JSON is not just two keys; preserve opaque metadata in protected wallet
backup storage without executing browser modules or adopting imported peers.
[Export](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1244).

| Source field or state | Guard meaning |
| --- | --- |
| `crypto[ticker].isActivated`, `.address`, `.balance`; `wallet.preferred_crypto` | Other-crypto indicators. Stored balances are cached. An activated or otherwise indicated unsupported crypto cannot be declared freshly zero by native SAITO lookup. Preserve the payload; classify unverifiable assets as unknown. |
| `mixin.user_id`, `mixin_legacy`; NFT metadata | Other asset/account indicators requiring preservation. Do not interpret, log or discard secret-bearing module payloads. SAITO sum zero cannot establish that all these assets are absent. |
| `keys[]` entry matching the outgoing `publicKey`, with `identifier` | Cached name warning. Snake strips the domain suffix and validates the local name. This is not fresh Registry ownership proof. An incoming file's unrelated key entries do not describe the outgoing identity. |
| Top-level `pending_txs`; legacy wallet pending fields | Serialized pending-work indicators, not confirmed history. Malformed content is unknown rather than clear. Do not broadcast imported pending transactions as an import side effect. |
| Native drafts/outbox/in-flight work | Assess the currently active identity's own local state separately. Absence of such entries in imported JSON cannot clear outgoing work. |

Sources: [crypto activation/cache](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/templates/cryptomodule.js#L261),
[crypto persistence](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/templates/cryptomodule.js#L508),
[Mixin persistence](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/mixin/mixin.js#L1376),
[key identifier](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/keychain.ts#L575),
[Snake normalization](/home/david/snake-stickerbook/shared.js:17),
[wallet pending persistence](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L775).

In particular, browser pending transactions are saved in **LocalForage**, while
server mode saves top-level `options.pending_txs`. Ordinary browser options
export does not include that separate store. A backup lacking `pending_txs`
therefore does not prove clear pending state. Snake also has separate signed
and unsigned receipt outboxes and an unsigned Daily queue, with explicit
acknowledgment before a switch.
[Pending restore](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L648),
[receipt guard](/home/david/snake-stickerbook/web/wallet-loader.js:587),
[Daily queue](/home/david/snake-stickerbook/web/daily-client.js:136).

## Preserve the actual Snake guard

For a **different identity**, Snake assesses the **outgoing current wallet**,
requires its candidate-bound downloaded backup when any balance is positive or
unknown, and requires acknowledgment for pending/unknown work or a name.
Only known zero across SAITO and all active crypto modules avoids that backup
requirement. Unsupported crypto APIs already yield unknown. It rechecks at
activation, verifies candidate/current-key binding, writes a durable rollback
record before mutation, verifies persisted identity afterward and blocks further
activation if rollback fails. Same-identity restoration has separate handling.
[Assessment](/home/david/snake-stickerbook/snake.js:495),
[activation](/home/david/snake-stickerbook/snake.js:961).

Native feasibility is straightforward for identity validation, fixed-host HTTP
snapshot parsing, metadata warnings and the guarded atomic transition using
the existing native wallet store. Conservative unknown states cover unsupported
assets, unavailable freshness evidence and browser-only pending history;
they must not silently become zero or clear. This research does not establish
live balance compatibility, choose a freshness threshold, or change import
implementation.
