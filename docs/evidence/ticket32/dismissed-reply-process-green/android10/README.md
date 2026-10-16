# Dismissed reply survives actual process death

Focused Android10 run [38827943929](https://github.com/robelene112/redsquare-android/actions/runs/38827943929)
at `cfb7c19816411940e430d946b3aa67798420e1ea` completed SUCCESS.
Core114 and debug/test assembly passed. This focused scope does not execute
release assembly, lint, the hot two-case class, full suites or Android17.

The first instrumentation visibly publishes the independently signed fixture reply,
physically swipes its system alert away, waits for visual and manager absence,
and maintains absence for two seconds before deliberately killing PID3447.
The host proves that PID is gone and observes a positive encrypted ledger size
of219bytes without copying its contents. Deliberate Process crashed is expected
here, not a passing JUnit result by itself.

Fresh PID3632 verifies unchanged full fixture-wallet digest and selected backend,
then suppresses the same reference with two seconds of continuously absent own
alerts. An independent signed reply still produces a visible reply alert, proving
receiver liveness. Recovery passes one test3.923s. Independent restoration passes
one test0.657s and checks the original full wallet digest and backend before
removing device-only snapshots/checkpoint. The final continuity marker is written
only after restoration succeeds. Both exercised peer journeys assert zero outgoing
transaction/report/Relay writes.

Root inspected all four original PNGs individually and parsed all four XMLs.
Initial and fresh-reply captures visibly contain Red Square/Replied to your post;
both dismissed captures contain only system notices. Images and hierarchy are
separate samples. SHA256SUMS covers retained raw public evidence, not this prose.
Private installation snapshots remain on the disposable device; no encrypted wallet
contents, production configuration, tokens or APKs are retained here.

This demonstrates local receipt durability after completed publication and physical
dismissal. Android notification publication and disk acknowledgement are not atomic.
Interruption inside that window, storage faults, indefinite silence, mentions,
exact taps, public FCM/closed-app delivery and physical-phone behavior remain
unproved. The new hot-class identity assertions were not executed in this scope.
No production entry point invokes this local receiver. Public beta24 stays current;
#32 remains open.
