# Registry diagnostic plan, 2026-10-15

Research planning for #19 while root owns #40. Only this document was written;
no prototype, source/test/harness changes, execution, CI, node, wallet or
credential access. Reused this session's maintenance checks. The
[diagnosis](REGISTRY_READINESS_DIAGNOSIS_2026_10_15.md) establishes a pinned-source
callback-race candidate, not its occurrence on either live backend.

## Smallest useful boundary

Add an internal diagnostic request entry point beside the existing
[SaitoPeer.request](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt),
leaving normal request overloads unchanged. Require `BuildConfig.DEBUG` and an
explicit probe capability created only after the existing
[public fixture](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/PublicRegistryReadTest.kt)
opt-in/package/emulator checks. Restrict endpoints to the two approved URLs;
allow validated loopback only for controlled instrumentation. Register only
exact constructed Registry read requests, with expected operation/name/key.
No global logging switch, intent option, transaction submission or production
caller activates this path. Release attempts fail before registration.

Use an independent connection-local `index -> observation` map. Bind observation
before socket send. For authenticated, structurally valid 13/14 frames, record
against that map **before** existing `pending.remove(index)`. Preserve the
unchanged first-result/type 14-error completion, timeout, frame rejection and
late-response discard behavior. Observation membership survives first completion;
it never makes an ignored reply an application result.

## Deadline, ownership and bounds

Capture monotonic request-entry time inside the existing 12-second timeout,
before `ready.await()`. Share its absolute deadline with observation; handshake
time consumes that budget. Do not start another 12 seconds after send/reply.
The probe owns the handle even if its request throws. Within `peer.use`, drain
only the remaining original window before snapshot/disposal. Outer 110-second
probe timeout remains sufficient for its existing eight bounded operations.
Cancellation/disconnect ends observation immediately and reports truncation;
never wait noncancellably to complete evidence. Mark deadline expiry explicitly.

On the WebSocket thread, make only timestamp/type/index/length records and
bounded byte copies. Suggested fixed limits: three requests per backend,
four replies per request, 64KiB maximum copied body, and capped dropped counters.
Larger bodies retain length/oversize metadata without body/hash. Thus at most
24 copied bodies total, 1.5MiB; no unbounded queues. Hash and strict UTF-8/JSON
classification run later on the probe's IO dispatcher. Record full SHA256 only
for fully copied bodies, never mislabel a prefix hash.

Persist exact bounded fallback error, empty-array classification, or allowlisted
public proof fields validated by
[SaitoRegistry](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoRegistry.kt).
Do not retain arbitrary JSON fields/strings, invalid-body dumps or wallet data.
Preserve body length/hash and rejection category when proof validation fails.
Report ordinal first/late independently of concurrent pending-map state.

Protect each collector's append/freeze with one short lock; use a concurrent
index map. Freeze snapshot before JSON rendering. Close/failure disables all
registered observations without erasing already captured evidence. Observer
failures must not reach the listener's outer catch, which disconnects peers;
record a bounded diagnostic failure instead. Preserve original test failures
if final report persistence also fails.

## Alternatives and verification

[RegistryLookup](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/RegistryLookup.kt)
alone sees only the first body. An `onIncoming` hook sees unsolicited 12/4,
not 13/14. A generic response callback risks unrelated traffic and slow/throwing
listeners. Modifying pending completion or extending timeout changes production
semantics. The restricted observer avoids those costs.

At the existing controlled WebSocket seam, extend
[ControlledFeedPeer](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/ControlledFeedPeer.kt)
and follow [peer instrumentation](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/PrivateChatPeerPreviewTest.kt):
first fallback then delayed valid array must retain both while returning the
first unchanged; type 14 then valid array must still throw RequestError.
Cover unrelated indices, unobserved normal requests, cap/oversize floods,
deadline, cancellation and disconnect. Verify report truncation and no extra
requests/broadcasts. Test strict proof classification at existing core vectors.
These fit [specification boundaries 1/2/3/4](https://github.com/robelene112/redsquare-android/issues/1).
No tests or live observations are claimed by this plan.
