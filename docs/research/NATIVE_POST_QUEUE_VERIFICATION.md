# Native post queues

Ticket #11, review base4bc4cd6. Accepted seams: actual Android application flows
against controlled authenticated peers, including persistence/recovery and
compatible emitted transactions (specification#1 testing boundaries1–3).

First slice: explicit Send to an unavailable selected server becomes durable
Queued work. A fresh activity recovers it; cancellation retains text as an
unsent draft; a subsequent peer connection emits no transaction.

Actual functional red38002111207 at634b2ce fails at the Queued status wait
(PostQueuePreviewTest line38). Composition and explicit Send complete first;
this is missing application behavior, not a build/setup failure. Public log is
retained under docs/evidence/ticket10/first-red.

Queued status is stored with the exact signed candidate in the existing encrypted
wallet/backend/parent-scoped record. Before socket transmission, authenticated
readiness is required and a verified durable write makes the operation no longer
cancellable. A crash after that boundary is conservatively uncertain, rather
than falsely advertised as safely cancellable. Failed state persistence prevents
transmission. Legacy records remain readable and default to unqueued.

Local debug/test assembly and lint pass2m45; follow-up persistence error handling
compiles1m44. Focused runtime green pending. Automatic selected-backend resumption,
queue visibility outside the composer and actual process/wallet replacement
recovery remain subsequent slices. No production transaction, new APK or
completion claim.

Focused38003100661 reaches Queued, then the fixture incorrectly asks for
SetText on a disabled field. Select its text while preserving the disabled-state
assertion. This is a fixture failure; restart/cancellation have not passed yet.
Corrected test assembly passes50s.

Focused38003662459 at16af65c passes the first complete queue/cancellation
application case. Durable Queued recovery and cancellation retaining an unsent
draft are runtime verified. Automatic backend-bound resumption is next.

Actual backend-resumption red38004250015 ate8229a7 passes cancellation,
then fails waiting for automatic original-backend transmission after switching
back (line97). No writes occur while the other backend is selected.

A lifecycle-bound foreground resumer now reads only durable queued items for
the selected identity/backend. It checks exact verified Archive availability
before transmission, claims the unchanged record under the shared durable
wallet/backend lock and persists uncertainty before enqueue. Cancellation uses
a compare-and-save boundary, and manual/automatic attempts cannot overlap for
the same signature. Composer saves preserve newer delivery state; verified
availability is durable and visible to an open composer. Root/reply record
names and existing encrypted formats remain readable. Local assembly passes
through final confirmation adjustment (1m24). Focused green pending. Background
scheduling, uncertain automatic retries, queue-management UI and actual process/
wallet replacement acceptance remain incomplete.

Focused38005470104 atc3d5643 passes both cancellation and selected-original-
backend automatic foreground delivery. The emitted transaction retains its
originating wallet and text; the other peer receives no transaction. Background
execution remains unimplemented and is the next vertical slice.

Actual background red38006073973 at3301f11 passes2/3 cases. The background
case stops the foreground activity before reconnecting, then times out waiting
for transmission (line137). WorkManager2.12.0 now schedules unique connected
work for persisted explicit intent. Worker inputs contain public identifiers and
an endpoint, never plaintext posts or private signing material. The worker
rereads selected backend/active identity; the existing atomic claim rechecks
both before transmission. Paused/cancelled records are not redirected. Debug
fixture endpoints remain loopback-only; release workers use fixed backends.
Startup repairs the persistence/scheduling gap; retries before the sending
claim retain Queued. OS delays/force-stop limits are documented in
ANDROID_POST_QUEUE_BACKGROUND.md. Local debug/test assembly passes2m20.
Focused background green pending; uncertain automatic retries and actual process/
replacement acceptance remain incomplete.

Focused38006955485 at6603f2f passes3/3 application cases, including background
reconnection with original identity and verified availability on return. This
proves the controlled background flow, not immediate OS delivery or force-stop
execution. Failed-persistence truthfulness is next.

Actual failed-save red38007571494 at35287be passes3/4 cases. The failing
case retains text and shows the storage error, but incorrectly still displays
Queued (line174). It aborts before its no-transmission and repaired-retry
assertions, so those are not claimed passed in that run. A separate verified
queue-save flag now gates Queued, cancellation and scheduling. Local debug/test
assembly passes1m29; focused green next.
