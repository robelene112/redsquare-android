# Owner Registry readiness: next check, 2026-10-15

Source-only research for [#19](https://github.com/robelene112/redsquare-android/issues/19).
Only this note was written. Exact public GitHub source was fetched; no live
Owner/Saito endpoint, protocol, runtime, options, configuration, wallet, database
or log was accessed. No initialization, implementation, build or CI occurred.

## Existing useful route

Both exact pinned core/server.ts files are identical: 32,031 bytes, SHA256
`eba6b3f67f5b8fc307443ffc9371f8b740ad679465c0411b652cda792630bd66`.
[A source](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L825-L840),
[B source](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/core/server.ts#L825-L840).

They register **GET `/json/peers`**, reading the server's
`app.core.network.getPeers()`. The exact response wrapper is `{ "peers": [...] }`,
not a top-level array. Output rows contain publicKey, status, services,
id, keyList and synctype, without a host/IP field. This can establish whether
a peer matching the **published** registrar appears in that server snapshot.
It does not reveal effective `Registry.registry_publickey` or prove forwarding.
[Core getPeers](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_network.rs#L54-L64)
filters **is_verified**, not connection status. Missing means no matching verified
snapshot row, not no TCP connection. The
[JS bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/saito.ts#L381-L385)
wraps peers before projection. Both pins' identical
[status getter](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-wasm/src/wasm_peer.rs#L84-L92)
returns string `connected` if is_connected, otherwise `connecting` if is_connecting,
otherwise `disconnected`. New peers default to connecting, initially unverified.
Projected id is a string; publicKey/status are getter strings, keyList an array,
synctype lite/full. Services are WASM-backed objects passed directly to JSON:
plain service fields are not guaranteed. Treat missing/unrecognized fields as
unknown, never disconnected/no-service. No field is explicitly optional in the
projection, but live serialization/deployment can differ.

There is **no server-side selector**: the route returns every peer row. Do not
print, persist or publish that response. If root separately authorizes a bounded
collector to receive it transiently, retain only fixed published-registrar
presence, matching-row count, that match's bounded status and Registry-service
boolean. Discard all unrelated rows and keyList/id/synctype fields. Record
HTTP/content/schema/bounds failures honestly. At research time no collector had run.
Subsequent root observations are retained separately: [wrong-wrapper schema failure](../evidence/ticket19/owner-public-peer-projection/README.md) and [corrected registrar-only projection](../evidence/ticket19/owner-public-peer-projection-corrected/README.md).
The old `/stats/peers` remains explicitly discouraged by
[maintained operations references](/home/david/snake-stickerbook/docs/SAITO_RUNTIME_UPGRADE.md:243).

## Limits and remaining seam

[Native evidence](../evidence/ticket19/native-public-after-fix/android10/README.md)
verifies official proof/empty availability; Owner checks time out.
[Registry forwarding](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L837-L868)
requires exact matching effective authority. Its
[helper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/templates/modtemplate.js#L1258-L1291)
returns silently without a match; key-map fallback differs.
[Initialization](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L171-L194)
can replace the compiled registrar for localhost/testnet. This is a source
possibility, not an Owner finding. Fallback-only therefore proves no cause.

Route registration in pinned source does not establish live deployment/reachability.
No source-established route exposes effective Registry authority. If connection
metadata remains insufficient, the useful future server observation is one fixed
public namecheck: authority-match/self-is-registrar booleans, matching count,
forwarding enqueue, callback and timing/error category. No existing authorized
runtime hook is established. Instrumentation, staging/deployment and repairs
need separately scoped authorization under the
[operations guide](/home/david/snake-stickerbook/docs/OPERATIONS.md).
No options, full peer lists, SQL or new diagnostic implementation is proposed.

Earlier root compared only three exact public disk-file hashes through a
network-none/read-only container: Registry/dispatcher matchA; helper matchesA/B.
[Retained source check](../evidence/ticket19/owner-public-source-check/README.md)
does not establish running code, effective authority or peers. No upgrade is
proved as a repair. This source-only research does not supersede that observation.
