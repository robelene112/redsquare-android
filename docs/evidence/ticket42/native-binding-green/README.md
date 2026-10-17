# Native gateway binding: controlled acceptance

Exact source17fb72605cf6e3eb740cbaba73b429a2b5de1bfa, native minimum base1fdd7f8b,
whole#42base5a048524. Source reviews Standards0/Spec0 after correcting Spec1:
Android channel choices now determine requested categories and shared SDK demand.
The individual-channel change is source-reviewed, not an executed UI toggle here.

| Run | Actual result |
| --- | --- |
| [Android10](android10/README.md),38994269120 | SUCCESS, binding1/1.235s, priorUI11/84.307s, worker3/46.275s |
| [Android17](android17/README.md),38994270968 | SUCCESS, binding1/11.810s, priorUI11/115.412s, worker3/47.324s |
| [Source](source/README.md),38994268560 | SUCCESS, core/gateway tasks, debug/test APKs, release and lint |

The new native journey uses actual Android encrypted device storage and owned
HTTP with synthetic wallets/FIDs. It covers initial binding, wallet replacement
without inherited consent, keyless retirement, opt-in on official, backend/FID
renewal on the same current ID and final opt-out. Each fixture reports3bindings/
2revocations and restores wallet/backend/scoped state before success. The actual
[first-bind RED](../native-binding-red/android10/README.md) remains0.205sFAIL;
its later tails and prior14cases were unexecuted.

Both retained core reports are114/0/0 with actual build/cache states in logs.
Per-device packets contain14/15 hashes and23 source hashes each; source packet2
hashes; root packet3 hashes for the stopped local builds. Root parsed2XML and
viewed the2retained original opaque reply-alert PNGs. Other raw existing UI/worker
captures remain outside Git and are not newly claimed as inspected. The new
HTTP/storage binding journey has no screenshot.

Both local builds were deliberately stopped, actualexit143: the first after the
concrete category finding, the corrected duplicate only after all three exact-
source hosted runs succeeded. Neither is presented as a local pass. No owned
local build remains. No further unchanged run is needed for this checkpoint.

The SDK worker hook is source-reviewed; these unconfigured builds execute no
actual Firebase registration/callback/rotation or Google delivery. Client-process
death, deployed gateway, lock/Doze/Pixel acceptance and whole#42/#32 remain open.
Beta24 unchanged. Final independent evidence reviews are pending.
