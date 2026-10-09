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
