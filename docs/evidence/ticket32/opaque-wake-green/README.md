# Opaque gateway wake: native acceptance

Additional ordinary[#32](https://github.com/robelene112/redsquare-android/issues/32)
transport work, bounded review base68e420ee; minimum861e795c after the retained
[intended RED](../opaque-wake-red/android10/README.md).

| Check | Exact2685f5fcb002f1512c29b23d9990d4686655e420 result |
| --- | --- |
| Android10 /38987459024 | SUCCESS,11 social86.777s plus3 worker46.679s |
| Android17 /38987461150 | SUCCESS,11 social124.78s plus3 worker47.545s |
| Source /38987439730 | SUCCESS, core/debug/test/release/lint |

[Android10](android10/README.md),[Android17](android17/README.md) and
[exact source metadata](source-check-run.json) retain passing outputs and originals.
Both devices report core114/0/0. All51 per-device manifested files and18 XMLs
are checked. Root visually reviewed the6 new opaque originals. The12 earlier
Archive-mode originals are retained with passing assertions, without claiming
new visual inspection. PNG/XML acquisition is sequential, not atomic.

The app schedules an opaque wake, requests its reference from an app-configured
resolver with the current recipient's Saito signature, retries an actual503,
verifies the recovered signed reply and owned parent through Archive, and shows
one actual Android reply alert without an Activity. Assertions require exact
version/wake-only HTTP body, recipient and signed domain/full URL/wake headers,
pending duplicate's persisted UUID, completed duplicate's unchanged OS postTime,
full wallet/backend, zero outgoing Saito writes and scoped execution-drained
restoration. Both previous malformed/unanswered Archive journeys still pass.
The gateway is controlled test infrastructure, not a deployed implementation.

The initial RED remains FAILURE; later positive request/auth/recovery/alert tails
were unexecuted there. Recipient proof assertions were added after that RED.
Minimum local core/debug/test passes3m10s. Source reviews at861e795c and handoff
correction5aac5d04 report Standards0/Spec0. Final committed-evidence reviews at22d65f7b report Standards0/Spec0;
both independently checked52 GREEN hashes/18XML and retained RED provenance. Both native runs use the exact same source; no equivalence exception.

This proves local opaque-wake resolution and verified publication, not Firebase
SDK/registration, actual Google delivery, independent web interoperability,
deployed gateway/observer, both-backend transport viability, process restart in
this new journey, locked/Doze/Pixel behavior or whole#32 completion. Earlier
[autonomous scheduling](../scheduled-process-green/README.md) has separate evidence.
No SDK/config/account/node/deployment change or new beta. Public beta24 unchanged.
