# First stalled photo preparation regression, Android 10

Run38722937990 exacta8361663cb10453680977640953e6fa8294726ef FAILED:
7cases6PASS1FAIL53.514s. The failure case never observes provider opened plus
Preparing images within20s at164, so edited text, actual timed-out pipe handling,
durable latest draft and reopening are unexecuted. Root original PNG seen/XML
parsed: a preparation error and original caption are visible. Actual provider
route/state was not logged, so no production timeout defect is inferred.

AOSP ContentProvider defaults for cancellable asset/typed-asset opening delegate
to legacy overloads. This fixture originally overrode only cancellable openFile.
The correction implements cancellable asset and typed-asset entry points too,
returning the same pipe with UNKNOWN_LENGTH and preserving the cancellation
signal. It also binds cancellation to its own writer generation and emits safe
provider status counters on any future failure. Production/acceptance criteria
are unchanged. The source-based explanation requires corrected runtime proof.
