# Incoming retention and shared read progress

Source: `cce5d423cab31164134785e81177a4d941e8ceec`.
The real binary transport regression in ec8299061 fails its original5s read
callback while incoming retention is held. It requires no ACK before retention.
The original log, XML and exact source/hash binding are retained here.

Type4/12 delivery now runs FIFO on one connection-owned worker. The socket reader
continues processing callbacks and heartbeats. Positive ACK still follows actual
retention, and can enqueue only while the originating connection is live. There
are at most32 waiting tasks and16MiB retained original bytes including active
work. Overload retires the connection without ACKing discarded work. Retirement
clears waiting tasks with once-only byte accounting and interrupts active work
without joining under a lock. Interruption is cooperative; arbitrary callbacks
are not claimed to have stopped merely because retirement was requested.

Both independent source/core reviews pass with zero findings. All229 core tests
in44 exact XML reports pass, zero failures/errors/skips. Five additive tests use
actual authenticated binary sockets: held-retention callback progress/no early
ACK, FIFO across features, last-owner retirement/replacement, queue-count pressure
and two independently valid9MiB frames isolating byte pressure. Original existing
transport tests and deadlines remain. Earlier225/228 green observations remain
intermediate; the229 result is the current complete core observation.

The independently reviewed bounded diagnostic source e44991e6d is absent from the
product. Calibration43961449941 passed with zero observation drops/errors. Its
owned server replied to a group read at27.227s, while the client callback was
observed at30.214s after earlier incoming work and a30.072s connection failure.
These events motivated the red transport seam; they do not establish the exact
cause of the earlier full10 failure. The longer unchanged-source Chat diagnostic
43958604824 remains separate. Neither diagnostic pass supplies acceptance.

Original native bodies/goals are unchanged. Fresh conversation-viewport10/17
runs43965648855/43965653546 finished:10failed the original15s incomingACK
and17passed. This focused set is rejected. See the subsequent exact pending
coalescing correction in `../incoming-coalescing/README.md`. Full SDK
qualification, signed updates and delivery remain pending. The old rejected parent and all earlier signed
candidates remain intermediate. See `reviews.json` and the parent packet.
