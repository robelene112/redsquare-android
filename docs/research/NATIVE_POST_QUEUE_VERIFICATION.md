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

Focused38008231610 at497920d passes4/4 cases, including failed persistence
truthfulness, retained work, no transmission before saving and successful repaired
Retry. Uncertain queued-delivery reconciliation is the next slice.

Run38008923408 at7f288f7 passes4/5; the uncertainty case reaches availability
but fails peer-health checking because MockWebServer WebSocket.cancel() throws
NullPointerException instead of establishing the intended interruption. This is
a fixture failure, not a functional red. The fixture now sends a normal1001
close and ignores subsequent Archive requests only on that interrupted session.
Later connections can serve the accepted original transaction. Local assembly
passes55s; actual uncertainty red is still pending.

Actual uncertainty red38009504360 at92b643a times out waiting for automatic
availability after the correctly interrupted accepted transaction (line210).
Three other cases pass. The backend case completes transmission/identity checks
but then times out waiting for refreshed feed content (line102); it clicked
Refresh without requiring enabled state. No screenshot proves that failed button
state. A readiness wait and failure capture now preserve the same feed assertion.

Durable automatic-delivery intent now survives the Queued-to-uncertain claim.
The foreground and worker resumers include those original signed records, query
exact verified availability first and retry the same transaction if needed.
Scheduling marks automatic intent before enqueue; legacy/manual pending records
remain nonautomatic. Candidate state is preserved by composer saves. Local
debug/test assembly passes1m30; focused five-case green next.

Focused38010212716 at8257bfb passes4/5, including actual uncertain automatic
reconciliation without rebroadcast. The backend case passes transmission and
identity checks, then fails feed visibility even after enabled Refresh. Its
inspected image shows older cached posts at the retained list position; this
does not prove the target exists elsewhere. The fixture now searches the lazy
list through bounded scrolling and requires the target displayed, preserving
the actual feed assertion and failure capture. Local assembly passes54s.
Production unchanged; five-case focused retry next.

Focused38010789123 atb6ec690 passes5/5, including bounded scrolling to the
resumed post, original-backend/identity delivery, automatic uncertain reconciliation
without rebroadcast, background delivery, cancellation and failed-save repair.
Queue management outside the composer and actual process/wallet replacement
acceptance remain next.

Management red38011274167 at853747f passes5/6. Queued work persists and
returns to the feed, but Queued posts entry is absent (line245). A wallet-scoped
overview now lists pending work for both fixed backends, labels paused items and
permits cancellation only while the unchanged durable record is Queued. Cancelling
retains its unsent text; claimed uncertainty cannot be cancelled as unpublished.
Local debug/test assembly passes1m36. Focused management green pending.

Focused38011852811 at9ca2205 passes6/6 cases including feed management,
server/status disclosure, safe cancellation, retained unsent draft and no later
transmission. Actual process-death and wallet-replacement regressions are next.

Actual offline queue process regression added through separate instrumentation
phases. Phase one verifies visible durable Queue, captures only public wallet/
text/signing-time bounds and original PID, then kills the process. WorkManager
may restart it; the host requires the original PID gone, then holds recovery
with force-stop. A cold user interaction restores the same original loopback
endpoint and requires one verified transaction with the pre-kill signing time,
identity/text and visible feed result. No private material is captured. This
verifies pre-interruption operation recovery, not arbitrary power loss or
force-stop execution. Local test assembly52s, shell/Python syntax pass; actual
runtime regression next.
