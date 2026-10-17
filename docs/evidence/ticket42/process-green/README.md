# Standalone process recovery: local acceptance

Exact corrected sourcea71e70f5, slice basecc332826, minimum base72c5c641.
Initial minimum build PASS44s. Independent source review found2 Standards/1 Spec
fixture findings: startup teardown could replace its primary error, and the
forced-stop observation needed positive pre-stop liveness. Correction preserves
primary/suppressed errors, attempts bounded forced cleanup on interruption and
asserts the child is alive immediately before destroyForcibly. Corrected source
reviews report Standards0/Spec0. Corrected build PASS33s, gateway3/0/0 including
all earlier HTTP journeys; core up-to-date114/0/0; installDist up-to-date after
its actual successful initial build. Final evidence reviews are pending.

The new journey starts the standalone command in an owned JVM with an ephemeral
loopback port, obtains a signed subscription acknowledgement, observes the child
alive, terminates it forcibly and waits for its exit. A separately started JVM
opens the same state directory. HTTP status retains owner/backend/expiry, then
keyless retirement succeeds and status becomes inactive. The startup readiness
RED remains [FAIL51s](../process-red/README.md); later new tails did not run there.
The read-before-kill observation and kill are separate OS operations; this is a
controlled process-termination check, not power-loss or hardware durability.

Six manifested files retain both actual build logs, two JUnit files, core index
and nine compiled-source hashes. No Android integration, Firebase delivery,
public deployment or whole#42 acceptance is claimed. Beta24 unchanged.
