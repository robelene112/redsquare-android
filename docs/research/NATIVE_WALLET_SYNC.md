# Native wallet synchronization and unfunded posting

Read-only foundation research for wallet import #7, ordinary posting #10 and
balance/SAITO sending #23, 2026-10-08. Applied the project-local research skill
and maintained app/Saito routing. Static deployed sources were inspected in
a network-disabled, read-only Docker runner; comparisons used public official
source. A pure installed WASM snapshot parser was exercised with published
synthetic keys only, without initializing Saito. No saved wallet/options,
environment values, database rows, live RPC, transactions or service changes
were accessed. Only this research file was written.

## Immediate findings

There is an existing **HTTP GET `/balance/<publicKey>`** endpoint. It returns
a textual UTXO snapshot at the node's chain tip, not a JSON number and not a
new WebSocket balance message. This is the browser wallet's own resynchronization
path. It can bootstrap native balance/slips without downloading the entire
active blockchain. Continued native sending still needs fresh state, pending
input reservations and reorganization handling.

An imported backup's zero balance, empty slips, a successful feed read or a
failed balance fetch does not verify zero. Snapshot results are trusted-node
assertions delivered over HTTPS, not signed consensus proofs; their tip/time
must be validated for the chosen backend. Available zero can coexist with
locked positive holdings or pending work. Preserve the accepted conservative
replacement safeguards when state is unknown.

Ordinary zero-value, zero-fee Saito transactions support identity inputs without
funding. Red Square uses this path. Native posting can construct/sign such a
post before funded-wallet synchronization, but source support is not a promise
of live relay acceptance, block inclusion or a specific delivery time.

## Version authority

