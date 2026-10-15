# Registry readiness diagnosis, 2026-10-15

Read-only research for [#19](https://github.com/robelene112/redsquare-android/issues/19),
while the primary agent owns #40. Read project instructions, workflow,
operations, current issue/comments, username contract/verification, native
request/parser/registration code and retained public records. Fetched only
public pinned Saito sources. Only this document was written. No node access,
configuration, wallet/credential inspection, registration, network transaction,
device test, build, CI dispatch or source change occurred. The earlier YouTube
research document was left frozen.

## Finding

The native lookup JSON and response framing agree with the published contract;
no actionable native serialization/parser defect is demonstrated. There is a
more specific **pinned-source callback race candidate** than simply assuming
that the registrar is absent: the Registry relay path can return unhandled
before its forwarded database callback, causing the module dispatcher to emit
`{"err":"no response"}` first. The native peer consumes only the first response
for a request index. A later usable callback could consequently be discarded.

This is a source-supported failure mechanism, not proof of the running nodes'
revision, a late response in the measured runs, or the actual live cause.
The smallest next observation is the native first response body plus bounded
same-index late-response metadata. Do not retry the unchanged public probe,
change production, or weaken registration proof requirements on this basis.

## What the retained records prove

The [Oct 15 native record](../evidence/ticket19/public-oct15-recheck/android10/readiness.json)
is run 38681076101 at `e3edf1c971c25f49d5b793d98cfb2de85dd70aac`.
Report collection passed one case in 1.853 s. Both approved peers authenticated;
own-wallet key-map reads completed with zero hints; both the unique probe name
and `Saitoverse@saito` namecheck produced 21 bytes rejected as unusable.
[Retained qualification](../evidence/ticket19/public-oct15-recheck/android10/README.md).

That test deliberately records unavailable outcomes rather than asserting
registration readiness. A zero-hint query asks about this emulator wallet,
not all public names. It does not establish an empty Registry, missing module,
missing registrar or absent advertised service. The probe does not call
`peer.services()`, and its report does not retain service announcements.
[Public probe](../../app/src/androidTest/kotlin/nl/davidengelen/redsquare/PublicRegistryReadTest.kt).

The compact UTF-8 JSON `{"err":"no response"}` is exactly 21 bytes. This matches
the measured length, but **length correlation does not identify the native
body**, which was not retained. Separately, the
[Oct 11 host-only record](../evidence/ticket18/final-host-registry-read/readiness.json)
actually retains that error object for known and unknown names on both peers,
alongside working Archive reads and nonempty unsigned key maps. It used its own
fixture wallet and date; it is not a body capture from the Oct 15 native run.
Its `.filter` exception belongs to the helper summary and proves no server
key-map failure.

## Native request and parsing trace

| Boundary | Actual behavior | Consequence |
| --- | --- | --- |
| Lookup envelope | Outer `request:"registry query"`, inner `data.request:"registry namecheck"` with full `identifier` | Matches A/B handler dispatch. No missing outer `module` is indicated. |
| Key lookup | Same outer request, inner `request:"registry query"`, array `keys` | Matches A/B browser key query. A successful map is an unsigned hint only. |
| Peer framing | Type 12, big-endian 32-bit request index, serialized unsigned transaction carrying JSON | Matches the published application-request transport; ordinary lookup does not require a signed registration transaction. |
| Reply framing | Type13 body bytes returned unchanged after index; type 14 raises RequestError | An application error JSON can arrive inside a transport-success type 13 frame. |
| Matching | `pending.remove(index)` before completing deferred | Only the first same-index result/error is delivered; later frames are ignored. |
| Decode/proof | Bounded strict UTF-8/JSON array, exact identifier/key, registrar signature, block hash and chain-state checks | An error object, malformed body or invalid proof must fail closed, not become available. |

Native sources: [RegistryLookup](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/RegistryLookup.kt),
[SaitoWire](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoWire.kt),
[SaitoPeer](../../app/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoPeer.kt),
[proof verifier](../../core/src/main/kotlin/nl/davidengelen/redsquare/saito/SaitoRegistry.kt).
Primary contract: [A query construction](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L396-L417),
[B dispatch](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L613-L644),
[unsigned request wrapper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/saito.ts#L430-L463),
[API wire framing](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-core/src/core/network/msg/api_message.rs#L9),
[JSON callback bridge](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/rust/saito-js/shared_methods.browser.ts#L313-L326).
Agreement of source construction does not establish byte-for-byte browser
equivalence for an uncaptured live native frame.

## Pinned-source callback mechanism

A = `9a4ca7e9442eb5e872168ced361ce741d7d139d7`;
B = `214504ecece76d1cd20a245fe4468eccbfca2951`. Both have the relevant flow:

1. Registry namecheck at the actual registrar queries its database, invokes the
   callback with an array and returns 1. At another node it instead forwards
   `rawSQL` only to a peer whose public key matches the configured registrar.
2. The relay awaits `sendPeerDatabaseRequestWithFilter`, but that helper calls
   the asynchronous `sendPeerRequestWithFilter` without awaiting or returning
   its promise. The relay subsequently returns 0; it does not claim the pending
   request as handled. Its eventual forwarding callback passes `res?.rows` to
   the original callback.
3. The module dispatcher tracks truthy handler return values, not whether a
   callback has already run or remains outstanding. If all handlers return
   false, it invokes the original callback with the fallback error object.
4. The shared bridge serializes callback objects into API-success responses.
   The dispatcher does not wrap this fallback in a special delayed-response
   protocol. A later callback can therefore produce another response for the
   same original request index; whether it beats the fallback depends on
   scheduling and the other handlers.

[A Registry path](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/registry/registry.js#L837-L868),
[B Registry path](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L933-L964),
[A helper](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/templates/modtemplate.js#L1207-L1290),
[B helper](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/templates/modtemplate.js#L1207-L1290),
[A dispatcher](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/lib/saito/modules.ts#L134-L184),
[B dispatcher](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/lib/saito/modules.ts#L134-L184).

The no-matching-registrar case also produces no forwarded result, and the helper
does not invoke the success callback when no peer matches. Thus fallback-only
and fallback-before-late-array are separate possibilities. Immediate failure
in 1.853 s does not establish that a 12-second request timeout elapsed.

Key lookup follows a different path: when no registrar peer is found, it can
invoke its callback with locally found keys, including an empty object, then
return 0. A working empty key-map response can coexist with the dispatcher's
fallback behavior. It does not resolve the namecheck path.
[B key fallback](https://github.com/SaitoTech/saito/blob/214504ecece76d1cd20a245fe4468eccbfca2951/node/mods/registry/registry.js#L827-L931).

## Registration safeguards that a workaround must preserve

The [native state](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/UsernameState.kt)
checks the exact name before enabling Register and rechecks before sending.
It signs once, saves the immutable candidate before submission, checks mounted
wallet/backend ownership, and reconciles afterward. Recovery reads only;
explicit Retry reuses the same candidate. Unavailable lookup cannot mean an
available name or confirmed ownership. The
[request store](../../app/src/main/kotlin/nl/davidengelen/redsquare/registry/UsernameRequestStore.kt)
also verifies the exact Registry message, registrar recipient, outgoing author
and original identity/backend before a send claim. Preserve those boundaries.

Changing to raw local database reads is not an established safe replacement.
The [earlier retained local record](../evidence/ticket18/registry-local-readiness/registry-local-readiness.json)
has a time-like `bsh`, not its signed block hash, and zero verified proofs.
The existing [contract](NATIVE_USERNAME_CONTRACT.md) traces the Email overwrite
that can cause that shape. Local empty rows on a relay do not prove global
namespace availability either. Do not skip signature verification, trust key
maps, reinterpret errors as `[]`, or submit a registration just to test it.

## Smallest useful next diagnostic and conditional fix

Prepare a debug-only observer for the opt-in read-only native probe, retaining
the existing request deadline and unchanged production request semantics:

- Record exact public lookup JSON or its verified construction, authenticated
  selected peer, operation/request index, frame type, elapsed time, body length
  and hash. Preserve the bounded first public Registry response body or a strict
  JSON-kind/error classification that establishes whether it is the fallback.
- Observe same-index frames **before** the ordinary `pending.remove` lookup,
  including frames arriving after its deferred completes. Keep observation
  scoped to this probe's known public requests and bounded to the original
  12-second request window. The current quick loop/disposal and ignored late
  indices cannot establish that no later array was sent.
- Classify every later array through the existing strict proof verifier. A
  captured late empty array is only evidence of that callback's successful
  result; it does not prove a registration or durable namespace reservation.
  Record advertised Registry service separately if collected; it does not
  identify the registrar or prove forwarding connectivity.

An offline controlled reproduction using the exact pinned handler/dispatcher
and a delayed forwarding callback would establish the race independently of
production. Neither that reproduction nor the proposed observer was executed
here. Do not change the generic peer to wait through every error or choose any
later callback without a separately reviewed, bounded protocol decision.

If a usable late proof is observed, a narrowly scoped native read reconciliation
candidate can be evaluated against explicit duplicate/error/timeout cases,
while keeping wallet/backend binding and the deadline. If only the fallback is
observed, that still does not distinguish missing authority, moderation,
handler exception or failed forwarding. A genuine server fix would need to
verify the deployed source and establish handled/pending callback ownership,
one terminal response and explicit forwarding failure. That is separate
authorized node work, not an assumed upgrade or block-production toggle.
#19 remains open until live optional registration readiness is actually proved.
