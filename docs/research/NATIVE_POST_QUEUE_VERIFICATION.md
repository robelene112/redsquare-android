# Native post queues

Ticket #11, review base `4bc4cd6`, reviewed source `f03832a`.
Accepted seams: observable Android flows against controlled authenticated peers,
persistence/recovery and compatible transactions (specification #1 boundaries 1–3).
Full `38013725295` passes Android 10. Android 17 stops at an existing
thread-recovery test, before queue acceptance. Companion full acceptance is next.

## Behavior

Explicit Send stores the exact signed candidate in the existing encrypted,
wallet/backend/parent-scoped record. Queued status and cancellation require
verified saving. Failed saving retains work and prevents transmission. Cancel
preserves the text as an unsent draft. Saved drafts never schedule themselves.
Legacy records remain readable and default to nonautomatic delivery.

Foreground resumption and WorkManager 2.12.0 operate only for the active wallet
and selected originating backend. Shared durable-state locking serializes claims
with cancellation and wallet/backend mutations. A verified claim makes work
uncertain before transmission; it is no longer advertised as cancellable.
Concurrent manual/background attempts cannot transmit the same candidate.

Automatic intent survives uncertainty. Both manual and automatic retry use the
shared exact-signature Archive check, validating the signed author and text
before treating the original post as available. Retry retains the same signed
operation. Composer saves preserve authoritative delivery progress. A queue
screen lists both fixed backends, identifies paused work and permits cancellation
only while its durable record remains Queued.

Worker inputs contain public operation identifiers and endpoint only. Private
keys and plaintext work stay in device storage. Release endpoints are fixed;
debug test endpoints remain loopback-only. Startup repairs a storage/scheduling
gap. Android may delay background execution; see
[background delivery research](ANDROID_POST_QUEUE_BACKGROUND.md).

## Verification

Actual TDD failures and subsequent greens are retained in `docs/evidence/ticket10`.

| Behavior | Actual failing run | Passing run |
| --- | --- | --- |
| Durable Queue and cancellation | 38002111207 | 38003662459 |
| Original selected backend resumption | 38004250015 | 38005470104 |
| Delivery with stopped foreground activity | 38006073973 | 38006955485 |
| No false Queued status after failed saving | 38007571494 | 38008231610 |
| Uncertain accepted post reconciled without rebroadcast | 38009504360 | 38010789123 |
| Feed queue management and cancellation | 38011274167 | 38011852811 |

Fixture failures were separate: disabled field selection in 38003100661;
MockWebServer cancel() NPE in 38008923408; unguarded Refresh and retained lazy-list
position in 38009504360/38010212716. Corrections preserved the displayed-feed and
peer-health assertions. The latter image showed older cached posts, without
proving where the target was. Constructor compilation failure was not a
functional red.

`38012413217` passes six queue cases plus actual offline-queue process death and
cold delivery. `38013042673` at `3b28fac` passes all seven normal queue cases in
54.743 seconds, plus one completed cold-recovery case and its deliberate kill
stage. The additional case uses real SAF wallet replacement and existing
zero-balance/acknowledgement safeguards: no outgoing work is transmitted while
the replacement is active; restoring the original wallet resumes one original
signed post. Restoration is test-only and no private material is exported.

The process checkpoint contains public owner/text/signing-time bounds and PID.
After confirming the original process is gone, force-stop holds it until the
cold user interaction. The recovered transaction retains its pre-interruption
signing time, wallet and text, and appears in the feed with one transmission.
This is process recovery evidence, not arbitrary power-loss or force-stop
background delivery proof.

Both review axes approve `f03832a` with zero actionable findings. Standards
cleanup shares availability verification and runs wallet replacement once in
full scope, retaining it in focused queue scope. Local core/debug/test/release/
lint checks pass in 4m25s. See `docs/evidence/ticket10/REVIEW.md`.

The inspected cold-recovery bitmap shows the recovered post and is retained.
Replacement/restoration captures show stale UI despite passing identity
assertions; exclude those bitmaps from visual acceptance. Final full runtime
acceptance remains pending. No new APK, production transaction, immediate OS
execution guarantee or physical Pixel proof is claimed.


Full `38013725295` at `f03832a`: core19, debug/test/release assembly and lint pass.
Android10 passes78 completed cases (74 top-level plus4 recovered process cases),
with4 additional intentional process-kill stages. Posting15/15 passes79.949s;
queue6/6 passes29.195s. Both public feeds, cache/wallet cold launches and signed
5-to-8 continuity pass. Selected public logs are retained in ticket10/full-android10.

Android17 passes7/8 browse cases, failing at ThreadProfilePreviewTest:418 while
waiting for a cached reply after fresh activity launch. The failure capture was
after activity disposal and shows the launcher, so the cause is unestablished.
The fixture now requires enabled feed readiness, displayed root and actual
Conversation navigation, and captures inside the recovery activity. Original
reply/history assertions remain. Production source is unchanged; local test
assembly passes1m13s. A full Android17 companion run remains required.
