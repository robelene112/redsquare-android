# Bounded photo-heavy public reads, #125

Parent: [#117](https://github.com/robelene112/redsquare-android/issues/117), ST7.
Review base: `0f4672bacdd0d059aa3d54161647b4883cf6f864`.
Product/source/lint and original Search: `6aed8c3700ee4aa4f3e161c4f793c90f4763cd17`.
Corrected Android10 photo fixture: `a5eb8e9ad10750b2debc7449c196d8bf192b5732`.
Final photo fixture/harness: `baae54a41a5b2f04a4ae6a0f583af87b697e29ec`.

Native qualification is complete at the exact sources below.

Feed, Following and Search adapt public Archive responses that exceed the existing
16MiB receive bound. A wallet/endpoint/query policy learns the smaller request size;
verified cards remain available while bounded continuations are published. Normal
signed admission, author/root filtering, logical signature deduplication, image
bounds and encrypted/public persistence limits remain unchanged.

The actual deployed Archive clamps ordinary limits to at least10. When even ten
rows overflow, a bounded first-row metadata hint from the rejected frame routes an
exact-ID read. The hint authorizes no content; the complete response passes the
existing signature/content decoder. Timestamp ties use highest/lowest ID batches
and bounded exact-ID reads through their interval before moving earlier. Sparse,
malformed or foreign-author rows cannot prematurely terminate a sound interval.
Entirely cursorless responses publish their valid content and truthfully stop
impossible continuation until an explicit pull/search, rather than looping.

Each author/stream read has at most16 continuation steps, with unfinished work
retained for the next poll. Equal-time cards use deterministic timestamp/signature ordering.
Short cached endings retain a bounded amount of unused viewport space when newer
cards are prepended, preventing LazyColumn end clamping from moving the old card.
The target is idempotent and removed once that ending leaves the viewport.
All posts reads no longer restart when unrelated Following account loading finishes.

Search distinguishes the unchanged1,000-entry stop from byte-filled windows.
Incoming matching IDs get a visible window before retained newer cache entries can
exclude them. A later stream that would evict matches consumed in the current
action stays pending, without advancing its cursor, and goes first on Load more.
No unbounded response queue is retained. A quiet footer explains large windows;
ordinary navigation and offline reattachment retain the visible matching window.

The outgoing social factory stays1MiB, incoming envelope reader8MiB, peer/public
store16MiB, image count4/inline2MiB and response admission100rows. The large-row
fixture independently signs valid incoming type0 envelopes under existing reader
bounds; it does not relax publisher admission. Peer request deadlines remain12s,
photo/cancellation/deadline class bounds180s and native waits unchanged.

## Final relevant qualification

| Scope | Complete successful parent | Normal methods | ZIP entries | TAR files |
| --- | --- | ---: | ---: | ---: |
| Photos, Android10 | [43813578386](https://github.com/robelene112/redsquare-android/actions/runs/43813578386) | 10 | 97 | 36 |
| Photos, Android17 | [43815624179](https://github.com/robelene112/redsquare-android/actions/runs/43815624179) | 10 | 86 | 36 |
| Original Search, Android10 | [43812335808](https://github.com/robelene112/redsquare-android/actions/runs/43812335808) | 17 | 99 | 193 |
| Original Search, Android17 | [43812338833](https://github.com/robelene112/redsquare-android/actions/runs/43812338833) | 17 | 88 | 193 |
| Source/debug/lint | [43812333098](https://github.com/robelene112/redsquare-android/actions/runs/43812333098) | Core224 | 55 | 0 |

Both SDKs display all20 eligible cards in each standard photo scenario, all eight
large incoming records, and retain the Following anchor exactly198to198px. Standard
scenarios each encounter one oversized callback; the large-row traversal encounters
four, and the two fitting streams zero. Combined fixture responses range from
18,797,972to22,547,121bytes. Every recorded scenario has zero publications.

Core actually executes224 tests across44 classes with zero failures/ignored.
Gateway is explicitly FROM-CACHE. Actual debug/AndroidTest assembly and separate
lintDebug pass, with zero errors,95 warnings and three hints. An echoed unused
release/performance command does not establish those operations. The original
primary Search group lacks a separate exit/status file; its exact nine normal
terminal completions and native OK marker are retained, alongside the other
five groups' normal completions and existing capture/exit receipts.

The photo scope completes all10 normal methods across six classes on each SDK:

- Twenty individually accepted signed photo posts in a combined response over16MiB,
  with every distinct card actually displayed in Feed and Search.
- Older matching timestamps remain reachable after a byte-filled search window.
- Following retains its cached card within the original2px goal while admitting
  all20 valid photos across sparse IDs, malformed anchors, a foreign author and a
  duplicate signature. The wrong author remains hidden.
- Eight individually accepted three-image posts remain reachable when even the
  minimum ten-row query exceeds16MiB, using bounded exact-ID continuation.
- Twenty root/quote matches remain reachable across two individually fitting
  streams whose combined cache window exceeds the unchanged byte budget.
- Original cold/cached read-deadline recovery and both caller/enclosing-deadline
  cancellation methods remain additive and complete normally. No navigation,
  Activity restart or Retry supplies automatic deadline recovery.

Native assertions retain exact wallet, selected backend, real signed decoding,
complete card counts, individual displayed cards and zero public transactions.
Original Search retains all17 methods across six classes, byte-identical to the
review base. Source/debug and separate lintDebug are qualified independently.
These scopes do not execute release or performance assembly, or whole-app delivery.

## Owned seams and compatibility authority

The actual deployed public Archive source was extracted read-only, without node
initialization, configuration, wallet, database or provider mutation. SHA256:
`e1bd400f035979461070a34b9887d09b50866417057d06940661b78119d80743`.
The official legacy reference is
[Archive at9a4ca7e](https://github.com/SaitoTech/saito/blob/9a4ca7e9442eb5e872168ced361ce741d7d139d7/node/mods/archive/archive.js).
The newer0db0062d reference supports created_cursor; the actual owner deployment
lacks that field. This client assumes neither an upgrade nor an offset/sub-ten limit.

Owned emulator loopback peers perform actual authentication, signatures and binary
callbacks. Pinned OkHttp5.4 rejects outgoing frames over16MiB before a client can
receive them. The opt-in broker fixture therefore queues only the test frame into
its original writer queue/thread/framing, bypassing the broker outgoing cap. The
app receive cap and normal fixtures remain intact. Callback diagnostics retain at
most256 public metadata records, with no transaction bodies or wallet secrets.
Configured node labels establish selection, not live public-node interoperability.

Following precomputes its deliberately large response before the real RPC starts,
observes a completed held author callback, establishes a healthy visible cached
baseline and selects the latest completed held index after screenshot/UI-idle
observation, then releases that exact index. This selection is an observation, not
a formal guarantee against a later IO generation change; the accepted run must
correlate that released index with its successful continuation. Holding and enqueueing share a
lock. The new<5s age precondition does not extend any app/UI/class deadline.
The a5-to-baae bridge changes only the owned photo fixture and adds a focused
large-row workflow/harness option. Existing scopes/goals are intact. Each returned
row is JSON-encoded before launch; per-query filtering, ordering, min10 and exact
array bytes are unchanged. Existing product/core/resources/build files match6aed.
Both independent source axes approve retaining the complete10/source/Search results
through this exact bridge, conditioned on a complete successful new17 parent.
Temporary product diagnostics are removed. Shared-fixture cancellation checks are
added to the final photo scope, preserving their original goals.

## Original failed and intermediate parents

All originals remain at
`/mnt/storage/redsquare-android-artifacts/photo-pages125`, including complete logs,
API metadata, server-digest-verified ZIPs, every extracted ZIP/TAR member and
source-bound nonacceptance receipts. The retained-run inventory lists every parent.
No successful prefix from a failed/cancelled parent contributes acceptance.

- 43793947196@fe207064f: real oversized feed callbacks repeatedly reject and no
  first photo appears within30s. 43794703836@c876d504b was superseded before device
  execution and remains cancelled.
- 43794996168@bb142f8b4: initial Search overflow; Feed also exposes a forward-only
  scroll operator seeking an earlier card. 43796046639@fd3d2fa80 exposes Search's
  viewport-dependent Load more readiness. Fixed-header readiness and returning to
  index0 retain all eventual individual display goals and deadlines.
- Five f098cf4e3 parents fail compilation before native execution. Shared rule
  typing and an inaccessible core-only factory are corrected at the owned seam.
- 43798521718@d7ec30bd0: older matches disappear behind a disabled Load more.
  43798937443@35853df93: malformed anchors prevent the complete Following state.
- 43800191007/43800205191@cef9e10c8: equal-time forward scans miss a card;
  deterministic ordering is added. A later success alone does not prove their
  historical timing cause. Source43800261423 is intermediate only.
- 43800971180@4e661e446: a second stream evicts first-stream matches before they
  are seen. 43801663675@f0dc3c711 fails nullable-guard compilation; its four other
  scopes are cancelled. The correction equals the existing rendered predicate.
- 43803263515/43803266576@961105d67: an initial last-photo viewport assumption
  fails although Android10 visibly shows photo17. Any-photo initial readiness
  retains all20 eventual displayed-card assertions and original deadlines.
- 43804352632/43804355535@9366: real short-cache end clamping moves the anchor
  198to295px. Bounded spare ending space corrects that97px movement.
- 43806908880@1a098d3c: a later54px shift coincides with a Node unavailable bar;
  its exact historical cause remains unproved. 43806911413 passes Following but
  fails the minimum-page repeated-overflow count after displaying all eight cards.
- 43809089904@6edd is a successful focused diagnostic only. Fixture timing races
  are possible from actual source schedules; that success does not prove the
  historical54px cause. Product diagnostics are subsequently removed.
- 43811272011@e0 fails the initial feed overflow count. Its retained metadata
  shows two near-simultaneous limit20 reads, a disconnected retry20, then fitting
  limit10 continuations. All posts unnecessarily depends on Following readiness;
  the final source removes that cancellation trigger without weakening the count.
  Successful e0 Android10/source/Search parents remain intermediate only.

- 43812327678@6aed passes the three photo cases, then Following moves198to252px.
  Its actual trace records held author060 completed before author061; screenshot
  observation spans the replacement, then the fixture releases old060 and a later
  retry starts. The fixture now selects after that observer work. This fresh
  schedule does not establish the cause of older54px failures or why that reader
  was replaced.
- 43812330245@6aed passes initial photos and Following, then the large-row counter
  fails after all eight cards display. Trace has two startup20 queries, a retry20,
  then10/high/low overflows. It is not an ordinary fresh-poll observation.

- 43814070773@b940 is a failed focused diagnostic: it reaches only six of eight
  cards within the original30s complete-count goal. It records one foreground
  reader and the expected four overflows, with fixture JSON encoding taking up
  to4.7s per large response. Encoding immutable row bodies before launch removes
  this generator cost from the measured traversal. This observation does not
  prove the cause of earlier duplicate-start failures. Temporary effect loggers
  are removed before the final source.

## Evidence and delivery limits

Independent source and original-native reviews report zero remaining confirmed
findings on both Spec and Standards axes, including every original archive byte
and the explicit source bridges.
The five accepted parent archives contain425 byte-verified ZIP files and458
byte-verified TAR capture files. Server digests, source metadata, complete native
logs and exact extracted bytes are retained externally; the packet copies the
native logs, photo captures, receipts, source bridge and full run inventory.
`copy-index.json` records every copied path, its original source and SHA256.

These checks establish the approved owned native/protocol/storage behavior. They
do not establish physical/OEM performance, live community/provider interoperability,
complete permanent history or final combined beta acceptance. Public beta remains
0.1.131 until #131 completes original integration/release/performance/update goals
and signed delivery. Real provider/node/schema/funding/physical/Play gates remain
open under #36. No production node/provider was changed or public transaction sent.
