# Native funded SAITO transfers

Source-only research and pure synthetic checks, 2026-10-09. `/opt/saito` HEAD
remains [`9a4ca7e9442eb5e872168ced361ce741d7d139d7`](https://github.com/SaitoTech/saito/commit/9a4ca7e9442eb5e872168ced361ce741d7d139d7),
node source `/opt/saito/node`. Referenced wallet, slip, transaction, mempool,
block, snapshot, WASM-wallet and network files match that Git object. This is
checkout evidence, not served-bundle or live interoperability evidence. Read
maintained operations, `saito-dev`, [wallet snapshot research](NATIVE_WALLET_SYNC.md),
[import safeguards](NATIVE_WALLET_IMPORT_ASSESSMENT.md) and
[broadcast contract](NATIVE_TRANSACTION_BROADCAST.md). No production requests,
real balances/options/wallets/database, initialization, broadcasts or changes.
Only this note is added; no implementation/test/dependency changes or commit.

## Snapshot inputs and spendability

Native can fetch selected-backend HTTPS `/balance/<activePublicKey>`. Its
header is `<timestamp>-<height>-<blockHash>.snap`; rows are:

`publicKey blockId transactionOrdinal slipIndex amount slipType`

Coordinates and amounts are unsigned integers: block/ordinal/amount u64,
index/type u8. Currency is Nolan, **100,000,000 per SAITO**. Preserve all input
fields exactly; do not replace input index with its position in the new
transaction. Validate ownership, compressed curve point, row uniqueness and
integer ranges before atomically replacing observations.
[Snapshot format](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/util/balance_snapshot.rs#L56),
[currency](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/defs.rs#L5).

The **59-byte UTXO identity** is `point33 || blockId8 || ordinal8 || index1
|| amount8 || type1`; network slip order instead is `point33 || amount8 ||
blockId8 || ordinal8 || index1 || type1`. All integers are big endian. Reserve
by this complete identity, not amount, signature or address alone.
[Identity/network encodings](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L132).

| Type | Interpretation for ordinary wallet selection |
| --- | --- |
| 0 Normal, 1 ATR, 5 MinerOutput, 7 RouterOutput | Wallet-owned ordinary fee-bearing input classes recognized by the authorizer validator. Preserve original type when consumed; new payment/change outputs use Normal. |
| 8 BlockStake, 9 Bound | Separate stake/NFT obligations; ordinary available balance excludes these. Snapshot presence must not make them ordinary payment funding. |
| 10 P2SH | Script authorization path, not an ordinary wallet-key spend. |
| 2 VipInput, 3 VipOutput, 4 MinerInput, 6 RouterInput | Defined legacy/protocol types, not interchangeable with Normal. Historical issuance loader maps deprecated VipOutput to Normal; do not reinterpret arbitrary snapshot rows that way. Unknown/special positive assets remain unresolved rather than silently spendable. |

This is ordinary-SAITO classification, not evidence that every listed slip is
currently available. Snapshot selection excludes Bound and expired entries,
but does **not** filter stake maturity. Its age cutoff is
`tip.saturating_sub(genesis_period)`; wallet selection uses
`tip - genesis_period + 2` when `tip > genesis_period`, otherwise 1. A snapshot
therefore includes edge entries the wallet may currently skip. Header alone
does not provide genesis period or stake-unlock parameters; obtain compatible
network parameters/chain context rather than guessing them. Available balance
also differs from immediately selectable balance at that edge.
[Types](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/slip.rs#L17),
[authorizers/scripts](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L936),
[snapshot cutoff](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/blockchain.rs#L2893),
[wallet cutoff](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L227),
[legacy issuance mapping](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/storage/storage.rs#L273).

HTTPS authenticates the selected server, not the snapshot as a ledger proof.
It has no signature, nonce, row count, UTXO proof or independent absence proof.
Bind freshness/readiness to backend, active identity and generation; missing,
malformed or stale information is unknown. Cached balances, header hash alone
and restored options cannot authorize a current spend.
[Trust boundary](NATIVE_WALLET_IMPORT_ASSESSMENT.md#native-balance-path-and-its-trust-boundary).

## Funded construction, fee and signing

Core `create_transaction` selects positive inputs covering payment plus fee,
then **prepends one zero Normal author input**. Payment output comes first,
followed by one author change output, including when change is zero. Selection
uses hash-set order in production; tests alone sort by amount. A native
deterministic selection order need not reproduce that incidental hash order.
Construct from validated/reserved inputs; do not use the stateful WASM wallet
constructor in an independent reference runner.
[Core constructor](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1007),
[selection](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L777),
[stateful WASM bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_wallet.rs#L62).

`change = selectedInputSum - payment - fee`; fee is the implicit difference
between input/output totals, **not a separate wire fee field**. Defaults use
zero fee, but a persisted browser preference can differ. No inspected source
guarantees current fee-free acceptance or a universal recommended fee.
Use exact decimal-to-Nolan conversion with at most eight fractional digits,
checked u64 sums and positive payment. Avoid the browser conversion's Number
round-trip or accepting a value that silently rounds. Display and bind the
actual fee before authentication.
[Fee totals](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L532),
[browser fee/default and conversion](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L33).

Browser payment metadata uses **module `Saito`**, request `crypto payment`,
then `amount` as SAITO text, `from`, `to`, `hash` (application deduplication
identifier) and `memo`. Ledger amounts/recipient come from slips, not those
claims. `signAndEncryptTransaction` can encrypt metadata when the recipient
has a saved DH secret; amount/address slips remain public. Plain compatible
metadata is valid construction, but memo privacy must not be assumed.
[Payment sender](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L445),
[module name](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L143),
[encryption encoding](NATIVE_CHAT_CRYPTO.md).

Normal transaction type0, replacements1, millisecond timestamp, exact UTF-8
message. Signing assigns output indices 0,1,... **before** hashing. Author
preimage is timestamp8, 43-byte signature slips, replacements4, type4 and raw
message; BLAKE3 once, compact low-S secp256k1 signature. Input block/ordinal
coordinates are intentionally absent from that preimage. Preserve exact
original network bytes and UTXO reservations; signature verification alone
does not authenticate those coordinates. Consensus assigns new output block
coordinates during inclusion. Maximum accepted input/output counts are 255
each; the compatibility zero input consumes one input slot. Never silently
truncate selections to satisfy that limit.
[Signing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L813),
[count bounds](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L366),
[broadcast/routing bytes](NATIVE_TRANSACTION_BROADCAST.md).

## Reservations, pending and rejection

Source selection marks slips spent **before** reporting insufficient funding;
snapshot reload restores them unspent while leaving pending transactions.
Pending balance includes anticipated self/change outputs; it is not another
spendable pool. Source propagation adds its author's transaction to pending
before sending. Browser pending persistence is separate LocalForage and an
options backup cannot prove that no sends exist.
[Snapshot reload](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L2195),
[pending propagation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/network.rs#L86),
[pending persistence](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L648).

**Native recommendation:** select without mutating observed UTXOs, validate
full funding, then atomically persist a wallet/backend-bound transfer intent,
signed bytes and reservations before the first possible broadcast. Subtract
reservations after every snapshot refresh and restart; serialize competing
sends. Authentication must bind the reviewed recipient, payment, fee, identity
and current operation, with a live connection rechecked at send. Cancel an
unsent attempt safely. Once delivery is uncertain, keep it pending/unknown;
do not unlock inputs merely because the socket closed or a timeout elapsed.
Any deliberate retry preserves the original signed transaction. Re-signing
with a fresh timestamp creates another spend attempt. Transfers use neither
automatic offline post/Chat queue nor an imported pending file as broadcast
authorization. These are application requirements, not source guarantees.
[Accepted wallet scope](../PROJECT_CONTEXT.md#decisions-already-made).

Type4 propagation has no correlated acceptance/rejection RPC. Positive inputs
must exist as spendable exact UTXO keys; output totals cannot exceed inputs;
author/hop signatures and input authorizer constraints must pass. Mempool
rejects another positive spend of an already held UTXO and deduplicates by
signature. A chain update can invalidate previously acceptable inputs. These
checks do not expose a native final rejection receipt, so absent Archive rows
or snapshot changes alone cannot distinguish rejection, pending delivery,
another spend or reorganization.
[UTXO validation](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/transaction.rs#L1682),
[mempool conflicts](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/mempool.rs#L139),
[chain cleanup](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/blockchain.rs#L1024).

## Confirmation evidence and gaps

Browser `OnTransactionSent` follows wallet processing of a longest-chain block
and removal of a matching pending transaction, with signature/block fields.
It is not a socket-send callback. However, pinned SPV block `validate()`
explicitly returns early without the normal block signature/consensus checks.
Reproducing that event must not be described as independent validation.
Authenticated block references and `/lite-block/<hash>/<walletKey>` can provide
selected-peer block data; native currently needs the corresponding parsing,
transaction matching, chain tracking and reorganization handling.
[Wallet event](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L343),
[SPV validation branch](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/block.rs#L2899),
[light-peer protocol](NATIVE_WALLET_IMPORT_ASSESSMENT.md#light-peer-messages).

Distinguish locally constructed/sent, unresolved pending, backend-reported
inclusion and independently verified inclusion. Stronger proof requires the
matching transaction, block commitment/header validation and compatible chain
context, not unsigned Archive `block_id` or a fresh HTTPS balance alone. Finality
depth, trusted starting checkpoint/network parameters, retention and unresolved
input-release policy remain implementation decisions requiring explicit
evidence. This research establishes none of them and performs no consensus
admission or live inclusion check.

## Executed pure funded reference

Used the existing [network-disabled read-only runner](NATIVE_SAITO_PROTOCOL.md#reproducing-reference-calculations-safely),
Node24.13.0, installed WASM0.3.2, Elliptic6.6.1. WASM SHA-256:
`f59be69dbb949368ce1fc7ef47d414cbe0281a86a206fe4bfb1128a4d087e01e`.
Only pure `WasmTransaction`/`WasmSlip`, `hash`, `sign_buffer` and signature
verification functions were called; no Saito/wallet initialization.

Published test scalars 1/2/3 produce author
`jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR`, recipient
`pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU`, and routing peer
`tENBoQttzyKaCW6d61tQjceaVA3GsZ2WrBLngrYHcXEx`. They are synthetic public
test identities, unsuitable for real funds. Timestamp1700000000000, type0,
replacements1; inputs all author:

| Amount Nolan | Block | Ordinal | Index | Type |
| --- | --- | --- | --- | --- |
| 0 | 0 | 0 | 0 | 0 |
| 500000000 | 2000000 | 7 | 2 | 0 |
| 250000000 | 2000001 | 9 | 0 | 7 |

Outputs: recipient325000000/index0, author415000000/index1, both Normal with
initial block/ordinal0. Fee10000000 Nolan. Exact 233-byte message:

```json
{"module":"Saito","request":"crypto payment","amount":"3.25","from":"jesTu2BpszP8DKSoi1R5G6ggjHrsrVnboLdx6V47vkoR","to":"pncSjsftdmspedXgmCkkHLRCeak12ex2WVrne2qdefSU","hash":"synthetic-transfer-01","memo":"Native transfer reference"}
```

WASM signature hash matched manually assembled preimage; WASM and independent
Elliptic verified author/routing signatures; actual WASM deserialize/reserialize
matched complete bytes. Author preimage464 bytes, network621, routed type4
frame752. Preimage BLAKE3:
`5844cda6d091a7734ffdba6c8617c6b2d8e9a5c5cbe68aade34550462cf65648`;
network BLAKE3:
`cc3e274ec4089ac08145a42e215e28287122fc028b641ef3ee17e9723d7d5717`;
routed-frame BLAKE3:
`23b58fd19930513d56035ab99bf4926fba58b58774f3c379e53882cf352c7ecf`.
Compact author signature:
`ca7881660dd0370fe554b8d041c31d196669a9db3202a604eb50618eccf39a2d09b45ee53f48a0136e93e83ea2db0c3dda53dce869d9f0c80e0cac4e13da4426`.

Separate checks preserved exact input9007199254740993 Nolan through WASM,
retained the zero change output for exact funding, rejected an amount-byte
signature mutation, and confirmed that changing an input's wire block coordinate
leaves its signature preimage unchanged. These fictitious UTXOs do not exist on
chain: passing serializer/signature checks does **not** show consensus validity.
Permanent fixtures should retain literal network bytes and add insufficient
funding, duplicate inputs, reservation/restart/snapshot races, stale/expired
slips, exact decimal parsing, unknown special types, conflicting spends and
reorganization/status evidence. Generate expected bytes independently, then
compare native output; never use native signing as its own reference oracle.
