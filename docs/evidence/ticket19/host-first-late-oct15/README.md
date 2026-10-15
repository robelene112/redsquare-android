# Host-only first/late Registry observation, 2026-10-15

Read-only research at16:36:12UTC while #40 owns application implementation.
This executes a standalone public-transport observer, not Android/Saito runtime
initialization. Both fixed approved peers authenticate using the published
unsafe scalar1 fixture identity. Never fund it. Only two unsigned Registry
namecheck API requests per peer are sent, plus handshake/service/keepalive frames.
No broadcast, registration, transfer, owner-wallet read or production-state write.
Runtime24.13.0 and WASM0.3.2/hash are checked. The container is read-only, unprivileged,
capabilities dropped, with only runtime/dependency and probe mounts. No options,
node source/configuration/database, service control or credential mount.

For each exact request index, capture up to four responses during the original
12-second observation window after authentication/send. Both full windows complete
without dropped frames. This host observer classifies at most64KiB bodies; no
native request semantics are changed. It retains hashes/length and JSON kind,
only the exact known fallback body, or array row count. It does not retain or
verify returned ownership rows. Both peers advertise registry on these sessions;
a service announcement is not registrar identity or forwarding readiness.

Owner peer tRFyaSvHix3HDHguPRcb7cLooiTnBKquFgahkuScxfrg returns exactly one
21-byte type13 {"err":"no response"} for each namecheck, around7ms. No later
same-index response is observed within12s. This does not prove none can ever
arrive, a missing Registry module, or a specific forwarding/configuration cause.

Saito peer wnUrrEMtWhwwpULFdeWBto8McZzcydqvpJpfEgLQJx2g first returns the
same exact fallback around169.7ms, then a second type13 for EACH same index
around173.3ms: [] for NativeReadOnlyProbe20261015@saito and a431-byte one-row
array for Saitoverse@saito. The row's strict signature/block proof is not checked
here. This establishes actual fallback-before-later-array on this host connection,
consistent with the separately reproduced pinned callback mechanism. It is not
proof of the deployed source revision or the uncaptured Oct15 native body.

Current native SaitoPeer consumes pending.remove(index) on the first response,
so its source would ignore such a later array. Confirm this path natively before
shipping a narrow Registry workaround. Do not reinterpret errors as availability,
accept unsigned hints, bypass proof verification or change generic peer errors.
No public registration, namespace ownership or complete #19 acceptance is claimed.
Socket close callbacks after the completed window can add cleanup close metadata;
windowCompleted/observationCompleted describe the measured window, not an app
connection-liveness guarantee. Previous native failures remain separately qualified.
