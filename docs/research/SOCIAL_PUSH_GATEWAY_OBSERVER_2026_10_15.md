# Social push: public observer contract, 2026-10-15

Read-only source research for [#32](https://github.com/robelene112/redsquare-android/issues/32).
This narrows the [local seam](SOCIAL_PUSH_LOCAL_SEAM_2026_10_15.md) and
[closed-app requirements](NATIVE_CLOSED_APP_PUSH_REQUIREMENTS_2026_10_15.md).
No backend connections, transactions, credentials, configuration, wallet/database
access, builds, CI or service changes occurred. Only this note was authored.

## Finding

**A public full-block observer is source-supported; a gateway's own lite-block
identity is insufficient for arbitrary root mentions.** The pinned
[`/block/:hash` route](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L486-L518)
loads and serializes the block without recipient-key filtering. By contrast,
[`generate_lite_block`](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/consensus/block.rs#L2723-L2769)
retains a full transaction only when its inputs/outputs match the supplied keys,
or it is a golden ticket; other transactions lose message data in SPV placeholders.
Text or signed `mentions` entries do not satisfy that slip-key test. The
[`lite-block` route](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/core/server.ts#L334-L374)
uses the connected identity's key list, otherwise the requested key alone.
Watching public recipient keys needs no user secrets, but still misses
content-only mentions.

Use one gateway-owned signing identity for peer authentication. The
[handshake](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L461-L553)
verifies that connection's nonce signature; there is no need to authenticate as
each subscriber. This avoids the recipient-socket conflict described in the
existing requirements. It does not prove either public deployment currently
serves usable full blocks.

For provenance, fetched A=`9a4ca7e9442eb5e872168ced361ce741d7d139d7` and
B=`214504ecece76d1cd20a245fe4468eccbfca2951` directly from official GitHub.
The inspected server, sync, routing, network, handshake/message/blockchain wire
and consensus-block files match byte for byte. Archive differs only by its
`status = 'prod'` declaration. These are source comparisons, not fresh deployment
revision attestations. Red Square differs between these pins.

## Discovery and bounded continuation

The [wire schema](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/blockchain.rs#L1-L58)
provides type21 requests and type22 responses, with at most128 references,
tip/fork ID, shared ancestor and chunk bounds. Full sync is0; SPV is1.

The [request handler](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/sync.rs#L418-L576)
returns longest-chain references without recipient filtering. Full requests with
zero cursor start at the retained genesis floor; SPV zero-cursor bootstrap starts
at `max(genesis, tip-9)`. A matching saved tip resumes at its successor. Otherwise
fork ID informs the shared ancestor; absent ancestry falls back to the recent
window. **Full-response `transactions` is always0 in this implementation.** SPV
uses a relevance flag. Neither value permits skipping full-block fetches for a
content scanner.

Proposed minimal observer: bootstrap recent references with SPV, fetch every
reference through the full-block route, then use Full continuation. Persist
backend, last completely processed height/hash, returned fork ID and a bounded
ancestry journal. Advance only after durable candidate processing. Fetch/verify
hash, height and previous-hash continuity; on reorg withdraw pending orphan
candidates and resume from the shared ancestor. Unknown ancestry, missing blocks
or retention loss means a recorded coverage gap, not successful catch-up.

[Type6 announcements](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/network.rs#L55-L85)
can trigger reconciliation, not establish selected-chain membership. Type23's
[request handler is empty](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/routing_thread.rs#L205-L209).
Use bounded polling/reconnect reconciliation as well. An already displayed alert
cannot acquire irreversible finality through this observer.

## Exact selected-backend visibility gate

Query each subscription's approved backend over its authenticated application
request channel:

```json
{"request":"archive","data":{"request":"load","field1":"RedSquare","sig":"<candidate signature>","flagged_ne":1,"limit":1}}
```

[Archive](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/archive/archive.js#L714-L838)
binds schema fields including `sig` and `field1` and excludes flagged1 rows.
**Correction to the earlier seam note: requested limit1 is clamped to10**, so
strictly reject unrelated/malformed results rather than assume one response row.
Verify signed envelope ID/content, reply parent author, explicit mention and
self-exclusion using the existing
[social readers](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/RedSquareFeed.kt).
Archive metadata alone proves neither inclusion nor every moderator rule.
Missing rows require bounded retry, then unavailable status, not bypassing the
backend. Recheck on the phone before display/tap.

Existing [SaitoPeer](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt)
does not handle types6/21/22. Existing
[SaitoFullBlock](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoFullBlock.kt)
checks bounded header/Merkle content but keeps transactions private and exposes
payment containment only. Neither is an implemented generic gateway scanner or
independent consensus validator.

## Smallest proposed isolated API

These are proposed gateway endpoints, not existing Saito APIs:

| Operation | Minimum contract |
| --- | --- |
| Challenge | Short-lived, single-use server nonce, domain/version separation and expiry. |
| Bind/renew/revoke subscription | Locally signed challenge binding wallet public key, one approved backend ID, installation, FCM token hash, requested categories and expiry. Token replacement/revocation invalidates old bindings. Upload no wallet secret. |
| Resolve/reconcile events | Authenticated, bounded pending-event page with opaque cursor; responses contain backend, original public signature, proposed category and validity/withdrawal state. Persist recipient/backend/signature dedup and enforce expiry. |

FCM carries only a version and unpredictable opaque wake reference. The phone
resolves it, fetches/verifies originals from its still-selected backend, applies
fresh identity, permission, mute and category controls, then displays. A wakeup
is never authority to change backend or trust server-supplied notification text.
Gateway storage inevitably links a registered public wallet/backend with a push
token; opaque FCM data does not eliminate that gateway privacy boundary.

**Recommended next step:** implement a bounded, read-only capability tracer first:
gateway-owned identity, type22 bootstrap/continuation, one full-block fetch and
exact Archive lookup on each approved backend. Retain only public hashes/counts
and explicit gaps. This establishes actual read viability before building the
observer around an assumed endpoint. Source supports an isolated gateway without
node changes or block-production enablement; deployment capability remains
unverified. Relay-only/unconfirmed posts, pruned data, unavailable parent/name
proofs and outages remain coverage limits. Firebase setup and actual closed-app
delivery remain separate #32 acceptance, as recorded in the existing requirements.

## Subsequent bounded public observation

Root subsequently executed one read-only capability probe on both approved
backends at21:35:56UTC. Both authenticate, provide ten recent references, serve
the same verified full block (five full transactions) and accept a known-absent
exact Archive query. This narrows the deployment-capability uncertainty; it does
not establish continuation, actual social candidate visibility or delivery.
[Original script, public metadata and limits](../evidence/ticket32/public-observer-oct15/README.md)
retain the single actual observation. No production node changes or user secrets.