Deployed reference: official commit
[`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
from Snake's maintained runtime pin, with installed saito-js/wasm 0.3.2.
Compared official prod
[`214504ecece76d1cd20a245fe4468eccbfca2951`](https://github.com/SaitoTech/saito/commit/214504ecece76d1cd20a245fe4468eccbfca2951)
and staging
[`e9f3635fa56450008774f2d8d76731121268fe7a`](https://github.com/SaitoTech/saito/commit/e9f3635fa56450008774f2d8d76731121268fe7a).
The balance-snapshot serializer, message enum and blockchain sync wire layout
are byte-identical at these revisions. The HTTP balance route is unchanged;
staging's server-file changes concern web fallback handling. Exact saito.io
deployment SHA remains unestablished. See [version provenance](SAITO_VERSION_PROVENANCE.md).

Current wallet code has meaningful accounting differences: prod/staging replace
the older pending-balance calculation; staging also excludes non-longest-chain
wallet slips from available balance/input selection and invalidates cached slips
past a reorganization ancestor. Preserve compatible wire semantics while
implementing correct native reservations and chain eligibility rather than
copying the older accounting defects. Primary comparisons:
[pinned wallet](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L698),
[current prod wallet](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/rust/saito-core/src/core/consensus/wallet.rs#L701),
[staging wallet](https://github.com/SaitoTech/saito/blob/e9f3635fa56450008774f2d8d76731121268fe7a/rust/saito-core/src/core/consensus/wallet.rs#L801),
[staging sync reorganization](https://github.com/SaitoTech/saito/blob/e9f3635fa56450008774f2d8d76731121268fe7a/rust/saito-core/src/core/network/sync.rs#L614).

## Existing balance/slip read API

Deployed `/opt/saito/node/lib/saito/core/server.ts:522–545`
[defines GET `/balance/:keys?`](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L522):

- One Base58 public key is the normal wallet request. Multiple keys are
  semicolon-separated. A 66-character compressed-point hex input is converted
  to Base58.
- It calls core `getBalanceSnapshot(keys)`, sets an attachment filename and
  returns `snapshot.toString()`. Errors return 404; they do not mean zero.
- No wallet signature or owner authorization is checked by this HTTP handler.
  The requested public keys identify public UTXOs; do not send private keys.
- Omitted keys request the whole snapshot. The WASM conversion also silently
  drops invalid public keys, potentially turning an invalid-only request into
  an empty key filter. Validate the requested key locally, always send exactly
  the active public key, and reject foreign-key response rows. Never use the
  unfiltered route as the mobile-wallet fallback.

The conversion is at
[saitowasm.rs:979–994](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L979)
and [1434–1450](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/saitowasm.rs#L1434).
The browser
[fetchBalanceSnapshot, wallet.ts:1334–1344](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L1334)
fetches `/balance/<key>`, parses text and invokes `updateBalanceFrom`.
Its catch/log path does not expose a reliable verified-zero state to copy.
Native code must distinguish HTTP, parse, freshness and state failures.

Actual public exposure and freshness on both approved domains were not probed
in this research. This endpoint is source-backed; `/wallet/balance`, a JSON
UTXO RPC or an FCM-style wallet-state request is not established by inspection.

## Snapshot wire format and inclusion rules

[BalanceSnapshot, balance_snapshot.rs:14–211](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/balance_snapshot.rs#L14)
uses newline-delimited text. First line:

```text
<chain_timestamp>-<latest_block_id>-<latest_block_hash_hex>.snap
```

Each following row has **six**, not five, space-separated fields:

```text
<Base58 public key> <block_id> <transaction ordinal> <slip index> <nolan amount> <slip type>
```

The comment's five-column sketch is stale; `get_rows()` includes slip type.
Header timestamp comes from `blockchain.last_timestamp`, not a signed current
wall-clock observation. The snapshot has no explicit requested-key echo,
signature, pagination token, row-count checksum or cryptographic absence proof.

[Blockchain generation, lines 2893–2941](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/blockchain.rs#L2893)
iterates the node's UTXO set where the spendability boolean is true, excludes
`Bound` slips, excludes block IDs older than the node's active genesis-period
cutoff, then filters by requested keys. There is no 10/100-row Archive limit;
do not treat it as feed pagination. A syntactically valid empty snapshot means
no matching included UTXOs at that node's reported tip, not universal absence
of assets, pending transfers or archive history.

Amounts and coordinates are integers. Core
[defs.rs:5–25](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/defs.rs#L5)
uses u64 currency and `100_000_000` nolan per SAITO. Parse/compute with exact
unsigned integers or `BigInteger`, not floating point. Reject negative values,
overflow, unsupported type codes, invalid compressed keys, inconsistent
duplicates, malformed/truncated HTTP bodies and unreasonable resource sizes.
Atomic replacement after complete validation protects the prior state on
failure. A transport-truncated body can still be valid text, so source format
alone is not a completeness proof.

Slip types are
[Normal 0, ATR 1, VipInput 2, VipOutput 3, MinerInput 4, MinerOutput 5,
RouterInput 6, RouterOutput 7, BlockStake 8, Bound 9, P2SH 10](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L17).
Do not spend special script/stake slips merely because they appear in a snapshot.

The 59-byte UTXO key is **not** the network-slip serialization order:
[slip.rs:132–143](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L132)
concatenates compressed key 33, block ID u64, transaction ordinal u64, index u8,
amount u64, type u8, with big-endian integers. Network slips put amount before
block ID, as documented in [the protocol reference](NATIVE_SAITO_PROTOCOL.md).

## Available balance, reservations and verified zero

Pinned
[get_available_balance, wallet.rs:750–767](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L750)
adds unspent, unreserved entries excluding `Bound` and `BlockStake`. Staging
additionally requires `lc: true`. The
[WASM balance getter](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_wallet.rs#L502)
returns available balance; it does not independently query the chain.

Input generation
[wallet.rs:777–880](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L777)
also skips slips before `minimum_block_id`. That cutoff is updated on chain
events to a narrower rebroadcast-safe window, at
[lines 227–243](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L227).
Thus snapshot-included amount, displayed available balance and immediately
selectable inputs are related but not interchangeable. Obtain the consensus
window/current chain context through compatible core sync or an explicitly
implemented backend adapter; do not invent a hardcoded genesis period.

The pinned
[snapshot update, lines 2195–2224](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L2195)
clears and repopulates slips as unspent/longest-chain, leaving pending
transactions unchanged. Native code must reapply local pending reservations
after a fresh snapshot; otherwise a transaction still awaiting inclusion could
be spent twice. It must not copy this function as an authoritative pending-work
reconciliation API. Imported cached slips and balances also require fresh
reconciliation before spending.

For #7 safeguards, use these distinctions:

| State | Meaning for replacement |
| --- | --- |
| Backup/cache says zero; no successful fresh reconciliation | Unknown; require backup under the agreed rule |
| Offline, failed/malformed/foreign-key snapshot, stale/zero tip, unresolved reorganization | Unknown; preserve existing wallet and safeguards |
| Fresh trusted-backend snapshot has ordinary positive spendable slips | Positive; require backup |
| Available sum is zero but positive locked stake, protected holdings or pending/unknown work exists | Do not present this as permission to discard an empty wallet; backup/acknowledgment protections still apply |
| Completely validated current trusted-backend snapshot has no relevant positive holdings, with local pending/identity obligations accounted for | Eligible for a backend-verified zero classification, subject to accepted registered-name/pending-work warnings |

The last row is a proposed native trust/state contract, not an existing signed
network guarantee. Bind it to public key, selected backend, observed tip/hash,
observation time and reconciliation generation; invalidate on identity change,
staleness or fork uncertainty. Pending work on another device cannot be proven
absent by this snapshot. If the app cannot establish the needed freshness or
obligations, retain **unknown** rather than infer zero. The accepted rules are
in [project context](../PROJECT_CONTEXT.md), including backup proof bound to
outgoing identity. No new product question is required for this conservative
default.

## Lite/SPV protocol and incremental synchronization

The inspected protocol supports authenticated peer chain sync, not a standalone
WebSocket request that returns spendable wallet slips. The active
[message enum](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/message.rs#L213)
includes:

| Type byte | Payload |
| --- | --- |
| 15 | KeyList: concatenated compressed 33-byte public keys |
| 21 | RequestBlockchain |
| 22 | Blockchain response |
| 6 | BlockReference announcement |
| 17 / 18 | RequestGenesisBlockReference / GenesisBlockReference |
| 23 | RequestBlockReference structure; the inspected routing arm is empty, so do not depend on it as a working tip lookup |
| 24 / 25 | RequestEndpoint / Endpoint for block-fetch origin metadata |

Legacy type 5 is explicitly rejected. Handshake and API messages remain those
in [native protocol research](NATIVE_SAITO_PROTOCOL.md). Type 15 updates peer
key-list metadata through
[routing_thread.rs:327–328](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L327).
Watching a public key is a filter, not proof that it is owned, and never requires
uploading its private key.

[RequestBlockchain wire layout](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/blockchain.rs#L6)
is big-endian latest-known block ID u64, block hash 32, fork ID 32, sync type u8,
public key 33, key count u32, then key-count × 33-byte keys. Minimum payload
110 bytes before type byte; sync types Full 0, SPV 1, Ghost 2. Use the supported
SPV path rather than infer Ghost is the current default. Core
[sync.rs:339–407](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/sync.rs#L339)
chooses Full/SPV based on mode and includes its own key.

Type-22 response has 192 fixed bytes **before** its u32 reference count,
so its minimum payload is 196 bytes, then 53-byte BlockReferences: ID u64,
hash 32, timestamp u64, transaction count
u32, golden-ticket boolean u8. Response header carries latest tip, fork ID,
shared ancestor and payload earliest/latest boundaries. Chunk bound is 128
references. Sources:
[blockchain.rs:15–59](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/blockchain.rs#L15),
[block reference serializer](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/block.rs#L54).

Fresh SPV requests with latest-known ID zero begin at
`max(genesis_block_id, latest_id - 9)`, rather than replaying all retained
wallet history. SPV reference transaction counts are 1/0 flags for whether the
requested key list has relevant transactions, not the full block's count.
[sync.rs:490–543](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/sync.rs#L490).
Therefore the recent SPV bootstrap alone cannot reconstruct every old unspent
slip; the snapshot is valuable for that bootstrap.

Fetch relevant block bytes through existing HTTP
[`/lite-block/<hash>/<publicKey>`](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L334).
The route uses the connected peer's key list when available, otherwise just
the requested public key, and returns a binary lite block despite text-like
HTTP headers. Respect the app's approved origins when deriving fetch URLs
from peer announcements. Header/reference tracking, output discovery, spent
input removal, pending reconciliation, rebroadcast transitions and fork rollback
are real native wallet work beyond a feed socket.

The pinned
[SPV block validation branch](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/block.rs#L2898)
skips full block validation. Do not market these snapshot/SPV paths as independent
full consensus verification. An owned adapter can make readiness and derived
state explicit, but its answer is still an operator assertion unless a separate
proof scheme is actually implemented.

## Funded transactions, fees and unfunded posts

[Wallet.create_transaction, lines 1007–1111](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1007)
requests input value equal to recipient amounts plus fee, creates recipient
outputs and change, and returns an insufficient-balance error if a positive
request cannot be covered. Fee is not a separate network field: it is the
difference between total input and output amounts, per
[transaction.rs:532–578](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L532).
Use exact integers, recipient/amount bounds, special-slip eligibility and
durable reservations before signing. Native implementation should roll back
candidate input reservations atomically on construction failure; the older
`generate_slips` marks entries spent before discovering a shortage.

The JS
[createUnsignedTransaction helper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L78)
defaults amount and fee to zero, defaulting recipient to the local identity.
[Red Square createTweetTransaction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/redsquare/redsquare.js#L1132)
calls this zero-payment helper, adds `RedSquare/create tweet` data and recipient
keys, then signs/propagates. These are separate from SAITO payments.
The separate
[default-fee helper, lines 42–74](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L42)
can fall back to zero fee on insufficient balance caused by the selected fee.
This is not a dynamic fee quote or an instruction to silently alter an approved
SAITO transfer fee.

Zero-amount slips
[pass UTXO validation without needing an existing UTXO](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L223).
Author signature/routing/value checks still apply. Be precise about reference
construction: pinned `generate_slips(0)` supplies a zero identity input and
zero change output; `create_transaction` also inserts an initial zero input
before appending those generated inputs. The standard one-recipient zero-fee
construction consequently has two zero identity inputs and two zero outputs
in this source, rather than the minimal one-input/one-output fixture used for
manual signature tests. That earlier fixture proves serialization, not byte
identity with wallet-generated transactions. Confirm whichever native shape
is used against reference validation and live submission in the posting ticket.

Actual propagation uses type **4**, including a routing hop signed toward the
identified receiving peer, through
[network.rs:113–137](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/network.rs#L113).
An application RPC result or completed socket send is not inclusion.
Zero fees are not categorically rejected by the inspected mempool; block
production still depends on routing-work/timing conditions at
[mempool.rs:341 onward](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/mempool.rs#L341).
No unfunded posting transaction was submitted by this research.

## Independent synthetic parser references

The installed pinned `WasmBalanceSnapshot.from_string` parsed and byte-for-byte
round-tripped these bodies. The published scalar-1 public key is a test fixture,
not a user identity. Final newline is intentional.

Empty snapshot:

```text
1700000000000-100-0101010101010101010101010101010101010101010101010101010101010101.snap
```

Mixed normal/stake snapshot:

```text
1700000000000-100-0101010101010101010101010101010101010101010101010101010101010101.snap
jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR 90 2 0 125000000 0
jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR 91 3 1 500000000 8
```

Pure parsing confirms six fields and header format. Applying the referenced
availability rule yields 125,000,000 nolan = 1.25 available SAITO, with another
500,000,000 nolan in BlockStake excluded from that available sum. This arithmetic
is a source-derived expectation, not an initialized-wallet balance test.
Reference parser bridge:
[wasm_balance_snapshot.rs:35–43](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_balance_snapshot.rs#L35).
Protocol serializers also include independent
[RequestBlockchain round-trip fixtures](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/blockchain.rs#L283)
and [BlockReference fixtures](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/block.rs#L102).
Existing signing/transaction synthetic vectors remain in
[the protocol reference](NATIVE_SAITO_PROTOCOL.md).

## Missing API capabilities and implementation boundary

Established: per-key HTTP snapshot, authenticated chain references, lite-block
fetch and normal signed transaction propagation. Not established: a native
JSON spendable-slip service, signed balance/absence proof, complete remote
mempool/pending-wallet enumeration, dynamic fee estimate or authoritative
wallet-ready endpoint. Archive payment history is not current spendable UTXO
state and cannot substitute for it.

Implement the existing protocol first where it supplies the accepted behavior.
If native code needs explicit node readiness, current genesis-window metadata,
stable transaction-status/pending reconciliation or an easier JSON projection
that public sources do not expose, build a reviewed owned adapter under separate
production authorization rather than invent an endpoint. Using that adapter
for saito.io requires either a compatible official service or a clearly defined
independent observer/trust path. The source inventory does not authorize node
changes, upload of wallet keys, or replacing accepted sending/backup safeguards
with an optimistic cached number.
