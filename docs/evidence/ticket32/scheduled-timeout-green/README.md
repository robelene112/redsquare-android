# Scheduled receipt: native unanswered-Archive recovery

Additional ordinary[#32](https://github.com/robelene112/redsquare-android/issues/32)
verification of the already-reviewed worker timeout mapping. It is not presented
as a new RED cycle: the earlier no-op RED establishes missing processing, while
the first passing worker fixture exercised malformed responses rather than timeout.

| Check | Exact5463170d result |
| --- | --- |
| Android10 /38982505303 |SUCCESS,11 social90.863s plus2 worker35.448s |
| Android17 /38982507198 |SUCCESS,11 social128.618s plus2 worker35.981s |
| Source /38982505012 |SUCCESS, core/debug/test/release/lint |

[Android10](android10/README.md),[Android17](android17/README.md) and
[exact source metadata](source-check-run.json) retain outputs/originals/hashes.
Core reports show114/0/0. Root viewed6 timeout originals and parsed all12 XMLs.
Malformed captures are retained separately and their full assertions pass.

Default-false response withholding leaves ordinary ControlledFeedPeer behavior
intact. The second journey exercises an observed authenticated Archive request,
real elapsed time and scheduled retry, recovery, actual Android alert, pending
duplicate's persisted UUID, completed duplicate's unchanged OS postTime, no
Activity, full wallet/backend, zero outgoing writes and execution-drained
restoration. The elapsed lower bound includes scheduling/authentication. The
12s peer timer is inside the15s verification timer; no claim is made about which
outer deadline fired. Existing11/180s and worker-class150s ceilings remain.

[Production equivalence](production-equivalence.json) proves unchanged app/core
source/build trees from the accepted c69a1896 minimum. Both source reviews at
0f50c2a4 report0 findings. No additional local rebuild was claimed: exact-source
CI compiles before both native observations. Final committed-evidence reviews
at9663f3a5 report Standards0/Spec0 (44 hashes and12 XMLs checked).

Earlier failed attempts, their unknown group-editor cause and unexecuted tails
remain qualified in[scheduled receipt acceptance](../scheduled-receipt-green/README.md).
This verifies ordinary local timeout recovery; it does not establish autonomous
process restart, incoming FCM, locked/Doze/Pixel behavior, viable gateway on both
backends or whole#32 completion. No SDK/config/node/deployment or new beta.
Public signed beta24 remains unchanged.
