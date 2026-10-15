# Registry late-callback design, 2026-10-15

Proposal for [#19](https://github.com/robelene112/redsquare-android/issues/19),
under [parent #1](https://github.com/robelene112/redsquare-android/issues/1).
Only this research note was written. No implementation, tests, builds, CI,
live requests, node/wallet access or acceptance outcome is claimed.

## Evidence and smallest change

[Retained host evidence](../evidence/ticket19/host-public-proof-oct15/README.md)
records an exact type13 `{"err":"no response"}` followed by a same-index public
proof on saito.io. The unchanged core verifies that proof. The owner endpoint
provided fallback only within its observed window. The
[exact-source experiment](REGISTRY_CALLBACK_EXPERIMENT_2026_10_15.md) explains
one compatible callback ordering; neither establishes the deployed cause.

Currently [SaitoPeer](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt)
removes the pending deferred before completing the first type13/14 response.
[RegistryLookup](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/RegistryLookup.kt)
therefore cannot recover the later callback itself.

Recommend one **internal feature-named request entry**, such as
`requestRegistryNamecheck(message)`, sharing `requestFrame`. Store a private
closed flag/policy beside each pending deferred; ordinary overloads default to
first response. Only Registry namechecks opt in. Hints retain ordinary behavior.

In the listener, obtain `pending[index]` before removal. For an opted-in request,
skip only type13 whose complete payload equals the exact 21 UTF-8 bytes above.
Check length first; compare bytes directly, without JSON parsing, decoding,
hashing, cryptography or arbitrary caller callbacks. Then remove and complete
for every other response. Type14 always removes and completes exceptionally,
even with identical fallback bytes. A valid response arriving first completes
normally; subsequent duplicate fallback is ignored because no request remains.

Keep the existing single `withTimeout(12_000)` around readiness, send and await.
Do not retry, allocate another index, reset the deadline or retain skipped
payloads. Existing `finally` removes pending state on cancellation/timeout;
`fail()` must complete the deferred inside the new record and clear entries.
Disconnects remain failures. Fallback-only namechecks become timeout/unavailable,
never empty availability. Keep the existing response bounds and strict UTF-8/
[registrar verifier](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoRegistry.kt).

## Tradeoffs and verification

This adds small Registry coupling to transport but makes the exception explicit.
A generic predicate is more reusable, but permits expensive/throwing listener
work and broadens unrelated request semantics. A second observer/queue is
unnecessary for this single exact discard rule.

At the accepted controlled app/peer seam, verify fallback then signed proof,
empty array, fallback-only deadline, malformed/proof-invalid body, type14,
generic first response, wrong index, cancellation and disconnect. Preserve
identity/backend binding, immutable candidates and explicit identical Retry.
The [public native probe](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/PublicRegistryReadTest.kt)
must use the same namecheck entry for meaningful post-fix evidence; its hints
stay ordinary. No backend failover or production change follows from this proposal.
