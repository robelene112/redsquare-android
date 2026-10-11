# Native Saito username contract

Read-only public-source inspection for #19, 2026-10-10. A/B are official
`SaitoTech/saito@9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
`214504ecece76d1cd20a245fe4468eccbfca2951`. Existing profile/first-use notes
were checked against fetched pinned Registry, wallet, keychain, schema and
template sources. No live registration, node access, private data, builds or
deployment changes. Pins do not identify current backend deployments.

## Authority and discovery

Both Registry pins publish registrar key
`sFQuGQ6teVaHs6AAtBBWeDwtGMCGCPNyhTJqYDXCvvVK`. Any full node loading Registry
advertises service `registry` / domain `saito`, including relay nodes. Service
presence therefore does not make the selected peer the registrar. Browser
initialization disables the local-development override outside localhost/testnet.
Preserve the published registrar for these production domains; authenticate the
selected peer separately. [A constructor:17–20](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L17),
[B initialization/services:178–215](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L178),
[A override:450–451](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L450).

Both supported app backends can serve or relay the same registrar's namespace.
Registry presence, their registrar connectivity and usable lookup responses
remain unverified here. A relay's namecheck forwards a database request only to
connected peers matching the registrar key; no matching peer can leave the
callback unanswered. Apply finite request deadlines and show unavailable,
never interpret timeout as name availability.
[A forwarding:837–867](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L837),
[peer-filter no-match:1274–1277](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/templates/modtemplate.js#L1274).

## Exact public requests

Send outer `request:"registry query"`, with `data` either
`{request:"registry query",keys:[publicKey,...]}` for key-to-name maps, or
`{request:"registry namecheck",identifier:"Name@saito"}` for full record rows.
Use the selected authenticated backend; no arbitrary backend or raw SQL UI.
Both pins handle these requests. A successful empty namecheck is currently
available, not reserved. Returned matching records mean taken, or already owned
when their validated key matches the active wallet.
[A requests:396–417,539–550](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L396),
[B handlers:622–632](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L622).

Validate bounded response shape, exact identifier/key and proof records. Ordinary
key-to-name maps contain no registration proof. Records contain identifier,
publickey, bid, bsh, sig, signer and lc. The registrar signs UTF-8
`identifier + publickey + decimalBid + blockHash` without separators. Verify
against the published registrar and require matching fields; lc is mutable
server chain-state metadata, not signed proof of current finality.
[B proof:677–701](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L677),
[A crypto:26–39](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/crypto.ts#L26),
[schema](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/sql/records.sql),
[B reorg:1012–1015](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L1012).

## Registration transaction and prerequisites

Signed message is `{module:"Registry",request:"register",identifier:"Name@saito"}`,
with identifier at the top level, **not** under data. A accepts ASCII
alphanumeric base names; B additionally limits the full identifier to 51
characters. A compatible native input accepts 1–45 ASCII letters/digits and
appends `@saito`. Preserve case; no lowercase normalization appears in these
paths, and the schema's identifier uniqueness is not explicitly case-insensitive.
[A registration:363–388](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L363),
[B validation/signing:369–399](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L369).

Registry requests a zero-amount transaction addressed to the registrar with the
wallet's default fee. A/B default fee is 0; the wallet even retries with 0 when a
positive configured fee alone prevents construction. No registration price or
payment check appears in the receiver. Source supports unfunded zero-fee
construction, not guaranteed inclusion. Do not invent a required SAITO balance.
[A wallet:33–87](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/wallet.ts#L33).

For exact browser-compatible output order, registrar is the recipient output
before any wallet change. Existing native social transaction creation forces
self first; Registry's receiver uses `isTo`, so source does not prove self-first
invalid, but it is not the browser reference shape. Verify an explicit registrar
recipient transaction independently before claiming compatibility.
[A Rust outputs:1079–1108](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/wallet.rs#L1079).

## Confirmation, recovery and native strategy

Only `onConfirmation(conf=0)` at the registrar registers an addressed request.
The unique-identifier insert can lose a race after an availability check. The
registrar then broadcasts a signed Email success/failure response; success
contains an additional registration proof. Broadcast completion is not success.
Backend block production can stay disabled if propagation and the existing
network's registrar/confirmation path work.
[A confirmation:574–654](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L574),
[B insert:966–1009](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L966).

Prefer signed namecheck records for reconciliation. A/B Email construction
overwrites `bsh` with unixtime after creating its proof, so reconstructing proof
from Email fields alone fails. Namecheck at the actual registrar retains the
original record's block hash. Do not silently skip verification or claim chain
finality to accommodate this defect.
[B response:715–726](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L715).

Recommended native flow: optional settings action, validate, deadline-bound
availability check, persist wallet/backend/name/original signed candidate,
submit, show pending, and reconcile verified ownership. Preserve original bytes
on Retry; query before rebroadcast. Different valid owner means taken/conflict;
invalid proof, malformed response or unavailable service means unresolved/error.
Bind callbacks to the original identity and backend. A wallet import re-queries
its public key then verifies the resulting full-name record; never assigns the
outgoing wallet's cached name to the replacement or automatically re-registers.
Keep feed/posting/Chat usable throughout.

Display registered identifier with public key still accessible; fallback to the
existing generated identity when absent. Cache verified public mappings by key,
retain source/freshness and distinguish pending names from registered ones.
[B keychain:575–601](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/keychain.ts#L575).
No backend change is yet proven necessary. Missing service/registrar connectivity
or unusable signed records would be concrete readiness blockers if observed;
source-only research has not observed them. Controlled signatures, races,
interruption and read-only readiness on both live backends remain required.

## Read-only backend readiness, 2026-10-11

During #18 final regression wait, an ignored scratch client issued only unsigned
application read requests (frame12), using the existing native wire layout and
public unsafe scalar1 for the authenticated handshake. It never sent a broadcast
frame4 or initialized a Saito application. Final probe used a read-only mount,
empty environment and the pinned pure WASM/WebSocket dependencies; stdout was
written by the owning shell into ignored scratch. The earlier probe wrote only
its scratch result through a writable host bind, which was removed from the
final invocation; no service/node/wallet/configuration writes were performed.

Both approved wss endpoints authenticated (03:57:26–03:59 UTC). A normal Archive
read returned ten public rows on each, and Registry key lookup returned one
username hint on the owner endpoint and four on the official endpoint. Neither
map establishes signed ownership. Namecheck for a syntactically valid synthetic
unknown name returned `{err:"no response"}` on both. A second namecheck for an
actual name from each endpoint's map returned the same error. These are not
empty record arrays and must never be interpreted as availability or ownership.
Exact public read results and scratch client are retained locally in
.scratch/registry-readiness-first.json, registry-readiness-second.json and
registry-readiness.cjs; they are not native Android runtime proof or a deployed
revision claim. The scratch client's initial array-only proof summary failed
on the error object; no signature verification or valid-record claim follows.

The working Archive/key-map reads distinguish this observation from a general
connection outage. The pinned relay-only namecheck path could lack a connected
registrar or return an unusable forwarded response; the actual cause is not
proven. Do not enable block production or change a node speculatively. Continue
controlled implementation with truthful unresolved states; live namecheck and
registration readiness remain an external acceptance concern until resolved.
